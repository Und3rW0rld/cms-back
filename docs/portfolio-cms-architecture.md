# Architecture and data model — Headless CMS

## Checklist

- [x] Define the functional goal of the CMS
- [x] Confirm headless multi-user model
- [x] Decide on UUID-only identification (no slug)
- [x] Confirm draft/publish as a required workflow
- [x] Confirm single API serves both CMS UI and each user's frontend
- [x] Rename portfolio → site for generality
- [x] Replace typed content model with generic Map<String, Object>
- [x] Replace PostCard with generic Entry entity
- [x] Separate draft and published content into distinct documents
- [x] Replace /admin prefix with /cms for user-owned resource endpoints
- [x] Simplify roles: ADMIN + EDITOR only, drop VIEWER
- [x] Drop ARCHIVED status — DRAFT | PUBLISHED only
- [x] Add contentSchema field to sites
- [x] Add optimistic locking (version + ETag/If-Match) on draft documents
- [x] Define delete cascade behavior
- [x] Define parentId cycle protection (depth-unlimited with cycle detection)
- [x] Define unpublish semantics (delete *_published document)
- [x] Define initial content state on create (content: {})
- [x] Document ?type filter two-step query flow
- [x] Remove status from PG — publication state determined by existence of *_published in Mongo
- [x] Rename order → sort_order (SQL reserved word)
- [x] Add cache TTL + evict on unpublish and delete
- [x] Add rate limiting on /cms/** per authenticated user
- [x] Add content size cap
- [x] Add pagination on public entries
- [x] Define top-level entries query convention (?parentId=root)
- [x] Declare PATCH metadata as last-write-wins
- [x] Document contentSchema as breaking change for consumers
- [x] Document publish concurrency as last-write-wins (acceptable)

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| System type | Headless CMS API — serves JSON, not HTML |
| Consumers | CMS UI (admin) + each user's own frontend (public) |
| Deployment | Single API — no microservice split needed at this stage |
| Users | Multi-user; anyone can register and manage their own content |
| Public identification | UUID only — no slug |
| Draft/publish | Saving never auto-publishes. Draft and published are separate documents |
| Publication state | Determined by existence of `*_published` document in MongoDB — no `status` field in PostgreSQL |
| PG responsibility | "What exists and who owns it" — identity, ownership, relationships, navigation metadata |
| Mongo responsibility | "What content does it have and is it published?" — all content, draft/published state |
| No shared state | No field is duplicated between PG and Mongo. They are complementary, not redundant |
| Autoguardado | Frontend concern — calls `PUT .../draft` periodically. Backend has no special logic |
| Optimistic locking | `version: Long` on draft documents. `GET .../draft` returns ETag. `PUT .../draft` requires `If-Match`. Returns `412` on mismatch |
| PATCH metadata | Last-write-wins — no optimistic locking on title/summary/contentSchema. Declared intentional |
| Publish concurrency | Last-write-wins — two simultaneous publishes: last one wins. Acceptable for human click action |
| Roles | `ADMIN` = access to `/admin/**`. `EDITOR` = manages own sites. `VIEWER` dropped |
| Content model | `Map<String, Object>` — backend stores and serves without interpreting structure |
| Content size cap | 1MB limit validated in use case on every `PUT .../draft`. Protects Mongo and memory |
| contentSchema | `VARCHAR(100) NULL` on `sites`. Written by CMS UI. Included in public response. **Changing it in production is a breaking change for deployed frontends** |
| Entry type | Frontend label — backend does not validate its value, only that it is non-blank |
| Series / hierarchy | `parentId` self-reference on `site_entries`. Depth unlimited. Cycle detection on every PATCH that sets `parentId` |
| Top-level entries query | `?parentId=root` returns entries where `parent_id IS NULL` |
| Status | No `status` field — publication state derived from Mongo document existence |
| Unpublish | Deletes `*_published` document. Public endpoint returns `404` until next publish |
| Initial content | Draft created with `content: {}` on site/entry create. `GET .../draft` always `200` if resource exists |
| Delete site | Cascades: all `site_entries`, all 4 MongoDB document types |
| Delete entry without children | Deletes row + `site_entry_drafts` + `site_entry_published` |
| Delete entry with children | Returns `409` — caller deletes children first |
| Pagination | Public entries: default `limit=20`, max `limit=200`. Offset-based to start |
| ?type filter | Two-step: query PG `(site_id, type)` → batch fetch Mongo `site_entry_published` by `entryId` list |
| Rate limiting | `/public/**`: ~20 req/s per IP. `/cms/**`: ~10 req/s per authenticated user |
| Cache | Caffeine with `expireAfterWrite=1h` as safety TTL. Evict on publish, unpublish, and delete |
| sort_order | Column renamed from `order` (SQL reserved word) to `sort_order` |
| JWT refresh/revocation | Pending — not defined yet |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It does not render HTML — it delivers JSON.

A **site** is the root publishable unit. It can represent a portfolio, blog, product page, CV, or anything else. A site contains **entries** — any piece of content that needs its own page (posts, projects, series, etc.).

Two consumer types from a single API:
- **CMS UI** — logs in, edits drafts, publishes
- **Each user's frontend** — reads published content, no authentication

---

## 2. Architecture

### 2.1 Functional domain split

**A. Identity & Access** — users, JWT, roles

**B. Site Management** — sites, entries, draft/publish lifecycle

**C. Public Content Delivery** — public endpoints, always serves published documents

---

### 2.2 Hexagonal architecture

**Domain models:** `Site`, `SiteEntry`

**Input ports:**
- `CreateSiteUseCase`, `UpdateSiteDraftUseCase`, `PublishSiteUseCase`, `UnpublishSiteUseCase`, `DeleteSiteUseCase`, `GetSitePublicUseCase`
- `CreateEntryUseCase`, `UpdateEntryDraftUseCase`, `PublishEntryUseCase`, `UnpublishEntryUseCase`, `DeleteEntryUseCase`, `GetEntryPublicUseCase`

**Outbound ports:**
- `SiteRepository`, `SiteDraftRepository`, `SitePublishedRepository`
- `SiteEntryRepository`, `SiteEntryDraftRepository`, `SiteEntryPublishedRepository`

---

### 2.3 Persistence strategy

**PostgreSQL** answers: *"what exists and who owns it?"*
- Identity, ownership, relationships, navigation metadata
- Never stores content or publication state

**MongoDB** answers: *"what content does it have and is it published?"*
- All content as `Map<String, Object>`
- Publication state determined by document existence: `site_published` exists = published

**No field is duplicated between the two databases.** They are complementary.

The only cross-database join happens in the application layer as a batch query — never N+1.

---

## 3. Domain model

### 3.1 `Site` — PostgreSQL

```text
Site
- id: UUID
- ownerUserId: Long
- title: String
- summary: String              ← CMS listing metadata, not derived from content
- contentSchema: String?       ← e.g. "portfolio-v1". Hint for public frontend rendering.
                                  Changing this in production is breaking for deployed frontends.
- createdAt: Instant
- updatedAt: Instant
```

---

### 3.2 `SiteEntry` — PostgreSQL

```text
SiteEntry
- id: UUID
- siteId: UUID
- parentId: UUID?              ← null = top-level. References another entry for hierarchy/series
- type: String                 ← frontend label: "post", "project", "series" — not validated by backend
- sortOrder: Integer?
- createdAt: Instant
- updatedAt: Instant
```

**Hierarchy rules:**
- Depth unlimited. No maximum nesting level enforced.
- On every PATCH that sets `parentId`: walk the ancestor chain. If the current entry appears in it, return `400 Cycle detected`.
- `parentId` must reference an entry within the same site.
- `?parentId=root` in queries means `parent_id IS NULL` — returns top-level entries.

---

### 3.3 Content documents — MongoDB

Content is always `Map<String, Object>`. Backend never interprets structure.
Maximum content size: **1MB** — validated in use case before writing to Mongo.

#### `site_drafts`
```text
SiteDraftDocument
- id: ObjectId
- siteId: UUID
- version: Long                ← starts at 1, incremented on every save
- content: Map<String, Object>
- updatedAt: Instant
```
One per site. Created with `content: {}` when site is created. Overwritten on every save.

#### `site_published`
```text
SitePublishedDocument
- id: ObjectId
- siteId: UUID
- content: Map<String, Object> ← snapshot from draft at publish time
- publishedAt: Instant
```
One per site. **Its existence means the site is published.**
Created/overwritten on publish. **Deleted on unpublish or site delete.**

#### `site_entry_drafts`
```text
SiteEntryDraftDocument
- id: ObjectId
- entryId: UUID
- siteId: UUID
- version: Long                ← optimistic locking
- content: Map<String, Object>
- updatedAt: Instant
```
One per entry. Created with `content: {}` on entry creation.

#### `site_entry_published`
```text
SiteEntryPublishedDocument
- id: ObjectId
- entryId: UUID
- siteId: UUID
- content: Map<String, Object>
- publishedAt: Instant
```
One per entry. **Its existence means the entry is published.**
**Deleted on unpublish or entry/site delete.**

---

### 3.4 Optimistic locking on drafts

Prevents two browser tabs or concurrent users from silently overwriting each other.

```
GET /cms/sites/{id}/draft
← 200 { "content": {...}, "version": 3 }
    ETag: "3"

PUT /cms/sites/{id}/draft
    If-Match: "3"
    Body: { "content": {...} }
← 200 { "content": {...}, "version": 4 }   ← version incremented
← 412 if server version != 3
```

Frontend 412 handling: *"Someone else saved changes since you opened this draft. Reload to continue."*

Same pattern applies to `site_entry_drafts`.

**PATCH metadata** (`title`, `summary`, `contentSchema`) is last-write-wins — no optimistic locking. Metadata edits are infrequent and manual.

**Concurrent publish** is last-write-wins — two simultaneous publish clicks: last one wins. Acceptable for a human action.

---

### 3.5 Example documents

```json
// site_drafts
{
  "siteId": "b7fd3b44-...",
  "version": 3,
  "content": {
    "seo": { "title": "Santiago | Backend Dev", "description": "..." },
    "hero": { "greeting": "Hi, I'm", "name": "Santiago" },
    "skills": [{ "name": "Java", "slug": "openjdk" }]
  },
  "updatedAt": "2026-07-18T10:00:00Z"
}

// site_entry_drafts — a post
{
  "entryId": "c1d2e3f4-...",
  "siteId": "b7fd3b44-...",
  "version": 1,
  "content": {
    "title": "Designing hexagonal APIs",
    "date": "2026-07-18",
    "body": "# Introduction\n\nHexagonal architecture separates...",
    "tags": ["architecture", "spring"],
    "readTime": "8 min read"
  },
  "updatedAt": "2026-07-18T10:00:00Z"
}

// site_entry_drafts — a series index
{
  "entryId": "series-uuid-...",
  "siteId": "b7fd3b44-...",
  "version": 1,
  "content": {
    "title": "Hexagonal Architecture Series",
    "description": "A 3-part series on building clean Java backends."
  },
  "updatedAt": "2026-07-18T10:00:00Z"
}
```

---

## 4. PostgreSQL schema

### User identity

```sql
users
- id            BIGSERIAL PK
- email         VARCHAR(100) NOT NULL UNIQUE
- name          VARCHAR(100) NOT NULL
- enabled       BOOLEAN NOT NULL DEFAULT TRUE
- created_at    TIMESTAMP NOT NULL
- updated_at    TIMESTAMP NOT NULL

roles
- id            SERIAL PK
- name          VARCHAR(50) NOT NULL UNIQUE   -- ADMIN | EDITOR

user_roles
- user_id       BIGINT NOT NULL FK users(id)
- role_id       INT NOT NULL FK roles(id)
- PK (user_id, role_id)

user_credentials
- user_id       BIGINT PK FK users(id)
- password_hash VARCHAR(255) NOT NULL
- created_at    TIMESTAMP NOT NULL
- updated_at    TIMESTAMP NOT NULL     ← password rotation tracking

user_oauth_providers
- id                BIGSERIAL PK
- user_id           BIGINT NOT NULL FK users(id)
- provider          VARCHAR(30) NOT NULL        -- GOOGLE | GITHUB
- provider_user_id  VARCHAR(255) NOT NULL
- created_at        TIMESTAMP NOT NULL
- UNIQUE (provider, provider_user_id)

user_profiles
- user_id       BIGINT PK FK users(id)
- last_name     VARCHAR(100) NULL
- phone         VARCHAR(30) NULL
- bio           TEXT NULL
- avatar_url    VARCHAR(500) NULL
- website       VARCHAR(255) NULL
- metadata      JSONB NULL DEFAULT '{}'
- updated_at    TIMESTAMP NOT NULL
```

### Sites and entries

```sql
sites
- id                UUID PK
- owner_user_id     BIGINT NOT NULL FK users(id)
- title             VARCHAR(150) NOT NULL
- summary           VARCHAR(255) NULL
- content_schema    VARCHAR(100) NULL
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

-- No status column. Published state = existence of site_published in MongoDB.

site_entries
- id                UUID PK
- site_id           UUID NOT NULL FK sites(id)
- parent_id         UUID NULL FK site_entries(id)
- type              VARCHAR(50) NOT NULL
- sort_order        INT NULL                    ← renamed from 'order' (SQL reserved word)
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

-- No status column. Published state = existence of site_entry_published in MongoDB.

CREATE INDEX idx_site_entries_site_id   ON site_entries (site_id);
CREATE INDEX idx_site_entries_filter    ON site_entries (site_id, type);
CREATE INDEX idx_site_entries_parent    ON site_entries (site_id, parent_id);
```

### Optional (future)

```sql
media_assets
- id            UUID PK
- owner_user_id BIGINT NOT NULL
- storage_key   VARCHAR(255) NOT NULL UNIQUE
- public_url    VARCHAR(500) NOT NULL
- alt           VARCHAR(255) NULL
- mime_type     VARCHAR(100) NOT NULL
- created_at    TIMESTAMP NOT NULL
```

---

## 5. MongoDB indexes

```text
site_drafts
  - unique: siteId

site_published
  - unique: siteId

site_entry_drafts
  - unique: entryId
  - index: siteId

site_entry_published
  - unique: entryId
  - index: siteId
  - index: (siteId, entryId)   ← batch fetch by site + list of entryIds
```

---

## 6. Endpoints

### 6.1 Authentication
```
POST /auth/register
POST /auth/login
```

### 6.2 /cms — authenticated, user's own resources

**Sites**
```
POST   /cms/sites
GET    /cms/sites                          ← user's sites + published state (batch Mongo query)
GET    /cms/sites/{id}
PATCH  /cms/sites/{id}                     ← title, summary, contentSchema (last-write-wins)
DELETE /cms/sites/{id}                     ← cascades all entries and all MongoDB documents
```

**Site draft/publish**
```
GET    /cms/sites/{id}/draft               ← returns content + version as ETag
PUT    /cms/sites/{id}/draft               ← If-Match required; 412 on mismatch; 1MB limit
POST   /cms/sites/{id}/publish             ← copies draft → site_published
POST   /cms/sites/{id}/unpublish           ← deletes site_published
```

**Entries**
```
POST   /cms/sites/{id}/entries
GET    /cms/sites/{id}/entries             ← ?type=post, ?parentId=root, ?parentId={uuid}
GET    /cms/sites/{id}/entries/{entryId}
PATCH  /cms/sites/{id}/entries/{entryId}   ← type, sort_order, parentId (cycle-checked)
DELETE /cms/sites/{id}/entries/{entryId}   ← 409 if has children; cascades MongoDB docs
```

**Entry draft/publish**
```
GET    /cms/sites/{id}/entries/{entryId}/draft
PUT    /cms/sites/{id}/entries/{entryId}/draft     ← If-Match required; 412 on mismatch; 1MB limit
POST   /cms/sites/{id}/entries/{entryId}/publish
POST   /cms/sites/{id}/entries/{entryId}/unpublish ← deletes site_entry_published
```

### 6.3 /public — unauthenticated read-only
```
GET /public/sites/{id}
    ← published site content + contentSchema
    ← 404 if site_published does not exist

GET /public/sites/{id}/entries
    ← ?type=post, ?parentId=root, ?parentId={uuid}
    ← ?limit=20 (default), max 200
    ← only entries with existing site_entry_published document
    ← two-step: query PG (site_id, type) → batch fetch Mongo by entryId list

GET /public/sites/{id}/entries/{entryId}
    ← 404 if site_entry_published does not exist
```

### 6.4 /admin — reserved for future system-level operations
```
(not implemented yet)
GET /admin/users
GET /admin/sites
```

---

## 7. Editorial flow

### Site
1. Create → `sites` row inserted, `site_drafts` created `{ content: {}, version: 1 }`
2. Edit → `PUT /cms/sites/{id}/draft` with `If-Match: {version}` → version incremented
3. Publish → `site_drafts.content` copied to `site_published` (overwrite if exists)
4. Public reads `site_published` — 404 if document absent
5. Unpublish → `site_published` deleted → public returns 404

### Entry
Same flow, independent from site lifecycle:
1. Create → `site_entries` row, `site_entry_drafts` created `{ content: {}, version: 1 }`
2. Edit → `PUT .../draft` with `If-Match`
3. Publish → draft copied to `site_entry_published`
4. Unpublish → `site_entry_published` deleted

### Published state in CMS listing

`GET /cms/sites` flow:
```
1. SELECT id, title, summary, content_schema, created_at, updated_at
   FROM sites WHERE owner_user_id = ?
   → list of siteIds

2. db.site_published.find({ siteId: { $in: siteIds } }, { siteId: 1 })
   → set of published siteIds

3. Merge in application layer → each site gets isPublished: boolean
```

Single batch query — not N+1.

### Series
1. Create entry with `type: "series"`
2. Create children with `parentId = seriesEntryId`
3. `GET .../entries?parentId={seriesId}` returns published children ordered by `sort_order`
4. `GET .../entries?parentId=root` returns top-level entries (parent_id IS NULL)

### Delete cascade
- `DELETE /cms/sites/{id}` → deletes `site_entries`, `site_drafts`, `site_published`, all `site_entry_drafts`, all `site_entry_published`
- `DELETE .../entries/{entryId}` with no children → entry row + draft doc + published doc
- `DELETE .../entries/{entryId}` with children → `409 Conflict`

### Cycle detection on parentId
On every `PATCH .../entries/{entryId}` that sets `parentId`:
```java
UUID cursor = newParentId;
while (cursor != null) {
    if (cursor.equals(entryId)) throw CycleDetectedException(); // 400
    cursor = entryRepository.findById(cursor).parentId;
}
```

---

## 8. Business validations

### Site
- `title` required
- Ownership validated in use case — not only in authentication
- Only `EDITOR` role may write (own sites only)

### SiteEntry
- `type` required, non-blank
- `parentId` if set must reference an entry within the same site
- Cycle detection on every PATCH that sets `parentId`

### Content documents
- `content` must be a valid JSON object — not null, not an array
- `content` serialized size must not exceed **1MB**
- Backend does not validate internal structure

---

## 9. Public endpoint protection

### Response caching — implement now
Spring Cache + Caffeine.
- `expireAfterWrite = 1h` — safety TTL in case eviction fails
- Evict on: publish, unpublish, delete (site and entry)

```java
@Cacheable(value = "published-site", key = "#id")
public PublicSiteResponse getPublished(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void publish(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void unpublish(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void delete(UUID id) { ... }
```

Migrate to Redis when running multiple instances.

### Rate limiting — implement now
Bucket4j:
- `/public/**`: ~20 req/s per IP → `429`
- `/cms/**`: ~10 req/s per authenticated user → `429`

In-memory to start, Redis-backed when scaling.

### UUID obscurity — in place
UUID v4 space is 2^122 — brute force enumeration infeasible.

### CDN / reverse proxy — future
Cloudflare or nginx for volumetric DDoS.

---

## 10. Image strategy

**Phase 1:** URLs only — CDN, Cloudinary, S3, static assets.

**Phase 2 (future):** `media` module, upload to backend, store in `media_assets`, return public URL.

---

## 11. Confirmed decisions

### Keep
- Normalized user schema: `users`, `user_credentials` (with `updated_at`), `user_oauth_providers`, `user_roles`, `user_profiles`
- `ADMIN` + `EDITOR` only — `VIEWER` dropped
- No `status` column in `sites` or `site_entries` — publication state from Mongo document existence
- PG = ownership + relationships + navigation metadata. Mongo = content + publication state
- `Map<String, Object>` for all content, 1MB cap
- `contentSchema` on sites — **breaking change for consumers if modified in production**
- Separate MongoDB documents for draft and published
- Optimistic locking on drafts (version + ETag/If-Match + 412)
- PATCH metadata is last-write-wins — intentional
- Concurrent publish is last-write-wins — intentional
- `sort_order` (not `order`)
- Series via `parentId`, depth unlimited, cycle detection in use case
- `?parentId=root` for top-level entries
- Pagination on public entries: default 20, max 200
- Two-step ?type query: PG → batch Mongo
- Unpublish deletes `*_published` document
- Delete cascades; entry with children returns 409
- Cache with 1h TTL + evict on publish/unpublish/delete
- Rate limiting on both `/public/**` and `/cms/**`

### Avoid
- `status` field in PG — redundant with Mongo document existence
- `VIEWER` role until collaboration exists
- `ARCHIVED` status until there is a defined flow
- Typed content classes — `Map<String, Object>` only
- Mixing draft and published in the same document
- Slug as identifier
- Filtering by `type` directly in MongoDB — `type` lives in PG
- `PUT .../draft` without `If-Match` — always required
- Content over 1MB

### Pending decisions
- **OAuth2 social login**: schema ready. Resolve email-merge edge case first.
- **Pricing / plan tiers**: `user_roles` ready. Add `plans` table when needed.
- **JWT refresh / revocation**: not defined yet.

---

## 12. MVP

### Persistence
- PostgreSQL: `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`, `sites`, `site_entries`
- MongoDB: `site_drafts`, `site_published`, `site_entry_drafts`, `site_entry_published`

### Minimum endpoints
```
POST /auth/register
POST /auth/login
POST /cms/sites
GET  /cms/sites
GET  /cms/sites/{id}/draft
PUT  /cms/sites/{id}/draft            ← If-Match required
POST /cms/sites/{id}/publish
POST /cms/sites/{id}/entries
GET  /cms/sites/{id}/entries
GET  /cms/sites/{id}/entries/{entryId}/draft
PUT  /cms/sites/{id}/entries/{entryId}/draft  ← If-Match required
POST /cms/sites/{id}/entries/{entryId}/publish
GET  /public/sites/{id}
GET  /public/sites/{id}/entries
GET  /public/sites/{id}/entries/{entryId}
```

---

## 13. Confirmed architecture

1. **PostgreSQL** — ownership, relationships, navigation metadata. Never content or state.
2. **MongoDB** — all content + publication state via document existence
3. **Hexagonal architecture** with ports for each use case
4. **Generic content model** — `Map<String, Object>`, frontend defines structure
5. **Multi-user headless CMS** — API serves JSON, each user integrates their own frontend
6. **Single API** — `/cms`, `/public`, `/auth`; `/admin` reserved for system ops

---

## 14. Suggested next steps

1. Rewrite `V1__create_users_table.sql` — full normalized user schema
2. Update `UserEntity` — remove `UserDetails` implementation
3. `V2__create_sites_table.sql` — no `status`, with `content_schema`
4. `V3__create_site_entries_table.sql` — no `status`, `sort_order`, indexes
5. MongoDB documents: `SiteDraftDocument` (with `version`), `SitePublishedDocument`, `SiteEntryDraftDocument` (with `version`), `SiteEntryPublishedDocument`
6. Domain models: `Site`, `SiteEntry`
7. Output ports and adapters
8. Use cases — cycle detection in `UpdateEntryUseCase`, cascade in `DeleteSiteUseCase`, batch Mongo query in listing
9. Controllers: `AuthController`, `CmsSiteController`, `CmsEntryController`, `PublicSiteController`
