# Task breakdown

One task = one branch = one PR. Design reference: `docs/rfc.md`. Conventions: `CLAUDE.md`.

```
T0 Bootstrap ─▶ T1 API contract ─▶ T2 DB schema ─▶ T3 Shared kernel ─┬─▶ T4 Distribution lists ─▶ T5 Members ─┐
                                                                     │                                       ├─▶ T7 Create notification ─▶ T8 Dispatch ─▶ T9 Tracking queries ─▶ T10 Metrics ─▶ T11 Submission
                                                                     └─▶ T6 Notification domain ─────────────┘
```

T4→T5 and T6 are independent and can be done in parallel.

---

## [x] T0 — Bootstrap and tooling

**Goal:** a clean, buildable skeleton with the quality gates in place from the first PR.

- `git init`, first commit of the Initializr project, push to GitHub.
- `spring.application.name: notifications-service`; remove unused `static/` and `templates/` folders.
- Remove the spec left over from the discarded attempt (`openapi/notifications-service.yaml`); T1 writes the new one.
- Maven:
  - JaCoCo `report` + `check` (line ≥ 0.90), excluding the `Application` class and generated code
  - Spotless with palantir-java-format
  - Testcontainers (PostgreSQL) + `spring-boot-testcontainers`
- `application.yaml`:
  - datasource from env vars
  - `ddl-auto: validate`
  - `open-in-view: false`
  - `application-local.yaml` for docker compose
- Empty Liquibase `db.changelog-master.yaml`.
- `docker-compose.yml`: `postgres:18`, Mailpit.
- `IntegrationTest` base class (Postgres container, `@ServiceConnection`); replace the generated context test with one extending it.
- Actuator: `health` only.
- GitHub Actions running `./mvnw verify` on push and PR.

**Done when:** CI is green, and the app starts locally against compose.

## [x] T1 — API contract

**Goal:** the full API reviewed before any implementation (contract-first). RFC §6, §6.3, §7.2.

- Write `openapi/notifications-service.yaml` in **OpenAPI 3.1**:
  - all endpoints from RFC §6.1–6.2
  - `oneOf` + discriminator request for `POST /notifications`
  - response and Problem Details schemas
  - security schemes, with the required role documented on each operation
- `schemas/regulatory-document-matched.v1.json` (RFC §7.2).
- `openapi-generator-maven-plugin`:
  - `spring` generator, `interfaceOnly`, Jakarta / Boot 4 compatible
  - `schemaMappings` for `*Parameters` → `JsonNode`
  - `validateSpec=true`
  - Check the generated `oneOf` output early; if it's unusable, apply the fallback noted in RFC §6.3 and record it in the RFC.
- Serve the spec and the schema files as static resources so Swagger UI resolves the external `$ref`. Point springdoc's Swagger UI at the static spec. springdoc's own spec generation can't be disabled without losing Swagger UI, so `/v3/api-docs` is denied by security instead (RFC §6.3).

**Done when:** the build generates the interfaces, and Swagger UI shows the complete API. No controllers yet.

## [x] T2 — Database schema and seed

**Goal:** the complete schema from RFC §10, created by Liquibase.

- Changesets for:
  - `notification_types`
  - `notification_templates`
  - `distribution_lists`
  - `distribution_list_members`
  - `notifications`
  - `notification_deliveries`
  - `delivery_attempts`
  - `revinfo`, `distribution_lists_aud`, `distribution_list_members_aud`
- Include all CHECK constraints, unique keys and partial indexes (`WHERE active`, `WHERE status = 'PENDING'`).
- Seed:
  - type `REGULATORY_DOCUMENT_MATCHED` (`parameters_schema` via `valueClobFile` from the T1 schema file)
  - its EMAIL template (RFC §8)
- Integration test with JDBC against a clean `postgres:18`:
  - migrations apply
  - seed present
  - key constraints reject violations: dedup key, one active list per (customer, type, channel), unique member address (case-insensitive), template subject required for EMAIL

**Done when:** the migrations and constraint tests are green.

## [ ] T3 — Shared kernel

**Goal:** the cross-cutting pieces every feature PR relies on.

- **Security:**
  - OAuth2 resource server
  - mapping of claims (`tenantId`, `userId`, `roles`) → `CurrentPrincipal`
  - endpoint authorization rules in the `SecurityFilterChain` mirroring the spec
  - `local` profile with a dev signing key, plus a test-scope helper that prints a token per role
- **Auditing:**
  - Spring Data JPA auditing with an `AuditorAware`
  - Envers custom `RevisionEntity` + listener storing the user id
- **Errors:** global `@RestControllerAdvice` producing `ProblemDetail` with stable `type` URIs; 401/403 responses also as Problem Details.
- **Request id:** filter (`X-Request-Id`, echoed or generated) + MDC.
- **Clock:** `Clock` bean.
- **Test support:**
  - `IntegrationTest` gets JWT helpers per role
  - request/response validation against the spec (e.g. `swagger-request-validator`)
  - DB truncation between tests

**Done when:** unauthenticated → 401 and wrong role → 403 on protected paths (generated interfaces return 501 for the rest). Covers **AC-17**.

## [ ] T4 — Distribution lists

**Goal:** tenant-scoped CRUD for distribution lists. RFC §6.2, A-4, A-13, D-5.

- Minimal read-only notification type catalog in `notification` (entity + repository + `NotificationTypeQuery.findActiveByCode`), used to validate the list's type. T6 extends it.
- `DistributionList` aggregate:
  - `@Audited`
  - optimistic locking
  - soft delete
- Use cases: create, get, list, update (name/active + version), delete.
- Controller implementing the generated interface.
- Fixtures for lists of two tenants.

**Done when:** **AC-18** (cross-tenant → 404), **AC-20** (stale version → 409) and **AC-21** (second active list → 409) pass, and each change creates an Envers revision with the user id (lists part of **AC-3**).

## [ ] T5 — Members

**Goal:** manage members of a list. RFC §6.2, A-5, D-4.

- `AddressValidator` port, plus an `EmailAddressValidator` (selected by channel).
- Members are added through the list aggregate:
  - re-adding a removed address reactivates it
  - an already-active address → 409
  - limit of 100 active members per list
- Use cases: add, list, remove (soft). `@Audited`.
- Distribution query service for resolving the active members of a customer's active lists (used by T7).

**Done when:** **AC-3** is complete and **AC-19** passes (invalid email → 400, duplicate → 409).

## [ ] T6 — Notification domain

**Goal:** the pure logic of turning parameters into an event key and a rendered message. Mostly unit tests, no endpoints. RFC §7.2, §7.3, §8.

- JSON Schema validation of parameters (`networknt/json-schema-validator`, draft 2020-12), returning a list of violations.
- Normalization (trim, collapse whitespace) + RFC 8785 canonical JSON + SHA-256 event key.
- `{{placeholder}}` renderer with schema-driven formatting:
  - `format: date` → `d MMMM yyyy`, English
  - `oneOf` `const` → its `title`
  - CR/LF stripped in subjects
- Startup validation: every template placeholder exists in its type's schema, otherwise the context fails to start.
- Extend the type catalog from T4 with templates per channel.

**Done when:** unit tests cover **AC-2** (exact sample message), **AC-7** (whitespace → same key), **AC-8** (confidence change → new key) and the **AC-12** violations.

## [ ] T7 — Create notification

**Goal:** `POST /notifications`, end to end up to `PENDING` deliveries. RFC §7.1.

- `RecipientResolver` port + adapter over the T5 query service.
- Use case:
  - load type
  - validate
  - event key
  - per customer, `INSERT … ON CONFLICT DO NOTHING`
  - resolve recipients
  - render per channel
  - persist the notification + `PENDING` deliveries with address snapshot and rendered content, in one transaction
- Response: 202 / 200 with per-customer outcomes `ACCEPTED` / `NO_RECIPIENTS` / `DUPLICATE`.
- Publish an after-commit event (consumed by T8).
- Contract drift guard test (RFC §6.3).

**Done when:**
- **AC-1** acceptance part (deliveries created; sending comes in T8)
- **AC-5** and **AC-6**
- **AC-9**: two parallel requests → one notification per customer
- **AC-10**, **AC-11**, **AC-12** (via HTTP), **AC-16**

## [ ] T8 — Dispatch and email channel

**Goal:** deliver `PENDING` deliveries with retries and full attempt history. RFC §9.

- `ChannelSender` port, plus `EmailChannelSender` (`JavaMailSender`, one message per recipient; store the Message-ID).
- Dispatcher:
  - `FOR UPDATE SKIP LOCKED`, one transaction per delivery
  - `@Scheduled` polling + after-commit trigger
  - can be run synchronously in tests
- Error classification (retryable vs permanent), backoff of 1/5/15/60 min, at most 5 attempts.
- Every attempt is appended to `delivery_attempts`.
- GreenMail integration tests, including SMTP failure simulation.

**Done when:** **AC-1** end to end, plus **AC-13**, **AC-14** and **AC-15**.

## [ ] T9 — Tracking queries

**Goal:** read side for tracking and audit. RFC §6.1.

- `GET /notifications/{id}`: notification, rendered content, deliveries with status and attempts.
- `GET /notifications?eventKey=…&customerId=…`: publisher sees all tenants; tenant users only their own (other tenants → empty result / 404).

**Done when:** **AC-4** passes, and cross-tenant reads are covered.

## [ ] T10 — Metrics

**Goal:** the adoption and performance metrics from RFC §13.

- Micrometer metrics: counters, timers and gauges as listed in §13.1 and §13.2.
- Expose `/actuator/prometheus` (publisher or ops access only).
- Integration test asserting the key metrics change after a notify and dispatch cycle.

**Done when:** all metrics in RFC §13 are exposed and tested.

## [ ] T11 — Hardening and submission

**Goal:** a submission a reviewer can run and understand in 10 minutes.

- `README.md`:
  - what it is
  - how to run (compose + token helper + curl / Swagger examples)
  - how to test
  - links to RFC sections
  - assumptions summary
- AC traceability table: AC → test class/method.
- Review coverage (≥ 90 %, meaningful tests).
- Final RFC sync with what was built; resolve or annotate the open questions.
- Package: GitHub repo access or zip.

**Done when:** a clean clone runs `./mvnw verify` green, and the README walkthrough works.
