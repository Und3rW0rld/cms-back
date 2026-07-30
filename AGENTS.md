# AGENTS.md

## What this repo is

**Headless CMS API** — multi-user, Java 26 + Spring Boot 4.0, Hexagonal (Ports & Adapters) Architecture.

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

**Pending before first feature implementation:**
- Rewrite `UserEntity` — remove `UserDetails` implementation from the JPA entity (issue #10)

---

## Commands

```bash
mvn spring-boot:run        # runs with dev profile (application-dev.properties)
mvn test                   # always runs with test profile (forced by Surefire)
mvn package -DskipTests
```

No CI, no Makefile, no Docker Compose. Maven only.

---

## Spring profiles

| Profile | When active | DB | Flyway | ddl-auto |
|---|---|---|---|---|
| `dev` | `mvn spring-boot:run` | `cms_db` | enabled | `validate` |
| `test` | `mvn test` (always, forced by Surefire) | `cms_db_test` | disabled | `create-drop` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | `${DB_URL}` (no default) | enabled | `none` |

**`mvn test` always uses the `test` profile** — this is enforced by `maven-surefire-plugin` in `pom.xml`, not by the developer. No manual profile switching needed.

Test classes that use `@SpringBootTest` should also annotate with `@ActiveProfiles("test")` as a safety net.

In `prod`: no defaults for DB credentials — missing env vars cause startup failure intentionally. Swagger UI is disabled.

---

## Required services

PostgreSQL via Neon (cloud, free tier). No local database required.

Configure credentials in `.env` at the project root (never committed — in `.gitignore`):

```
DB_URL=jdbc:postgresql://<host>/neondb?sslmode=require
DB_USERNAME=<username>
DB_PASSWORD=<password>
JWT_SECRET=<min-32-chars>
```

Load `.env` before running:
```powershell
Get-Content .env | Where-Object { $_ -notmatch '^#' -and $_ -ne '' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
}
mvn spring-boot:run
```

For `cms_db_test` (integration tests): also needs a separate database in Neon or local PostgreSQL.

---

## Environment variables

| Variable | Required in | Notes |
|---|---|---|
| `DB_URL` | dev, prod | Full JDBC URL including `?sslmode=require` for Neon |
| `DB_USERNAME` | dev, prod | |
| `DB_PASSWORD` | dev, prod | |
| `JWT_SECRET` | prod | Min 32 chars. Dev has insecure default in `application-dev.properties` |
| `ALLOWED_ORIGINS` | prod | Comma-separated list of allowed CORS origins (e.g. `https://app.example.com`) |

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
V1  users, roles, user_roles, user_credentials, user_oauth_providers, user_profiles  ← DONE
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

## Testing conventions

### Four layers

| Layer | Annotation | DB | Use for |
|---|---|---|---|
| Unit | `@ExtendWith(MockitoExtension.class)` | None | Use case logic, domain rules |
| Integration | `@SpringBootTest` + `@Testcontainers` | PostgreSQL container | Repository queries, cascade, ltree, JSONB |
| Web slice | `@WebMvcTest` | None (mocked) | HTTP contracts, status codes, error envelope |
| JPA slice | `@DataJpaTest` + `@Testcontainers` | PostgreSQL container | Custom queries, index usage |

### Rules
- All test classes annotate with `@ActiveProfiles("test")` — always, even if Surefire already forces it
- `@Transactional` on integration test classes — automatic rollback, no manual cleanup
- `@DataJpaTest` must use `@AutoConfigureTestDatabase(replace = NONE)` — prevents H2 replacement, required for ltree queries
- Use Testcontainers for any test that hits the DB — do not depend on `cms_db_test` being pre-created (breaks CI)
- Unit tests have no profile dependency — keep them pure Java

### Testcontainers base pattern
```java
@Testcontainers
class MyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

Testcontainers requires Docker running. Dependencies added in M6 milestone.

---

## Test profile

`spring.profiles.active=test` activates `application-test.properties`:
- DB: `cms_db_test` (fallback for slice tests without Testcontainers)
- `ddl-auto: create-drop` — Hibernate manages schema
- Flyway disabled
- Logging: WARN — minimal output

---

## API surface

- Base path: `/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- API docs: `http://localhost:8080/api/v3/api-docs`
- Auth header: `Authorization: Bearer <token>`
