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
- [x] Drop `site_publications` from initial scope
- [x] Confirm single API serves both CMS UI and each user's frontend
- [x] Rename portfolio → site for generality
- [x] Replace typed content model with generic Map<String, Object> (Option C hybrid)
- [x] Replace PostCard with generic Entry entity
- [x] Separate draft and published content into distinct documents
- [x] Replace /admin prefix with /cms for user-owned resource endpoints

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| System type | Headless CMS API — serves JSON, not HTML |
| Consumers | Two types: CMS UI (admin) + each user's own frontend (public) |
| Deployment | Single API — no microservice split needed at this stage |
| Users | Multi-user; anyone can register and manage their own content |
| Public identification | UUID only — no slug. Each user's frontend is responsible for its own URLs |
| Draft/publish | Required — saving never auto-publishes. Draft and published are separate documents |
| Autoguardado | Frontend responsibility — calls `PUT /cms/sites/{id}/entries/{entryId}/draft` periodically. Backend has no special logic for it |
| Publication history | Out of scope for now |
| User isolation | Each site has `ownerUserId`; use cases must validate ownership |
| Content model | `Map<String, Object>` — backend does not interpret content structure |
| Typed entities | Only what the backend needs to manage: `Site` metadata, `Entry` metadata, draft/published documents |
| Entry | Generic content unit with its own page. Type is a frontend label (`post`, `project`, `page`, etc.) — backend does not distinguish |
| Series | Entries reference a parent entry via `parentId`. No separate collection needed |
| Naming — user endpoints | `/cms/**` — user's own resources |
| Naming — system admin | `/admin/**` — reserved for future system-level operations (user management, global metrics) |
| Naming — public | `/public/**` — unauthenticated read-only |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It acts as a content management layer for any frontend that wants to integrate against it. It does not render HTML — it delivers JSON.

A **site** is the core content unit. It can represent a portfolio, a personal blog, a product page, a CV, or anything else. A site contains **entries** — any piece of content that needs its own page (posts, projects, series, etc.).

The system serves **two types of consumers from a single API**:
- **CMS UI** (admin frontend) — logs in, edits drafts, publishes content
- **Each user's frontend** — reads the published version with no authentication

### Target capabilities

1. **Register users** and authenticate them with JWT.
2. **Manage sites and entries** from private, per-user endpoints (`/cms/**`).
3. **Draft/publish** — edit without breaking what the frontend is already serving.
4. **Expose published content** through public endpoints with no authentication.
5. **Generic content** — backend stores and serves `Map<String, Object>`, frontend defines structure.

---

## 2. Current project state

- Hexagonal architecture base already exists.
- Spring Security + JWT already in place.
- Hybrid persistence already configured: PostgreSQL + MongoDB.
- `users` table already lives in PostgreSQL.

---

## 3. Architecture

## 3.1 Functional domain split

### A. Identity & Access
- Users, JWT authentication, roles (`ADMIN`, `EDITOR`, `VIEWER`)

### B. Site Management
- Sites, entries, draft/publish lifecycle

### C. Public Content Delivery
- Public endpoints, always serves published documents

---

## 3.2 Hexagonal architecture applied

### Domain models
- `Site`
- `SiteEntry`
- `PublicationStatus`

### Input ports
- `CreateSiteUseCase`
- `UpdateSiteDraftUseCase`
- `PublishSiteUseCase`
- `GetSitePublicUseCase`
- `CreateEntryUseCase`
- `UpdateEntryDraftUseCase`
- `PublishEntryUseCase`
- `GetEntryPublicUseCase`

### Outbound ports
- `SiteRepository`
- `SiteDraftRepository`
- `SitePublishedRepository`
- `SiteEntryRepository`
- `SiteEntryDraftRepository`
- `SiteEntryPublishedRepository`

---

## 3.3 Persistence strategy

- **PostgreSQL**: identity and metadata — `users`, `sites`, `site_entries`. Strong integrity, ownership queries, status filtering.
- **MongoDB**: all content — `site_drafts`, `site_published`, `site_entry_drafts`, `site_entry_published`. Flexible schema, no migrations needed when content structure changes.

---

## 4. Domain model

## 4.1 `Site` — PostgreSQL

The site is the root publishable unit. Metadata lives in PostgreSQL; content lives in MongoDB.

```text
Site
- id: UUID
- ownerUserId: Long
- title: String
- summary: String
- status: DRAFT | PUBLISHED | ARCHIVED
- createdAt: Instant
- updatedAt: Instant
```

> No `slug` — identified by UUID only.

---

## 4.2 `SiteEntry` — PostgreSQL

An entry is any piece of content within a site that has its own page. The backend manages its lifecycle; the backend does not interpret its content.

```text
SiteEntry
- id: UUID
- siteId: UUID
- parentId: UUID?    ← null if direct child of site; points to another entry if part of a series
- type: String       ← frontend label: "post", "project", "page", "series", etc.
- published: boolean
- order: Integer?
- createdAt: Instant
- updatedAt: Instant
```

**Series:** an entry with `type: "series"` is just another entry. Its children set `parentId` to its `id`. No separate collection or model needed.

---

## 4.3 Content documents — MongoDB

Content is always `Map<String, Object>`. The backend stores and serves it without interpreting its structure.

### `site_drafts`
```text
SiteDraftDocument
- id: ObjectId
- siteId: UUID
- content: Map<String, Object>   ← free, frontend defines structure
- updatedAt: Instant
```

One document per site. Overwritten on every save.

### `site_published`
```text
SitePublishedDocument
- id: ObjectId
- siteId: UUID
- content: Map<String, Object>   ← snapshot copied from draft at publish time
- publishedAt: Instant
```

One document per site. Overwritten on every publish.

### `site_entry_drafts`
```text
SiteEntryDraftDocument
- id: ObjectId
- entryId: UUID
- siteId: UUID
- content: Map<String, Object>   ← free
- updatedAt: Instant
```

One document per entry. Overwritten on every save (autoguardado hits this).

### `site_entry_published`
```text
SiteEntryPublishedDocument
- id: ObjectId
- entryId: UUID
- siteId: UUID
- content: Map<String, Object>   ← snapshot copied from draft at publish time
- publishedAt: Instant
```

One document per entry. Overwritten on every publish.

---

## 4.4 Example document

```json
// site_drafts — the CMS UI writes whatever structure the frontend expects
{
  "siteId": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "content": {
    "seo": {
      "title": "Santiago Acevedo | Backend Developer",
      "description": "Java, Spring Boot, hexagonal architecture"
    },
    "hero": {
      "greeting": "Hi, I'm",
      "name": "Santiago Acevedo",
      "tagline": "Backend developer building APIs and scalable systems."
    },
    "skills": [
      { "name": "Java", "slug": "openjdk", "category": "BACKEND" }
    ],
    "jobs": [
      {
        "company": "Acme",
        "role": "Backend Developer",
        "period": "2022 — Present",
        "highlights": ["Built resilient REST APIs"]
      }
    ]
  },
  "updatedAt": "2026-06-18T10:00:00Z"
}

// site_entry_drafts — a blog post
{
  "entryId": "c1d2e3f4-...",
  "siteId": "b7fd3b44-...",
  "content": {
    "title": "Designing hexagonal APIs",
    "date": "2026-06-18",
    "body": "# Introduction\n\nHexagonal architecture separates...",
    "tags": ["architecture", "spring"],
    "readTime": "8 min read",
    "banner": "https://cdn.example.com/banner.png"
  },
  "updatedAt": "2026-06-18T10:00:00Z"
}

// site_entry_drafts — a series index
{
  "entryId": "series-uuid-...",
  "siteId": "b7fd3b44-...",
  "content": {
    "title": "Hexagonal Architecture Series",
    "description": "A 3-part series on building clean Java backends.",
    "banner": "https://cdn.example.com/series-banner.png"
  },
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

---

## 5. Persistence model — PostgreSQL tables

### User identity schema

```text
users                          ← core identity, never grows
- id           BIGSERIAL PK
- email        VARCHAR(100) NOT NULL UNIQUE
- name         VARCHAR(100) NOT NULL
- enabled      BOOLEAN NOT NULL DEFAULT TRUE
- created_at   TIMESTAMP NOT NULL
- updated_at   TIMESTAMP NOT NULL

roles
- id           SERIAL PK
- name         VARCHAR(50) NOT NULL UNIQUE  -- ADMIN | EDITOR | VIEWER

user_roles
- user_id      BIGINT NOT NULL FK users(id)
- role_id      INT NOT NULL FK roles(id)
- PK (user_id, role_id)

user_credentials
- user_id      BIGINT PK FK users(id)
- password_hash VARCHAR(255) NOT NULL
- created_at   TIMESTAMP NOT NULL

user_oauth_providers
- id           BIGSERIAL PK
- user_id      BIGINT NOT NULL FK users(id)
- provider     VARCHAR(30) NOT NULL   -- GOOGLE | GITHUB
- provider_user_id VARCHAR(255) NOT NULL
- created_at   TIMESTAMP NOT NULL
- UNIQUE (provider, provider_user_id)

user_profiles
- user_id      BIGINT PK FK users(id)
- last_name    VARCHAR(100) NULL
- phone        VARCHAR(30) NULL
- bio          TEXT NULL
- avatar_url   VARCHAR(500) NULL
- website      VARCHAR(255) NULL
- metadata     JSONB NULL DEFAULT '{}'
- updated_at   TIMESTAMP NOT NULL
```

### Sites and entries

```text
sites
- id                    UUID PK
- owner_user_id         BIGINT NOT NULL FK users(id)
- title                 VARCHAR(150) NOT NULL
- summary               VARCHAR(255) NULL
- status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
- created_at            TIMESTAMP NOT NULL
- updated_at            TIMESTAMP NOT NULL

site_entries
- id                    UUID PK
- site_id               UUID NOT NULL FK sites(id)
- parent_id             UUID NULL FK site_entries(id)   ← self-reference for series
- type                  VARCHAR(50) NOT NULL             ← frontend label, not validated by backend
- published             BOOLEAN NOT NULL DEFAULT FALSE
- order                 INT NULL
- created_at            TIMESTAMP NOT NULL
- updated_at            TIMESTAMP NOT NULL
```

### Optional table `media_assets` (future)

```text
media_assets
- id UUID PK
- owner_user_id BIGINT NOT NULL
- storage_key VARCHAR(255) NOT NULL UNIQUE
- public_url VARCHAR(500) NOT NULL
- alt VARCHAR(255) NULL
- mime_type VARCHAR(100) NOT NULL
- created_at TIMESTAMP NOT NULL
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
  - index: (siteId, entryId)
```

---

## 7. Endpoints

## 7.1 Authentication

```
POST /auth/register
POST /auth/login
```

## 7.2 /cms — authenticated user's resources

### Sites
```
POST   /cms/sites
GET    /cms/sites                          ← only the authenticated user's sites
GET    /cms/sites/{id}
PATCH  /cms/sites/{id}                     ← metadata only (title, summary)
DELETE /cms/sites/{id}
```

### Site content (draft/publish)
```
GET    /cms/sites/{id}/draft               ← returns site_drafts document
PUT    /cms/sites/{id}/draft               ← overwrites site_drafts (autoguardado hits this)
POST   /cms/sites/{id}/publish             ← copies draft → site_published
POST   /cms/sites/{id}/unpublish
```

### Entries
```
POST   /cms/sites/{id}/entries
GET    /cms/sites/{id}/entries             ← all entries (supports ?type=post, ?parentId=x)
GET    /cms/sites/{id}/entries/{entryId}
PATCH  /cms/sites/{id}/entries/{entryId}   ← metadata only (type, order, parentId)
DELETE /cms/sites/{id}/entries/{entryId}
```

### Entry content (draft/publish)
```
GET    /cms/sites/{id}/entries/{entryId}/draft
PUT    /cms/sites/{id}/entries/{entryId}/draft     ← autoguardado hits this
POST   /cms/sites/{id}/entries/{entryId}/publish
POST   /cms/sites/{id}/entries/{entryId}/unpublish
```

## 7.3 /public — unauthenticated read-only

```
GET /public/sites/{id}                            ← published site content
GET /public/sites/{id}/entries                    ← published entries (?type=post, ?parentId=x)
GET /public/sites/{id}/entries/{entryId}          ← single published entry
```

## 7.4 /admin — reserved for future system-level operations

```
(not implemented yet)
GET /admin/users
GET /admin/sites
```

---

## 8. Editorial flow

### Site draft/publish

1. User creates a site → `sites` row inserted, `site_drafts` document created empty.
2. User edits content → `PUT /cms/sites/{id}/draft` overwrites `site_drafts`.
3. Autoguardado → frontend calls `PUT /cms/sites/{id}/draft` periodically.
4. User publishes → `POST /cms/sites/{id}/publish` copies `site_drafts.content` into `site_published`.
5. Public endpoint reads `site_published` — never the draft.

### Entry draft/publish

Same flow, independent from the site:

1. User creates entry → `site_entries` row inserted, `site_entry_drafts` document created empty.
2. User edits → `PUT /cms/sites/{id}/entries/{entryId}/draft` overwrites `site_entry_drafts`.
3. User publishes → `POST /cms/sites/{id}/entries/{entryId}/publish` copies draft → `site_entry_published`, sets `site_entries.published = true`.
4. Public endpoint reads `site_entry_published`.

### Series

1. Create an entry with `type: "series"`.
2. Create child entries with `parentId` set to the series entry id.
3. `GET /public/sites/{id}/entries?parentId={seriesId}` returns all published children ordered by `order`.

---

## 9. Business validations

### Site
- `title` required
- Ownership validated in use case — not just authentication
- Only `ADMIN` or `EDITOR` roles may write

### SiteEntry
- `type` required, non-blank — backend does not validate its value, only that it exists
- `parentId` if provided must reference an entry in the same site
- `order` optional

### Content documents
- `content` must be a valid JSON object — not null, not an array
- Backend does not validate internal structure

---

## 10. Suggested package structure

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
│       │   │   └── GetSitePublicUseCase.java
│       │   └── entry/
│       │       ├── CreateEntryUseCase.java
│       │       ├── UpdateEntryDraftUseCase.java
│       │       ├── PublishEntryUseCase.java
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
│       │   └── GetSitePublicService.java
│       └── entry/
│           ├── CreateEntryService.java
│           ├── UpdateEntryDraftService.java
│           ├── PublishEntryService.java
│           └── GetEntryPublicService.java
└── adapters/
    ├── in/web/
    │   ├── controller/
    │   │   ├── AuthController.java
    │   │   ├── MeSiteController.java
    │   │   ├── MeEntryController.java
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

`GET /public/sites/{id}` is unauthenticated. Two mitigations required before production:

### Response caching — implement now
**Stack:** Spring Cache + Caffeine (in-memory). Migrate to Redis when running multiple instances.

```java
@Cacheable(value = "published-site", key = "#id")
public PublicSiteResponse getPublished(UUID id) { ... }

@CacheEvict(value = "published-site", key = "#id")
public void publish(UUID id) { ... }
```

### Rate limiting — implement now
**Stack:** Bucket4j. ~20 req/s per IP on `/public/**`. Returns `429` when exceeded. In-memory to start, Redis-backed when scaling.

### UUID as passive obscurity — already in place
UUID v4 space is 2^122 — brute force enumeration is infeasible.

### CDN / reverse proxy — future
Cloudflare or nginx for volumetric DDoS. Out of scope for now.

---

## 12. Image strategy

### Phase 1
- Store URLs only — CDN, Cloudinary, S3, or static assets

### Phase 2 (future)
- Add `media` module, upload to backend, store metadata in `media_assets`, return public URL

---

## 13. Confirmed decisions

### Keep
- Normalized user identity schema: `users`, `user_credentials`, `user_oauth_providers`, `user_roles`, `user_profiles`
- `user_profiles` with `metadata JSONB` for free-form fields
- `site` as the generic content unit
- `entry` as the generic child content unit with its own page
- Draft and published content in separate MongoDB documents — never mixed in one object
- `Map<String, Object>` for all content — backend does not interpret structure
- `/cms/**` for user-owned resources, `/public/**` for read-only, `/admin/**` reserved for system ops
- Autoguardado is a frontend concern — backend just exposes `PUT .../draft`
- Series via `parentId` on entries — no separate collection

### Avoid
- Typed content classes for sections (`SeoBlock`, `HeroBlock`, etc.) — use `Map<String, Object>`
- Storing draft and published content in the same document
- `/admin` prefix for user content management endpoints
- Slug as identifier — UUID only
- Exposing draft documents on public endpoints
- Validating content structure in the backend

### Pending decisions
- **OAuth2 social login** (Google, GitHub): schema ready. Resolve email-merge edge case before implementing.
- **Pricing / plan tiers**: `user_roles` supports this. Add `plans` table when needed.
- **Projects with individual pages**: follow entry pattern — create with `type: "project"`, same endpoints.

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
PUT  /cms/sites/{id}/draft
POST /cms/sites/{id}/publish
POST /cms/sites/{id}/entries
GET  /cms/sites/{id}/entries
GET  /cms/sites/{id}/entries/{entryId}/draft
PUT  /cms/sites/{id}/entries/{entryId}/draft
POST /cms/sites/{id}/entries/{entryId}/publish
GET  /public/sites/{id}
GET  /public/sites/{id}/entries
GET  /public/sites/{id}/entries/{entryId}
```

---

## 15. Confirmed architecture

1. **PostgreSQL** for users + site/entry metadata
2. **MongoDB** for all content — draft and published as separate documents
3. **Hexagonal architecture** with ports for each use case
4. **Generic content model** — `Map<String, Object>`, frontend defines structure
5. **Multi-user headless CMS** — API serves JSON, each user integrates their own frontend
6. **Single API** — `/cms`, `/public`, `/auth`; `/admin` reserved for system ops

---

## 16. Suggested next steps

1. Rewrite `V1__create_users_table.sql` — full normalized user schema
2. Update `UserEntity` — remove `UserDetails` implementation from JPA entity
3. Flyway `V2__create_sites_table.sql`
4. Flyway `V3__create_site_entries_table.sql`
5. MongoDB documents: `SiteDraftDocument`, `SitePublishedDocument`, `SiteEntryDraftDocument`, `SiteEntryPublishedDocument`
6. Domain models: `Site`, `SiteEntry`
7. Output ports and adapters
8. Use cases
9. Controllers: `AuthController`, `MeSiteController`, `MeEntryController`, `PublicSiteController`
