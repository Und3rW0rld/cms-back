# Architecture and data model — Headless CMS

## Status
This document reflects the current design decisions. It is a living document — update it before implementing any section.

---

## Strategic positioning

**Target:** SaaS multi-tenant headless CMS. Initial user is the author (portfolio use case). Architecture is designed for B (multi-tenant SaaS), built for A first (single user, known use case).

**Rule:** design for B, build for A. Decisions that are cheap now and expensive later are made now. Features that only make sense with real users are deferred.

---

## Checklist

- [x] Single database: PostgreSQL only — MongoDB removed
- [x] JSONB for all content — same flexibility, one system
- [x] ltree for entry hierarchy — replaces parent_id adjacency list
- [x] No status column — publication state from row existence in site_published
- [x] Optimistic locking on drafts (version + ETag/If-Match)
- [x] Generic content model: Map<String, Object> for now
- [x] /cms/** user resources, /public/** read-only, /auth/**, /admin/** reserved
- [x] ADMIN + EDITOR roles only
- [x] sort_order (not order)
- [x] contentSchema on sites
- [x] Delete cascade behavior defined
- [x] Pagination on public entries
- [x] ?parentId=root for top-level entries
- [x] Rate limiting on /public/** and /cms/**
- [x] Cache with TTL + evict on publish/unpublish/delete
- [x] 1MB content size cap
- [x] plan_id NULL on users (future monetization hook)
- [x] site_collaborators table created empty (future per-site roles)
- [x] media_assets table created empty (future uploads)
- [x] site_domains table created empty (future custom domains)
- [x] site_webhooks table created empty (future webhooks)

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| Database | PostgreSQL only. JSONB for content. MongoDB removed |
| Why single DB | Transactions, RLS, ltree, one system to operate — no cross-DB join in application layer |
| PG responsibility | Everything: identity, metadata, content (JSONB), publication state (row existence) |
| Publication state | Row existence in `site_published` / `site_entry_published`. No status column |
| Content model | `JSONB` column — `Map<String, Object>` at application level. Backend does not interpret structure |
| Content size cap | 1MB — validated in use case before writing |
| Entry hierarchy | `ltree` extension. `path LTREE` column on `site_entries`. Cycles impossible by construction |
| Top-level entries | `?parentId=root` = entries where path is depth 1 |
| Optimistic locking | `version: Long` on draft rows. ETag/If-Match. 412 on mismatch |
| PATCH metadata | Last-write-wins — intentional |
| Publish | Upsert into `site_published` in a single transaction. Copies draft content |
| Unpublish | Deletes row from `site_published`. Public returns 404 |
| Delete site | Transaction: deletes `site_entries`, `site_drafts`, `site_published`, all entry drafts/published |
| Delete entry without children | Deletes row + draft + published rows |
| Delete entry with children | Returns 409 |
| Roles | `ADMIN` = `/admin/**`. `EDITOR` = own sites via `/cms/**`. VIEWER dropped |
| Per-site collaboration | `site_collaborators` table created, no logic yet |
| contentSchema | `VARCHAR(100) NULL`. Breaking change for consumers if modified in production |
| Pagination | Public entries: default 20, max 200 |
| ?type filter | Query PG `(site_id, type)` index → no secondary fetch needed (content in same DB) |
| Rate limiting | `/public/**` ~20 req/s per IP; `/cms/**` ~10 req/s per user |
| Cache | Caffeine `expireAfterWrite=1h`. Evict on publish, unpublish, delete |
| Autoguardado | Frontend concern. Backend exposes `PUT .../draft` |
| Concurrent publish | Last-write-wins — acceptable for human action |
| plan_id | `UUID NULL` on `users` — hook for future monetization. No logic yet |
| Media | `media_assets` table created. No upload logic yet |
| Custom domains | `site_domains` table created. No logic yet |
| Webhooks | `site_webhooks` table created. No logic yet |
| content_types | Deferred — implement when second type of client has different schema needs |
| RLS | Deferred — implement when enterprise clients require engine-level tenant isolation |
| i18n | Deferred — implement when a concrete client requests it |
| Preview tokens | Deferred — implement when a client has a static frontend |
| JWT refresh/revocation | Pending — not defined yet |
| OAuth2 social login | Pending — schema ready. Resolve email-merge edge case first |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It does not render HTML — it delivers JSON.

A **site** is the root publishable unit. It can represent a portfolio, blog, product page, CV, or anything else. A site contains **entries** — any content with its own page (posts, projects, series, etc.).

Two consumer types from a single API:
- **CMS UI** — logs in, edits drafts, publishes
- **Each user's frontend** — reads published content, no authentication

---

## 2. Architecture

### 2.1 Functional domain split

**A. Identity & Access** — users, JWT, roles

**B. Site Management** — sites, entries, draft/publish lifecycle

**C. Public Content Delivery** — public endpoints, always serves published rows

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

**Single database: PostgreSQL.**

- Metadata and identity: normalized relational tables
- Content: `JSONB` columns — flexible schema, GIN-indexable, TOAST for large values
- Publication state: row existence in `site_published` / `site_entry_published`
- Hierarchy: `ltree` extension on `site_entries`
- All operations within a single transaction when needed

---

## 3. Domain model

### 3.1 `Site`

```text
Site
- id: UUID
- ownerUserId: Long
- title: String
- summary: String          ← CMS listing metadata, not derived from content
- contentSchema: String?   ← e.g. "portfolio-v1". Hint for frontend rendering.
                              Changing in production is breaking for deployed frontends.
- createdAt: Instant
- updatedAt: Instant
```

---

### 3.2 `SiteEntry`

```text
SiteEntry
- id: UUID
- siteId: UUID
- path: LTree              ← e.g. "root.series_uuid.entry_uuid"
- type: String             ← frontend label: "post", "project", "series" — not validated
- sortOrder: Integer?
- createdAt: Instant
- updatedAt: Instant
```

**ltree rules:**
- Root entries: path = `root.{entryId}`
- Children: path = `parent.path.{entryId}`
- Descendants of X: `WHERE path <@ 'root.seriesId'`
- Ancestors of X: `WHERE path @> 'root.seriesId.entryId'`
- Move entry (and all descendants) to new parent: update path in a single transaction
- Cycles: **impossible by construction** — ltree is acyclic by definition

**Querying:**
- `?parentId=root` → `WHERE site_id = ? AND nlevel(path) = 1`
- `?parentId={id}` → `WHERE path <@ '{parentPath}.{id}' AND nlevel(path) = nlevel(parentPath)+2`

---

### 3.3 Draft and published rows

All content is `JSONB`. Backend never interprets structure. Maximum size: **1MB**.

#### `site_drafts`
```text
- id: UUID PK (= siteId, 1:1)
- site_id: UUID UNIQUE FK sites(id)
- version: Long            ← optimistic locking, starts at 1
- content: JSONB
- updated_at: Timestamp
```
Created with `content: '{}'` when site is created. Upserted on every save.

#### `site_published`
```text
- id: UUID PK (= siteId, 1:1)
- site_id: UUID UNIQUE FK sites(id)
- content: JSONB           ← snapshot from draft at publish time
- published_at: Timestamp
```
**Its existence means the site is published.** Created/overwritten on publish. Deleted on unpublish or site delete.

#### `site_entry_drafts`
```text
- id: UUID PK (= entryId, 1:1)
- entry_id: UUID UNIQUE FK site_entries(id)
- site_id: UUID FK sites(id)
- version: Long
- content: JSONB
- updated_at: Timestamp
```

#### `site_entry_published`
```text
- id: UUID PK (= entryId, 1:1)
- entry_id: UUID UNIQUE FK site_entries(id)
- site_id: UUID FK sites(id)
- content: JSONB
- published_at: Timestamp
```
**Its existence means the entry is published.** Deleted on unpublish or delete.

---

### 3.4 Optimistic locking

```
GET /cms/sites/{id}/draft
← 200 { "content": {...}, "version": 3 }
    ETag: "3"

PUT /cms/sites/{id}/draft
    If-Match: "3"
    Body: { "content": {...} }
← 200 { "content": {...}, "version": 4 }
← 412 if server version != 3
```

Frontend 412 handling: *"Someone else saved changes. Reload to continue."*

PATCH metadata (`title`, `summary`, `contentSchema`) is last-write-wins — intentional.

---

### 3.5 Publish transaction

```sql
BEGIN;
  INSERT INTO site_published (site_id, content, published_at)
  SELECT site_id, content, NOW() FROM site_drafts WHERE site_id = ?
  ON CONFLICT (site_id) DO UPDATE
    SET content = EXCLUDED.content, published_at = EXCLUDED.published_at;
  -- update sites.updated_at if needed
COMMIT;
```

Single transaction. No cross-database inconsistency possible.

---

### 3.6 Example content (JSONB)

```json
// site_drafts.content — portfolio
{
  "seo": { "title": "Santiago | Backend Dev", "description": "..." },
  "hero": { "greeting": "Hi, I'm", "name": "Santiago" },
  "skills": [{ "name": "Java", "slug": "openjdk" }],
  "jobs": [{ "company": "Acme", "role": "Backend Dev" }]
}

// site_entry_drafts.content — blog post
{
  "title": "Designing hexagonal APIs",
  "date": "2026-07-18",
  "body": "# Introduction\n\nHexagonal architecture separates...",
  "tags": ["architecture", "spring"],
  "readTime": "8 min read"
}

// site_entry_drafts.content — series index
{
  "title": "Hexagonal Architecture Series",
  "description": "A 3-part series on building clean Java backends."
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
- plan_id       UUID NULL FK plans(id)    ← NULL until monetization is implemented
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
- updated_at    TIMESTAMP NOT NULL

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
CREATE EXTENSION IF NOT EXISTS ltree;

sites
- id                UUID PK
- owner_user_id     BIGINT NOT NULL FK users(id)
- title             VARCHAR(150) NOT NULL
- summary           VARCHAR(255) NULL
- content_schema    VARCHAR(100) NULL
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

-- No status column. Published = row exists in site_published.

site_entries
- id                UUID PK
- site_id           UUID NOT NULL FK sites(id)
- path              LTREE NOT NULL
- type              VARCHAR(50) NOT NULL
- sort_order        INT NULL
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

-- No status column. Published = row exists in site_entry_published.

CREATE INDEX idx_site_entries_path_gist ON site_entries USING GIST (path);
CREATE INDEX idx_site_entries_site_path ON site_entries (site_id, path);
CREATE INDEX idx_site_entries_type      ON site_entries (site_id, type);

site_drafts
- site_id           UUID PK FK sites(id)
- version           BIGINT NOT NULL DEFAULT 1
- content           JSONB NOT NULL DEFAULT '{}'
- updated_at        TIMESTAMP NOT NULL

site_published
- site_id           UUID PK FK sites(id)
- content           JSONB NOT NULL
- published_at      TIMESTAMP NOT NULL

site_entry_drafts
- entry_id          UUID PK FK site_entries(id)
- site_id           UUID NOT NULL FK sites(id)
- version           BIGINT NOT NULL DEFAULT 1
- content           JSONB NOT NULL DEFAULT '{}'
- updated_at        TIMESTAMP NOT NULL

site_entry_published
- entry_id          UUID PK FK site_entries(id)
- site_id           UUID NOT NULL FK sites(id)
- content           JSONB NOT NULL
- published_at      TIMESTAMP NOT NULL

CREATE INDEX idx_entry_published_site ON site_entry_published (site_id);
```

### Future tables — created now, no logic yet

```sql
-- Per-site collaboration (future roles per site)
site_collaborators
- site_id       UUID NOT NULL FK sites(id)
- user_id       BIGINT NOT NULL FK users(id)
- role          VARCHAR(30) NOT NULL   -- OWNER | EDITOR | AUTHOR | VIEWER
- PK (site_id, user_id)

-- Media uploads
media_assets
- id            UUID PK
- owner_user_id BIGINT NOT NULL FK users(id)
- site_id       UUID NULL FK sites(id)
- storage_key   VARCHAR(255) NOT NULL UNIQUE
- public_url    VARCHAR(500) NOT NULL
- alt           VARCHAR(255) NULL
- mime_type     VARCHAR(100) NOT NULL
- size_bytes    INT NOT NULL
- created_at    TIMESTAMP NOT NULL

-- Custom domains
site_domains
- id            UUID PK
- site_id       UUID NOT NULL FK sites(id)
- domain        VARCHAR(255) NOT NULL UNIQUE
- verified      BOOLEAN NOT NULL DEFAULT FALSE
- dns_challenge JSONB NULL
- created_at    TIMESTAMP NOT NULL

-- Webhooks on publish/unpublish
site_webhooks
- id            UUID PK
- site_id       UUID NOT NULL FK sites(id)
- url           VARCHAR(500) NOT NULL
- events        TEXT[] NOT NULL   -- ['publish', 'unpublish', 'delete']
- secret        VARCHAR(255) NULL
- created_at    TIMESTAMP NOT NULL

-- Pricing plans (future)
plans
- id            UUID PK
- name          VARCHAR(50) NOT NULL UNIQUE   -- FREE | PRO | BUSINESS
- max_sites     INT NOT NULL
- max_entries   INT NOT NULL
- max_storage_mb INT NOT NULL
- created_at    TIMESTAMP NOT NULL
```

---

## 5. Endpoints

### 5.1 Authentication
```
POST /auth/register
POST /auth/login
```

### 5.2 /cms — authenticated, user's own resources

**Sites**
```
POST   /cms/sites
GET    /cms/sites                     ← user's sites; published state from site_published JOIN
GET    /cms/sites/{id}
PATCH  /cms/sites/{id}                ← title, summary, contentSchema (last-write-wins)
DELETE /cms/sites/{id}                ← transaction: cascades entries, drafts, published rows
```

**Site draft/publish**
```
GET    /cms/sites/{id}/draft          ← content + version as ETag
PUT    /cms/sites/{id}/draft          ← If-Match required; 412 on mismatch; 1MB cap
POST   /cms/sites/{id}/publish        ← upserts site_published in transaction
POST   /cms/sites/{id}/unpublish      ← deletes site_published row
```

**Entries**
```
POST   /cms/sites/{id}/entries
GET    /cms/sites/{id}/entries        ← ?type=post, ?parentId=root, ?parentId={uuid}
GET    /cms/sites/{id}/entries/{entryId}
PATCH  /cms/sites/{id}/entries/{entryId}   ← type, sort_order, parentId (ltree path update)
DELETE /cms/sites/{id}/entries/{entryId}   ← 409 if has children; cascades drafts/published
```

**Entry draft/publish**
```
GET    /cms/sites/{id}/entries/{entryId}/draft
PUT    /cms/sites/{id}/entries/{entryId}/draft     ← If-Match required; 412 on mismatch; 1MB cap
POST   /cms/sites/{id}/entries/{entryId}/publish
POST   /cms/sites/{id}/entries/{entryId}/unpublish ← deletes site_entry_published row
```

### 5.3 /public — unauthenticated read-only
```
GET /public/sites/{id}
    ← site_published content + contentSchema
    ← 404 if no site_published row

GET /public/sites/{id}/entries
    ← entries with existing site_entry_published row
    ← ?type=post, ?parentId=root, ?parentId={uuid}
    ← ?limit=20 (default), max 200

GET /public/sites/{id}/entries/{entryId}
    ← 404 if no site_entry_published row
```

### 5.4 /admin — reserved, not implemented
```
GET /admin/users
GET /admin/sites
```

---

## 6. Editorial flow

### Site
1. Create → `sites` row + `site_drafts` row `{ content: '{}', version: 1 }`
2. Edit → `PUT /cms/sites/{id}/draft` with `If-Match: {version}` → version incremented
3. Publish → transaction: upsert `site_published` from `site_drafts.content`
4. Public reads `site_published` — 404 if row absent
5. Unpublish → delete `site_published` row

### Entry
Same flow, independent lifecycle:
1. Create → `site_entries` row (path assigned) + `site_entry_drafts` row `{ content: '{}', version: 1 }`
2. Edit → `PUT .../draft` with `If-Match`
3. Publish → upsert `site_entry_published`
4. Unpublish → delete `site_entry_published` row

### Published state in CMS listing
```sql
SELECT s.*, sp.published_at IS NOT NULL AS is_published
FROM sites s
LEFT JOIN site_published sp ON sp.site_id = s.id
WHERE s.owner_user_id = ?
```
Single query. No application-layer join needed.

### Move entry to new parent (ltree)
```sql
BEGIN;
  UPDATE site_entries
  SET path = ? || subpath(path, nlevel(old_parent_path))
  WHERE site_id = ? AND path <@ old_entry_path;
COMMIT;
```
Moves entry and all descendants atomically.

### Series
1. Create entry with `type: "series"` → path = `root.{seriesId}`
2. Create children → path = `root.{seriesId}.{entryId}`
3. `GET .../entries?parentId={seriesId}` → `WHERE path <@ 'root.seriesId' AND nlevel(path) = 2`
4. No cycle detection needed — ltree is acyclic by construction

### Delete cascade
```sql
BEGIN;
  -- Check no children
  -- Delete entry_published, entry_drafts, site_entries row
COMMIT;
```

---

## 7. Business validations

### Site
- `title` required
- Ownership validated in use case
- Only `EDITOR` role may write

### SiteEntry
- `type` required, non-blank
- `parentId` if set must reference an entry within the same site
- No cycle detection needed — ltree handles it

### Content
- Must be valid JSON object — not null, not array
- Serialized size ≤ 1MB
- Backend does not validate internal structure

---

## 8. Package structure

```text
src/main/java/com/cms/
├── domain/
│   ├── model/
│   │   ├── user/
│   │   │   └── User.java
│   │   └── site/
│   │       ├── Site.java
│   │       ├── SiteEntry.java
│   │       └── PublicationStatus.java
│   └── port/
│       ├── in/
│       │   ├── site/
│       │   │   ├── CreateSiteUseCase.java
│       │   │   ├── UpdateSiteDraftUseCase.java
│       │   │   ├── PublishSiteUseCase.java
│       │   │   ├── UnpublishSiteUseCase.java
│       │   │   ├── DeleteSiteUseCase.java
│       │   │   └── GetSitePublicUseCase.java
│       │   └── entry/
│       │       ├── CreateEntryUseCase.java
│       │       ├── UpdateEntryDraftUseCase.java
│       │       ├── PublishEntryUseCase.java
│       │       ├── UnpublishEntryUseCase.java
│       │       ├── DeleteEntryUseCase.java
│       │       └── GetEntryPublicUseCase.java
│       └── out/
│           ├── SiteRepository.java
│           ├── SiteDraftRepository.java
│           ├── SitePublishedRepository.java
│           ├── SiteEntryRepository.java
│           ├── SiteEntryDraftRepository.java
│           └── SiteEntryPublishedRepository.java
├── application/
│   └── usecase/
│       ├── site/
│       │   ├── CreateSiteService.java
│       │   ├── UpdateSiteDraftService.java
│       │   ├── PublishSiteService.java
│       │   ├── UnpublishSiteService.java
│       │   ├── DeleteSiteService.java
│       │   └── GetSitePublicService.java
│       └── entry/
│           ├── CreateEntryService.java
│           ├── UpdateEntryDraftService.java
│           ├── PublishEntryService.java
│           ├── UnpublishEntryService.java
│           ├── DeleteEntryService.java
│           └── GetEntryPublicService.java
└── adapters/
    ├── in/web/
    │   ├── controller/
    │   │   ├── AuthController.java
    │   │   ├── CmsSiteController.java
    │   │   ├── CmsEntryController.java
    │   │   └── PublicSiteController.java
    │   └── dto/
    │       ├── request/
    │       └── response/
    └── out/persistence/jpa/
        ├── entity/
        │   ├── UserEntity.java
        │   ├── SiteEntity.java
        │   ├── SiteEntryEntity.java
        │   ├── SiteDraftEntity.java
        │   ├── SitePublishedEntity.java
        │   ├── SiteEntryDraftEntity.java
        │   └── SiteEntryPublishedEntity.java
        ├── repository/
        │   ├── UserJpaRepository.java
        │   ├── SiteJpaRepository.java
        │   ├── SiteEntryJpaRepository.java
        │   ├── SiteDraftJpaRepository.java
        │   ├── SitePublishedJpaRepository.java
        │   ├── SiteEntryDraftJpaRepository.java
        │   └── SiteEntryPublishedJpaRepository.java
        └── adapter/
            ├── SitePersistenceAdapter.java
            ├── SiteEntryPersistenceAdapter.java
            ├── SiteDraftPersistenceAdapter.java
            ├── SitePublishedPersistenceAdapter.java
            ├── SiteEntryDraftPersistenceAdapter.java
            └── SiteEntryPublishedPersistenceAdapter.java
```

---

## 9. Public endpoint protection

### Response caching
Spring Cache + Caffeine. `expireAfterWrite=1h`. Evict on publish, unpublish, delete.
Migrate to Redis for multiple instances.

### Rate limiting
Bucket4j:
- `/public/**`: ~20 req/s per IP → `429`
- `/cms/**`: ~10 req/s per authenticated user → `429`

### CDN / reverse proxy — future
Cloudflare or nginx for volumetric DDoS.

---

## 10. Confirmed decisions

### Keep
- PostgreSQL only — MongoDB removed
- JSONB for all content
- ltree for entry hierarchy
- No status column — row existence = published
- Optimistic locking on drafts (version + ETag/If-Match + 412)
- PATCH metadata last-write-wins
- Concurrent publish last-write-wins
- `plan_id UUID NULL` on users
- Future tables created (site_collaborators, media_assets, site_domains, site_webhooks, plans)
- contentSchema as breaking change warning
- 1MB content cap
- Cache with TTL + evict on all state changes
- Rate limiting on both /public and /cms

### Avoid
- MongoDB — removed
- status column in sites or site_entries
- Typed content classes
- parent_id adjacency list — use ltree path
- Cycle detection code — ltree handles it
- Slug as identifier
- PUT .../draft without If-Match

### Deferred until real users/clients
- content_types + content_fields (schema builder)
- RLS
- i18n
- Preview tokens
- Webhooks logic
- Custom domains logic
- Media upload logic
- site_collaborators logic
- JWT refresh/revocation strategy
- OAuth2 social login

---

## 11. MVP

### Flyway migrations
```
V1__create_users_schema.sql
V2__create_sites_schema.sql
V3__create_site_entries_schema.sql        ← includes ltree extension
V4__create_draft_published_schema.sql
V5__create_future_tables.sql              ← site_collaborators, media_assets, site_domains, site_webhooks, plans
```

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

## 12. Confirmed architecture

1. **PostgreSQL only** — identity, metadata, content (JSONB), publication state (row existence)
2. **ltree** for entry hierarchy — acyclic, depth-unlimited, efficient queries
3. **Hexagonal architecture** with ports for each use case
4. **Generic content model** — JSONB / `Map<String, Object>`, frontend defines structure
5. **Multi-user headless CMS** — API serves JSON, each user integrates their own frontend
6. **Single API** — `/cms`, `/public`, `/auth`; `/admin` reserved

---

## 13. Suggested next steps

1. `V1__create_users_schema.sql` — full normalized user schema with `plan_id NULL`
2. Update `UserEntity` — remove `UserDetails` implementation
3. `V2__create_sites_schema.sql`
4. `V3__create_site_entries_schema.sql` — ltree extension + indexes
5. `V4__create_draft_published_schema.sql`
6. `V5__create_future_tables.sql`
7. JPA entities for all tables
8. Domain models: `Site`, `SiteEntry`
9. Output ports and adapters
10. Use cases
11. Controllers: `AuthController`, `CmsSiteController`, `CmsEntryController`, `PublicSiteController`
