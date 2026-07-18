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

### Planned next (from architecture doc, section 16)
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

- **PostgreSQL**: identity (`users`), site and entry metadata (`sites`, `site_entries`). `ddl-auto: validate` — Hibernate does NOT manage schema; Flyway does.
- **MongoDB**: editorial content in four collections:
  - `site_drafts` — current draft per site (`content: {}` on create, `version` field for optimistic locking)
  - `site_published` — published snapshot per site; deleted on unpublish
  - `site_entry_drafts` — current draft per entry (`version` field)
  - `site_entry_published` — published snapshot per entry; deleted on unpublish
- Content is always `Map<String, Object>` — backend never interprets structure
- `site_publications` (publication history) is **explicitly out of scope** for now
- Flyway migrations live in `src/main/resources/db/migration/`. Naming: `V{n}__{description}.sql`

---

## Key design rules

### Identification
- Sites and entries are identified by **UUID only** — no slug.
- Public endpoint uses UUID. Slug-based URLs are the frontend's responsibility.

### Roles
- `ADMIN` = access to `/admin/**` (future system operations)
- `EDITOR` = manages own sites and entries via `/cms/**`
- `VIEWER` is **dropped** until per-site collaboration exists

### Status
- `DRAFT | PUBLISHED` only — `ARCHIVED` is dropped
- Both `sites` and `site_entries` use the same enum
- Both tables have `CHECK (status IN ('DRAFT','PUBLISHED'))`

### Multi-user isolation
- Each site has `ownerUserId`. Use cases must validate ownership — never rely on auth alone.
- `GET /cms/sites` returns only the authenticated user's sites.

### Draft/publish workflow
- Every site and entry has one draft document. Saving never auto-publishes.
- `POST /cms/sites/{id}/publish` copies draft content → published document.
- Public endpoints always read published documents — never drafts.
- `POST /cms/sites/{id}/unpublish` **deletes** the published document. `GET /public/sites/{id}` returns `404` until next publish.
- On create: draft document is initialized with `content: {}`. `GET .../draft` always returns `200` if the resource exists.

### Optimistic locking on drafts
- Draft documents have a `version: Long` field, incremented on every save.
- `GET .../draft` response includes `version`; expose as `ETag` header.
- `PUT .../draft` **requires** `If-Match` header with current version.
- Returns `412 Precondition Failed` if versions do not match.
- Applies to both `site_drafts` and `site_entry_drafts`.
- Frontend 412 handling: show "Someone else saved changes — reload to continue."

### Entry hierarchy (series)
- Entries reference a parent via `parentId` (self-reference in `site_entries`).
- Depth is unlimited — no 1-level restriction.
- On every `PATCH .../entries/{id}` that sets `parentId`: use case walks the ancestor chain to detect cycles. Returns `400` if current entry appears in the chain.
- `parentId` must reference an entry within the same site.

### Delete behavior
- `DELETE /cms/sites/{id}` — cascades: all `site_entries`, all 4 MongoDB document types.
- `DELETE /cms/sites/{id}/entries/{entryId}` with no children — deletes entry + draft + published docs.
- `DELETE /cms/sites/{id}/entries/{entryId}` with children — returns `409`. Caller must delete children first.

### ?type filter — two-step query
- `GET /public/sites/{id}/entries?type=post` queries PostgreSQL using index `(site_id, status, type)` → batch fetches MongoDB `site_entry_published` by the returned `entryId` list.
- Do not attempt to filter by `type` directly in MongoDB — `type` lives in PostgreSQL.

### contentSchema
- `VARCHAR(100) NULL` on `sites`. Written by CMS UI (e.g. `"portfolio-v1"`).
- Included in public response so the frontend can version its rendering logic.
- Backend does not validate or interpret its value.

### API contracts
- Public endpoints: `/public/sites/**` — no auth
- CMS endpoints: `/cms/sites/**` — require authentication (user's own resources)
- Auth endpoints: `/auth/**` — no auth required
- Admin endpoints: `/admin/**` — reserved for future system-level operations
- DTOs must be separate for public vs cms responses; never expose persistence documents directly
- Content is `Map<String, Object>` in both request and response — no typed section DTOs

### Public endpoint protection
`GET /public/sites/{id}` is unauthenticated. Two mitigations required before production:

- **Response caching** (Spring Cache + Caffeine): evict on publish and unpublish. Migrate to Redis for multiple instances.
- **Rate limiting** (Bucket4j): ~20 req/s per IP on `/public/**`, return `429`. In-memory to start.

---

## Test profile

`application-test.yml` activates with `spring.profiles.active=test`:
- Uses `cms_db_test` (PostgreSQL) and `cms_content_test` (MongoDB)
- `ddl-auto: create-drop` — Hibernate manages schema (no Flyway in tests)
- Flyway is **disabled** in test profile

---

## API surface

- Base path: `/api` (context path set in `application.yml`)
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API docs JSON: `http://localhost:8080/api/v3/api-docs`
- Auth: stateless JWT, header `Authorization: Bearer <token>`
- Public auth endpoints: `/api/auth/**` (no token required)
