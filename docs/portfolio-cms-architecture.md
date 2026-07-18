# Architecture and data model — Headless CMS

## Checklist

- [x] Define the functional goal of the CMS
- [x] Propose an architecture aligned with the existing hexagonal structure
- [x] Separate concerns between public reading, administration, and persistence
- [x] Ground the proposal in the current stack: Spring Boot + PostgreSQL + MongoDB
- [x] Define concrete endpoints, packages, and evolution path
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
- [x] Add optimistic locking (version field + ETag/If-Match) on draft documents
- [x] Define delete cascade behavior
- [x] Define parentId cycle protection (depth-unlimited with cycle detection)
- [x] Define unpublish semantics
- [x] Define initial content state on create
- [x] Document ?type filter two-step query flow

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| System type | Headless CMS API — serves JSON, not HTML |
| Consumers | CMS UI (admin) + each user's own frontend (public) |
| Deployment | Single API — no microservice split needed at this stage |
| Users | Multi-user; anyone can register and manage their own content |
| Public identification | UUID only — no slug |
| Draft/publish | Required — saving never auto-publishes. Draft and published are separate documents |
| Autoguardado | Frontend responsibility — calls `PUT .../draft` periodically. Backend has no special logic |
| Optimistic locking | `version` integer on draft documents. `GET .../draft` returns `version` as ETag. `PUT .../draft` requires `If-Match` header. Returns `412` if mismatch |
| Roles | `ADMIN` = access to `/admin/**`. `EDITOR` = manages own sites. `VIEWER` dropped until collaboration exists |
| Content model | `Map<String, Object>` — backend stores and serves without interpreting structure |
| contentSchema | `VARCHAR(100) NULL` on `sites`. CMS UI writes it (e.g. `"portfolio-v1"`). Public response includes it so frontends can version their rendering logic |
| Entry | Generic content unit with its own page. `type` is a frontend label — backend does not validate its value |
| Series | Entries reference parent via `parentId`. Depth unlimited. Cycle detection in use case on every PATCH that sets `parentId` |
| Publication status | `DRAFT` and `PUBLISHED` only — `ARCHIVED` dropped |
| Unpublish | Deletes the `*_published` document. Public endpoint returns `404` when no published document exists |
| Initial content state | On create: draft document is created with `content: {}`. `GET .../draft` always returns `200` if site/entry exists |
| Delete site | Cascades: deletes all `site_entries`, all 4 MongoDB document types for the site and its entries |
| Delete entry without children | Deletes entry row + `site_entry_drafts` + `site_entry_published` |
| Delete entry with children | Returns `409` — caller must delete children first |
| ?type filter | Two-step: query PostgreSQL `(site_id, type, published=true)` → batch fetch MongoDB by `entryId`. Index `(site_id, published, type)` on `site_entries` |
| sites.summary | "Display summary" for CMS UI listing — not derived from content. Two sources intentional |
| Naming | `/cms/**` user resources, `/public/**` read-only, `/auth/**` login, `/admin/**` reserved for system ops |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It does not render HTML — it delivers JSON.

A **site** is the root publishable unit. It can represent a portfolio, blog, product page, CV, or anything else. A site contains **entries** — any piece of content that needs its own page.

Two consumer types from a single API:
- **CMS UI** — logs in, edits drafts, publishes
- **Each user's frontend** — reads published content, no authentication

---

## 2. Current project state

- Hexagonal architecture base exists
- Spring Security + JWT in place
- PostgreSQL + MongoDB configured
- `users` table exists in PostgreSQL — to be replaced by normalized schema

---

## 3. Architecture

### 3.1 Functional domain split

**A. Identity & Access** — users, JWT, roles (`ADMIN`, `EDITOR`)

**B. Site Management** — sites, entries, draft/publish lifecycle

**C. Public Content Delivery** — public endpoints, always serves published documents

---

### 3.2 Hexagonal architecture applied

**Domain models:** `Site`, `SiteEntry`, `PublicationStatus`

**Input ports:**
- `CreateSiteUseCase`, `UpdateSiteDraftUseCase`, `PublishSiteUseCase`, `UnpublishSiteUseCase`, `GetSitePublicUseCase`, `DeleteSiteUseCase`
- `CreateEntryUseCase`, `UpdateEntryDraftUseCase`, `PublishEntryUseCase`, `UnpublishEntryUseCase`, `GetEntryPublicUseCase`, `DeleteEntryUseCase`

**Outbound ports:**
- `SiteRepository`, `SiteDraftRepository`, `SitePublishedRepository`
- `SiteEntryRepository`, `SiteEntryDraftRepository`, `SiteEntryPublishedRepository`

---

### 3.3 Persistence strategy

- **PostgreSQL**: metadata and lifecycle state — `users`, `sites`, `site_entries`
- **MongoDB**: all content — `site_drafts`, `site_published`, `site_entry_drafts`, `site_entry_published`

---

## 4. Domain model

### 4.1 `Site` — PostgreSQL

```text
Site
- id: UUID
- ownerUserId: Long
- title: String
- summary: String              ← display summary for CMS listings, not derived from content
- contentSchema: String?       ← e.g. "portfolio-v1". Written by CMS UI, read by public frontend
- status: DRAFT | PUBLISHED
- createdAt: Instant
- updatedAt: Instant
```

---

### 4.2 `SiteEntry` — PostgreSQL

```text
SiteEntry
- id: UUID
- siteId: UUID
- parentId: UUID?              ← null if direct child of site; references another entry for series/nesting
- type: String                 ← frontend label: "post", "project", "page", "series" — backend does not validate value
- status: DRAFT | PUBLISHED
- order: Integer?
- createdAt: Instant
- updatedAt: Instant
```

**Series:** an entry with `type: "series"` is just another entry. Children set `parentId` to its id. Depth is unlimited. On every PATCH that sets `parentId`, the use case walks the ancestor chain to detect cycles — if the new parent is a descendant of the current entry, return `400`.

---

### 4.3 Content documents — MongoDB

Content is always `Map<String, Object>`. Backend stores and serves it without interpreting structure.

#### `site_drafts`
```text
SiteDraftDocument
- id: ObjectId
- siteId: UUID
- version: Long                ← incremented on every save; used for optimistic locking
- content: Map<String, Object>
- updatedAt: Instant
```
One document per site. Initialized to `content: {}` on site creation.

#### `site_published`
```text
SitePublishedDocument
- id: ObjectId
- siteId: UUID
- content: Map<String, Object> ← snapshot copied from draft at publish time
- publishedAt: Instant
```
One document per site. Created/overwritten on publish. **Deleted on unpublish.**

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
One per entry. Initialized to `content: {}` on entry creation.

#### `site_entry_published`
```text
SiteEntryPublishedDocument
- id: ObjectId
- entryId: UUID
- siteId: UUID
- content: Map<String, Object> ← snapshot from draft
- publishedAt: Instant
```
One per entry. Created/overwritten on publish. **Deleted on unpublish.**

---

### 4.4 Optimistic locking — draft concurrency

Prevents two browser tabs or two users from silently overwriting each other's draft.

**Flow:**
```
GET /cms/sites/{id}/draft
← 200 { "content": {...}, "version": 3 }
   ETag: "3"

PUT /cms/sites/{id}/draft
    If-Match: "3"
    Body: { "content": {...} }
← 200 { "content": {...}, "version": 4 }
← 412 if server version != 3 (someone else saved first)
```

**Backend logic:**
```java
if (!draft.getVersion().equals(ifMatchVersion)) throw PreconditionFailedException();
draft.setContent(newContent);
draft.setVersion(draft.getVersion() + 1);
```

**Frontend handling for 412:** show a message — "Someone else saved changes since you opened this draft. Reload to see the latest version." No automatic merge required at this stage.

Same pattern applies to `site_entry_drafts`.

---

### 4.5 Example documents

```json
// site_drafts
{
  "siteId": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "version": 3,
  "content": {
    "seo": { "title": "Santiago | Backend Dev", "description": "..." },
    "hero": { "greeting": "Hi, I'm", "name": "Santiago", "tagline": "..." },
    "skills": [{ "name": "Java", "slug": "openjdk", "category": "BACKEND" }],
    "jobs": [{ "company": "Acme", "role": "Backend Dev", "highlights": ["..."] }]
  },
  "updatedAt": "2026-06-18T10:00:00Z"
}

// site_entry_drafts — a blog post
{
  "entryId": "c1d2e3f4-...",
  "siteId": "b7fd3b44-...",
  "version": 1,
  "content": {
    "title": "Designing hexagonal APIs",
    "date": "2026-06-18",
    "body": "# Introduction\n\nHexagonal architecture separates...",
    "tags": ["architecture", "spring"],
    "readTime": "8 min read"
  },
  "updatedAt": "2026-06-18T10:00:00Z"
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
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

---

## 5. PostgreSQL schema

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
- updated_at    TIMESTAMP NOT NULL             ← required for password rotation tracking

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
- status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'PUBLISHED'))
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

site_entries
- id                UUID PK
- site_id           UUID NOT NULL FK sites(id)
- parent_id         UUID NULL FK site_entries(id)   -- self-reference; cycle validated in use case
- type              VARCHAR(50) NOT NULL              -- not validated by backend
- status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'PUBLISHED'))
- order             INT NULL
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL

-- Indexes
CREATE INDEX idx_site_entries_site_id ON site_entries (site_id);
CREATE INDEX idx_site_entries_filter ON site_entries (site_id, status, type);
```

> `site_entries.status` uses the same `DRAFT | PUBLISHED` enum as `sites` — consistent across the model.

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

## 6. MongoDB indexes

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
```

---

## 7. Endpoints

### 7.1 Authentication
```
POST /auth/register
POST /auth/login
```

### 7.2 /cms — authenticated, user's own resources

**Sites**
```
POST   /cms/sites
GET    /cms/sites                          ← authenticated user's sites only
GET    /cms/sites/{id}
PATCH  /cms/sites/{id}                     ← metadata: title, summary, contentSchema
DELETE /cms/sites/{id}                     ← cascades all entries and MongoDB documents
```

**Site draft/publish**
```
GET    /cms/sites/{id}/draft               ← returns content + version (ETag)
PUT    /cms/sites/{id}/draft               ← requires If-Match header; 412 on mismatch
POST   /cms/sites/{id}/publish
POST   /cms/sites/{id}/unpublish           ← deletes site_published document
```

**Entries**
```
POST   /cms/sites/{id}/entries
GET    /cms/sites/{id}/entries             ← ?type=post, ?parentId=x, ?status=published
GET    /cms/sites/{id}/entries/{entryId}
PATCH  /cms/sites/{id}/entries/{entryId}   ← metadata: type, order, parentId (cycle-checked)
DELETE /cms/sites/{id}/entries/{entryId}   ← 409 if has children; cascades MongoDB docs
```

**Entry draft/publish**
```
GET    /cms/sites/{id}/entries/{entryId}/draft
PUT    /cms/sites/{id}/entries/{entryId}/draft     ← requires If-Match; 412 on mismatch
POST   /cms/sites/{id}/entries/{entryId}/publish
POST   /cms/sites/{id}/entries/{entryId}/unpublish ← deletes site_entry_published document
```

### 7.3 /public — unauthenticated read-only
```
GET /public/sites/{id}                           ← published site content + contentSchema
GET /public/sites/{id}/entries                   ← ?type=post, ?parentId=x (published only)
GET /public/sites/{id}/entries/{entryId}         ← 404 if no published document exists
```

> **?type filter flow:** query PostgreSQL `(site_id, status='PUBLISHED', type)` using index `idx_site_entries_filter` → batch fetch MongoDB `site_entry_published` by `entryId` list.

### 7.4 /admin — reserved for future system-level operations
```
(not implemented yet)
GET /admin/users
GET /admin/sites
```

---

## 8. Editorial flow

### Site
1. Create site → `sites` row inserted, `site_drafts` created with `content: {}`, `version: 1`
2. Edit → `PUT /cms/sites/{id}/draft` with `If-Match: {version}` → overwrites draft, increments version
3. Publish → copies `site_drafts.content` → `site_published`, sets `sites.status = PUBLISHED`
4. Public reads `site_published` — never the draft
5. Unpublish → deletes `site_published`, sets `sites.status = DRAFT`

### Entry
Same flow, independent from site lifecycle:
1. Create → `site_entries` row, `site_entry_drafts` with `content: {}`, `version: 1`
2. Edit → `PUT .../draft` with `If-Match`
3. Publish → copies draft → `site_entry_published`, sets `site_entries.status = PUBLISHED`
4. Unpublish → deletes `site_entry_published`, sets `site_entries.status = DRAFT`

### Series
1. Create entry with `type: "series"`
2. Create children with `parentId = seriesEntryId`
3. `GET /public/sites/{id}/entries?parentId={seriesId}` returns published children ordered by `order`
4. Cycle detection: on `PATCH .../entries/{id}` with `parentId`, walk ancestor chain — if current entry appears, return `400`

### Delete cascade
- `DELETE /cms/sites/{id}` → deletes `site_entries`, `site_drafts`, `site_published`, all `site_entry_drafts`, all `site_entry_published`
- `DELETE /cms/sites/{id}/entries/{entryId}` with no children → deletes entry row + `site_entry_drafts` + `site_entry_published`
- `DELETE /cms/sites/{id}/entries/{entryId}` with children → `409 Conflict`

---

## 9. Business validations

### Site
- `title` required
- `status` constrained to `DRAFT | PUBLISHED` (CHECK constraint in DB)
- Ownership validated in use case — not only in authentication
- Only `EDITOR` role may write (own sites only)

### SiteEntry
- `type` required, non-blank — value not validated by backend
- `status` constrained to `DRAFT | PUBLISHED`
- `parentId` if set must reference an entry within the same site
- `parentId` cycle detection on every PATCH

### Content documents
- `content` must be a valid JSON object — not null, not an array
- Backend does not validate internal structure

---

## 10. Package structure

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
    └── out/persistence/
        ├── jpa/
        │   ├── entity/
        │   │   ├── UserEntity.java
        │   │   ├── SiteEntity.java
        │   │   └── SiteEntryEntity.java
        │   ├── repository/
        │   │   ├── UserJpaRepository.java
        │   │   ├── SiteJpaRepository.java
        │   │   └── SiteEntryJpaRepository.java
        │   └── adapter/
        │       ├── SitePersistenceAdapter.java
        │       └── SiteEntryPersistenceAdapter.java
        └── mongo/
            ├── document/
            │   ├── SiteDraftDocument.java
            │   ├── SitePublishedDocument.java
            │   ├── SiteEntryDraftDocument.java
            │   └── SiteEntryPublishedDocument.java
            ├── repository/
            │   ├── SiteDraftMongoRepository.java
            │   ├── SitePublishedMongoRepository.java
            │   ├── SiteEntryDraftMongoRepository.java
            │   └── SiteEntryPublishedMongoRepository.java
            └── adapter/
                ├── SiteDraftPersistenceAdapter.java
                ├── SitePublishedPersistenceAdapter.java
                ├── SiteEntryDraftPersistenceAdapter.java
                └── SiteEntryPublishedPersistenceAdapter.java
```

---

## 11. Public endpoint protection

`GET /public/sites/{id}` is unauthenticated. Required before production:

### Response caching — implement now
Spring Cache + Caffeine. Evict on publish and unpublish. Migrate to Redis for multiple instances.

```java
@Cacheable(value = "published-site", key = "#id")
public PublicSiteResponse getPublished(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void publish(UUID id) { ... }
```

### Rate limiting — implement now
Bucket4j. ~20 req/s per IP on `/public/**`. Returns `429`. In-memory to start.

### UUID obscurity — in place
UUID v4 space is 2^122 — brute force infeasible.

### CDN / reverse proxy — future
Cloudflare or nginx for volumetric DDoS.

---

## 12. Image strategy

**Phase 1:** store URLs only — CDN, Cloudinary, S3, static assets.

**Phase 2 (future):** `media` module, upload to backend, store in `media_assets`, return public URL.

---

## 13. Confirmed decisions

### Keep
- Normalized user schema: `users`, `user_credentials`, `user_oauth_providers`, `user_roles`, `user_profiles`
- `user_credentials.updated_at` for password rotation tracking
- `ADMIN` + `EDITOR` roles only — `VIEWER` dropped until collaboration exists
- `DRAFT | PUBLISHED` status only — `ARCHIVED` dropped
- `contentSchema` on `sites` — frontend schema versioning hint
- `site` as generic content unit, `entry` as generic child content unit
- Separate MongoDB documents for draft and published — never mixed
- `Map<String, Object>` for all content
- Optimistic locking on drafts: `version` field + ETag/If-Match + 412
- `/cms/**` user resources, `/public/**` read-only, `/admin/**` reserved
- Autoguardado is a frontend concern
- Series via `parentId` — depth unlimited, cycle detection in use case
- `CHECK (status IN ('DRAFT','PUBLISHED'))` on `sites` and `site_entries`
- Index `(site_id, status, type)` on `site_entries` for ?type filter
- Two-step ?type query: PostgreSQL → batch MongoDB fetch
- Unpublish deletes `*_published` document
- Delete site cascades everything; delete entry with children returns 409
- `sites.summary` is CMS listing metadata, not derived from content

### Avoid
- `VIEWER` role until per-site collaboration exists
- `ARCHIVED` status until there is a defined flow for it
- Typed content classes — use `Map<String, Object>`
- Mixing draft and published in the same document
- Slug as identifier
- Exposing draft on public endpoints
- Validating content structure in the backend
- `PUT .../draft` without `If-Match` header — always required

### Pending decisions
- **OAuth2 social login**: schema ready. Resolve email-merge edge case first.
- **Pricing / plan tiers**: `user_roles` ready. Add `plans` table when needed.
- **JWT refresh / revocation**: expiration configured. Revocation strategy (blocklist or short-lived tokens) not defined yet.

---

## 14. MVP

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

## 15. Confirmed architecture

1. **PostgreSQL** for users + site/entry metadata + lifecycle state
2. **MongoDB** for all content — draft and published as separate documents
3. **Hexagonal architecture** with ports for each use case
4. **Generic content model** — `Map<String, Object>`, frontend defines structure
5. **Multi-user headless CMS** — API serves JSON, each user integrates their own frontend
6. **Single API** — `/cms`, `/public`, `/auth`; `/admin` reserved for system ops

---

## 16. Suggested next steps

1. Rewrite `V1__create_users_table.sql` — full normalized user schema
2. Update `UserEntity` — remove `UserDetails` implementation from JPA entity
3. `V2__create_sites_table.sql` — with `content_schema`, `CHECK` constraint
4. `V3__create_site_entries_table.sql` — with `CHECK` constraint, `idx_site_entries_filter`
5. MongoDB documents: `SiteDraftDocument` (with `version`), `SitePublishedDocument`, `SiteEntryDraftDocument` (with `version`), `SiteEntryPublishedDocument`
6. Domain models: `Site`, `SiteEntry`, `PublicationStatus`
7. Output ports and adapters
8. Use cases — including cycle detection in `UpdateEntryUseCase` and delete cascade in `DeleteSiteUseCase`
9. Controllers: `AuthController`, `CmsSiteController`, `CmsEntryController`, `PublicSiteController`
