#!/usr/bin/env bash
# Blue-green redeploy on the Oracle VM. Invoked by .github/workflows/deploy.yml
# over SSH after a new image has been pushed to GHCR. Never invoked with an
# untrusted image tag -- the caller (CI) always passes the exact tag it just
# built and pushed.
set -euo pipefail

IMAGE_TAG="$1"

BASE=/opt/backend-portfolio
STATE_DIR="$BASE/state"
STATE_FILE="$STATE_DIR/live_color"
SNIPPET=/etc/caddy/snippets/backend-portfolio.caddy

BLUE_PORT=8081
GREEN_PORT=8082

mkdir -p "$STATE_DIR"
[ -f "$STATE_FILE" ] || echo blue > "$STATE_FILE"
CURRENT=$(cat "$STATE_FILE")

if [ "$CURRENT" = blue ]; then
  TARGET=green
  TARGET_PORT=$GREEN_PORT
else
  TARGET=blue
  TARGET_PORT=$BLUE_PORT
fi

echo "Current live color: $CURRENT. Deploying $IMAGE_TAG to $TARGET (port $TARGET_PORT)."

docker pull "$IMAGE_TAG"

# Whatever was left in this slot from an older deploy is disposable.
docker rm -f "backend-$TARGET" >/dev/null 2>&1 || true

docker run -d \
  --name "backend-$TARGET" \
  --restart unless-stopped \
  -p "127.0.0.1:${TARGET_PORT}:8080" \
  --env-file "$BASE/prod.env" \
  -v "$BASE/prod_kafka_ca.pem:/app/prod_kafka_ca.pem:ro" \
  "$IMAGE_TAG"

HEALTHY=""
for _ in $(seq 1 30); do
  if curl -fsS --max-time 3 "http://127.0.0.1:${TARGET_PORT}/health" >/dev/null 2>&1; then
    HEALTHY=1
    break
  fi
  sleep 2
done

if [ -z "$HEALTHY" ]; then
  echo "Health check failed for backend-$TARGET; aborting. $CURRENT stays live." >&2
  docker logs --tail 50 "backend-$TARGET" || true
  docker stop "backend-$TARGET" >/dev/null 2>&1 || true
  exit 1
fi

echo "backend-$TARGET is healthy. Flipping Caddy upstream."

echo "reverse_proxy 127.0.0.1:${TARGET_PORT}" > "${SNIPPET}.tmp"
sudo mv "${SNIPPET}.tmp" "$SNIPPET"
sudo systemctl reload caddy

echo "$TARGET" > "${STATE_FILE}.tmp"
mv "${STATE_FILE}.tmp" "$STATE_FILE"

# Old color stays stopped (not removed) as an instant rollback target;
# it's cleaned up the next time this slot is reused, above.
docker stop "backend-$CURRENT" >/dev/null 2>&1 || true

echo "Deploy complete. Live color: $TARGET ($IMAGE_TAG)."
