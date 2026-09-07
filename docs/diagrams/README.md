# Diagrams

**Open only the `.html` files in a browser.** Nothing else in this folder is meant to be read.

| Open | What it is |
|---|---|
| [cms-system.architecture.html](cms-system.architecture.html) | System map: CMS UI, JWT, API, DB provider |
| [draft-save.sequence.html](draft-save.sequence.html) | GET draft → PUT with `If-Match` → 412 |
| [publication.lifecycle.html](publication.lifecycle.html) | Created → draft → published |

## Do not open this

| Folder | Purpose |
|---|---|
| `source/` | Archify JSON used to regenerate an HTML file. Not documentation. |

`*.visual-check.*` files and `.archify-delivery-*` folders are generator leftovers. They are in `.gitignore` and must not be committed.
