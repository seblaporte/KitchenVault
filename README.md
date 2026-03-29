# My Cookidoo

Application personnelle pour récupérer, stocker et consulter les recettes Cookidoo via une interface plus ergonomique que l'application officielle.

> La documentation complète est disponible dans [Antora](#documentation-antora).

## Architecture

```
Angular (4200) ──HTTP──▶ Spring Boot (8080) ──@HttpExchange──▶ FastAPI Python (8001)
                                │                                      │
                           PostgreSQL (5432)                    API Cookidoo (externe)
```

| Composant           | Rôle                                              | Technologie                            |
|---------------------|---------------------------------------------------|----------------------------------------|
| `cookidoo-service`  | Wrapper autour de l'API Cookidoo non-officielle   | Python 3.12 · FastAPI · cookidoo-api  |
| `backend`           | API REST, persistance, synchronisation planifiée  | Java 25 · Spring Boot 3.4 · PostgreSQL 17 |
| `frontend`          | Interface utilisateur                             | Angular 21 · TailwindCSS 4            |

## Prérequis

- [Podman](https://podman.io/) + podman-compose
- Java 25
- Maven 3.9+
- Node.js 22+
- Python 3.12+

## Démarrage

### 1. Variables d'environnement

```bash
cp .env.example .env
# Renseigner COOKIDOO_EMAIL et COOKIDOO_PASSWORD
```

### 2. Infrastructure (PostgreSQL + pgAdmin + microservice Python)

```bash
podman compose up -d
```

| Service          | URL                                            |
|------------------|------------------------------------------------|
| PostgreSQL       | `localhost:5432`                               |
| pgAdmin          | http://localhost:5050 (admin@mycookidoo.local / admin) |
| cookidoo-service | http://localhost:8001/health                   |

### 3. Backend Spring Boot

```bash
./mvnw spring-boot:run -pl backend
```

- API : http://localhost:8080
- Swagger UI : http://localhost:8080/swagger-ui.html

### 4. Frontend Angular

```bash
cd frontend
npm install
npm run generate:api   # génère le client depuis api.yaml
npm start
```

- Application : http://localhost:4200
- Interface d'administration : http://localhost:4200/admin

### Lancer une synchronisation

```bash
# Via curl
curl -X POST http://localhost:8080/api/v1/sync

# Vérifier le statut
curl http://localhost:8080/api/v1/sync/latest
```

## Tests

```bash
# Backend (JUnit 5 + Testcontainers + AssertJ-DB)
./mvnw test

# Frontend
cd frontend && ng test

# Python
cd cookidoo-service && python -m pytest
```

## Documentation Antora

La documentation technique détaillée (architecture, modèle de données, API, flux de synchronisation) est générée avec [Antora](https://antora.org/) depuis le dossier `docs/`.

```bash
cd docs
npm install
npx antora antora-playbook.yml
# → ouvrir build/site/index.html
```

Les diagrammes (architecture, flux, ERD) sont générés automatiquement en SVG depuis le code PlantUML via [Kroki](https://kroki.io/).

## Structure du projet

```
my-cookidoo/
├── compose.yaml              # Infrastructure Podman
├── pom.xml                   # Parent Maven multi-module
├── contracts/                # Contrat OpenAPI (api.yaml) — source of truth
├── backend/                  # Application Spring Boot
├── cookidoo-service/         # Microservice Python FastAPI
├── frontend/                 # Application Angular
└── docs/                     # Documentation Antora
```
