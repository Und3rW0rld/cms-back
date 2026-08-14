# Endpoints documentation

Each file in this folder documents one endpoint group — signature, purpose, request/response shape, auth requirements, and error cases. This is the source of truth for HTTP contracts; keep it in sync with the actual controllers as they're implemented.

Code comments explain *why* (architecture decisions, non-obvious business rules). This folder explains *what* the API does from a caller's perspective — the two are complementary, not duplicates.

**Stay agnostic to GitHub issue numbers.** This folder describes the current state of the system, not how it got built — issue references go stale the moment an issue closes or gets renumbered. If a decision needs a rationale/trigger, point to `docs/portfolio-cms-architecture.md` (e.g. "§14") instead of an issue number.

## Groups

| File | Scope | Status |
|---|---|---|
| [auth.md](auth.md) | `/auth/**` — register, login | Implemented |
| `cms-sites.md` | `/cms/sites/**` — CRUD + draft/publish | Not implemented |
| `cms-entries.md` | `/cms/sites/{id}/entries/**` — CRUD + draft/publish | Not implemented |
| `public.md` | `/public/**` — read published content | Not implemented |

## Postman collection

[`postman/cms-back.postman_collection.json`](../postman/cms-back.postman_collection.json) — importable collection mirroring these docs. Uses a collection variable `{{baseUrl}}` (default `http://localhost:8080/api`) and `{{accessToken}}` (set automatically by the login request's post-response script). Update it alongside this folder as endpoints are implemented.
