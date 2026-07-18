# AGENTS.md

## What this repo is

**Headless CMS API** — multi-user, built with **Java 21 + Spring Boot 3.5**, following strict **Hexagonal (Ports & Adapters) Architecture**.

The CMS is one application; each user's portfolio (or any other frontend) is a separate application that consumes this API. The CMS does not render HTML — it serves JSON. Any frontend integrates against it independently.

**Core purpose:** allow any registered user to manage structured content (portfolio, blog, etc.) through a UI, with draft/publish workflow, and expose the published version publicly via a read-only endpoint.

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
        ├── entity/UserEntity.java      # @Entity + UserDetails + Role enum
        └── repository/UserJpaRepository.java
```

- `UserEntity` implements `UserDetails` directly (intentional shortcut for early phase).
- `Role` enum embedded in `UserEntity`: `ADMIN`, `EDITOR`, `VIEWER` — authority strings prefixed `ROLE_`.
- **`domain/` and `application/` packages do not exist yet.**
- **No REST controllers exist yet.**
- **`src/test/` does not exist yet.**

### Planned next (from architecture doc, section 16)
In this order:
1. JPA entity `PortfolioEntity` + Flyway migration `V2__create_portfolios_table.sql`
2. MongoDB document `PortfolioContentDocument` (collection: `portfolio_contents`)
3. Domain ports (`domain/port/in/`, `domain/port/out/`)
4. Use case implementations (`application/usecase/`)
5. REST controllers: `AdminPortfolioController`, `PublicPortfolioController`

---

## Persistence strategy

- **PostgreSQL**: identity (`users`), portfolio metadata (`portfolios`). `ddl-auto: validate` — Hibernate does NOT manage schema; Flyway does.
- **MongoDB**: all editorial content (`portfolio_contents`) — embedded document per portfolio version with `skills`, `jobs`, `projects`, `posts`, `seo`, `hero`, `about`.
- `portfolio_publications` (publication history) is **explicitly out of scope** for now — omit it.
- Flyway migrations live in `src/main/resources/db/migration/`. Naming: `V{n}__{description}.sql`.

---

## Key design rules

### Identification
- Portfolios are identified by **UUID only** — no slug. Public endpoint uses UUID.
- Do not add slug-based lookup; that responsibility belongs to each user's own frontend.

### Multi-user isolation
- Each portfolio has an `ownerUserId`. Use case implementations must validate ownership — a user must never read or write another user's portfolio, regardless of role.
- `GET /admin/portfolios` must return only portfolios owned by the authenticated user, never all portfolios in the system.

### Draft/publish workflow
- Every portfolio has one active draft in MongoDB. Saving never auto-publishes.
- `POST /admin/portfolios/{id}/publish` snapshots the draft as the published version.
- The public endpoint always serves the published version, never the draft.
- Draft and published version can coexist in the same `portfolio_contents` document distinguished by `version` number.

### API contracts
- Public content endpoints: `/public/portfolios/{id}` — no auth
- Admin endpoints: `/admin/portfolios/**` — require `ADMIN` or `EDITOR` role
- DTOs must be separate for public vs admin responses; never expose persistence documents directly
- `live` field on `Project` must persist as `null`, not `"#"` — `null` means no URL
- `Post.date` persists as `LocalDate`, serialize as `YYYY-MM-DD`
- `Job.highlights`: min 2, max 4 recommended
- MongoDB compound unique index: `(portfolioId, version)` on `portfolio_contents`

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
