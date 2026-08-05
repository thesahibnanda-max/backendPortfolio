# API Reference

Base URL (local): `http://localhost:8080`. No class-level path prefix --
every endpoint is at the exact path shown below.

All request/response bodies are JSON, `camelCase` field names. All
non-Gate endpoints (everything except `/signup`, `/login`, `/health`)
require an `X-Auth-Token` header -- obtained from the `X-Auth-Token`
**response header** of `/signup` or `/login` (the token is never returned
in a response body).

Every endpoint shares the same error-response shape:

```json
{
  "showMessageAsIs": true,
  "errorMessage": "human-readable message"
}
```

| Field | Type | Description |
|---|---|---|
| `showMessageAsIs` | boolean | `true` for client-caused errors (400/401/403/404/409/429) and for `/health`'s 503 -- `errorMessage` is the real, specific cause, safe to show a user as-is. `false` for 500/502 -- `errorMessage` is a generic, safe placeholder, not the real internal error. |
| `errorMessage` | string | The message itself -- specific when `showMessageAsIs` is `true`, generic otherwise. |

See [`ARCHITECTURE.md`](ARCHITECTURE.md#error-handling) for the full
exception-to-status-code table.

## Endpoints

- [`POST /signup`](#post-signup)
- [`POST /login`](#post-login)
- [`POST /chats`](#post-chats)
- [`GET /chats`](#get-chats)
- [`GET /chats/{chatId}`](#get-chatschatid)
- [`PATCH /chats/{chatId}`](#patch-chatschatid)
- [`POST /chats/{chatId}/messages`](#post-chatschatidmessages)
- [`POST /chats/search`](#post-chatssearch)
- [`GET /health`](#get-health)

---

### `POST /signup`

Registers a new user. **No auth required.**

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | yes | The username to register. Must not already exist. |
| `password` | string | yes | Plain-text password. Must meet the configured strength rules (checked server-side; sent over TLS in production, never stored or logged in plain text). |

**Example**

```bash
curl -i -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "Str0ng!Pass"}'
```

**Response `201 Created`**

```
X-Auth-Token: <encrypted token>
```
```json
{}
```

The response body is intentionally empty -- the auth token is delivered
**only** via the `X-Auth-Token` response header. Save it and send it back
as a request header on every subsequent call.

**Errors**

| Status | Cause |
|---|---|
| 400 | `username`/`password` blank, or the password fails the strength rules |
| 409 | A user with that username already exists |
| 429 | Rate limit exceeded (30 requests/60s, global and per-username) |
| 500 | The new user could not be persisted |

---

### `POST /login`

Authenticates an existing user. **No auth required.**

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | yes | The username to authenticate. |
| `password` | string | yes | The plain-text password to check against the stored hash. |

**Example**

```bash
curl -i -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "Str0ng!Pass"}'
```

**Response `200 OK`**

```
X-Auth-Token: <encrypted token>
```
```json
{}
```

Same empty-body-plus-header shape as `/signup`.

**Errors**

| Status | Cause |
|---|---|
| 400 | `username`/`password` blank |
| 404 | No user exists with that username |
| 401 | Password doesn't match |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The lookup failed |

---

### `POST /chats`

Creates a new chat for the authenticated user. **Auth required.**

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `chatTitle` | string | yes | Display title for the new chat. |

**Example**

```bash
curl -X POST http://localhost:8080/chats \
  -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"chatTitle": "Trip to Kyoto"}'
```

**Response `201 Created`**

```json
{
  "chats": [
    {
      "chatId": "01J9Z8QK3F7N2Y6XABCD1234EF",
      "username": "alice",
      "chatTitle": "Trip to Kyoto",
      "messages": [],
      "createdAt": "2026-08-05T10:00:00",
      "updatedAt": "2026-08-05T10:00:00"
    }
  ]
}
```

`chats` is **every** chat owned by the user (newest first), including the
one just created -- not just the new chat by itself.

| Field | Type | Description |
|---|---|---|
| `chats[].chatId` | string | Unique chat identifier (ULID) |
| `chats[].username` | string | The chat's owner |
| `chats[].chatTitle` | string | Display title |
| `chats[].messages` | array | Ordered messages in the chat (empty for a brand-new chat) |
| `chats[].createdAt` | string (ISO-8601, no timezone) | When the chat was created |
| `chats[].updatedAt` | string (ISO-8601, no timezone) | When the chat was last modified |

**Errors**

| Status | Cause |
|---|---|
| 400 | `chatTitle` blank |
| 401 | Missing/invalid `X-Auth-Token` |
| 404 | The authenticated user no longer exists |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The chat could not be persisted |

---

### `GET /chats`

Lists every chat owned by the authenticated user. **Auth required.** No
request body.

**Example**

```bash
curl http://localhost:8080/chats -H "X-Auth-Token: <token>"
```

**Response `200 OK`**

Same shape as `POST /chats`'s response -- `{"chats": [...]}`, newest
first, every field as documented above.

**Errors**

| Status | Cause |
|---|---|
| 401 | Missing/invalid `X-Auth-Token` |
| 404 | The authenticated user no longer exists |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The lookup failed |

---

### `GET /chats/{chatId}`

Fetches a single chat owned by the authenticated user. **Auth required.**
No request body.

**Path parameters**

| Field | Type | Description |
|---|---|---|
| `chatId` | string | The chat's identifier |

**Example**

```bash
curl http://localhost:8080/chats/01J9Z8QK3F7N2Y6XABCD1234EF \
  -H "X-Auth-Token: <token>"
```

**Response `200 OK`**

```json
{
  "chat": {
    "chatId": "01J9Z8QK3F7N2Y6XABCD1234EF",
    "username": "alice",
    "chatTitle": "Trip to Kyoto",
    "messages": [
      {"role": "USER", "message": "What's the best time to visit?", "timestamp": "2026-08-05T10:05:00Z"},
      {"role": "ASSISTANT", "message": "Spring (March-May) for cherry blossoms...", "timestamp": "2026-08-05T10:05:03Z"}
    ],
    "createdAt": "2026-08-05T10:00:00",
    "updatedAt": "2026-08-05T10:05:03"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `chat.messages[].role` | string | `USER` or `ASSISTANT` |
| `chat.messages[].message` | string | The message text |
| `chat.messages[].timestamp` | string (ISO-8601 instant, with `Z`) | When the message was sent |

**Errors**

| Status | Cause |
|---|---|
| 401 | Missing/invalid `X-Auth-Token` |
| 404 | No chat exists with that id |
| 403 | The chat belongs to a different user |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The lookup failed |

---

### `PATCH /chats/{chatId}`

Renames a chat owned by the authenticated user. **Auth required.**

**Path parameters**

| Field | Type | Description |
|---|---|---|
| `chatId` | string | The chat's identifier |

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `chatTitle` | string | yes | The new title. |

**Example**

```bash
curl -X PATCH http://localhost:8080/chats/01J9Z8QK3F7N2Y6XABCD1234EF \
  -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"chatTitle": "Kyoto Trip 2026"}'
```

**Response `200 OK`**

Same shape as `GET /chats/{chatId}` -- `{"chat": {...}}` with the new
`chatTitle` and updated `updatedAt`.

**Errors**

| Status | Cause |
|---|---|
| 400 | `chatTitle` blank |
| 401 | Missing/invalid `X-Auth-Token` |
| 404 | No chat exists with that id |
| 403 | The chat belongs to a different user |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The update failed |

---

### `POST /chats/{chatId}/messages`

Sends a message and gets an AI-generated reply, persisting both. **Auth
required.**

**Path parameters**

| Field | Type | Description |
|---|---|---|
| `chatId` | string | The chat to send the message to |

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `message` | string | yes | The user's message text. |

**Example**

```bash
curl -X POST http://localhost:8080/chats/01J9Z8QK3F7N2Y6XABCD1234EF/messages \
  -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"message": "What programming languages does Sahib know?"}'
```

**Response `200 OK`**

Same shape as `GET /chats/{chatId}` -- `{"chat": {...}}` -- but `messages`
now includes both the message you just sent **and** the newly generated
assistant reply, appended in order. Internally this call routes the
question through the Orchestrator AI (picks which knowledge domains are
needed) and the Worker AI (answers using only that context) -- see
[`ARCHITECTURE.md`](ARCHITECTURE.md#the-chat-answering-pipeline).

**Errors**

| Status | Cause |
|---|---|
| 400 | `message` blank |
| 401 | Missing/invalid `X-Auth-Token` |
| 404 | No chat exists with that id |
| 403 | The chat belongs to a different user |
| 429 | Rate limit exceeded (**5 requests/60s** -- far lower than every other endpoint, since this is the only one that calls the LLM) |
| 502 | The Groq API call failed |
| 500 | Any other internal failure (e.g. the LLM's response couldn't be parsed as valid JSON, or the message couldn't be persisted) |

---

### `POST /chats/search`

Full-text searches the authenticated user's own chats. **Auth required.**

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `query` | string | yes | The search text. |

**Example**

```bash
curl -X POST http://localhost:8080/chats/search \
  -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"query": "kyoto travel itinerary"}'
```

**Response `200 OK`**

```json
{
  "chats": [
    {
      "chatId": "01J9Z8QK3F7N2Y6XABCD1234EF",
      "username": "alice",
      "chatTitle": "Trip to Kyoto",
      "messages": [ "..." ],
      "createdAt": "2026-08-05T10:00:00",
      "updatedAt": "2026-08-05T10:05:03"
    }
  ],
  "scores": {
    "01J9Z8QK3F7N2Y6XABCD1234EF": 12.4
  }
}
```

| Field | Type | Description |
|---|---|---|
| `chats` | array of `ChatObject` | Matching chats, **highest-scoring first**, at most 20. Each appears once, even if multiple of its messages matched. |
| `scores` | object | Every returned chat's relevance score, keyed by `chatId`. Higher is more relevant -- an exact-phrase match in the title scores highest, a typo-tolerant partial match in a message scores lowest. |

See [`ARCHITECTURE.md`](ARCHITECTURE.md#the-chat-search-pipeline) for how
ranking and scoping to the caller's own chats work.

**Errors**

| Status | Cause |
|---|---|
| 400 | `query` blank |
| 401 | Missing/invalid `X-Auth-Token` |
| 429 | Rate limit exceeded (30/60s) |
| 500 | The OpenSearch query failed |

---

### `GET /health`

Checks connectivity to every infrastructure dependency (Postgres, Valkey,
Kafka, OpenSearch). **No auth required.** No request body.

**Example**

```bash
curl -i http://localhost:8080/health
```

**Response `200 OK`**

```json
{}
```

An empty body confirms every dependency is reachable.

**Errors**

| Status | Cause |
|---|---|
| 503 | One or more dependencies failed to respond; `errorMessage` names which ones (e.g. `"Health check failed for: postgres, kafka"`) |
