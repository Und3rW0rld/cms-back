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
A freshly created site is always `published: false`. Publishing is a separate action
(`POST /cms/sites/{id}/publish`).

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

## `GET /cms/sites/{id}/draft`

Returns the working copy for the site (`site_drafts`), including `content` and `version`.
The same `version` is sent as an `ETag` header (quoted), for the next `PUT`.

**Response — `200 OK`**
```
ETag: "3"
```
```json
{
  "content": { "title": "My Portfolio", "summary": "A showcase of my work." },
  "version": 3,
  "updatedAt": "2026-08-15T14:00:00Z"
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site or draft row does not exist |

---

## `PUT /cms/sites/{id}/draft`

Replaces the draft content. **`If-Match` is required** (the `ETag` from `GET .../draft`).
The server compares it to `site_drafts.version`; a mismatch returns `412`. Content must be
a JSON object and at most 1MB.

**Request**
```
If-Match: "3"
```
```json
{
  "content": { "title": "My Portfolio", "summary": "A showcase of my work." }
}
```

**Response — `200 OK`**
```
ETag: "4"
```
```json
{
  "content": { "title": "My Portfolio", "summary": "A showcase of my work." },
  "version": 4,
  "updatedAt": "2026-08-15T14:05:00Z"
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site or draft row does not exist |
| 400 | | Missing `If-Match` header |
| 412 | `PRECONDITION_FAILED` | `If-Match` does not match `site_drafts.version` |
| 413 | `CONTENT_TOO_LARGE` | Serialized content exceeds 1MB |

---

## `POST /cms/sites/{id}/publish`

Copies the current draft snapshot into `site_published` (`INSERT … ON CONFLICT DO UPDATE`).
No request body and no `If-Match` — publish always takes the latest draft. Republishing the
same site overwrites `content` and `published_at` (last write wins). The draft row is not
modified. Does not return published content; that is `GET /public/sites/{id}` (not implemented).

**Response — `200 OK`**
```json
{
  "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "title": "Santiago Acevedo — Portfolio",
  "summary": "Backend Developer Portfolio",
  "contentSchema": "portfolio-v1",
  "published": true,
  "createdAt": "2026-08-15T10:00:00Z",
  "updatedAt": "2026-08-15T10:00:00Z"
}
```

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site or draft row does not exist |

---

## `POST /cms/sites/{id}/unpublish`

Deletes the `site_published` row. The draft is unchanged. No request body. Idempotent: if
the site is already unpublished, the response is still `204`. Does not return a body — the
caller can set `published: false` locally, or re-fetch `GET /cms/sites/{id}`.

**Response — `204 No Content`** (empty body)

**Errors**
| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Site exists but belongs to a different user |
| 404 | `NOT_FOUND` | Site does not exist |

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
