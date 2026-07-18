# Architecture and data model — Headless CMS

## Checklist

- [x] Define the functional goal of the CMS
- [x] Propose an architecture aligned with the existing hexagonal structure
- [x] Separate concerns between public reading, administration, and persistence
- [x] Design a data model covering `Photo`, `Fact`, `Skill`, `Job`, `Project`, and `Post`
- [x] Ground the proposal in the current stack: Spring Boot + PostgreSQL + MongoDB
- [x] Define concrete endpoints, packages, and evolution path
- [x] Confirm headless multi-user model
- [x] Decide on UUID-only identification (no slug)
- [x] Confirm draft/publish as a required workflow
- [x] Drop `site_publications` from initial scope
- [x] Confirm single API serves both CMS UI and each user's frontend
- [x] Rename portfolio → site for generality

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| System type | Headless CMS API — serves JSON, not HTML |
| Consumers | Two types: CMS UI (admin) + each user's own frontend (public) |
| Deployment | Single API — no microservice split needed at this stage |
| Users | Multi-user; anyone can register and manage their own content |
| Public identification | UUID only — no slug. Each user's frontend is responsible for its own URLs |
| Draft/publish | Required — saving never auto-publishes |
| Publication history (`site_publications`) | Out of scope for now |
| User isolation | Each site has `ownerUserId`; use cases must validate ownership |
| Content storage | MongoDB — flexible schema, embedded per version |
| Identity and metadata | PostgreSQL — strong integrity |
| Naming | `site` / `sites` — generic, not tied to portfolio use case |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It acts as a content management layer for any frontend that wants to integrate against it. It does not render HTML — it delivers JSON.

A **site** is the core content unit. It can represent a portfolio, a personal blog, a product page, a CV, or any other structured content a user wants to publish. The CMS does not care what the site represents — it manages its content and exposes it.

The system serves **two types of consumers from a single API**:
- **CMS UI** (admin frontend) — logs in, edits drafts, publishes content
- **Each user's frontend** — reads the published version with no authentication

The CMS and each user's frontend are separate applications. The CMS exposes an API; each user integrates their own frontend however they prefer.

### Target capabilities

1. **Register users** and authenticate them with JWT.
2. **Manage content** from private, per-user endpoints.
3. **Draft/publish** — edit without breaking what the frontend is already serving.
4. **Expose published content** through public endpoints with no authentication.
5. **Maintain schema flexibility** in MongoDB so different sites can have different sections.

---

## 2. Current project state

- Hexagonal architecture base already exists.
- Spring Security + JWT already in place for the admin zone.
- Hybrid persistence already configured:
  - **PostgreSQL**
  - **MongoDB**
- `users` table already lives in PostgreSQL.

This confirms the right split for this CMS:
- **PostgreSQL for identity, control, metadata, and strong integrity**
- **MongoDB for editorial site content**, because its sections are flexible and document-oriented

---

## 3. Architecture proposal

## 3.1 Functional domain split

The system divides into 3 conceptual areas:

### A. Identity & Access
- CMS users
- JWT authentication
- Roles (`ADMIN`, `EDITOR`, `VIEWER`)

### B. Site Management
- Sites
- Content sections
- Editorial validations
- Draft/published state

### C. Public Content Delivery
- Public endpoints for frontends
- Payload optimized for consumption
- Always serves the published version

---

## 3.2 Hexagonal architecture applied

### Domain
Business models and rules:
- `Site`
- `SiteContent`
- `Photo`
- `Fact`
- `Skill`
- `Job`
- `Project`
- `Post`
- `PublicationStatus`

### Input ports
Use cases as interfaces:
- `CreateSiteUseCase`
- `UpdateSiteDraftUseCase`
- `PublishSiteUseCase`
- `GetSitePublicUseCase`
- `GetSiteAdminUseCase`

### Application layer
Use case implementations:
- Orchestrate validations
- Decide what goes to PostgreSQL and what goes to MongoDB
- Control publishing and versioning

### Inbound adapters
- Admin REST controllers
- Public REST controllers
- JWT authentication

### Outbound adapters
- JPA repositories for relational metadata
- MongoDB repositories for document content
- Future storage adapter if file uploads are added

---

## 3.3 Persistence strategy

### PostgreSQL
Store here what requires:
- Strong uniqueness
- Relationships
- Simple auditing
- Administrative filtering

### MongoDB
Store here what is:
- Structured but flexible content
- Embedded sections
- Lists of skills, jobs, projects, and posts
- Draft/published snapshots

---

## 4. Recommended domain model

## 4.1 Main aggregate: `Site`

`Site` represents a publishable content unit. It can be a portfolio, blog, product page, or anything else.

### Responsibilities
- Site identity (UUID)
- Owner (`ownerUserId`)
- Publication status
- Reference to the published version
- Timestamps

### Conceptual model

```text
Site
- id: UUID
- ownerUserId: Long
- title: String
- summary: String
- status: DRAFT | PUBLISHED | ARCHIVED
- currentDraftVersion: Int
- currentPublishedVersion: Int?
- createdAt: Instant
- updatedAt: Instant
```

> No `slug` — sites are identified exclusively by UUID. The public URL is the responsibility of each user's frontend, not this API.

> `title` and `summary` are basic metadata. All heavy content lives in MongoDB.

---

## 4.2 Aggregate/document: `SiteContent`

This document holds the actual editable content for a given site version.

### Conceptual model

```text
SiteContent
- id: ObjectId
- siteId: UUID
- version: Int
- seo: SeoBlock
- hero: HeroBlock
- about: AboutBlock
- skills: List<Skill>
- jobs: List<Job>
- projects: List<Project>
- posts: List<PostCard>
- createdAt: Instant
- updatedAt: Instant
```

### Why embedded

Site frontends typically need to load most content in a single query. Additionally:
- Lists are not massive
- Everything belongs to the same visual aggregate
- High cohesion between sections

---

## 4.3 Reusable value objects

### `Photo`

```text
Photo
- src: String
- alt: String
```

**Rules:**
- `src` required
- `alt` required
- Ideally an absolute URL or path resolved by a media service

---

### `Fact`

```text
Fact
- label: String
- value: String
```

**Rules:**
- `label` short and stable

Future suggestion — add a `key` for internal consistency:

```text
Fact
- key: String   // location, experience, timezone
- label: String // Location
- value: String // Colombia
```

---

### `Skill`

```text
Skill
- name: String
- slug: String
- category: BACKEND | FRONTEND | CLOUD_DEVOPS
- fallbackIcon: String?
- description: String
```

**Rules:**
- `slug` intended for simple-icons
- `fallbackIcon` optional
- `name + category` could be unique within the same site

---

### `Job`

```text
Job
- company: String
- logoSlug: String?
- role: String
- period: String
- location: String
- highlights: List<String>
- tech: List<String>
```

**Rules:**
- `highlights`: minimum 2, recommended maximum 4
- `tech`: short list of tags
- Add `order` later if automatic ordering is needed

---

### `Project`

```text
Project
- name: String
- filename: String
- description: String
- tech: List<String>
- github: String
- live: String?
- preview: String?
```

**Rules:**
- `github` required and must be a valid URL
- `live` nullable in persistence — the frontend can convert `null` to `#` if needed visually
- Do not persist `#` — it does not represent a real resource

---

### `PostCard`

Card embedded in `site_contents`. References the full post document by `postId`.

```text
PostCard
- postId: String
- title: String
- date: LocalDate
- excerpt: String
- tags: List<String>
- readTime: String
- banner: String?
```

**Rules:**
- `date` must persist as a real date, not just a string
- Serialize as `YYYY-MM-DD`
- `tags` must never be `null`, though an empty list is fine

---

## 4.4 Additional recommended blocks

### `SeoBlock`

```text
SeoBlock
- title: String
- description: String
- canonicalUrl: String?
- ogImage: String?
```

### `HeroBlock`

```text
HeroBlock
- greeting: String
- name: String
- tagline: String
- primaryCtaLabel: String
- primaryCtaUrl: String
- secondaryCtaLabel: String?
- secondaryCtaUrl: String?
```

### `AboutBlock`

```text
AboutBlock
- title: String
- description: String
- photos: List<Photo>
- facts: List<Fact>
```

`Photo` and `Fact` are always scoped to `AboutBlock` — they do not float as orphan objects.

---

## 5. Persistence model

## 5.1 PostgreSQL: relational tables

### User identity schema

The current `users` table is replaced by a normalized schema that supports local auth, OAuth2 social login, multiple roles per user, and flexible profile data. The old single-table approach had `password NOT NULL`, which cannot support OAuth users.

```text
users                          ← core identity, never grows
- id           BIGSERIAL PK
- email        VARCHAR(100) NOT NULL UNIQUE
- name         VARCHAR(100) NOT NULL
- enabled      BOOLEAN NOT NULL DEFAULT TRUE
- created_at   TIMESTAMP NOT NULL
- updated_at   TIMESTAMP NOT NULL

roles                          ← role catalog
- id           SERIAL PK
- name         VARCHAR(50) NOT NULL UNIQUE  -- ADMIN | EDITOR | VIEWER

user_roles                     ← many-to-many: a user can hold multiple roles
- user_id      BIGINT NOT NULL FK users(id)
- role_id      INT NOT NULL FK roles(id)
- PK (user_id, role_id)

user_credentials               ← local email/password login
- user_id      BIGINT PK FK users(id)
- password_hash VARCHAR(255) NOT NULL
- created_at   TIMESTAMP NOT NULL

user_oauth_providers           ← Google, GitHub, etc.
- id           BIGSERIAL PK
- user_id      BIGINT NOT NULL FK users(id)
- provider     VARCHAR(30) NOT NULL   -- GOOGLE | GITHUB
- provider_user_id VARCHAR(255) NOT NULL
- created_at   TIMESTAMP NOT NULL
- UNIQUE (provider, provider_user_id)

user_profiles                  ← enriched profile data, evolves freely
- user_id      BIGINT PK FK users(id)
- last_name    VARCHAR(100) NULL
- phone        VARCHAR(30) NULL
- bio          TEXT NULL
- avatar_url   VARCHAR(500) NULL
- website      VARCHAR(255) NULL
- metadata     JSONB NULL DEFAULT '{}'   ← free-form fields, no migration needed
- updated_at   TIMESTAMP NOT NULL
```

**Why this split:**
- `users` stays stable — only fields that exist for every user, always
- `user_credentials` is absent for OAuth-only users; `user_oauth_providers` is absent for local-only users — no nullable hacks
- `user_roles` supports multiple roles per user and future pricing/plan-based permission models without touching the schema
- `user_profiles` separates identity from enriched data; `metadata JSONB` absorbs optional or experimental fields without Flyway migrations — promote to a column when a field becomes universal
- All identity tables stay in PostgreSQL for strong relational integrity; MongoDB is reserved for editorial site content only

### New table `sites`

```text
sites
- id UUID PK
- owner_user_id BIGINT NOT NULL FK users(id)
- title VARCHAR(150) NOT NULL
- summary VARCHAR(255) NULL
- status VARCHAR(20) NOT NULL
- current_draft_version INT NOT NULL DEFAULT 1
- current_published_version INT NULL
- created_at TIMESTAMP NOT NULL
- updated_at TIMESTAMP NOT NULL
```

> No `slug` column — sites are identified by UUID only.

### Table `site_publications` — **out of scope for now**

Intentionally omitted. Do not add until there is a concrete use case.

### Optional table `media_assets`

Only if file uploads to the backend are added later, instead of storing external URLs.

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

## 5.2 MongoDB: document collections

### Collection `site_contents`

Suggested document:

```json
{
  "siteId": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "version": 3,
  "seo": {
    "title": "Santiago Acevedo | Backend Developer",
    "description": "Java, Spring Boot, hexagonal architecture and cloud",
    "canonicalUrl": "https://your-domain.dev",
    "ogImage": "https://cdn.example.com/og.png"
  },
  "hero": {
    "greeting": "Hi, I'm",
    "name": "Santiago Acevedo",
    "tagline": "Backend developer building APIs, integrations and scalable systems.",
    "primaryCtaLabel": "View projects",
    "primaryCtaUrl": "/projects",
    "secondaryCtaLabel": "Contact",
    "secondaryCtaUrl": "/contact"
  },
  "about": {
    "title": "About me",
    "description": "Backend developer focused on Java, Spring, and API design.",
    "photos": [
      {
        "src": "https://cdn.example.com/profile.jpg",
        "alt": "Profile photo of Santiago Acevedo"
      }
    ],
    "facts": [
      { "label": "Location", "value": "Colombia" },
      { "label": "Experience", "value": "4+ years" }
    ]
  },
  "skills": [
    {
      "name": "Java",
      "slug": "openjdk",
      "category": "BACKEND",
      "description": "Primary language · 4+ years"
    }
  ],
  "jobs": [
    {
      "company": "Acme",
      "logoSlug": "spring",
      "role": "Backend Developer",
      "period": "2022 — Present",
      "location": "Remote",
      "highlights": [
        "Built resilient REST APIs",
        "Improved query performance by 40%"
      ],
      "tech": ["Java", "Spring Boot", "PostgreSQL"]
    }
  ],
  "projects": [
    {
      "name": "Event Stream Engine",
      "filename": "EventStreamEngine.java",
      "description": "Engine for domain event processing.",
      "tech": ["Java", "Kafka", "Docker"],
      "github": "https://github.com/user/repo",
      "live": null,
      "preview": "https://cdn.example.com/project-preview.png"
    }
  ],
  "posts": [
    {
      "postId": "64f1a2b3c4d5e6f7a8b9c0d1",
      "title": "Designing hexagonal APIs",
      "date": "2026-06-18",
      "excerpt": "What to separate into domain, application, and adapters.",
      "tags": ["architecture", "spring", "hexagonal"],
      "readTime": "8 min read",
      "banner": "https://cdn.example.com/post-banner.png"
    }
  ],
  "createdAt": "2026-06-18T10:00:00Z",
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

### Suggested MongoDB indexes on `site_contents`

- Compound unique index: `(siteId, version)`
- Simple index: `siteId`

---

## 6. Embedded vs separate collections — the expandable content pattern

### The core pattern

`site_contents` always holds the **card** — the minimum data needed to render a list item. When a section needs its own page, a separate collection holds the **full document**. The card references the full document by ID.

This pattern is consistent, cheap to extend, and requires no changes to the existing document structure when a new section grows.

```
site_contents
  └── posts[]
        └── postId  ──────────────→  site_posts { _id, body, ... }
  └── projects[]
        └── projectId (future) ───→  site_projects { _id, body, ... }
```

**The rule:** if a section needs any of these, extract it into its own collection:
- Individual URL per item
- Pagination
- Independent draft/publish per item
- Content too large to embed (e.g. full markdown body)

Otherwise, keep it embedded.

---

### What is separated from the start

#### `site_posts`

Posts need individual URLs, independent draft/publish, and a full markdown body that does not belong embedded in the site snapshot.

```json
// Card embedded in site_contents
{
  "postId": "64f1a2b3c4d5e6f7a8b9c0d1",
  "title": "Designing hexagonal APIs",
  "date": "2026-06-18",
  "excerpt": "What to separate into domain, application, and adapters.",
  "tags": ["architecture", "spring", "hexagonal"],
  "readTime": "8 min read",
  "banner": "https://cdn.example.com/post-banner.png"
}

// Full document in site_posts
{
  "_id": "64f1a2b3c4d5e6f7a8b9c0d1",
  "siteId": "b7fd3b44-...",
  "title": "Designing hexagonal APIs",
  "date": "2026-06-18",
  "excerpt": "What to separate into domain, application, and adapters.",
  "body": "# Introduction\n\nHexagonal architecture separates...",
  "tags": ["architecture", "spring", "hexagonal"],
  "readTime": "8 min read",
  "banner": "https://cdn.example.com/post-banner.png",
  "published": true,
  "createdAt": "2026-06-18T10:00:00Z",
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

Suggested indexes on `site_posts`:
- `siteId` (simple)
- `(siteId, published)` (compound — for public listing)

---

### What stays embedded for now

| Section | Reason to stay embedded |
|---|---|
| `skills` | Short stable catalog, no individual URL needed |
| `jobs` | Grows very slowly, no individual URL needed |
| `projects` | Card data is self-contained for now |
| `hero`, `about`, `seo` | Unique blocks, not lists |

---

### How to extend when a section outgrows embedded

When `projects` (or any other section) needs individual pages, the migration is:

1. Create `site_projects` collection with the same pattern as `site_posts`
2. Add `projectId` to each project card in `site_contents`
3. Add public endpoint `GET /public/sites/{id}/projects/{projectId}`
4. Add admin endpoints for CRUD + publish on `site_projects`

No changes to the rest of the system. The card in `site_contents` gains one field (`projectId`) and the full content moves to the new collection.

---

## 7. Endpoints

## 7.1 Public

### Site
- `GET /public/sites/{id}` — full published site with post cards embedded. No auth required.

> `id` is a UUID. There is no slug — each user's frontend is responsible for constructing friendly URLs.

### Posts
- `GET /public/sites/{id}/posts` — paginated list of published post cards
- `GET /public/sites/{id}/posts/{postId}` — full post with body

> The frontend builds the post page URL from the `postId` already present in the site card. If friendly URLs like `/blog/designing-hexagonal-apis` are needed, the frontend generates the slug from the title client-side — the API does not need to know about it.

---

## 7.2 Authentication

- `POST /auth/register` — open registration
- `POST /auth/login` — returns JWT

---

## 7.3 Admin

### Sites
- `POST /admin/sites`
- `GET /admin/sites` — **returns only sites owned by the authenticated user**, never all sites in the system
- `GET /admin/sites/{id}`
- `PATCH /admin/sites/{id}/metadata`

### Draft content
- `GET /admin/sites/{id}/draft`
- `PUT /admin/sites/{id}/draft`

### Publishing
- `POST /admin/sites/{id}/publish`
- `POST /admin/sites/{id}/unpublish`

### Posts
- `POST /admin/sites/{id}/posts`
- `GET /admin/sites/{id}/posts`
- `GET /admin/sites/{id}/posts/{postId}`
- `PUT /admin/sites/{id}/posts/{postId}`
- `POST /admin/sites/{id}/posts/{postId}/publish`
- `POST /admin/sites/{id}/posts/{postId}/unpublish`
- `DELETE /admin/sites/{id}/posts/{postId}`

### Media (future)
- `POST /admin/media`
- `GET /admin/media`

---

## 8. Response contracts

## 8.1 Public response

Frontend-oriented payload:

```json
{
  "id": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "title": "Santiago Acevedo",
  "summary": "Backend Developer",
  "seo": {},
  "hero": {},
  "about": {},
  "skills": [],
  "jobs": [],
  "projects": [],
  "posts": [],
  "publishedAt": "2026-06-18T10:00:00Z"
}
```

## 8.2 Admin response

Includes additionally:
- `status`
- `currentDraftVersion`
- `currentPublishedVersion`
- `updatedAt`

---

## 9. Business validations

### Site
- `title` required
- Ownership: a user may only read or write their own sites — validate in the use case, not just in authentication
- Only `ADMIN` or `EDITOR` roles may edit

### Photos
- `src` not blank
- `alt` required

### Skills
- `name`, `slug`, `category`, `description` required
- Category restricted to enum values

### Jobs
- `company`, `role`, `period`, `location` required
- `highlights.size >= 2`
- `highlights.size <= 4` recommended

### Projects
- `name`, `filename`, `description`, `github` required
- `github` must be a valid URL
- `live` nullable

### Posts
- `title`, `date`, `excerpt`, `readTime` required
- `tags` must never be `null`, though an empty list is fine

---

## 10. Suggested package structure

```text
src/main/java/com/cms/
├── domain/
│   ├── model/
│   │   └── site/
│   │       ├── Site.java
│   │       ├── SiteContent.java
│   │       ├── SitePost.java
│   │       ├── PublicationStatus.java
│   │       ├── SeoBlock.java
│   │       ├── HeroBlock.java
│   │       ├── AboutBlock.java
│   │       ├── Photo.java
│   │       ├── Fact.java
│   │       ├── Skill.java
│   │       ├── SkillCategory.java
│   │       ├── Job.java
│   │       ├── Project.java
│   │       └── PostCard.java
│   └── port/
│       ├── in/
│       │   ├── CreateSiteUseCase.java
│       │   ├── UpdateSiteDraftUseCase.java
│       │   ├── PublishSiteUseCase.java
│       │   ├── GetSitePublicUseCase.java
│       │   ├── CreatePostUseCase.java
│       │   ├── PublishPostUseCase.java
│       │   └── GetPostPublicUseCase.java
│       └── out/
│           ├── SiteRepository.java
│           ├── SiteContentRepository.java
│           ├── SitePostRepository.java
│           └── MediaAssetRepository.java
├── application/
│   └── usecase/
│       ├── CreateSiteService.java
│       ├── UpdateSiteDraftService.java
│       ├── PublishSiteService.java
│       ├── GetSitePublicService.java
│       ├── CreatePostService.java
│       ├── PublishPostService.java
│       └── GetPostPublicService.java
└── adapters/
    ├── in/web/controller/
    │   ├── AdminSiteController.java
    │   ├── AdminPostController.java
    │   └── PublicSiteController.java
    └── out/persistence/
        ├── jpa/
        │   ├── entity/
        │   │   └── SiteEntity.java
        │   ├── repository/
        │   │   └── SiteJpaRepository.java
        │   └── adapter/
        │       └── SitePersistenceAdapter.java
        └── mongo/
            ├── document/
            │   ├── SiteContentDocument.java
            │   └── SitePostDocument.java
            ├── repository/
            │   ├── SiteContentMongoRepository.java
            │   └── SitePostMongoRepository.java
            └── adapter/
                ├── SiteContentPersistenceAdapter.java
                └── SitePostPersistenceAdapter.java
```

---

## 11. Editorial flow

### Edit and publish a site

1. A user creates a site in PostgreSQL.
2. `version = 1` of the content is created in MongoDB as a draft.
3. The user updates the draft via `PUT /admin/sites/{id}/draft`.
4. On publish:
   - The full document is validated
   - `currentPublishedVersion` is updated
   - The public endpoint starts serving that version
5. On subsequent edits:
   - Overwrite the current draft, or
   - Create a new draft version at `publishedVersion + 1`

### Edit and publish a post

Posts have their own independent draft/publish lifecycle:
1. Create post via `POST /admin/sites/{id}/posts` — starts as unpublished
2. Edit via `PUT /admin/sites/{id}/posts/{postId}`
3. Publish via `POST /admin/sites/{id}/posts/{postId}/publish` — sets `published: true`
4. The post card in `site_contents` draft must be updated to include the `postId` and then the site republished for the card to appear on the public site.

### Practical recommendation

Start with the simplest scheme:
- **1 active draft per site**
- **1 published version referenced from PostgreSQL**

Do not add complex workflow until there is a real need for it.

---

## 12. Public endpoint protection

`GET /public/sites/{id}` is an unauthenticated endpoint. Two mitigations must be implemented before production.

### 12.1 Response caching — implement now

Published content does not change until the next `publish` action. It is an ideal cache candidate.

**Stack:** Spring Cache + Caffeine (in-memory). Migrate to Redis when running multiple instances.

```java
@Cacheable(value = "published-site", key = "#id")
public PublicSiteResponse getPublished(UUID id) { ... }
```

Cache invalidation: evict on `POST /admin/sites/{id}/publish`.

```java
@CacheEvict(value = "published-site", key = "#id")
public void publish(UUID id) { ... }
```

With caching in place, a flood of requests to the same site hits memory, not the database.

### 12.2 Rate limiting — implement now

Limit requests per IP on all public endpoints. **Stack:** Bucket4j (in-memory to start, Redis-backed when scaling).

Suggested limit: ~20 requests/second per IP on `/public/**`. Exceeding returns `429 Too Many Requests`.

No external infrastructure required for the initial setup.

### 12.3 UUID as passive obscurity — already in place

UUID v4 has a search space of 2^122. Enumerating valid IDs by brute force is computationally infeasible, unlike sequential IDs or predictable slugs. Not a security measure on its own, but it reduces targeted scraping risk.

### 12.4 CDN / reverse proxy — future

Cloudflare or nginx in front of the API handles volumetric DDoS at the network level before requests reach Spring. Relevant when there is real traffic or a concrete attack. Out of scope for now.

---

## 13. Image strategy

### Phase 1
- Store URLs only
- Support CDN, Cloudinary, S3, or static assets

### Phase 2 (future)
- Add `media` module
- Upload file to the backend
- Store metadata in `media_assets`
- Return a public URL to be assigned to `Photo.src` or `Post.banner`

---

## 14. Confirmed decisions

### Keep
- Normalized user identity schema: `users`, `user_credentials`, `user_oauth_providers`, `user_roles`, `user_profiles`
- Multiple roles per user via `user_roles` join table — supports future pricing/plan models
- `user_profiles` with `metadata JSONB` for free-form profile fields without migrations
- All identity and auth tables in PostgreSQL — no MongoDB for user data
- Site content in MongoDB
- `site` as the generic content unit — not tied to portfolio use case
- Public endpoints separate from admin endpoints
- Separate DTOs for admin and public responses
- Bean Validation + domain rule validations
- Draft/publish workflow — saving never auto-publishes
- Single API serving both CMS UI and each user's frontend
- Expandable content pattern: cards embedded in `site_contents`, full documents in separate collections (`site_posts`, future `site_projects`)

### Avoid
- `password NOT NULL` on `users` — breaks OAuth users
- Merging local auth and OAuth identity into a single table with nullable hacks
- Slug as identifier — use UUID only
- Forcing all site data into relational tables
- Persisting `#` as a value for absent links — use `null`
- Exposing persistence documents directly without DTOs
- Coupling frontends to the exact persistence structure
- Implementing `site_publications` until there is a concrete use case

### Pending decisions
- **OAuth2 social login** (Google, GitHub): schema is ready (`user_oauth_providers`). Implementation requires registering OAuth Apps, adding `spring-boot-starter-oauth2-client`, and writing the authorization code exchange endpoint. The email-as-identifier edge case must be resolved before implementing: if a user registers locally and then tries OAuth with the same email, decide whether to merge accounts or reject. Defer until local auth is fully working.
- **Pricing / plan tiers**: `user_roles` supports this already. When the time comes, add a `plans` table and either a `user_plan` column on `users` or a `user_plans` join table depending on whether a user can hold multiple plans simultaneously.
- **Projects with individual pages**: when needed, follow the same pattern as `site_posts` — create `site_projects`, add `projectId` to the embedded card, add public and admin endpoints.

---

## 15. MVP

### Persistence
- PostgreSQL:
  - `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`
  - `sites`
- MongoDB:
  - `site_contents`
  - `site_posts`

### Minimum endpoints
- `POST /auth/register`
- `POST /auth/login`
- `POST /admin/sites`
- `GET /admin/sites` — authenticated user's sites only
- `GET /admin/sites/{id}/draft`
- `PUT /admin/sites/{id}/draft`
- `POST /admin/sites/{id}/publish`
- `GET /public/sites/{id}`
- `POST /admin/sites/{id}/posts`
- `PUT /admin/sites/{id}/posts/{postId}`
- `POST /admin/sites/{id}/posts/{postId}/publish`
- `GET /public/sites/{id}/posts`
- `GET /public/sites/{id}/posts/{postId}`

### MVP content sections
- `seo`
- `hero`
- `about.photos`
- `about.facts`
- `skills`
- `jobs`
- `projects` (embedded cards, no individual page yet)
- `posts` (cards embedded in `site_contents`, full documents in `site_posts`)

---

## 16. Confirmed architecture

1. **PostgreSQL for users + site metadata**
2. **MongoDB for versioned site content**
3. **Hexagonal architecture** with separate ports for public reading and administration
4. **Aggregated document per version** to serve the full site to the frontend in one call
5. **Stable public DTOs** to avoid leaking internal details
6. **Multi-user headless CMS** — the API serves JSON; each user integrates their own frontend
7. **Single API** — no microservice split; admin and public concerns are separated logically, not by deployment

---

## 17. Suggested next steps

Implement in this order:

1. Rewrite `V1__create_users_table.sql` as the full normalized user schema: `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`
2. Update `UserEntity` and related JPA entities to match the new schema; remove `UserDetails` implementation from the entity
3. Flyway migration `V2__create_sites_table.sql`
4. JPA entity `SiteEntity`
5. MongoDB documents `SiteContentDocument` and `SitePostDocument`
6. Domain ports (`domain/port/in/`, `domain/port/out/`)
7. Use cases: create site, edit draft, publish site, create post, publish post, get public
8. Controllers `AdminSiteController`, `AdminPostController`, and `PublicSiteController`
