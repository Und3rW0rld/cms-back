# AGENTS.md

## What this repo is

**Headless CMS API** — multi-user, built with **Java 21 + Spring Boot 3.5**, following strict **Hexagonal (Ports & Adapters) Architecture**.

The CMS is one application; each user's frontend (portfolio, blog, product page, etc.) is a separate application that consumes this API. The CMS does not render HTML — it serves JSON. Any frontend integrates against it independently.

The core content unit is a **site** — a generic, publishable content unit. A site contains **entries** — any content with its own page (posts, projects, series, etc.).

**Core purpose:** allow any registered user to manage structured content through a UI, with draft/publish workflow, and expose the published version publicly via a read-only endpoint.

**Strategic positioning:** designed for multi-tenant SaaS (Product B), built for single user first (Product A). Architecture decisions are made for B; features are built for A.

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

Only PostgreSQL is required. MongoDB has been removed.

| Service | Default |
|---|---|
| PostgreSQL | `localhost:5432` — database: `cms_db` |

---

## Environment variables

| Variable | Default | Notes |
|---|---|---|
| `DB_USERNAME` | `postgres` | PostgreSQL user |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
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
- **MongoDB dependency exists in pom.xml — to be removed.**

### Planned next (from architecture doc, section 13)
In this order:
1. Remove MongoDB dependency from `pom.xml` and `application.yml`
2. Rewrite `V1__create_users_schema.sql` — full normalized user schema with `plan_id NULL`
3. Update `UserEntity` — remove `UserDetails` implementation from the JPA entity
4. `V2__create_sites_schema.sql`
5. `V3__create_site_entries_schema.sql` — ltree extension + indexes
6. `V4__create_draft_published_schema.sql`
7. `V5__create_future_tables.sql` — site_collaborators, media_assets, site_domains, site_webhooks, plans
8. JPA entities for all tables
9. Domain models: `Site`, `SiteEntry`
10. Output ports and adapters
11. Use cases
12. Controllers: `AuthController`, `CmsSiteController`, `CmsEntryController`, `PublicSiteController`

---

## Persistence strategy

**Single database: PostgreSQL only. MongoDB has been removed.**

| What | How | Where |
|---|---|---|
| Identity and ownership | Normalized relational tables | PostgreSQL |
| Site/entry metadata | Relational rows | PostgreSQL |
| Entry hierarchy | `ltree` extension, `path LTREE` column | PostgreSQL |
| All content | `JSONB` column | PostgreSQL |
| Publication state | Row existence in `site_published` / `site_entry_published` | PostgreSQL |

**No status column on `sites` or `site_entries`.** Published = row exists in `*_published` table.

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
- **No `status` column in PostgreSQL.**
- `site_published` row exists → site is published. Does not exist → draft only
- `site_entry_published` row exists → entry is published. Does not exist → draft only
- `GET /public/**` returns `404` when no published row exists
- Unpublish **deletes** the `*_published` row
- Published state in CMS listing via LEFT JOIN — single query, not N+1

### Multi-user isolation
- Each site has `owner_user_id`. Use cases must validate ownership — never rely on auth alone
- `GET /cms/sites` returns only the authenticated user's sites

### Draft/publish workflow
- Every site and entry has one draft row, initialized with `content: '{}'`
- `GET .../draft` always returns `200` if the resource exists
- Saving never auto-publishes — autoguardado is a frontend concern
- `POST .../publish` upserts `*_published` row in a single transaction
- All publish/unpublish/delete operations are transactional

### Optimistic locking on drafts
- Draft rows have `version: Long`, incremented on every save
- `GET .../draft` exposes `version` as `ETag` header
- `PUT .../draft` **requires** `If-Match` header — returns `412` on mismatch
- Frontend 412 handling: "Someone else saved changes — reload to continue"
- Applies to both `site_drafts` and `site_entry_drafts`
- PATCH metadata (`title`, `summary`, `contentSchema`) is last-write-wins — intentional

### Content rules
- Content is always `JSONB` / `Map<String, Object>` — backend never interprets structure
- `content` must be a JSON object (not null, not array)
- **Maximum content size: 1MB** — validated in use case before writing
- No typed section DTOs — ever

### Entry hierarchy (ltree)
- `site_entries` has a `path LTREE` column — e.g. `root.seriesId.entryId`
- Root entries: `nlevel(path) = 1`
- `?parentId=root` = top-level entries
- Cycles are **impossible by construction** — ltree is acyclic
- Moving an entry updates path for entry and all descendants in one transaction
- No cycle detection code needed

### Delete behavior
- `DELETE /cms/sites/{id}` — cascades in transaction: all entries, all draft/published rows
- `DELETE .../entries/{entryId}` with no children — deletes row + draft + published
- `DELETE .../entries/{entryId}` with children — returns `409`

### ?type filter
- `GET /public/sites/{id}/entries?type=post` uses index `(site_id, type)` on `site_entries`
- JOIN with `site_entry_published` in same query — no secondary fetch needed (single DB)

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
- DTOs separate for public vs cms responses — never expose entity objects directly

### Public endpoint protection
Two mitigations required before production:
- **Response caching** (Spring Cache + Caffeine, `expireAfterWrite=1h`): evict on publish, unpublish, and delete. Migrate to Redis for multiple instances.
- **Rate limiting** (Bucket4j): `/public/**` ~20 req/s per IP; `/cms/**` ~10 req/s per authenticated user. Returns `429`. In-memory to start.

### Pagination
- Public entries: `?limit=20` default, max `200`

### Future tables — created in migrations, no logic yet
- `site_collaborators` — per-site roles
- `media_assets` — file uploads
- `site_domains` — custom domains
- `site_webhooks` — publish/unpublish hooks
- `plans` — pricing tiers
- `users.plan_id UUID NULL` — monetization hook

---

## Test profile

`application-test.yml` activates with `spring.profiles.active=test`:
- Uses `cms_db_test` (PostgreSQL)
- `ddl-auto: create-drop` — Hibernate manages schema (no Flyway in tests)
- Flyway **disabled** in test profile

---

## API surface

- Base path: `/api` (context path in `application.yml`)
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API docs: `http://localhost:8080/api/v3/api-docs`
- Auth: stateless JWT, header `Authorization: Bearer <token>`
