# AGENTS.md

## What this repo is

**Headless CMS API** — multi-user, Java 26 + Spring Boot 3.5, Hexagonal (Ports & Adapters) Architecture.

Users manage content through a CMS UI; their own frontends (portfolio, blog, etc.) consume the public API. The CMS serves JSON only — no HTML rendering.

Core content unit: **site** (portfolio, blog, product page, etc.). A site contains **entries** — any content with its own page (posts, projects, series, etc.).

**Strategic principle:** design for multi-tenant SaaS, build for the known use case first. Tables and schema decisions that are cheap now and expensive later are done upfront; features that require real users are deferred.

Full architecture spec: [`docs/portfolio-cms-architecture.md`](docs/portfolio-cms-architecture.md)

---

## Current state

Early scaffolding phase. Security plumbing exists; domain/application core, controllers, and tests do not yet exist.

```
src/main/java/com/cms/
├── CmsBackApplication.java
└── adapters/
    ├── config/
    │   ├── ApplicationConfig.java      # UserDetailsService wiring
    │   ├── OpenApiConfig.java          # Springdoc + Bearer JWT
    │   └── SecurityConfig.java         # Stateless JWT, BCrypt
    └── in/security/jwt/
    │   ├── JwtAuthenticationFilter.java
    │   └── JwtProvider.java             # jjwt 0.12.x
    └── out/persistence/jpa/
        ├── entity/UserEntity.java       # to be refactored — implements UserDetails directly
        └── repository/UserJpaRepository.java
```

**Pending before first implementation:**
- Remove MongoDB dependency from `pom.xml` and `application.yml`
- Rewrite `UserEntity` — remove `UserDetails` implementation from the JPA entity

---

## Commands

```bash
mvn spring-boot:run
mvn package -DskipTests
mvn test
```

No CI, no Makefile, no Docker Compose. Maven only.

---

## Required services

PostgreSQL only. MongoDB has been removed.

| Service | Default |
|---|---|
| PostgreSQL | `localhost:5432` db: `cms_db` |

---

## Environment variables

| Variable | Default | Notes |
|---|---|---|
| `DB_USERNAME` | `postgres` | |
| `DB_PASSWORD` | `postgres` | |
| `JWT_SECRET` | insecure default | Min 32 chars |

---

## Database

**Single database: PostgreSQL.**

| What | How |
|---|---|
| Identity, ownership, metadata | Normalized relational tables |
| Entry hierarchy | `ltree` extension — `path LTREE` column on `site_entries` |
| All content | `JSONB` columns |
| Publication state | Row existence in `site_published` / `site_entry_published` |

`ddl-auto: validate` — Flyway manages schema, Hibernate does not.

Migrations: `src/main/resources/db/migration/V{n}__{description}.sql`

### Migration order

```
V1  users, roles, user_roles, user_credentials, user_oauth_providers, user_profiles
V2  sites
V3  site_entries (ltree extension, UNIQUE (site_id, path), indexes)
V4  site_drafts, site_published, site_entry_drafts, site_entry_published
V5  site_collaborators, media_assets, site_domains, site_webhooks, plans
```

---

## Key rules — things an agent would miss

### Publication state
There is **no `status` column**. A site or entry is published if and only if its `*_published` row exists. Public endpoints return `404` when the row is absent. Unpublish = delete the row.

### Optimistic locking on drafts
- Draft rows have `version BIGINT`. Incremented on every save.
- `GET .../draft` → include `version` as `ETag` header.
- `PUT .../draft` → **`If-Match` header required**. Return `412` if mismatch.
- Never accept `PUT .../draft` without `If-Match`.
- Applies to both `site_drafts` and `site_entry_drafts`.

### ltree path segments
- ltree only accepts `[A-Za-z0-9_]` — UUIDs have hyphens and must be converted.
- Build segment: `entryId.toString().replace('-', '_')`
- Recover UUID: `UUID.fromString(segment.replace('_', '-'))`
- Root entry path: `"root." + segment`
- Child path: `parentPath + "." + segment`
- `UNIQUE (site_id, path)` enforced in DB.
- Cycles impossible by construction — no cycle detection code needed.
- `?parentId=root` → `nlevel(path) = 1`
- `?parentId={uuid}` → resolve parent path, then `path <@ parentPath AND nlevel(path) = nlevel(parentPath) + 1`

### Delete cascade — use case, not DDL
No `ON DELETE CASCADE` in FK definitions. Use cases explicitly delete in this order:

**Delete site:** `site_entry_published` → `site_entry_drafts` → `site_entries` → `site_published` → `site_drafts` → `sites`

**Delete entry:** check for children first (`path <@ entryPath AND id != entryId`) → `409` if any exist → else delete `site_entry_published`, `site_entry_drafts`, `site_entries` row.

### Content rules
- `content` column: `JSONB NOT NULL` with `CHECK (jsonb_typeof(content) = 'object')` in DB.
- Maximum content size: **1MB** — validated in use case before writing.
- Backend never interprets content structure. No typed section DTOs.

### Ownership validation
Every use case that reads or writes a site or entry must validate `owner_user_id`. Never rely on authentication alone.

### Roles
- `ADMIN` = `/admin/**` (system operations, not yet implemented)
- `EDITOR` = `/cms/**` (own sites and entries)
- Default role on `POST /auth/register`: `EDITOR`
- `site_collaborators.role` uses `SITE_EDITOR` / `SITE_OWNER` etc. — different naming to avoid ambiguity with global roles

### contentSchema
- `VARCHAR(100) NULL` on `sites`. Written by CMS UI (e.g. `"portfolio-v1"`).
- Returned in public response so frontends know how to render.
- **Changing `contentSchema` in production breaks deployed frontends.**

### ?type filter
Uses index `(site_id, type)` on `site_entries`, joined with `site_entry_published`. Single DB query — no secondary fetch.

### API prefixes
```
/auth/**    no auth required
/cms/**     JWT required — user's own resources
/public/**  no auth required — read-only published content
/admin/**   reserved, not implemented
```

### Caching — required before production
Spring Cache + Caffeine, `expireAfterWrite=1h`. **Evict on publish, unpublish, AND delete.** Migrate to Redis for multiple instances.

### Rate limiting — required before production
Bucket4j: `/public/**` ~20 req/s per IP; `/cms/**` ~10 req/s per authenticated user. Returns `429`.

### plan_id on users
`UUID NULL` column exists. **No FK to `plans`** until plans logic is implemented.

### Future tables
`site_collaborators`, `media_assets`, `site_domains`, `site_webhooks`, `plans` are created in `V5` with no business logic. Do not add logic to them until the feature is explicitly scoped.

---

## Test profile

`spring.profiles.active=test` activates `application-test.yml`:
- DB: `cms_db_test`
- `ddl-auto: create-drop` — Hibernate manages schema
- Flyway disabled

---

## API surface

- Base path: `/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API docs: `http://localhost:8080/api/v3/api-docs`
- Auth header: `Authorization: Bearer <token>`
