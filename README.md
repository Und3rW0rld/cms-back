# cms-back

Multi-user headless CMS API — Java 21, Spring Boot 3.5, Hexagonal Architecture.

Users manage content through a CMS UI; their own frontends (portfolio, blog, product page, etc.) consume the public API. The API serves JSON only.

## Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5.0 |
| Language | Java 26 |
| Database | PostgreSQL 16 + Spring Data JPA |
| Migrations | Flyway |
| Hierarchy | PostgreSQL `ltree` extension |
| Auth | Spring Security + JWT (jjwt 0.12.6) |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Utilities | Lombok, Bean Validation |

## Architecture

Hexagonal (Ports & Adapters). Full design spec: [`docs/portfolio-cms-architecture.md`](docs/portfolio-cms-architecture.md)

```
src/main/java/com/cms/
├── domain/
│   ├── model/          # Domain models (Site, SiteEntry, User)
│   └── port/
│       ├── in/         # Input ports (use case interfaces)
│       └── out/        # Output ports (repository interfaces)
├── application/
│   └── usecase/        # Use case implementations
└── adapters/
    ├── config/         # Spring beans: Security, OpenAPI
    ├── in/web/
    │   ├── controller/ # REST controllers
    │   └── dto/        # Request / response DTOs
    └── out/persistence/jpa/
        ├── entity/     # JPA entities
        ├── repository/ # Spring Data repositories
        └── adapter/    # Port implementations
```

## API surface

| Prefix | Auth | Purpose |
|---|---|---|
| `/auth/**` | None | Register, login |
| `/cms/**` | JWT | User's own sites and entries |
| `/public/**` | None | Read published content |
| `/admin/**` | JWT + ADMIN | System operations (reserved) |

Base path: `/api` — e.g. `http://localhost:8080/api/cms/sites`

## Getting started

### Prerequisites

- Java 26+
- Maven 3.9+
- PostgreSQL 16 running on `localhost:5432` with database `cms_db`

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_USERNAME` | `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `JWT_SECRET` | *(insecure default)* | JWT signing secret — **min 32 chars in production** |

### Run

```bash
mvn spring-boot:run
```

### API docs

```
http://localhost:8080/api/swagger-ui.html
```

## Authentication

Stateless JWT. All `/cms/**` endpoints require:

```
Authorization: Bearer <token>
```

Obtain a token via `POST /api/auth/login`.

## Database migrations

Flyway runs automatically on startup. Migration scripts:

```
src/main/resources/db/migration/
```

Naming: `V{n}__{description}.sql`

Migration order:

```
V1  User identity schema
V2  Sites
V3  Site entries (ltree)
V4  Draft and published content tables
V5  Future tables (collaborators, media, domains, webhooks, plans)
```

## Content model

All site and entry content is stored as `JSONB`. The backend does not interpret or validate content structure — the CMS UI defines it, the public frontend consumes it.

Publication state is determined by row existence in `site_published` / `site_entry_published` — there is no status column.

## Development notes

- Working directory: `C:\Projects\cms-back` (not OneDrive — git repos should not live inside OneDrive)
- Test profile: `spring.profiles.active=test` — uses `cms_db_test`, Flyway disabled, `ddl-auto: create-drop`
