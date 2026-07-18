# AGENTS.md

## What this repo is

**Headless CMS API** — multi-user, built with **Java 21 + Spring Boot 3.5**, following strict **Hexagonal (Ports & Adapters) Architecture**.

The CMS is one application; each user's frontend (portfolio, blog, product page, etc.) is a separate application that consumes this API. The CMS does not render HTML — it serves JSON. Any frontend integrates against it independently.

The core content unit is a **site** — a generic, publishable content unit. A site contains **entries** — any content with its own page (posts, projects, series, etc.).

**Core purpose:** allow any registered user to manage structured content through a UI, with draft/publish workflow, and expose the published version publicly via a read-only endpoint.

Currently in early scaffolding phase: security plumbing and persistence foundation exist; domain/application core, REST controllers, and tests are **not yet implemented**.

Architecture design spec: [`docs/portfolio-cms-architecture.md`](docs/portfolio-cms-architecture.md)

---

## Commands

```bash
# Run
mvn spring-boot:run

# Build (skip tests - no test classes exist yet)
mvn package -DskipTests

# Run tests (when they exist)
mvn test
```

No CI, no Makefile, no Docker Compose — Maven is the only task runner.

---

## Required services

Both must be running before starting the app:

| Service | Default |
|---|---|
| PostgreSQL | `localhost:5432` — database: `cms_db` |
| MongoDB | `localhost:27017` — database: `cms_content` |

---

## Environment variables

| Variable | Default | Notes |
|---|---|---|
| `DB_USERNAME` | `postgres` | PostgreSQL user |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `MONGO_URI` | `mongodb://localhost:27017/cms_content` | Full connection URI |
| `JWT_SECRET` | insecure default in yml | Min 32 chars for HMAC-SHA256 |

---

## Architecture — what exists vs what is planned

### Exists
```
src/main/java/com/cms/
├── CmsBackApplication.java
└── adapters/
    ├── config/
    │   ├── ApplicationConfig.java     # UserDetailsService wiring
    │   ├── OpenApiConfig.java         # Springdoc + Bearer JWT scheme
    │   └── SecurityConfig.java        # Stateless JWT, RBAC, BCrypt
    └── in/security/jwt/
    │   ├── JwtAuthenticationFilter.java
    │   └── JwtProvider.java            # jjwt 0.12.x
    └── out/persistence/jpa/
        ├── entity/UserEntity.java      # @Entity + UserDetails + Role enum — to be refactored
        └── repository/UserJpaRepository.java
```

- `UserEntity` implements `UserDetails` directly (intentional shortcut — to be removed).
- `Role` enum: `ADMIN`, `EDITOR` — `VIEWER` dropped. Authority strings prefixed `ROLE_`.
- **`domain/` and `application/` packages do not exist yet.**
- **No REST controllers exist yet.**
- **`src/test/` does not exist yet.**

### Planned next (from architecture doc, section 14)
In this order:
1. Rewrite `V1__create_users_table.sql` as normalized user schema: `users`, `roles`, `user_roles`, `user_credentials`, `user_oauth_providers`, `user_profiles`
2. Update `UserEntity` — remove `UserDetails` implementation from the JPA entity
3. JPA entity `SiteEntity` + Flyway migration `V2__create_sites_table.sql`
4. JPA entity `SiteEntryEntity` + Flyway migration `V3__create_site_entries_table.sql`
5. MongoDB documents: `SiteDraftDocument`, `SitePublishedDocument`, `SiteEntryDraftDocument`, `SiteEntryPublishedDocument`
6. Domain ports (`domain/port/in/`, `domain/port/out/`)
7. Use case implementations (`application/usecase/`)
8. REST controllers: `AuthController`, `CmsSiteController`, `CmsEntryController`, `PublicSiteController`

---

## Persistence strategy

**PostgreSQL** answers: *"what exists and who owns it?"*
- Identity, ownership, relationships, navigation metadata (`type`, `sort_order`, `parent_id`)
- **No `status` column** — publication state is determined by MongoDB document existence
- `ddl-auto: validate` — Flyway manages schema

**MongoDB** answers: *"what content does it have and is it published?"*
- `site_drafts` — always exists, `content: {}` on create, `version` field for optimistic locking
- `site_published` — its existence means the site is published; deleted on unpublish
- `site_entry_drafts` — always exists, `version` field
- `site_entry_published` — its existence means the entry is published; deleted on unpublish

**No field is duplicated between the two databases.** They are complementary, not redundant.

Flyway migrations: `src/main/resources/db/migration/V{n}__{description}.sql`

---

## Key design rules

### Identification
- Sites and entries identified by **UUID only** — no slug
- Public endpoint uses UUID; slug-based URLs are the frontend's responsibility

### Roles
- `ADMIN` = access to `/admin/**` (future system operations)
- `EDITOR` = manages own sites and entries via `/cms/**`
- `VIEWER` dropped until per-site collaboration exists

### Publication state
- **No `status` field in PostgreSQL.** Published state = existence of `*_published` document in MongoDB
- `site_published` exists → site is published. Does not exist → draft only
- `site_entry_published` exists → entry is published. Does not exist → draft only
- `GET /public/**` returns `404` when no published document exists
- Unpublish **deletes** the `*_published` document

### Multi-user isolation
- Each site has `ownerUserId`. Use cases must validate ownership — never rely on auth alone
- `GET /cms/sites` returns only the authenticated user's sites
- Published state for listing: batch query `site_published` by `siteId` list — not N+1

### Draft/publish workflow
- Every site and entry has one draft document, initialized with `content: {}`
- `GET .../draft` always returns `200` if the resource exists
- Saving never auto-publishes — autoguardado is a frontend concern
- `POST .../publish` copies draft content → published document (overwrite if exists)

### Optimistic locking on drafts
- Draft documents have `version: Long`, incremented on every save
- `GET .../draft` exposes `version` as `ETag` header
- `PUT .../draft` **requires** `If-Match` header — returns `412` on mismatch
- Frontend 412 handling: "Someone else saved changes — reload to continue"
- Applies to both `site_drafts` and `site_entry_drafts`
- PATCH metadata (`title`, `summary`, `contentSchema`) is last-write-wins — intentional

### Content rules
- Content is always `Map<String, Object>` — backend never interprets structure
- `content` must be a JSON object (not null, not array)
- **Maximum content size: 1MB** — validated in use case before writing to MongoDB
- No typed section DTOs — ever

### Entry hierarchy (series)
- Entries reference parent via `parentId` (self-reference in `site_entries`)
- Depth unlimited — no level restriction
- `?parentId=root` = entries where `parent_id IS NULL`
- On every PATCH that sets `parentId`: walk ancestor chain, return `400` if cycle detected
- `parentId` must reference an entry within the same site

### Delete behavior
- `DELETE /cms/sites/{id}` — cascades: all `site_entries` + all 4 MongoDB document types
- `DELETE .../entries/{entryId}` with no children — deletes row + draft doc + published doc
- `DELETE .../entries/{entryId}` with children — returns `409`

### ?type filter — two-step query
- `GET /public/sites/{id}/entries?type=post` queries PostgreSQL index `(site_id, type)` → batch fetches MongoDB `site_entry_published` by `entryId` list
- Never filter by `type` directly in MongoDB — `type` lives in PostgreSQL

### contentSchema
- `VARCHAR(100) NULL` on `sites`. Written by CMS UI (e.g. `"portfolio-v1"`)
- Included in public response for frontend rendering versioning
- **Changing `contentSchema` in production is a breaking change for deployed frontends**
- Backend does not validate or interpret its value

### API contracts
- `/public/sites/**` — no auth
- `/cms/sites/**` — JWT required (user's own resources)
- `/auth/**` — no auth
- `/admin/**` — reserved for future system-level operations
- DTOs separate for public vs cms responses — never expose persistence documents directly

### Public endpoint protection
Two mitigations required before production:
- **Response caching** (Spring Cache + Caffeine, `expireAfterWrite=1h`): evict on publish, unpublish, and delete. Migrate to Redis for multiple instances.
- **Rate limiting** (Bucket4j): `/public/**` ~20 req/s per IP; `/cms/**` ~10 req/s per authenticated user. Returns `429`. In-memory to start.

### Pagination
- Public entries: `?limit=20` default, max `200`

---

## Test profile

`application-test.yml` activates with `spring.profiles.active=test`:
- Uses `cms_db_test` (PostgreSQL) and `cms_content_test` (MongoDB)
- `ddl-auto: create-drop` — Hibernate manages schema (no Flyway in tests)
- Flyway **disabled** in test profile

---

## API surface

- Base path: `/api` (context path in `application.yml`)
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API docs: `http://localhost:8080/api/v3/api-docs`
- Auth: stateless JWT, header `Authorization: Bearer <token>`
