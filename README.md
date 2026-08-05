# Backend Portfolio

An AI-powered portfolio chat backend. Users sign up, create chats, and ask
questions; an **Orchestrator AI** decides which personal-knowledge domains
(resume, GitHub, LeetCode, Codeforces, personality) a question actually
needs, and a **Worker AI** answers using only that context. Chats are
persisted in Postgres, their lifecycle is published to Kafka, and a
consumer indexes them into OpenSearch so users can full-text search their
own chat history. Valkey backs a two-tier cache and rate limiting; Loki
receives logs.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for how it all fits together and
[`API.md`](API.md) for every HTTP endpoint with sample `curl` requests.

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 25, virtual threads enabled |
| Framework | Spring Boot 4.0.0 |
| Database | PostgreSQL, accessed via jOOQ (typesafe SQL, no ORM) |
| Search | OpenSearch (official Java client 3.2.0) |
| Messaging | Apache Kafka (chat/user lifecycle events) |
| Cache / rate limiting | Valkey (Redis-compatible, via `valkey-glide`) |
| LLM provider | Groq (`GroqClient`), prompts rendered with JTE templates |
| Logging | Logback + Loki appender |
| Build | Maven |
| Tests | JUnit 5 (parallel), Testcontainers (Postgres), AssertJ, MockMvc |

## Prerequisites

- JDK 25
- Maven 3.9+
- Docker (no `docker-compose.yml` is provided -- run the four
  infrastructure containers individually, as below)

## Running the required infrastructure

These commands use the exact host/port/credential defaults already baked
into `application.yml`, so the app runs with **zero environment
variables set** once they're up. (These are only needed to run the app
itself or build/run its Docker image -- `mvn test`/`mvn clean test`
provisions its own containers automatically via Testcontainers; see
["Running the tests"](#running-the-tests).)

```bash
# PostgreSQL
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=root -e POSTGRES_DB=defaultdb \
  postgres:16-alpine

# Kafka (KRaft mode, no separate ZooKeeper container needed)
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  apache/kafka:3.9.0

# OpenSearch (security plugin disabled for local dev -- the app's default
# admin/admin credentials are sent but ignored, since there's no auth layer)
docker run -d --name opensearch -p 9200:9200 -p 9600:9600 \
  -e discovery.type=single-node \
  -e DISABLE_SECURITY_PLUGIN=true \
  opensearchproject/opensearch:latest

# Valkey
docker run -d --name valkey -p 6379:6379 valkey/valkey:latest
```

## Environment variables

All of these are optional -- every one has a default in `application.yml`
suitable for local development. Set them via `prod.env` (or your own
`.env`) for a real deployment.

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | Postgres host |
| `DB_PORT` | `5432` | Postgres port |
| `DB_NAME` | `defaultdb` | Postgres database name |
| `DB_SSL_MODE` | `false` | Whether the JDBC connection requires SSL |
| `DB_USERNAME` | `postgres` | Postgres username |
| `DB_PASSWORD` | `root` | Postgres password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_CONSUMER_GROUP_ID` | `backend-portfolio` | Base Kafka consumer group id |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | Kafka security protocol -- `PLAINTEXT` for the local dev broker, `SASL_SSL` (or `SASL_PLAINTEXT`) in prod |
| `KAFKA_SASL_MECHANISM` | *(none)* | SASL mechanism, e.g. `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512` -- required when `KAFKA_SECURITY_PROTOCOL` is a SASL variant |
| `KAFKA_SASL_JAAS_CONFIG` | *(none)* | Full JAAS config string from your Kafka provider, e.g. `org.apache.kafka.common.security.plain.PlainLoginModule required username="..." password="...";` -- required when `KAFKA_SECURITY_PROTOCOL` is a SASL variant |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` | *(none)* | Filesystem path to the CA cert your Kafka provider gave you -- only needed if that CA isn't publicly trusted. Drop the file at the project root (e.g. `ca.pem`, gitignored) and point this at that same relative path; it works unchanged whether run via `mvn spring-boot:run` or `java -jar`, since Kafka reads it as a raw file, not a classpath resource |
| `KAFKA_SSL_TRUSTSTORE_TYPE` | *(none)* | Truststore format, e.g. `PEM` -- required alongside `KAFKA_SSL_TRUSTSTORE_LOCATION` |
| `OPENSEARCH_HOST` | `localhost` | OpenSearch host |
| `OPENSEARCH_PORT` | `9200` | OpenSearch port |
| `OPENSEARCH_USERNAME` | `admin` | OpenSearch username |
| `OPENSEARCH_PASSWORD` | `admin` | OpenSearch password |
| `OPENSEARCH_HTTPS` | `false` | Whether to connect to OpenSearch over HTTPS |
| `VALKEY_HOST` | `localhost` | Valkey host |
| `VALKEY_PORT` | `6379` | Valkey port |
| `VALKEY_USERNAME` | *(none)* | Valkey username, if auth is enabled |
| `VALKEY_PASSWORD` | *(none)* | Valkey password, if auth is enabled |
| `VALKEY_USE_TLS` | `false` | Whether to connect to Valkey over TLS -- most managed Valkey/Redis providers (e.g. Aiven) require this |
| `LOKI_URL` | `http://localhost:3100` | Loki push endpoint for logs |
| `LOKI_USERNAME` | *(none)* | Loki basic-auth username |
| `LOKI_PASSWORD` | *(none)* | Loki basic-auth password |
| `AUTH_SECRET_KEY` | `local-dev-only-change-me` | Key used to encrypt/decrypt the `X-Auth-Token` |
| `GROQ_API_KEYS` | sample placeholder keys | Comma-separated Groq API keys (needed for real LLM calls) |
| `GROQ_BASE_URL` | `https://api.groq.com` | Groq API base URL |
| `LEETCODE_BASE_URL` | `https://leetcode.com` | LeetCode base URL |
| `CODEFORCES_BASE_URL` | `https://codeforces.com` | Codeforces base URL |
| `GITHUB_BASE_URL` | `https://api.github.com` | GitHub API base URL |
| `PROFILE_JSON_RETREIVAL_URL` | Supabase storage URL | Where the resume/profile JSON is fetched from |
| `PERSONALITY_JSON_RETREIVAL_URL` | Supabase storage URL | Where the personality JSON is fetched from |
| `DETAILS_LEETCODE_USERNAMES` | `imsahibnanda` | LeetCode username(s) `DetailsService` fetches profile data for -- feeds the `LEETCODE` context domain the Orchestrator/Worker AI can draw on |
| `DETAILS_CODEFORCES_USERNAMES` | `shisukenohara` | Codeforces username(s) -- feeds the `CODEFORCES` context domain |
| `DETAILS_GITHUB_USERNAMES` | `thesahibnanda-max,thesahibnanda` | GitHub username(s) -- feeds the `GITHUB` context domain |
| `RESUME_LINK` | Supabase storage URL | URL of the resume returned by `GET /details/professional` |
| `DETAILS_PROFILE_PHOTO_LINKS` | Supabase storage URLs | Comma-separated profile photo URL(s) returned by `GET /details/professional` |
| `DETAILS_LEETCODE_PROFILE_URL_FORMAT` | `%s/u/%s/` | `String.format` pattern (base URL, username) used to build each public LeetCode profile link -- the LeetCode API has no self-referential profile URL to read instead |
| `DETAILS_CODEFORCES_PROFILE_URL_FORMAT` | `%s/profile/%s` | `String.format` pattern (base URL, handle) used to build each public Codeforces profile link, same reasoning as above |

## Build & run

```bash
# Build (compiles, and generates jOOQ sources from schema.sql -- no live
# database needed for this step, schema.sql is read directly)
mvn clean install

# Run
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

Spotless and Checkstyle are **not** bound to `mvn clean install` -- run
them explicitly:

```bash
mvn spotless:apply          # auto-format
mvn spotless:check          # verify formatting, no changes
mvn checkstyle:check        # verify style (Sun ruleset: 80-column lines,
                             # Javadoc required on every src/main member)
```

### Docker

```bash
docker build -t backend-portfolio .

# --env-file accepts any file name -- use whichever environment's vars
# you want (.env, stage.env, prod.env, ...); all *.env / .env* names are
# gitignored, so none of these ever get committed.
docker run -p 8080:8080 --env-file .env backend-portfolio

# e.g. for a staging deploy:
docker run -p 8080:8080 --env-file stage.env backend-portfolio
```

If you're pointing the container at the infra containers from
["Running the required infrastructure"](#running-the-required-infrastructure)
above, `localhost` in your env file won't work -- inside the app's own
container, `localhost` means the container itself, not your host machine.
Use `host.docker.internal` instead (Docker Desktop resolves this to the
host automatically), e.g. `DB_HOST=host.docker.internal`,
`VALKEY_HOST=host.docker.internal`,
`KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092`,
`OPENSEARCH_HOST=host.docker.internal`.

## Running the tests

```bash
mvn clean test
```

Tests are fully self-contained via [Testcontainers](https://testcontainers.com/):
Postgres, Kafka, Valkey, and OpenSearch are each started automatically in
disposable Docker containers for the duration of the test run, so **none
of the manual `docker run` commands under
["Running the required infrastructure"](#running-the-required-infrastructure)
are needed to run the test suite** -- those are only needed to run the app
itself (`mvn spring-boot:run`) or to build/run the Docker image. A running
Docker daemon is still required (Testcontainers needs it to start the
containers), but nothing else. Tests use real Postgres/Kafka/OpenSearch/Valkey
against these ephemeral containers, not mocks. Tests run in parallel
(`src/test/resources/junit-platform.properties`) and take roughly
90-100 seconds end to end (plus a one-time image pull the first time you
run them).

## Project structure

| Package | Purpose |
|---|---|
| `controller` | HTTP entry points exposing every `Core` operation |
| `core` | Wires the Gate, Orchestrator, and Worker services together |
| `services` | Application services composing repositories and clients |
| `repository` (+ `jooq`, `init`, `observers`) | Persistence contracts, jOOQ implementations, idempotent schema init, change-notification observers |
| `client` | HTTP clients for external APIs (Groq, LeetCode, Codeforces, GitHub, Profile) |
| `api` | Thin wrappers around external client SDKs (OpenSearch) |
| `queue` | Kafka producer/consumer wiring |
| `cache` | In-process caching support |
| `config` | Spring `@ConfigurationProperties` records |
| `pojo` | Request/response envelopes exchanged with API consumers |
| `objects` | Reader-friendly DTOs composed from raw API responses/entities |
| `dto` | Data transfer objects published to external systems (Kafka payloads) |
| `models` | Request/response DTOs for external APIs |
| `entity` | Domain entities persisted by the repository layer |
| `enums`, `exception`, `options`, `jackson`, `templates`, `utils` | Enumerated constants, unchecked exceptions, per-call option objects, custom Jackson deserializers, JTE prompt templates, stateless utilities |

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for how these packages compose
into the two main request flows (answering a chat message, searching
chats), and [`API.md`](API.md) for the full HTTP API reference.

## License

BSD 3-Clause -- see [`LICENSE`](LICENSE).
