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
npm run e2e            # Cypress E2E headless (nécessite npm start dans un autre terminal)
npm run e2e:open       # Cypress interactif (GUI)
npm run e2e:ci         # Cypress headless Electron (CI)
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

## Interaction Style

- Prefer direct, concise answers over extensive autonomous exploration
- When investigating, state the hypothesis early and confirm before deep file-walking
- For debugging, propose a concrete fix within 2-3 tool calls when possible

## Git & PR Workflow
- After implementing changes, run the relevant build/tests before offering to commit
- Use conventional commit messages; group related file changes into single commits
- For PR reviews, read files manually if diff-based approaches return empty

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

## AI weekly meal planner

### Architecture

`WeeklyMealPlanService` orchestrates everything deterministically. The LLM does not write to the database — it returns a structured output and the service applies changes.

```
processChat()
  ├── Load/create WeeklyPlanSession
  ├── Fetch current week plan → inject into enriched message
  ├── agent.chat() → WeeklyPlanAgentResult {
  │     reply, quickActions,
  │     mealAssignments: [{date, mealType, recipeId, recipeName}],
  │     action: APPLY_PENDING | CLEAR_PENDING | null }
  ├── If action == APPLY_PENDING → apply pending changes from session
  ├── If action == CLEAR_PENDING → clear pending changes from session
  ├── If mealAssignments not empty:
  │     !initialDone → upsertEntry() each, setInitialDone(true)
  │     initialDone  → store as pendingChanges JSON on session
  └── Return WeeklyPlanChatResponse with proposedChanges if any pending
```

The agent has **no tools** — only the RAG `contentRetriever` for recipe context. The current week plan is injected as plain text in the enriched message.

### Key pitfalls

**`@Transactional` on `@Tool` classes breaks LangChain4J scanning.** Spring wraps transactional beans in CGLIB proxies; langchain4j scans the proxy class and doesn't find `@Tool` annotations. Fix: remove `@Transactional` from tool classes — the calling service's transaction propagates.

**`jsonb` column requires `@JdbcTypeCode(SqlTypes.JSON)`.** Using only `columnDefinition = "jsonb"` in `@Column` tells Hibernate the DDL type but not the JDBC binding type — Hibernate binds `String` as `varchar`, causing a runtime `SQLGrammarException`. Always add `@JdbcTypeCode(SqlTypes.JSON)` on any `String` field backed by a jsonb column.

**`ObjectMapper` is not auto-wired as a Spring bean by default** in some test/component contexts. Use field initialization `private final ObjectMapper objectMapper = new ObjectMapper()` instead of constructor injection in tool/service classes.

### Frontend drawer layout

The weekly planner drawer is `position: fixed; right: 0`. When open, a `drawer-open` class is added to `<body>` via `Renderer2`, and a CSS rule in `styles.css` removes the `max-w-6xl` constraint on `<main>` and adds `padding-right: 400px`, giving the content full viewport width minus the drawer:

```css
body.drawer-open main { max-width: none; padding-right: 400px; }
```

`MenuPlanComponent` toggles this class via an Angular `effect()` and cleans up in `ngOnDestroy`.

## Key configuration

| Property | Default | Purpose |
|---|---|---|
| `cookidoo.service.url` | `http://localhost:8001` | Python FastAPI base URL |
| `cookidoo.sync.cron` | `0 0 3 * * *` | Scheduled sync (set `"-"` to disable) |
| `cookidoo.sync.resync-after-hours` | `24` | Recipes older than this are re-fetched |
| `spring.threads.virtual.enabled` | `true` | Virtual threads for @Async |
| `spring.jpa.open-in-view` | `false` | OSIV disabled |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema owned by Liquibase |
| `ai.ovh.base-url` | — | OVH AI Endpoints base URL (LLM) |
| `ai.ovh.api-key` | — | OVH AI Endpoints API key |
| `ai.ovh.model-name` | — | LLM model name |
| `ai.ovh-embedding.base-url` | — | OVH AI Endpoints base URL (embeddings) |
| `ai.ovh-embedding.api-key` | — | OVH AI Endpoints API key (embeddings) |
| `ai.ovh-embedding.model-name` | — | Embedding model name |

## Documentation

Full technical documentation (architecture, data model, API reference, sync flow) is in `docs/` and built with Antora + asciidoctor-kroki. Diagrams are PlantUML/ERD as code inside `[kroki,plantuml,svg]` blocks.

```bash
cd docs
npm install
npm run serve   # build + HTTP server → http://localhost:8888
# NB: ne jamais ouvrir build/site/ directement en file://, les assets CSS/JS sont bloqués
```
