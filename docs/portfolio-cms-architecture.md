# Arquitectura y modelo de datos propuesto para el CMS de portafolios

## Checklist

- [x] Definir el objetivo funcional del micro CMS
- [x] Proponer una arquitectura alineada con la hexagonal ya presente
- [x] Separar responsabilidades entre lectura pública, administración y persistencia
- [x] Diseñar un modelo de datos compatible con `Photo`, `Fact`, `Skill`, `Job`, `Project` y `Post`
- [x] Aterrizar la propuesta al stack actual: Spring Boot + PostgreSQL + MongoDB
- [x] Dejar una guía concreta de endpoints, paquetes y evolución

---

## 1. Objetivo del sistema

Este backend puede funcionar como un **headless CMS orientado a portafolios**. La idea no es solo guardar contenido, sino exponerlo de forma consistente para uno o varios frontends.

En tu caso inicial, el primer consumidor será un **portafolio de desarrollador backend**, pero conviene diseñarlo como si mañana fueras a publicar otros perfiles o incluso otros tipos de portafolio.

### Capacidades objetivo

1. **Administrar contenido** desde endpoints privados.
2. **Publicar contenido** listo para frontend mediante endpoints públicos.
3. **Versionar o separar draft/published** para no romper el sitio mientras editas.
4. **Reutilizar estructuras** comunes como imágenes, links, tags y bloques de texto.
5. **Mantener flexibilidad** para cambiar secciones del portafolio sin rediseñar toda la base relacional.

---

## 2. Lectura del proyecto actual

Según la estructura actual del repositorio:

- Ya existe una base de **arquitectura hexagonal**.
- Ya tienes **Spring Security + JWT** para la zona administrativa.
- Ya existe persistencia híbrida con:
  - **PostgreSQL**
  - **MongoDB**
- `users` ya vive en PostgreSQL.

Eso sugiere una decisión muy buena para este CMS:

- usar **PostgreSQL para identidad, control, metadatos e integridad fuerte**
- usar **MongoDB para el contenido editorial del portafolio**, porque sus secciones son más flexibles y documentales

---

## 3. Propuesta de arquitectura

## 3.1 Separación por dominios funcionales

Te recomiendo dividir el sistema en 3 áreas conceptuales:

### A. Identity & Access
Responsable de:
- usuarios CMS
- autenticación JWT
- roles (`ADMIN`, `EDITOR`, `VIEWER`)

### B. Portfolio Management
Responsable de:
- portafolios
- secciones del contenido
- validaciones editoriales
- estado draft/published

### C. Public Content Delivery
Responsable de:
- endpoints públicos para frontend
- payload optimizado para consumo
- entrega de la versión publicada

---

## 3.2 Arquitectura hexagonal aterrizada al caso

### Dominio
Modelos y reglas de negocio:
- `Portfolio`
- `PortfolioContent`
- `Photo`
- `Fact`
- `Skill`
- `Job`
- `Project`
- `Post`
- `PublicationStatus`

### Puertos de entrada
Casos de uso como interfaces:
- `CreatePortfolioUseCase`
- `UpdatePortfolioDraftUseCase`
- `PublishPortfolioUseCase`
- `GetPortfolioPublicUseCase`
- `GetPortfolioAdminUseCase`

### Aplicación
Implementación de casos de uso:
- orquesta validaciones
- decide qué va a PostgreSQL y qué va a Mongo
- controla publicación y versionado

### Adaptadores de entrada
- REST controllers admin
- REST controllers public
- autenticación JWT

### Adaptadores de salida
- repositorios JPA para metadatos relacionales
- repositorios Mongo para contenido documental
- futuro adaptador de storage si luego subes imágenes propias

---

## 3.3 Estrategia recomendada de persistencia

## Opción recomendada: modelo híbrido

### PostgreSQL
Guardar aquí lo que requiere:
- unicidad fuerte
- relaciones
- auditoría simple
- filtros administrativos

### MongoDB
Guardar aquí lo que es:
- contenido estructurado pero flexible
- secciones embebidas
- listas de skills, jobs, projects y posts
- snapshots draft/published

Esta opción encaja perfecto con tu stack actual y evita forzar el contenido editorial a muchas tablas relacionales innecesarias.

---

## 4. Modelo de dominio recomendado

## 4.1 Aggregate principal: `Portfolio`

`Portfolio` representa el portafolio como unidad publicable.

### Responsabilidades
- identidad del portafolio
- dueño o autor
- slug público
- estado de publicación
- referencia a la versión publicada
- timestamps

### Propuesta conceptual

```text
Portfolio
- id: UUID
- ownerUserId: Long
- slug: String
- title: String
- summary: String
- status: DRAFT | PUBLISHED | ARCHIVED
- currentDraftVersion: Int
- currentPublishedVersion: Int?
- createdAt: Instant
- updatedAt: Instant
```

> `title` y `summary` son metadatos básicos. El contenido grande vive en Mongo.

---

## 4.2 Aggregate/documento: `PortfolioContent`

Este documento contiene el contenido editable real de una versión del portafolio.

### Propuesta conceptual

```text
PortfolioContent
- id: ObjectId
- portfolioId: UUID
- version: Int
- seo: SeoBlock
- hero: HeroBlock
- about: AboutBlock
- skills: List<Skill>
- jobs: List<Job>
- projects: List<Project>
- posts: List<Post>
- createdAt: Instant
- updatedAt: Instant
```

### Por qué conviene embebido

Porque el frontend del portafolio normalmente necesita cargar casi todo el contenido en una sola consulta o en muy pocas. Además:

- las listas no son masivas
- todo pertenece al mismo aggregate visual
- hay alta cohesión entre secciones

---

## 4.3 Value Objects reutilizables

### `Photo`

```text
Photo
- src: String
- alt: String
```

#### Reglas
- `src` obligatorio
- `alt` obligatorio
- idealmente URL absoluta o path resuelto por un media service

---

### `Fact`

```text
Fact
- label: String
- value: String
```

#### Reglas
- `label` corto y estable
- si quieres consistencia interna, puedes guardar además un `key`

Sugerencia futura:

```text
Fact
- key: String   // location, experience, timezone
- label: String // Location
- value: String // Colombia 🇨🇴
```

---

### `Skill`

```text
Skill
- name: String
- slug: String
- category: BACKEND | FRONTEND | CLOUD_DEVOPS
- fallbackIcon: String?
- description: String
```

#### Reglas
- `slug` pensado para simple-icons
- `fallbackIcon` opcional
- `name + category` podría ser único dentro del mismo portafolio

---

### `Job`

```text
Job
- company: String
- logoSlug: String?
- role: String
- period: String
- location: String
- highlights: List<String>
- tech: List<String>
```

#### Reglas
- `highlights`: mínimo 2, recomendado máximo 4
- `tech`: lista corta de tags
- si luego quieres orden automático, agrega `order`

---

### `Project`

```text
Project
- name: String
- filename: String
- description: String
- tech: List<String>
- github: String
- live: String?
- preview: String?
```

#### Reglas
- `github` obligatorio y válido
- `live` mejor nullable en persistencia
- el frontend puede convertir `null` a `#` si así lo necesita visualmente

> A nivel de modelo es mejor `null` que guardar `#`, porque `#` no representa un recurso real.

---

### `Post`

```text
Post
- title: String
- date: LocalDate
- excerpt: String
- tags: List<String>
- readTime: String
- banner: String?
```

#### Reglas
- `date` persistirlo como fecha real, no solo string
- serializar como `YYYY-MM-DD`
- `tags` con normalización opcional (`java`, `spring`, `architecture`)

---

## 4.4 Bloques recomendados adicionales

Aunque no los listaste explícitamente, para un portafolio real suele convenir agregar estos bloques desde el inicio.

### `SeoBlock`

```text
SeoBlock
- title: String
- description: String
- canonicalUrl: String?
- ogImage: String?
```

### `HeroBlock`

```text
HeroBlock
- greeting: String
- name: String
- tagline: String
- primaryCtaLabel: String
- primaryCtaUrl: String
- secondaryCtaLabel: String?
- secondaryCtaUrl: String?
```

### `AboutBlock`

```text
AboutBlock
- title: String
- description: String
- photos: List<Photo>
- facts: List<Fact>
```

Esto evita que `Photo` y `Fact` queden huérfanos dentro del documento.

---

## 5. Modelo de persistencia recomendado

## 5.1 PostgreSQL: tablas relacionales

### Tabla `users`
Ya existe y está bien ubicada en PostgreSQL.

### Nueva tabla `portfolios`

```text
portfolios
- id UUID PK
- owner_user_id BIGINT NOT NULL FK users(id)
- slug VARCHAR(120) NOT NULL UNIQUE
- title VARCHAR(150) NOT NULL
- summary VARCHAR(255) NULL
- status VARCHAR(20) NOT NULL
- current_draft_version INT NOT NULL DEFAULT 1
- current_published_version INT NULL
- created_at TIMESTAMP NOT NULL
- updated_at TIMESTAMP NOT NULL
```

### Tabla opcional `portfolio_publications`

Útil si quieres histórico editorial.

```text
portfolio_publications
- id UUID PK
- portfolio_id UUID NOT NULL FK portfolios(id)
- version INT NOT NULL
- published_by BIGINT NOT NULL FK users(id)
- published_at TIMESTAMP NOT NULL
- notes VARCHAR(255) NULL
```

### Tabla opcional `media_assets`

Solo si más adelante subes imágenes al backend en vez de guardar URLs externas.

```text
media_assets
- id UUID PK
- owner_user_id BIGINT NOT NULL
- storage_key VARCHAR(255) NOT NULL UNIQUE
- public_url VARCHAR(500) NOT NULL
- alt VARCHAR(255) NULL
- mime_type VARCHAR(100) NOT NULL
- created_at TIMESTAMP NOT NULL
```

---

## 5.2 MongoDB: colección documental

### Colección `portfolio_contents`

Documento sugerido:

```json
{
  "portfolioId": "b7fd3b44-66e6-4cb0-9d76-1f6239a11d5a",
  "version": 3,
  "seo": {
    "title": "Santiago Acevedo | Backend Developer",
    "description": "Java, Spring Boot, arquitectura hexagonal y cloud",
    "canonicalUrl": "https://tu-dominio.dev",
    "ogImage": "https://cdn.example.com/og.png"
  },
  "hero": {
    "greeting": "Hola, soy",
    "name": "Santiago Acevedo",
    "tagline": "Backend developer building APIs, integrations and scalable systems.",
    "primaryCtaLabel": "Ver proyectos",
    "primaryCtaUrl": "/projects",
    "secondaryCtaLabel": "Contactar",
    "secondaryCtaUrl": "/contact"
  },
  "about": {
    "title": "About me",
    "description": "Desarrollador backend enfocado en Java, Spring y diseño de APIs.",
    "photos": [
      {
        "src": "https://cdn.example.com/profile.jpg",
        "alt": "Foto de perfil de Santiago Acevedo"
      }
    ],
    "facts": [
      {
        "label": "Location",
        "value": "Colombia 🇨🇴"
      },
      {
        "label": "Experience",
        "value": "4+ years"
      }
    ]
  },
  "skills": [
    {
      "name": "Java",
      "slug": "openjdk",
      "category": "BACKEND",
      "description": "Primary language · 4+ years"
    }
  ],
  "jobs": [
    {
      "company": "Acme",
      "logoSlug": "spring",
      "role": "Backend Developer",
      "period": "2022 — Present",
      "location": "Remote",
      "highlights": [
        "Built resilient REST APIs",
        "Improved query performance by 40%"
      ],
      "tech": ["Java", "Spring Boot", "PostgreSQL"]
    }
  ],
  "projects": [
    {
      "name": "Event Stream Engine",
      "filename": "EventStreamEngine.java",
      "description": "Motor para procesamiento de eventos de dominio.",
      "tech": ["Java", "Kafka", "Docker"],
      "github": "https://github.com/user/repo",
      "live": null,
      "preview": "https://cdn.example.com/project-preview.png"
    }
  ],
  "posts": [
    {
      "title": "Designing hexagonal APIs",
      "date": "2026-06-18",
      "excerpt": "Qué separar en dominio, aplicación y adaptadores.",
      "tags": ["architecture", "spring", "hexagonal"],
      "readTime": "8 min read",
      "banner": "https://cdn.example.com/post-banner.png"
    }
  ],
  "createdAt": "2026-06-18T10:00:00Z",
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

### Índices sugeridos en Mongo

- índice único compuesto: `(portfolioId, version)`
- índice simple: `portfolioId`

---

## 6. Decisión importante: embebidos vs colecciones separadas

## Recomendación inicial

Mantén `skills`, `jobs`, `projects` y `posts` **embebidos dentro de `portfolio_contents`**.

### Ventajas
- lectura muy rápida para el frontend
- un solo documento representa una versión completa
- fácil publicar un snapshot consistente
- menos joins lógicos entre colecciones

### Cuándo separar `posts`
Si más adelante los posts crecen mucho o quieres:
- paginación real
- búsqueda full-text más avanzada
- URLs individuales por post
- borradores por post

entonces sí conviene moverlos a una colección propia, por ejemplo `portfolio_posts`.

---

## 7. Endpoints sugeridos

## 7.1 Públicos

### Obtener portafolio publicado completo
`GET /public/portfolios/{slug}`

Devuelve la versión publicada lista para frontend.

### Obtener solo una sección
Opcional si quieres optimización fina:
- `GET /public/portfolios/{slug}/skills`
- `GET /public/portfolios/{slug}/projects`
- `GET /public/portfolios/{slug}/posts`

> Empezaría con un endpoint agregado y solo separaría si realmente hace falta.

---

## 7.2 Administrativos

### Portafolios
- `POST /admin/portfolios`
- `GET /admin/portfolios`
- `GET /admin/portfolios/{id}`
- `PATCH /admin/portfolios/{id}/metadata`

### Draft content
- `GET /admin/portfolios/{id}/draft`
- `PUT /admin/portfolios/{id}/draft`

### Publicación
- `POST /admin/portfolios/{id}/publish`
- `POST /admin/portfolios/{id}/unpublish`

### Media
Más adelante, si agregas uploads:
- `POST /admin/media`
- `GET /admin/media`

---

## 8. Contratos de respuesta recomendados

## 8.1 Respuesta pública

Conviene que el endpoint público entregue un payload orientado al frontend, por ejemplo:

```json
{
  "slug": "santiago-acevedo",
  "title": "Santiago Acevedo",
  "summary": "Backend Developer",
  "seo": {},
  "hero": {},
  "about": {},
  "skills": [],
  "jobs": [],
  "projects": [],
  "posts": [],
  "publishedAt": "2026-06-18T10:00:00Z"
}
```

## 8.2 Respuesta admin

Puede incluir además:
- `id`
- `status`
- `currentDraftVersion`
- `currentPublishedVersion`
- `updatedAt`

---

## 9. Validaciones de negocio recomendadas

## Portafolio
- `slug` único
- `title` obligatorio
- solo usuarios `ADMIN` o `EDITOR` pueden editar

## Photos
- `src` no vacío
- `alt` obligatorio

## Skills
- `name`, `slug`, `category`, `description` obligatorios
- categoría restringida al enum

## Jobs
- `company`, `role`, `period`, `location` obligatorios
- `highlights.size >= 2`
- `highlights.size <= 4` recomendado

## Projects
- `name`, `filename`, `description`, `github` obligatorios
- `github` debe ser URL válida
- `live` nullable

## Posts
- `title`, `date`, `excerpt`, `readTime` obligatorios
- `tags` nunca `null`, aunque sí puede ser lista vacía

---

## 10. Estructura de paquetes sugerida

Aterrizado a tu proyecto actual:

```text
src/main/java/com/cms/
├── domain/
│   ├── model/
│   │   ├── portfolio/
│   │   │   ├── Portfolio.java
│   │   │   ├── PortfolioContent.java
│   │   │   ├── PublicationStatus.java
│   │   │   ├── SeoBlock.java
│   │   │   ├── HeroBlock.java
│   │   │   ├── AboutBlock.java
│   │   │   ├── Photo.java
│   │   │   ├── Fact.java
│   │   │   ├── Skill.java
│   │   │   ├── SkillCategory.java
│   │   │   ├── Job.java
│   │   │   ├── Project.java
│   │   │   └── Post.java
│   └── port/
│       ├── in/
│       │   ├── CreatePortfolioUseCase.java
│       │   ├── UpdatePortfolioDraftUseCase.java
│       │   ├── PublishPortfolioUseCase.java
│       │   └── GetPortfolioPublicUseCase.java
│       └── out/
│           ├── PortfolioRepository.java
│           ├── PortfolioContentRepository.java
│           └── MediaAssetRepository.java
├── application/
│   └── usecase/
│       ├── CreatePortfolioService.java
│       ├── UpdatePortfolioDraftService.java
│       ├── PublishPortfolioService.java
│       └── GetPortfolioPublicService.java
├── adapters/
│   ├── in/web/controller/
│   │   ├── AdminPortfolioController.java
│   │   └── PublicPortfolioController.java
│   └── out/persistence/
│       ├── jpa/
│       │   ├── entity/
│       │   │   └── PortfolioEntity.java
│       │   ├── repository/
│       │   │   └── PortfolioJpaRepository.java
│       │   └── adapter/
│       │       └── PortfolioPersistenceAdapter.java
│       └── mongo/
│           ├── document/
│           │   └── PortfolioContentDocument.java
│           ├── repository/
│           │   └── PortfolioContentMongoRepository.java
│           └── adapter/
│               └── PortfolioContentPersistenceAdapter.java
```

---

## 11. Flujo editorial recomendado

## Caso: edición y publicación

1. Un editor crea el portafolio en PostgreSQL.
2. Se crea `version = 1` del contenido en Mongo como draft.
3. El editor actualiza el draft desde `/admin/portfolios/{id}/draft`.
4. Al publicar:
   - se valida el documento completo
   - se actualiza `currentPublishedVersion`
   - el endpoint público empieza a servir esa versión
5. Si luego editas de nuevo:
   - puedes sobreescribir el draft actual
   - o crear una nueva versión draft `published + 1`

## Recomendación práctica

Empieza con el esquema más simple:
- **1 draft activo por portafolio**
- **1 versión publicada referenciada desde PostgreSQL**

No agregues workflow complejo hasta que realmente lo necesites.

---

## 12. Estrategia de imágenes

Dado que tus estructuras ya usan `src`, mi recomendación inicial es:

### Fase 1
- guardar solo URLs
- permitir CDN, Cloudinary, S3 o assets estáticos

### Fase 2
- agregar módulo `media`
- subir archivo al backend
- almacenar metadatos en `media_assets`
- devolver una URL pública que luego se asigna a `Photo.src` o `Post.banner`

Eso evita complejidad temprana sin cerrar la puerta a crecer.

---

## 13. Decisiones recomendadas desde ya

## Mantener
- `users` en PostgreSQL
- contenido del portafolio en MongoDB
- endpoints públicos separados de endpoints admin
- DTOs específicos para admin y public
- validaciones con Bean Validation + reglas de dominio

## Evitar
- meter todo el portafolio en tablas relacionales desde el inicio
- usar `#` como valor persistido para links ausentes
- exponer directamente documentos internos sin DTOs
- acoplar el frontend a la estructura exacta de persistencia

---

## 14. MVP recomendado

Si quieres iterar rápido, el MVP podría quedar así:

### Persistencia
- PostgreSQL:
  - `users`
  - `portfolios`
- MongoDB:
  - `portfolio_contents`

### Endpoints
- `POST /admin/portfolios`
- `GET /admin/portfolios/{id}/draft`
- `PUT /admin/portfolios/{id}/draft`
- `POST /admin/portfolios/{id}/publish`
- `GET /public/portfolios/{slug}`

### Secciones del contenido MVP
- `seo`
- `hero`
- `about.photos`
- `about.facts`
- `skills`
- `jobs`
- `projects`
- `posts`

Con eso ya puedes alimentar un portafolio personal muy completo.

---

## 15. Recomendación final

La mejor decisión para este proyecto, con el stack que ya tienes, es:

1. **PostgreSQL para usuarios + metadatos del portafolio**
2. **MongoDB para el contenido versionado del portafolio**
3. **Hexagonal architecture** con puertos separados para lectura pública y administración
4. **Documento agregado por versión** para servir el portafolio completo al frontend
5. **DTOs públicos estables** para no filtrar detalles internos

Esta arquitectura te da:
- flexibilidad para evolucionar secciones
- buen performance de lectura
- seguridad administrativa
- posibilidad real de soportar más de un portafolio

---

## 16. Siguiente paso sugerido

El siguiente paso natural en este repositorio sería implementar, en este orden:

1. entidad JPA `PortfolioEntity`
2. documento Mongo `PortfolioContentDocument`
3. puertos de dominio de lectura/escritura
4. casos de uso de creación, edición draft y publicación
5. controllers `admin` y `public`
6. migración Flyway para `portfolios`

Si quieres, el siguiente paso puede ser que te deje ya armado el **esqueleto de dominio + persistencia + endpoints base** siguiendo exactamente este diseño.
