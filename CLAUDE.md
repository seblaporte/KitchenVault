# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Java / Spring Boot)

```bash
# Activate Java 25 (reads .sdkmanrc — required before Maven commands)
sdk env

# Full build (parent + contracts + backend)
mvn clean install

# Run only backend
mvn spring-boot:run -pl backend

# Run all backend tests
mvn test -pl backend

# Run a single test class
mvn test -pl backend -Dtest=SyncServiceTest

# Build contracts only (regenerates Java interfaces from api.yaml)
mvn clean install -pl contracts
```

### Frontend (Angular)

```bash
cd frontend
npm run generate:api   # Regenerate TypeScript client from api.yaml (required before first run)
npm start              # ng serve — dev server on :4200
npm run build          # Production build
npm test               # Karma tests with coverage
npm run lint           # ESLint
```

### Python microservice

```bash
cd cookidoo-service
pip install -e ".[test]"
python -m pytest
```

### Infrastructure (Podman)

```bash
podman compose up -d                 # Start all (PostgreSQL, pgAdmin, cookidoo-service)
podman compose up -d postgres        # Start only PostgreSQL
podman compose stop cookidoo-service
```

## Architecture

### Multi-module layout

```
pom.xml (parent)
├── contracts/   → api.yaml → generates Java interfaces + DTOs (fr.seblaporte.kitchenvault.generated.*)
└── backend/     → implements generated interfaces via delegate pattern
```

The `contracts` module installs a JAR to `~/.m2`; `backend` depends on it as an ordinary Maven dependency. **Never modify generated code** — change `api.yaml` and rebuild `contracts`.

Frontend generates its own TypeScript client from the same `api.yaml` via `npm run generate:api` into `projects/api-client/src/generated/` (gitignored).

### OpenAPI delegate pattern (backend)

Generated code produces two interfaces per API tag:
- `XxxApi` — the Spring controller interface (routes, request mapping, HTTP verbs)
- `XxxApiDelegate` — the delegate interface for business logic

Controllers call through to delegates automatically. Always implement `*ApiDelegate`, never `*Api` directly:

```java
@Component
public class SyncDelegate implements SyncApiDelegate {
    @Override
    public ResponseEntity<SyncRunDto> triggerSync() { ... }
}
```

### `@HttpExchange` client (Spring → Python)

`CookidooServiceClient` is a declarative HTTP client configured in `CookidooClientConfig` using `RestClient` + `HttpServiceProxyFactory`. The base URL comes from `cookidoo.service.url` in `application.yml`.

### Async sync flow

`SyncService.triggerSync()` is transactional: it checks no sync is RUNNING, saves a `SyncRun` (status=RUNNING), fires `executeSyncAsync()` (`@Async`), and **returns immediately** with the RUNNING run. The actual sync runs in a virtual thread. The scheduled sync (`@Scheduled`) is synchronous and reuses `executeSync()`.

### MapStruct + Lombok annotation processor order

The `maven-compiler-plugin` in `backend/pom.xml` must keep this order in `annotationProcessorPaths`:
1. `lombok`
2. `lombok-mapstruct-binding`
3. `mapstruct-processor`
4. `spring-boot-configuration-processor`

Changing this order breaks MapStruct's ability to see Lombok-generated accessors.

### SyncMapper enum name clash

`SyncStatus` exists in both `fr.seblaporte.kitchenvault.entity` and `fr.seblaporte.kitchenvault.generated.model`. The `mapStatus()` default method in `SyncMapper` uses fully qualified names for both to avoid ambiguity.

## Testing

### Repository integration tests (AssertJ-DB)

Repository ITs use `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@ActiveProfiles("test")`.

The test profile (`application-test.yml`) configures `jdbc:tc:postgresql:17:///cookidoo` — Testcontainers JDBC URL that auto-starts a container with no `@Container` annotation needed.

Tests are annotated `@Transactional(propagation = NOT_SUPPORTED)` so saves are committed and visible to AssertJ-DB's separate JDBC connection. Each test cleans up in `@BeforeEach`.

AssertJ-DB assertions:
```java
org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe"))
    .hasNumberOfRows(1)
    .row(0).value("name").isEqualTo("Tarte aux pommes");
```

Standard AssertJ is imported statically; AssertJ-DB is called fully qualified to avoid the `assertThat` name clash.

### Service unit tests

`@ExtendWith(MockitoExtension.class)` + `@InjectMocks` + `@Mock`. The `CookidooProperties` record is mocked by stubbing `properties.sync()` etc. in `@BeforeEach`.

### Controller tests

`@WebMvcTest` with `@Import(XxxDelegate.class)` and `@MockitoBean` for services and mappers. The generated `*Api` controller is loaded automatically via `@WebMvcTest`'s component scan.

### Test profile notes

- `cookidoo.sync.cron: "-"` disables `@Scheduled` in tests
- `cookidoo.service.url: http://localhost:9999` is intended for WireMock overrides in integration tests

## Key configuration

| Property | Default | Purpose |
|---|---|---|
| `cookidoo.service.url` | `http://localhost:8001` | Python FastAPI base URL |
| `cookidoo.sync.cron` | `0 0 3 * * *` | Scheduled sync (set `"-"` to disable) |
| `cookidoo.sync.resync-after-hours` | `24` | Recipes older than this are re-fetched |
| `spring.threads.virtual.enabled` | `true` | Virtual threads for @Async |
| `spring.jpa.open-in-view` | `false` | OSIV disabled |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema owned by Liquibase |

## Documentation

Full technical documentation (architecture, data model, API reference, sync flow) is in `docs/` and built with Antora + asciidoctor-kroki. Diagrams are PlantUML/ERD as code inside `[kroki,plantuml,svg]` blocks.

```bash
cd docs
npm install
npm run serve   # build + HTTP server → http://localhost:8888
# NB: ne jamais ouvrir build/site/ directement en file://, les assets CSS/JS sont bloqués
```
