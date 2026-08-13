# Auth endpoints — `/auth/**`

---

## `POST /auth/register`

Creates a new user account. Assigns the `EDITOR` role by default (docs §5). Password is hashed with BCrypt before storage — never logged or persisted in plaintext.

**Request**
```json
{
  "email": "user@example.com",
  "password": "at-least-8-characters",
  "name": "Jane Doe"
}
```

**Response — `201 Created`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 409 | `CONFLICT` | Email already registered (`EmailAlreadyExistsException`) |
| 422 | `VALIDATION_ERROR` | Missing/invalid email, password too short, blank name |

---

## `POST /auth/login`

Authenticates with email + password, returns a JWT.

**Request**
```json
{
  "email": "user@example.com",
  "password": "at-least-8-characters"
}
```

**Response — `200 OK`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Wrong password, unknown email, OR disabled account — deliberately generic (see architecture docs §5, "Auth error responses" deferred decision: avoids user enumeration on a public endpoint with no rate limiting yet) |

---

## Using the token

Every `/cms/**` request needs:
```
Authorization: Bearer <accessToken>
```

Token lifetime is controlled by `jwt.expiration` (application properties) — currently 24h. No refresh token endpoint yet (deferred, see architecture docs §14 — needs a blocklist vs short-lived-tokens strategy decision first).

The token itself carries `userId` and `roles` as claims (not just email as subject) — this is an implementation detail (avoids a DB round-trip per authenticated request, see architecture docs §14 "JWT claims"), not part of the API contract callers should parse. Roles reflect the state at login/register time; there's no way to change roles after registration yet (issue #32), so this has no practical staleness window today.
