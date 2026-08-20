# CMS Sites endpoints — `/cms/sites/**`

All endpoints require `Authorization: Bearer {token}`. Every operation is scoped to the
authenticated user's own sites — accessing another user's site returns `403 Forbidden`
(ownership is validated in the use case, not inferred from authentication alone).

---

## `POST /cms/sites`

Creates a new site owned by the authenticated user.

**Request**
```json
{
  "title": "Santiago Acevedo — Portfolio",
  "summary": "Backend Developer Portfolio",
  "contentSchema": "portfolio-v1"
}
```
`title` is required. `summary` and `contentSchema` are optional.

**Response — `201 Created`**
```json
{
  "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "title": "Santiago Acevedo — Portfolio",
  "summary": "Backend Developer Portfolio",
  "contentSchema": "portfolio-v1",
  "published": false,
  "createdAt": "2026-08-15T10:00:00Z",
  "updatedAt": "2026-08-15T10:00:00Z"
}
```
A freshly created site is always `published: false` — publishing is a separate action
(`POST /cms/sites/{id}/publish`, not yet implemented).

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 422 | `VALIDATION_ERROR` | Missing `title`, or any field exceeds its max length (title 150, summary 255, contentSchema 100) |

---

## `GET /cms/sites`

Lists every site owned by the authenticated user, including publication state — computed
via a single query with a `LEFT JOIN` against `site_published` (no per-site follow-up query).

**Response — `200 OK`**
```json
[
  {
    "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
    "title": "Santiago Acevedo — Portfolio",
    "summary": "Backend Developer Portfolio",
    "contentSchema": "portfolio-v1",
    "published": true,
    "createdAt": "2026-08-15T10:00:00Z",
    "updatedAt": "2026-08-15T14:00:00Z"
  }
]
```
Empty array if the user owns no sites. Ordered by `createdAt DESC`.

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |

---

## `GET /cms/sites/{id}`

Returns a single site owned by the authenticated user, including publication state.

**Response — `200 OK`**
```json
{
  "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "title": "Santiago Acevedo — Portfolio",
  "summary": "Backend Developer Portfolio",
  "contentSchema": "portfolio-v1",
  "published": true,
  "createdAt": "2026-08-15T10:00:00Z",
  "updatedAt": "2026-08-15T14:00:00Z"
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site does not exist |

---

## `PATCH /cms/sites/{id}`

Updates site metadata — `title`, `summary`, `contentSchema`. All fields optional; only the
fields present in the request body are overwritten. **Last-write-wins** — no optimistic
locking on metadata (unlike the draft content lifecycle, which requires `If-Match`).
Publication state is unaffected by this operation.

**Request**
```json
{
  "title": "New Title"
}
```

**Response — `200 OK`**
```json
{
  "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "title": "New Title",
  "summary": "Backend Developer Portfolio",
  "contentSchema": "portfolio-v1",
  "published": true,
  "createdAt": "2026-08-15T10:00:00Z",
  "updatedAt": "2026-08-15T15:00:00Z"
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site does not exist |
| 422 | `VALIDATION_ERROR` | Any provided field exceeds its max length |

---

## `DELETE /cms/sites/{id}`

Deletes a site owned by the authenticated user. `ON DELETE CASCADE` (DB-level, see
architecture docs §4) removes every dependent row — entries, drafts, and published
snapshots — atomically, in a single `DELETE` statement.

**Response — `204 No Content`** (empty body)

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site does not exist |
