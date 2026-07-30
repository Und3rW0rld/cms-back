# Headless CMS — Architecture and Data Model

## Strategic positioning

SaaS multi-tenant headless CMS. Initial user is the author. Principle: **design for scale, build for the known use case first**. Decisions that are cheap now and expensive later are made upfront. Features that only make sense with real users are deferred explicitly.

---

## 1. System overview

A **site** is the root publishable unit — a portfolio, blog, product page, or anything else. A site contains **entries**: any content that needs its own page (posts, projects, series, etc.).

The API has three consumer types:
- **CMS UI** — authenticated, edits drafts, publishes
- **Each user's frontend** — reads published content, no authentication
- **System admin** — `/admin/**`, reserved, not yet implemented

---

## 2. Technology decisions

| Concern | Choice | Why |
|---|---|---|
| Database | PostgreSQL only | Single system: transactions, ltree, RLS, JSONB — no cross-DB joins |
| Content storage | `JSONB` columns | Flexible schema, GIN-indexable, TOAST for large values, same DB as metadata |
| Entry hierarchy | `ltree` extension | Acyclic by construction, depth-unlimited, efficient subtree queries, atomic moves |
| Publication state | Row existence in `*_published` tables | Single source of truth — no status column to sync |
| Auth | Stateless JWT | Standard for headless APIs |

---

## 3. Database schema

### 3.1 User identity

```sql
users
- id            BIGSERIAL PK
- email         VARCHAR(100) NOT NULL UNIQUE
- name          VARCHAR(100) NOT NULL
- enabled       BOOLEAN NOT NULL DEFAULT TRUE
- plan_id       UUID NULL                      -- no FK until plans logic exists
- created_at    TIMESTAMP NOT NULL
- updated_at    TIMESTAMP NOT NULL

roles
- id            SERIAL PK
- name          VARCHAR(50) NOT NULL UNIQUE    -- ADMIN | EDITOR

user_roles
- user_id       BIGINT NOT NULL FK users(id)
- role_id       INT NOT NULL FK roles(id)
- PK (user_id, role_id)

user_credentials                               -- local email/password login
- user_id       BIGINT PK FK users(id)
- password_hash VARCHAR(255) NOT NULL
- created_at    TIMESTAMP NOT NULL
- updated_at    TIMESTAMP NOT NULL             -- tracks password rotation

user_oauth_providers                           -- Google, GitHub, etc. (future)
- id                BIGSERIAL PK
- user_id           BIGINT NOT NULL FK users(id)
- provider          VARCHAR(30) NOT NULL        -- GOOGLE | GITHUB
- provider_user_id  VARCHAR(255) NOT NULL
- created_at        TIMESTAMP NOT NULL
- UNIQUE (provider, provider_user_id)

user_profiles                                  -- optional enriched data
- user_id       BIGINT PK FK users(id)
- last_name     VARCHAR(100) NULL
- phone         VARCHAR(30) NULL
- bio           TEXT NULL
- avatar_url    VARCHAR(500) NULL
- website       VARCHAR(255) NULL
- metadata      JSONB NULL DEFAULT '{}'        -- free-form fields, no migration needed
- updated_at    TIMESTAMP NOT NULL
```

### 3.2 Sites

```sql
sites
- id                UUID PK
- owner_user_id     BIGINT NOT NULL FK users(id)
- title             VARCHAR(150) NOT NULL
- summary           VARCHAR(255) NULL          -- CMS listing display, not derived from content
- content_schema    VARCHAR(100) NULL          -- e.g. "portfolio-v1", hint for frontend rendering
- created_at        TIMESTAMP NOT NULL
- updated_at        TIMESTAMP NOT NULL
```

> **`content_schema`:** written by the CMS UI, read by the public frontend to know how to render the content. Changing it in production is a **breaking change** for deployed frontends.

### 3.3 Entries

```sql
CREATE EXTENSION IF NOT EXISTS ltree;

site_entries
- id            UUID PK
- site_id       UUID NOT NULL FK sites(id)
- path          LTREE NOT NULL
- type          VARCHAR(50) NOT NULL           -- frontend label: "post", "project", "series", etc.
- sort_order    INT NULL
- created_at    TIMESTAMP NOT NULL
- updated_at    TIMESTAMP NOT NULL

CREATE UNIQUE INDEX idx_site_entries_site_path ON site_entries (site_id, path);
CREATE INDEX idx_site_entries_path_gist ON site_entries USING GIST (path);
CREATE INDEX idx_site_entries_type ON site_entries (site_id, type);
```

**ltree path segments:** UUID with `-` replaced by `_` (ltree only accepts `[A-Za-z0-9_]`).

```java
// Build segment
String segment = entryId.toString().replace('-', '_');
// path = "root." + segment  (root entry)
// path = parentPath + "." + segment  (child entry)

// Recover UUID from segment
UUID id = UUID.fromString(segment.replace('_', '-'));
```

The original UUID always lives in the `id` column. The path is only used for hierarchy queries.

**Why ltree instead of `parent_id`:**
- Cycles are impossible by construction — ltree is acyclic by definition
- Get all descendants: `WHERE path <@ 'root.abc_123'` — single indexed query
- Get direct children: `WHERE path <@ parent_path AND nlevel(path) = nlevel(parent_path) + 1`
- Move entry and all descendants: one `UPDATE` with `subpath()` in a transaction
- No recursive CTEs, no cycle detection code

### 3.4 Content tables

All content is `JSONB`. The backend never interprets the content structure — it stores and serves it as-is. Maximum size: **1MB** (validated in use case).

```sql
site_drafts
- site_id       UUID PK FK sites(id)
- version       BIGINT NOT NULL DEFAULT 1
- content       JSONB NOT NULL DEFAULT '{}'
                CONSTRAINT chk_site_draft_obj CHECK (jsonb_typeof(content) = 'object')
- updated_at    TIMESTAMP NOT NULL

site_published
- site_id       UUID PK FK sites(id)
- content       JSONB NOT NULL
                CONSTRAINT chk_site_pub_obj CHECK (jsonb_typeof(content) = 'object')
- published_at  TIMESTAMP NOT NULL

site_entry_drafts
- entry_id      UUID PK FK site_entries(id)
- site_id       UUID NOT NULL FK sites(id)
- version       BIGINT NOT NULL DEFAULT 1
- content       JSONB NOT NULL DEFAULT '{}'
                CONSTRAINT chk_entry_draft_obj CHECK (jsonb_typeof(content) = 'object')
- updated_at    TIMESTAMP NOT NULL

site_entry_published
- entry_id      UUID PK FK site_entries(id)
- site_id       UUID NOT NULL FK sites(id)
- content       JSONB NOT NULL
                CONSTRAINT chk_entry_pub_obj CHECK (jsonb_typeof(content) = 'object')
- published_at  TIMESTAMP NOT NULL

CREATE INDEX idx_entry_published_site ON site_entry_published (site_id);
```

**Publication state:** a site or entry is published if and only if its corresponding `*_published` row exists. No status column. Public endpoints return `404` when the row is absent.

**Draft initialization:** on create, draft row is inserted with `content = '{}'` and `version = 1`. `GET .../draft` always returns `200` if the resource exists.

### 3.5 Future tables — created now, no logic yet

These tables exist in the schema from day one to avoid painful migrations later.

```sql
-- Per-site collaboration
-- Note: roles prefixed SITE_ to avoid ambiguity with global roles in user_roles
site_collaborators
- site_id       UUID NOT NULL FK sites(id)
- user_id       BIGINT NOT NULL FK users(id)
- role          VARCHAR(30) NOT NULL  -- SITE_OWNER | SITE_EDITOR | SITE_AUTHOR | SITE_VIEWER
- PK (site_id, user_id)

-- File uploads
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

-- Publish/unpublish webhooks
site_webhooks
- id            UUID PK
- site_id       UUID NOT NULL FK sites(id)
- url           VARCHAR(500) NOT NULL
- events        TEXT[] NOT NULL       -- e.g. ['publish', 'unpublish']
- secret        VARCHAR(255) NULL
- created_at    TIMESTAMP NOT NULL

-- Pricing plans
-- Add FK users.plan_id -> plans(id) in the migration that activates this
plans
- id            UUID PK
- name          VARCHAR(50) NOT NULL UNIQUE   -- FREE | PRO | BUSINESS
- max_sites     INT NOT NULL
- max_entries   INT NOT NULL
- max_storage_mb INT NOT NULL
- created_at    TIMESTAMP NOT NULL
```

---

## 4. Draft/publish lifecycle

### Optimistic locking on drafts

Two browser tabs or two concurrent users cannot silently overwrite each other.

```
GET /cms/sites/{id}/draft
← 200  { "content": {...}, "version": 3 }
        ETag: "3"

PUT /cms/sites/{id}/draft
    If-Match: "3"
    Body: { "content": {...} }
← 200  { "content": {...}, "version": 4 }   -- version incremented
← 412  if server version != 3
```

Frontend 412 handling: *"Someone else saved changes. Reload to continue."*

The same pattern applies to `site_entry_drafts`.

`PUT .../draft` without `If-Match` must be rejected — always required.

### Publish

Single transaction — no inconsistency possible:

```sql
BEGIN;
  INSERT INTO site_published (site_id, content, published_at)
  SELECT site_id, content, NOW() FROM site_drafts WHERE site_id = ?
  ON CONFLICT (site_id) DO UPDATE
    SET content = EXCLUDED.content,
        published_at = EXCLUDED.published_at;
COMMIT;
```

### Unpublish

Deletes the `*_published` row. Public endpoint returns `404` until next publish.

### Autoguardado

Frontend concern — calls `PUT .../draft` on a timer. Backend has no special logic for it.

### PATCH metadata

`title`, `summary`, `contentSchema` on `PATCH /cms/sites/{id}` is last-write-wins — intentional. Metadata edits are infrequent and manual.

### Concurrent publish

Two simultaneous publish clicks: last one wins. Acceptable for a human action.

### Delete cascade — explicit in use case, not in DDL

Cascades are written explicitly in use case code, not as `ON DELETE CASCADE` in DDL. This keeps the intent visible in code and makes it easy to add side effects (webhook calls, media cleanup) later.

**Delete site:**
```
BEGIN transaction
  DELETE FROM site_entry_published WHERE site_id = ?
  DELETE FROM site_entry_drafts    WHERE site_id = ?
  DELETE FROM site_entries         WHERE site_id = ?
  DELETE FROM site_published       WHERE site_id = ?
  DELETE FROM site_drafts          WHERE site_id = ?
  DELETE FROM sites                WHERE id = ?
COMMIT
```

**Delete entry:**
```
BEGIN transaction
  IF EXISTS (SELECT 1 FROM site_entries WHERE path <@ entry_path AND id != entry_id)
    → return 409 Conflict — delete children first
  DELETE FROM site_entry_published WHERE entry_id = ?
  DELETE FROM site_entry_drafts    WHERE entry_id = ?
  DELETE FROM site_entries         WHERE id = ?
COMMIT
```

---

## 5. Roles and access

| Role | Scope | Access |
|---|---|---|
| `ADMIN` | Global | `/admin/**` — future system operations |
| `EDITOR` | Global | `/cms/**` — manages own sites and entries |

**Default role on `POST /auth/register`:** `EDITOR`.

`VIEWER` is not implemented. Per-site collaboration roles (`SITE_EDITOR`, etc.) live in `site_collaborators` and are deferred.

---

## 6. Endpoints

### 6.1 Authentication
```
POST /auth/register     -- assigns EDITOR role by default
POST /auth/login
```

### 6.2 /cms — authenticated, user's own resources

**Sites**
```
POST   /cms/sites
GET    /cms/sites                           -- user's sites + published state via LEFT JOIN
GET    /cms/sites/{id}
PATCH  /cms/sites/{id}                      -- title, summary, contentSchema (last-write-wins)
DELETE /cms/sites/{id}                      -- cascades all entries and content rows
```

**Site draft/publish**
```
GET    /cms/sites/{id}/draft                -- returns content + version (ETag header)
PUT    /cms/sites/{id}/draft                -- If-Match required; 412 on mismatch; 1MB cap
POST   /cms/sites/{id}/publish
POST   /cms/sites/{id}/unpublish            -- deletes site_published row
```

**Entries**
```
POST   /cms/sites/{id}/entries
GET    /cms/sites/{id}/entries              -- ?type=, ?parentId=root, ?parentId={uuid}
GET    /cms/sites/{id}/entries/{entryId}
PATCH  /cms/sites/{id}/entries/{entryId}    -- type, sort_order, parentId (updates ltree path)
DELETE /cms/sites/{id}/entries/{entryId}    -- 409 if has children
```

**Entry draft/publish**
```
GET    /cms/sites/{id}/entries/{entryId}/draft
PUT    /cms/sites/{id}/entries/{entryId}/draft    -- If-Match required; 1MB cap
POST   /cms/sites/{id}/entries/{entryId}/publish
POST   /cms/sites/{id}/entries/{entryId}/unpublish
```

### 6.3 /public — unauthenticated read-only

```
GET /public/sites/{id}
    -- returns site_published.content + contentSchema
    -- 404 if site_published row does not exist

GET /public/sites/{id}/entries
    -- returns entries with existing site_entry_published row
    -- ?type=post  → queries idx_site_entries_type, joins site_entry_published
    -- ?parentId=root  → nlevel(path) = 1
    -- ?parentId={uuid} → path <@ parentPath AND nlevel = nlevel(parentPath) + 1
    -- ?limit=20 (default), max 200

GET /public/sites/{id}/entries/{entryId}
    -- 404 if site_entry_published row does not exist
```

### 6.4 /admin — reserved, not yet implemented

---

## 7. CMS listing — published state

```sql
SELECT s.*, (sp.site_id IS NOT NULL) AS is_published
FROM sites s
LEFT JOIN site_published sp ON sp.site_id = s.id
WHERE s.owner_user_id = ?
```

Single query. No application-layer merge.

---

## 8. Move entry — ltree path update

```sql
BEGIN;
  UPDATE site_entries
  SET path = CAST(
    :newParentPath || '.' || subpath(path, nlevel(CAST(:oldParentPath AS ltree)))
    AS ltree
  )
  WHERE site_id = :siteId
    AND path <@ CAST(:oldEntryPath AS ltree);
COMMIT;
```

Moves the entry and all descendants atomically.

---

## 9. Content model example

```json
// site_drafts.content — portfolio
{
  "seo":    { "title": "Santiago | Backend Dev", "description": "..." },
  "hero":   { "greeting": "Hi, I'm", "name": "Santiago" },
  "skills": [{ "name": "Java", "slug": "openjdk" }],
  "jobs":   [{ "company": "Acme", "role": "Backend Dev" }]
}

// site_entry_drafts.content — blog post
{
  "title":    "Designing hexagonal APIs",
  "date":     "2026-07-18",
  "body":     "# Introduction\n\nHexagonal architecture...",
  "tags":     ["architecture", "spring"],
  "readTime": "8 min read"
}

// site_entry_drafts.content — series index
{
  "title":       "Hexagonal Architecture Series",
  "description": "A 3-part series on building clean Java backends."
}
```

---

## 10. Business validations

| Entity | Rules |
|---|---|
| Site | `title` required. Ownership validated in use case — not only in authentication |
| SiteEntry | `type` required, non-blank. Backend does not validate its value |
| Content | Valid JSON object (enforced by DB CHECK). Serialized size ≤ 1MB (enforced in use case) |

---

## 11. Public endpoint protection

### Caching — required before production
Spring Cache + Caffeine, `expireAfterWrite=1h`. Evict on publish, unpublish, and delete.

```java
@Cacheable(value = "published-site", key = "#id")
public PublicSiteResponse getPublished(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void publish(UUID id) { ... }  // also on unpublish and delete
```

Migrate to Redis when running multiple instances.

### Rate limiting — required before production
Bucket4j:
- `/public/**` — ~20 req/s per IP → `429`
- `/cms/**` — ~10 req/s per authenticated user → `429`

---

## 12. Package structure

```
src/main/java/com/cms/
├── domain/
│   ├── model/
│   │   ├── user/
│   │   │   └── User.java
│   │   └── site/
│   │       ├── Site.java
│   │       └── SiteEntry.java
│   │       ← no PublicationStatus enum — state = row existence
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

## 13. Flyway migration order

```
V1__create_users_schema.sql
  -- users, roles, user_roles, user_credentials, user_oauth_providers, user_profiles
  -- plan_id UUID NULL on users, no FK

V2__create_sites_schema.sql
  -- sites

V3__create_site_entries_schema.sql
  -- CREATE EXTENSION IF NOT EXISTS ltree
  -- site_entries with path LTREE, UNIQUE (site_id, path), indexes

V4__create_draft_published_schema.sql
  -- site_drafts, site_published, site_entry_drafts, site_entry_published
  -- CHECK (jsonb_typeof(content) = 'object') on all four

V5__create_future_tables.sql
  -- site_collaborators (SITE_* roles)
  -- media_assets, site_domains, site_webhooks, plans
```

---

## 14. Deferred decisions

These are not deferred because they are unimportant — they are deferred because implementing them without real users would be speculative.

| Feature | Trigger to implement |
|---|---|
| `content_types` + `content_fields` schema builder | Second client type with different schema needs |
| Row-Level Security (RLS) | Enterprise client due diligence |
| i18n | Concrete client request |
| Preview tokens | Client with static-generated frontend |
| Webhooks logic | Client with Netlify/Vercel deployment |
| Custom domains logic | Client request |
| Media upload logic | Client that cannot use external CDN |
| `site_collaborators` logic | Per-site team collaboration request |
| OAuth2 social login | Resolve email-merge edge case first |
| JWT refresh/revocation | Define strategy (blocklist vs short-lived tokens) |
| `plans` + `plan_id` FK | Monetization launch |
