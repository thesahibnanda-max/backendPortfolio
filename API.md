# API Reference

Base URL (local): `http://localhost:8080`. No class-level path prefix --
every endpoint is at the exact path shown below.

All request/response bodies are JSON, `camelCase` field names. All
non-Gate endpoints (everything except `/signup`, `/login`, `/health`, and
the six `/details/*` endpoints) require an `X-Auth-Token` header --
obtained from the `X-Auth-Token` **response header** of `/signup` or
`/login` (the token is never returned in a response body).

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
- [`GET /details/professional`](#get-detailsprofessional)
- [`GET /details/leetcode`](#get-detailsleetcode)
- [`GET /details/codeforces`](#get-detailscodeforces)
- [`GET /details/github`](#get-detailsgithub)
- [`GET /details/personality`](#get-detailspersonality)
- [`GET /details/profile`](#get-detailsprofile)

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

---

### `GET /details/professional`

Returns the portfolio owner's professional links: a public profile link
for every configured LeetCode/Codeforces/GitHub account, the resume link,
the profile photo link(s), and the personal websites/Twitter recorded on
the primary LeetCode account. **No auth required.** No request body.
Rate-limited globally (not per-user, since there's no authenticated
caller) at 30 requests/60s.

**Example**

```bash
curl -i http://localhost:8080/details/professional
```

**Response `200 OK`**

```json
{
  "professionalDetails": {
    "leetcodeLinks": ["https://leetcode.com/u/imsahibnanda/"],
    "codeforcesLink": ["https://codeforces.com/profile/shisukenohara"],
    "githubLinks": [
      "https://github.com/thesahibnanda-max",
      "https://github.com/thesahibnanda"
    ],
    "resumeLink": "https://.../Sahib_Nanda_Resume.pdf",
    "profilePhotoLink": [
      "https://.../ProPic1.png",
      "https://.../ProPic2.png"
    ],
    "websites": ["https://portfolio-sahib-nanda.vercel.app/"],
    "twitterUrl": "https://twitter.com/..."
  }
}
```

| Field | Type | Description |
|---|---|---|
| `leetcodeLinks` | array of string | One public LeetCode profile URL per configured username, built locally from configuration -- the LeetCode API has no self-referential profile URL. |
| `codeforcesLink` | array of string | One public Codeforces profile URL per configured handle, built locally the same way. |
| `githubLinks` | array of string | One public GitHub profile URL per configured username, taken directly from the GitHub API's own response (the one field of the three that's API-provided rather than constructed). |
| `resumeLink` | string | URL of the portfolio owner's resume. |
| `profilePhotoLink` | array of string | URL(s) of the portfolio owner's profile photo(s). |
| `websites` | array of string | Personal websites, as recorded on the primary LeetCode account. Omitted if unset (see `spring.jackson.default-property-inclusion` at the top of `application.yml`). |
| `twitterUrl` | string | Twitter profile URL, as recorded on the primary LeetCode account. Omitted if unset. |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The LeetCode or GitHub API call failed |

---

### `GET /details/leetcode`

Returns competitive-programming stats for every configured LeetCode
account. **No auth required.** No request body. Rate-limited globally at
30 requests/60s.

Many fields below are genuinely nullable, not just theoretically --
LeetCode doesn't return contest/ranking data for an account that's never
entered a contest, for example. A `null` field is omitted from the JSON
entirely (`spring.jackson.default-property-inclusion: non_null`), so
treat every field as optional -- don't assume presence.

**Example**

```bash
curl -i http://localhost:8080/details/leetcode
```

**Response `200 OK`**

```json
{
  "leetcodeDetails": [
    {
      "username": "imsahibnanda",
      "ranking": 48213,
      "reputation": 12,
      "totalSolved": 640,
      "easySolved": 220,
      "mediumSolved": 320,
      "hardSolved": 100,
      "badges": ["100 Days Badge 2026"],
      "languageProblemsSolved": {"Java": 420, "Python": 220},
      "advancedTagsSolved": {"Dynamic Programming": 45},
      "intermediateTagsSolved": {"Binary Search": 60},
      "fundamentalTagsSolved": {"Array": 300},
      "contestRating": 1743.5,
      "contestGlobalRanking": 128340,
      "currentStreak": 12,
      "totalActiveDays": 410
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `leetcodeDetails[].username` | string | Always present. |
| `leetcodeDetails[].ranking` | integer or omitted | Global LeetCode ranking. Omitted if LeetCode has none on file. |
| `leetcodeDetails[].reputation` | integer or omitted | Reputation score. Omitted if unavailable. |
| `leetcodeDetails[].totalSolved` / `easySolved` / `mediumSolved` / `hardSolved` | integer or omitted | Problems solved by difficulty. Any of these can be individually omitted if that difficulty bucket isn't in the account's data. |
| `leetcodeDetails[].badges` | array of string | Always present, possibly empty. |
| `leetcodeDetails[].languageProblemsSolved` | object | Language name -> count. Always present, possibly empty. |
| `leetcodeDetails[].advancedTagsSolved` / `intermediateTagsSolved` / `fundamentalTagsSolved` | object | Topic tag -> count, by difficulty tier. Always present, possibly empty. |
| `leetcodeDetails[].contestRating` | number or omitted | Omitted entirely if the account has never entered a rated contest. |
| `leetcodeDetails[].contestGlobalRanking` | integer or omitted | Omitted alongside `contestRating` for the same reason. |
| `leetcodeDetails[].currentStreak` | integer or omitted | Current daily submission streak. Omitted if no calendar data is available. |
| `leetcodeDetails[].totalActiveDays` | integer or omitted | Omitted alongside `currentStreak` for the same reason. |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The LeetCode API call failed |

---

### `GET /details/codeforces`

Returns competitive-programming stats for every configured Codeforces
account. **No auth required.** No request body. Rate-limited globally at
30 requests/60s.

**Example**

```bash
curl -i http://localhost:8080/details/codeforces
```

**Response `200 OK`**

```json
{
  "codeforcesDetails": [
    {
      "handle": "shisukenohara",
      "currentRating": 1743,
      "maxRating": 1810,
      "contestsCount": 23,
      "ratingHistory": [
        {
          "contestName": "Codeforces Round 1000",
          "rank": 1204,
          "oldRating": 1700,
          "newRating": 1743,
          "contestTime": "2026-07-01T18:35:00Z"
        }
      ]
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `codeforcesDetails[].handle` | string | Always present. |
| `codeforcesDetails[].currentRating` | integer or omitted | Rating after the most recent rated contest. **Omitted entirely for a handle with zero rated contests** -- this is a real, reachable state, not an edge case. |
| `codeforcesDetails[].maxRating` | integer or omitted | Omitted alongside `currentRating` for the same reason. |
| `codeforcesDetails[].contestsCount` | integer | Always present -- `0` for a handle with no rated contests. |
| `codeforcesDetails[].ratingHistory` | array | Always present, empty for a handle with no rated contests. Each entry has `contestName` (string), `rank` (integer), `oldRating`/`newRating` (integer), `contestTime` (ISO-8601 instant, with `Z`). |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The Codeforces API call failed |

---

### `GET /details/github`

Returns profile and repository stats for every configured GitHub
account. **No auth required.** No request body. Rate-limited globally at
30 requests/60s.

**Example**

```bash
curl -i http://localhost:8080/details/github
```

**Response `200 OK`**

```json
{
  "githubDetails": [
    {
      "username": "thesahibnanda-max",
      "name": "Sahib Nanda",
      "avatarUrl": "https://avatars.githubusercontent.com/u/...",
      "bio": "Backend engineer",
      "publicRepos": 42,
      "followers": 128,
      "following": 30,
      "htmlUrl": "https://github.com/thesahibnanda-max",
      "repositories": [
        {
          "name": "backendPortfolio",
          "description": "AI-powered portfolio chat backend",
          "htmlUrl": "https://github.com/thesahibnanda-max/backendPortfolio",
          "language": "Java",
          "stars": 4,
          "forks": 1,
          "updatedAt": "2026-08-05T12:00:00Z"
        }
      ]
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `githubDetails[].username` | string | Always present. |
| `githubDetails[].name` | string or omitted | GitHub display name. Omitted if the account hasn't set one -- a real, common state, not an edge case. |
| `githubDetails[].avatarUrl` / `htmlUrl` | string | Always present. |
| `githubDetails[].bio` | string or omitted | Omitted if the account has no bio set. |
| `githubDetails[].publicRepos` / `followers` / `following` | integer | Always present (`0` if genuinely zero). |
| `githubDetails[].repositories` | array | Always present, possibly empty (first page only). Each entry: `name`/`htmlUrl` (string, always present), `description` (string or omitted -- many repos have none), `language` (string or omitted -- a repo with no detected primary language), `stars`/`forks` (integer, always present), `updatedAt` (ISO-8601 instant). |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The GitHub API call failed |

---

### `GET /details/personality`

Returns the portfolio owner's personality profile -- interests,
appearance, lifestyle, and a self-written biography. **No auth
required.** No request body. Rate-limited globally at 30 requests/60s.

**Example**

```bash
curl -i http://localhost:8080/details/personality
```

**Response `200 OK`**

```json
{
  "personalityDetails": {
    "personalProfile": {
      "basicInfo": {"...": "..."},
      "physicalAppearance": {"...": "..."},
      "personality": {"...": "..."},
      "interests": {"...": "..."},
      "favorites": {"...": "..."},
      "lifestyle": {"...": "..."},
      "languages": {"...": "..."}
    },
    "aboutMe": "I'm a backend engineer who..."
  }
}
```

| Field | Type | Description |
|---|---|---|
| `personalityDetails.personalProfile` | object or omitted | The hosted personality JSON's full contents (appearance, personality, interests, favorites, lifestyle, languages). Omitted if the hosted document is unavailable. |
| `personalityDetails.aboutMe` | string or omitted | Self-written biography, as recorded on the primary LeetCode account. Omitted if unset there. |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The LeetCode or profile-JSON call failed |

---

### `GET /details/profile`

Returns the portfolio owner's resume-equivalent profile: experience,
education, projects, skills, and achievements. **No auth required.** No
request body. Rate-limited globally at 30 requests/60s.

**Example**

```bash
curl -i http://localhost:8080/details/profile
```

**Response `200 OK`**

```json
{
  "profileDetails": {
    "profileDetails": {"name": "Sahib Nanda", "email": "..."},
    "projects": [{"...": "..."}],
    "languages": ["English", "Hindi"],
    "achievements": ["..."],
    "experience": [{"...": "..."}],
    "education": [{"...": "..."}],
    "skillsByCategory": {"Backend": ["Java", "Spring Boot"]},
    "leetcodeUsernames": ["imsahibnanda"],
    "codeforcesUsernames": ["shisukenohara"],
    "githubUsernames": ["thesahibnanda-max", "thesahibnanda"],
    "linkedinUrl": "https://linkedin.com/in/...",
    "twitterUrl": "https://twitter.com/...",
    "websites": ["https://portfolio-sahib-nanda.vercel.app/"],
    "countryName": "India"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `profileDetails.profileDetails` | object or omitted | Basic identity (`name`, `email`). Omitted if the hosted profile JSON is missing this key. |
| `profileDetails.projects` / `experience` / `education` | array or omitted | Sourced directly from the hosted profile JSON -- omitted if that document doesn't define the key, not guaranteed to be an empty array. |
| `profileDetails.languages` / `achievements` | array or omitted | Same as above. |
| `profileDetails.skillsByCategory` | object or omitted | Category name -> list of skills. Omitted if undefined in the hosted document. |
| `profileDetails.leetcodeUsernames` / `codeforcesUsernames` / `githubUsernames` | array of string | Always present -- sourced from this app's own configuration, not the hosted document. |
| `profileDetails.linkedinUrl` / `twitterUrl` / `websites` / `countryName` | string / array / string, or omitted | As recorded on the primary LeetCode account. Omitted if unset there, exactly like `ProfessionalDetails.websites`/`twitterUrl`. |

**Errors**

| Status | Cause |
|---|---|
| 429 | Rate limit exceeded (30/60s, global) |
| 502 | The LeetCode or profile-JSON call failed |
