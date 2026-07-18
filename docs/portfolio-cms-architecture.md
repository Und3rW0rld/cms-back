# Architecture and data model — Portfolio CMS

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
- [x] Drop `portfolio_publications` from initial scope
- [x] Confirm single API serves both CMS UI and portfolio frontends

---

## Confirmed design decisions

| Decision | Resolution |
|---|---|
| System type | Headless CMS API — serves JSON, not HTML |
| Consumers | Two types: CMS UI (admin) + each user's own portfolio frontend (public) |
| Deployment | Single API — no microservice split needed at this stage |
| Users | Multi-user; anyone can register and manage their own content |
| Public identification | UUID only — no slug. Each user's frontend is responsible for its own URLs |
| Draft/publish | Required — saving never auto-publishes |
| Publication history (`portfolio_publications`) | Out of scope for now |
| User isolation | Each portfolio has `ownerUserId`; use cases must validate ownership |
| Content storage | MongoDB — flexible schema, embedded per version |
| Identity and metadata | PostgreSQL — strong integrity |

---

## 1. System objective

This backend is a **multi-user headless CMS**. It acts as a content management layer for any frontend that wants to integrate against it. It does not render HTML — it delivers JSON.

The system serves **two types of consumers from a single API**:
- **CMS UI** (admin frontend) — logs in, edits drafts, publishes content
- **Portfolio frontends** (one per user) — reads the published version with no authentication

The CMS and each portfolio frontend are separate applications. The CMS exposes an API; each user integrates their own frontend however they prefer.

### Target capabilities

1. **Register users** and authenticate them with JWT.
2. **Manage content** from private, per-user endpoints.
3. **Draft/publish** — edit without breaking what the frontend is already serving.
4. **Expose published content** through public endpoints with no authentication.
5. **Maintain schema flexibility** in MongoDB so different portfolios can have different sections.

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
- **MongoDB for editorial portfolio content**, because its sections are flexible and document-oriented

---

## 3. Architecture proposal

## 3.1 Functional domain split

The system divides into 3 conceptual areas:

### A. Identity & Access
- CMS users
- JWT authentication
- Roles (`ADMIN`, `EDITOR`, `VIEWER`)

### B. Portfolio Management
- Portfolios
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
- `Portfolio`
- `PortfolioContent`
- `Photo`
- `Fact`
- `Skill`
- `Job`
- `Project`
- `Post`
- `PublicationStatus`

### Input ports
Use cases as interfaces:
- `CreatePortfolioUseCase`
- `UpdatePortfolioDraftUseCase`
- `PublishPortfolioUseCase`
- `GetPortfolioPublicUseCase`
- `GetPortfolioAdminUseCase`

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

## 4.1 Main aggregate: `Portfolio`

`Portfolio` represents the portfolio as a publishable unit.

### Responsibilities
- Portfolio identity (UUID)
- Owner (`ownerUserId`)
- Publication status
- Reference to the published version
- Timestamps

### Conceptual model

```text
Portfolio
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

> No `slug` — portfolios are identified exclusively by UUID. The public URL is the responsibility of each user's frontend, not this API.

> `title` and `summary` are basic metadata. All heavy content lives in MongoDB.

---

## 4.2 Aggregate/document: `PortfolioContent`

This document holds the actual editable content for a given portfolio version.

### Conceptual model

```text
PortfolioContent
- id: ObjectId
- portfolioId: UUID
- version: Int
- seo: SeoBlock
- hero: HeroBlock
- about: AboutBlock
- skills: List<Skill>
- jobs: List<Job>
- projects: List<Project>
- posts: List<Post>
- createdAt: Instant
- updatedAt: Instant
```

### Why embedded

Portfolio frontends typically need to load most content in a single query. Additionally:
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
- `name + category` could be unique within the same portfolio

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

### `Post`

```text
Post
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
- All identity tables stay in PostgreSQL for strong relational integrity; MongoDB is reserved for editorial portfolio content only

### New table `portfolios`

```text
portfolios
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

> No `slug` column — portfolios are identified by UUID only.

### Table `portfolio_publications` — **out of scope for now**

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

## 5.2 MongoDB: document collection

### Collection `portfolio_contents`

Suggested document:

```json
{
  "portfolioId": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
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

### Suggested MongoDB indexes

- Compound unique index: `(portfolioId, version)`
- Simple index: `portfolioId`

---

## 6. Embedded vs separate collections

## Initial recommendation

Keep `skills`, `jobs`, `projects`, and `posts` **embedded inside `portfolio_contents`**.

### Advantages
- Very fast reads for the frontend
- A single document represents a complete version
- Easy to publish a consistent snapshot
- No logical joins between collections

### When to separate `posts`
If posts grow significantly and you need:
- Real pagination
- More advanced full-text search
- Individual post URLs
- Per-post drafts

Then move them to their own collection, e.g. `portfolio_posts`.

---

## 7. Endpoints

## 7.1 Public

### Get full published portfolio
`GET /public/portfolios/{id}`

Returns the published version ready for frontend consumption. No authentication required.

> `id` is a UUID. There is no slug — each user's frontend is responsible for constructing friendly URLs from the UUID it receives from the API.

### Get a single section (optional)
Only if fine-grained optimization is needed:
- `GET /public/portfolios/{id}/skills`
- `GET /public/portfolios/{id}/projects`
- `GET /public/portfolios/{id}/posts`

> Start with the aggregated endpoint and only split if there is a real need.

---

## 7.2 Authentication

- `POST /auth/register` — open registration
- `POST /auth/login` — returns JWT

---

## 7.3 Admin

### Portfolios
- `POST /admin/portfolios`
- `GET /admin/portfolios` — **returns only portfolios owned by the authenticated user**, never all portfolios in the system
- `GET /admin/portfolios/{id}`
- `PATCH /admin/portfolios/{id}/metadata`

### Draft content
- `GET /admin/portfolios/{id}/draft`
- `PUT /admin/portfolios/{id}/draft`

### Publishing
- `POST /admin/portfolios/{id}/publish`
- `POST /admin/portfolios/{id}/unpublish`

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

### Portfolio
- `title` required
- Ownership: a user may only read or write their own portfolios — validate in the use case, not just in authentication
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
│   │   └── portfolio/
│   │       ├── Portfolio.java
│   │       ├── PortfolioContent.java
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
│   │       └── Post.java
│   └── port/
│       ├── in/
│       │   ├── CreatePortfolioUseCase.java
│       │   ├── UpdatePortfolioDraftUseCase.java
│       │   ├── PublishPortfolioUseCase.java
│       │   └── GetPortfolioPublicUseCase.java
│       └── out/
│           ├── PortfolioRepository.java
│           ├── PortfolioContentRepository.java
│           └── MediaAssetRepository.java
├── application/
│   └── usecase/
│       ├── CreatePortfolioService.java
│       ├── UpdatePortfolioDraftService.java
│       ├── PublishPortfolioService.java
│       └── GetPortfolioPublicService.java
└── adapters/
    ├── in/web/controller/
    │   ├── AdminPortfolioController.java
    │   └── PublicPortfolioController.java
    └── out/persistence/
        ├── jpa/
        │   ├── entity/
        │   │   └── PortfolioEntity.java
        │   ├── repository/
        │   │   └── PortfolioJpaRepository.java
        │   └── adapter/
        │       └── PortfolioPersistenceAdapter.java
        └── mongo/
            ├── document/
            │   └── PortfolioContentDocument.java
            ├── repository/
            │   └── PortfolioContentMongoRepository.java
            └── adapter/
                └── PortfolioContentPersistenceAdapter.java
```

---

## 11. Editorial flow

### Edit and publish

1. A user creates a portfolio in PostgreSQL.
2. `version = 1` of the content is created in MongoDB as a draft.
3. The user updates the draft via `PUT /admin/portfolios/{id}/draft`.
4. On publish:
   - The full document is validated
   - `currentPublishedVersion` is updated
   - The public endpoint starts serving that version
5. On subsequent edits:
   - Overwrite the current draft, or
   - Create a new draft version at `publishedVersion + 1`

### Practical recommendation

Start with the simplest scheme:
- **1 active draft per portfolio**
- **1 published version referenced from PostgreSQL**

Do not add complex workflow until there is a real need for it.

---

## 12. Public endpoint protection

`GET /public/portfolios/{id}` is an unauthenticated endpoint. Two mitigations must be implemented before production.

### 12.1 Response caching — implement now

Published content does not change until the next `publish` action. It is an ideal cache candidate.

**Stack:** Spring Cache + Caffeine (in-memory). Migrate to Redis when running multiple instances.

```java
@Cacheable(value = "published-portfolio", key = "#id")
public PublicPortfolioResponse getPublished(UUID id) { ... }
```

Cache invalidation: evict on `POST /admin/portfolios/{id}/publish`.

```java
@CacheEvict(value = "published-portfolio", key = "#id")
public void publish(UUID id) { ... }
```

With caching in place, a flood of requests to the same portfolio hits memory, not the database.

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

## 13. Confirmed decisions

### Keep
- Normalized user identity schema: `users`, `user_credentials`, `user_oauth_providers`, `user_roles`, `user_profiles`
- Multiple roles per user via `user_roles` join table — supports future pricing/plan models
- `user_profiles` with `metadata JSONB` for free-form profile fields without migrations
- All identity and auth tables in PostgreSQL — no MongoDB for user data
- Portfolio content in MongoDB
- Public endpoints separate from admin endpoints
- Separate DTOs for admin and public responses
- Bean Validation + domain rule validations
- Draft/publish workflow — saving never auto-publishes
- Single API serving both CMS UI and portfolio frontends

### Avoid
- `password NOT NULL` on `users` — breaks OAuth users
- Merging local auth and OAuth identity into a single table with nullable hacks
- Slug as identifier — use UUID only
- Forcing all portfolio data into relational tables
- Persisting `#` as a value for absent links — use `null`
- Exposing persistence documents directly without DTOs
- Coupling frontends to the exact persistence structure
- Implementing `portfolio_publications` until there is a concrete use case

### Pending decisions
- **OAuth2 social login** (Google, GitHub): schema is ready (`user_oauth_providers`). Implementation requires registering OAuth Apps, adding `spring-boot-starter-oauth2-client`, and writing the authorization code exchange endpoint. The email-as-identifier edge case must be resolved before implementing: if a user registers locally and then tries OAuth with the same email, decide whether to merge accounts or reject. Defer implementation until local auth is fully working.
- **Pricing / plan tiers**: `user_roles` supports this already. When the time comes, add a `plans` table and either a `user_plan` column on `users` or a `user_plans` join table depending on whether a user can hold multiple plans simultaneously.

---

## 14. MVP

### Persistence
- PostgreSQL:
  - `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`
  - `portfolios`
- MongoDB:
  - `portfolio_contents`

### Minimum endpoints
- `POST /auth/register`
- `POST /auth/login`
- `POST /admin/portfolios`
- `GET /admin/portfolios` — authenticated user's portfolios only
- `GET /admin/portfolios/{id}/draft`
- `PUT /admin/portfolios/{id}/draft`
- `POST /admin/portfolios/{id}/publish`
- `GET /public/portfolios/{id}`

### MVP content sections
- `seo`
- `hero`
- `about.photos`
- `about.facts`
- `skills`
- `jobs`
- `projects`
- `posts`

---

## 15. Confirmed architecture

1. **PostgreSQL for users + portfolio metadata**
2. **MongoDB for versioned portfolio content**
3. **Hexagonal architecture** with separate ports for public reading and administration
4. **Aggregated document per version** to serve the full portfolio to the frontend in one call
5. **Stable public DTOs** to avoid leaking internal details
6. **Multi-user headless CMS** — the API serves JSON; each user integrates their own frontend
7. **Single API** — no microservice split; admin and public concerns are separated logically, not by deployment

---

## 16. Suggested next steps

Implement in this order:

1. Drop existing `V1__create_users_table.sql` and rewrite as the full normalized user schema: `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`
2. Update `UserEntity` and related JPA entities to match the new schema; remove `UserDetails` implementation from the entity
3. Flyway migration `V2__create_portfolios_table.sql` — no `slug` column
4. JPA entity `PortfolioEntity`
5. MongoDB document `PortfolioContentDocument`
6. Domain ports (`domain/port/in/`, `domain/port/out/`)
7. Use cases: create portfolio, edit draft, publish, get public
8. Controllers `AdminPortfolioController` and `PublicPortfolioController`
