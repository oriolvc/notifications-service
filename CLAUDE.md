# CLAUDE.md

Notification service for the Qualifyze Backend Chapter Lead challenge. When a regulatory document (warning letter, OAI, non-compliance report) is matched to a manufacturing site, it notifies the affected customers' distribution lists (email first, more channels later) and keeps an auditable record of everything.

## Read first

| Doc | What it is |
|---|---|
| `docs/challenge.md` | The original challenge: requirements (FR.1.x), deliverables, evaluation criteria |
| `docs/rfc.md` | **The design and the source of truth** for behaviour, API, data model, assumptions (A-x), decisions (D-x), acceptance criteria (AC-x) |
| `docs/tasks.md` | PR breakdown and status. Work on **one task at a time** |

If the implementation needs to diverge from the RFC, stop and ask. Once agreed, update `docs/rfc.md` in the same PR.

## Stack

Java 25 · Spring Boot 4.1 (Spring Framework 7, Hibernate 7, Jackson 3) · Liquibase 5 · PostgreSQL 18 · Maven wrapper.

Spring Boot 4 is newer than most training data:
- Starters are modular (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa-test`, …) and test-slice annotations moved packages.
- Jackson 3 lives in `tools.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`).
- **Don't guess** package names, class names or config properties. Check the jars in `~/.m2` or the official docs.

## Commands

```bash
./mvnw verify                      # full build: codegen, all tests, Spotless + Checkstyle, JaCoCo gate (≥ 90 % lines). Must pass before any PR
./mvnw spotless:apply              # format (palantir-java-format)
./mvnw test -Dtest=ClassName       # single test class
docker compose up -d               # local postgres:18 (5432) + Mailpit (SMTP 1025, UI http://localhost:8025)
./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.oriolvc.notificationsservice.LocalTokens
                                   # print 12 h dev tokens per role for the local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Integration tests use Testcontainers, so Docker must be running. (Spotless, compose and the coverage gate are introduced in task T0.)

## Architecture

Single deployable, hexagonal, two bounded contexts plus a small shared kernel.

```
com.oriolvc.notificationsservice
├── distribution/              who receives what: distribution lists and members
│   ├── domain/                aggregates, value objects, repository interfaces
│   ├── application/           use cases, query services
│   │   └── model/             commands, views and other records the use cases take or return
│   └── infrastructure/
│       ├── web/               controllers implementing generated API interfaces + DTO mappers
│       └── persistence/       DBOs, DAOs and the domain repository implementations
├── notification/              types, templates, notifications, deliveries, dispatch
│   ├── domain/
│   ├── application/           incl. ports: RecipientResolver, ChannelSender
│   └── infrastructure/
│       ├── web/
│       ├── persistence/
│       ├── distribution/      RecipientResolver adapter → distribution application layer
│       ├── channel/email/     EmailChannelSender
│       └── scheduling/        dispatcher triggers
└── shared/                    security, tenancy, auditing, error handling, config
```

Rules:
- Dependencies point inward: `infrastructure → application → domain`.
- `notification` reaches `distribution` **only** through the `RecipientResolver` port. Never import `distribution.domain` or its repositories from `notification`. The reverse direction (validating a notification type code) goes through a query service in `notification.application`.
- **Separate persistence model:** `domain` is plain Java, with no JPA, Envers or Spring Data annotations. Jackson 3 `JsonNode` is allowed there for JSON-shaped values (parameters, schemas). Per aggregate, `infrastructure/persistence` holds (all package-private):
  - `XxxDbo`: the `@Entity` (`@Audited`, `@Version`, audit columns via `AuditedDbo`)
  - `XxxDao`: the Spring Data interface over the DBO; it runs the queries
  - `XxxDboMapper`: domain ↔ DBO
  - `JpaXxxRepository`: implements the domain repository interface by calling the DAO and mapping. On save it loads the current row, checks the version the aggregate was read at, copies the state onto it and `saveAndFlush`es; it returns the aggregate as stored (id, version, `AuditMetadata`).
  Aggregates are rebuilt with a `restore(...)` factory, which skips the validation of `create(...)`.
- Aggregates protect their invariants: no public setters, static factories, intention-revealing methods (`deactivate()`, `addMember(...)`).
- **Use cases:** one class per use case, a single public method `execute(Command)`. Commands and results are records in `application/model`, apart from the use cases. No `@Transactional` in `application`.
- **Transactions belong to the repository adapters** (`Jpa…Repository`): `@Transactional(readOnly = true)` on the class, `@Transactional` on the writes. A use case's read and its later save run in separate transactions, which is safe because `save` checks the version the aggregate was read at. Data that must be written atomically is saved by one repository call.
- Generated OpenAPI code (DTOs, interfaces) never leaks past `infrastructure/web`.

## Conventions

**Java**
- Records for value objects, commands and results. No Lombok.
- Locals, parameters, catch parameters and for-each variables that are never reassigned are `final` (lambda parameters excepted). Checkstyle enforces it in `verify` (`config/checkstyle/checkstyle.xml`); formatting stays with Spotless.
- `Optional` only as a return type.
- `Instant` for timestamps. Inject `java.time.Clock`; never call `Instant.now()` directly.

**API (contract-first)**
- `src/main/resources/openapi/notifications-service.yaml` (OpenAPI 3.1) is the source of truth. Change the spec first, regenerate, then implement.
- Write schemas both validators read the same way: the generated DTOs use Bean Validation and the tests use JSON Schema.
  - Anchor every `pattern` (`'^…$'`): `@Pattern` must match the whole string, JSON Schema only part of it.
  - No `allOf` in response schemas: the contract validator rejects each part's properties as unknown to the others. Repeat the fields.
- Parameter schemas live in `src/main/resources/schemas/<type>.vN.json`. The spec `$ref`s them and Liquibase loads them (`valueClobFile`). **Never edit an existing `vN`**; add `vN+1` (RFC §6.3).
- Errors are RFC 9457 `ProblemDetail`, built by one `@RestControllerAdvice` with stable `type` URIs:
  - 400: malformed request
  - 422: semantic validation (unknown type, schema violations)
  - 409: version conflict or duplicate
  - 404: missing resource **or another tenant's resource** (never 403 for cross-tenant)
  A context that needs a binding failure reported as something other than 400 contributes an `UnreadableBodyTranslator` bean (e.g. an unknown notification `type` → 422) instead of adding context knowledge to the advice.

**Persistence**
- `spring.jpa.hibernate.ddl-auto=validate`. The schema comes only from Liquibase YAML.
- Changesets go in `db/changelog/changes/NNN-description.yaml`, included from `db.changelog-master.yaml`. **Never edit an applied changeset**; add a new one. Envers `revinfo` and `_aud` tables are Liquibase too.
- Tables and columns are snake_case (tables plural). Enums are stored as `text` with a `CHECK` constraint.
- Primary keys are bigserial: a `bigint` column with `DEFAULT nextval('<table>_<column>_seq')` and its own sequence (increment 1, `OWNED BY` the column). Liquibase turns `type: bigserial`/`autoIncrement` into an identity column on Postgres, so write the `createSequence` + `defaultValueSequenceNext` + `ALTER SEQUENCE … OWNED BY` explicitly (see `001-create-notification-types.yaml`). Never use identity columns.
- DBOs map ids with that sequence and `allocationSize = 1` (never `IDENTITY`, never the default 50):
  ```java
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "distribution_lists_id_seq")
  @SequenceGenerator(name = "distribution_lists_id_seq", sequenceName = "distribution_lists_id_seq", allocationSize = 1)
  private Long id;
  ```
- Uniqueness rules live in unique constraints/indexes only; don't check them in the use case. Save (the repository flushes) and let the error handler map the unique violation to 409 `already-exists`.
- Lists and members are soft deleted only (`active = false`). Optimistic locking uses `@Version`; a stale version returns 409.
- Audit columns come from Spring Data auditing (`AuditorAware` = JWT `userId`, or the client id for service tokens). Envers revisions store the same user id.

**Security**
- OAuth2 resource server. JWT claims: `tenantId`, `userId`, `roles`.
- Roles:
  - `NOTIFICATION_PUBLISHER`: service account, cross-tenant
  - `DISTRIBUTION_LIST_ADMIN`: tenant-scoped
  - `NOTIFICATION_VIEWER`: tenant-scoped
- Endpoint authorization lives in **one place**, the `SecurityFilterChain`, and mirrors the spec. Tenant scoping happens in the application layer.
- For tenant-scoped endpoints the tenant **always comes from the JWT**, never from the request.

**Logging**
- SLF4J with a request id in MDC.
- **Never log email addresses, rendered bodies or parameters.** Log ids only.

**Dependencies**
- Add only what the current task needs.
- Prefer versions managed by the Boot BOM; put other versions in `<properties>`.

## Testing

- **Unit** (domain and application): JUnit + AssertJ. Prefer hand-written fakes for ports over Mockito.
- **Integration:** classes named `*IntegrationTest`, extending the `IntegrationTest` base, which provides:
  - Testcontainers `postgres:18` (singleton, `@ServiceConnection`)
  - GreenMail (`greenMail`, emptied before each test), plus `ScriptedSmtpServer` for SMTP 4xx/5xx replies
  - MockMvc, with `get`/`post`/`patch`/`delete` helpers that send a bearer token and `assertProblem(result, status, type)`
  - JWT helpers per role (`JwtTokens`: real tokens signed with the dev key, so the decoder and claim mapping are exercised)
  - validation of every exchange against the OpenAPI spec (`OpenApiContractMatcher`): responses always; requests unless the server rejected them with a 4xx; 501 stubs skipped. An undocumented status or body fails the test, so fix the spec or the code, never the matcher

  Exercise the HTTP API end to end. Dispatch triggers are off in tests (`notifications.dispatch.enabled=false`); call `DispatchDueDeliveries.execute()` and move `next_attempt_at` back instead of changing the clock.
- **Fixtures:** `src/test/resources/fixtures/*.sql` loaded with `@Sql`. Each test starts from a clean DB (`CleanDatabaseTestExecutionListener` truncates everything but the Liquibase seed, before `@Sql` runs); never depend on test order.
- Tests that prove an acceptance criterion carry its id: `@DisplayName("AC-5 same event posted twice is reported as duplicate")`.
- **Coverage:** JaCoCo line coverage ≥ 90 % is enforced in `verify`; generated code and the `Application` class are excluded. Test behaviour, not getters.

## Workflow

- One task in `docs/tasks.md` = one branch = one PR. Branch names look like `T04-distribution-lists`.
- Use conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`).
- Before opening a PR:
  - `./mvnw verify` is green
  - Spotless has been applied
  - the task is ticked in `docs/tasks.md`
  - `docs/rfc.md` is updated if the design changed
- PR description: what and why, the ACs covered, anything deferred.
- Keep PRs reviewable. If a task grows beyond its scope, propose a split before continuing.
