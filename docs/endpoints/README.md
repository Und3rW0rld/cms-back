# Endpoints documentation

Each file in this folder documents one endpoint group — signature, purpose, request/response shape, auth requirements, and error cases. This is the source of truth for HTTP contracts; keep it in sync with the actual controllers as they're implemented.

Code comments explain *why* (architecture decisions, non-obvious business rules). This folder explains *what* the API does from a caller's perspective — the two are complementary, not duplicates.

## Groups

| File | Scope | Status |
|---|---|---|
| [auth.md](auth.md) | `/auth/**` — register, login | Use cases done (#23), controller pending (#24) |
| `cms-sites.md` | `/cms/sites/**` — CRUD + draft/publish | Pending (#25-#27) |
| `cms-entries.md` | `/cms/sites/{id}/entries/**` — CRUD + draft/publish | Pending (#28-#29) |
| `public.md` | `/public/**` — read published content | Pending (#30) |

## Postman collection

[`postman/cms-back.postman_collection.json`](../postman/cms-back.postman_collection.json) — importable collection mirroring these docs. Uses a collection variable `{{baseUrl}}` (default `http://localhost:8080/api`) and `{{accessToken}}` (set automatically by the login request's post-response script). Update it alongside this folder as endpoints are implemented.
