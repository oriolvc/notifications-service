# Notifications service

When a regulatory document (an FDA warning letter, an OAI, a non-compliance report) is matched to a manufacturing site, the detection process calls this service with the affected customers. The service emails each customer's distribution list and keeps an auditable record of every notification, delivery attempt and configuration change.

Built for the Qualifyze Backend Chapter Lead challenge (`docs/challenge.md`).

| Doc | What's in it |
|---|---|
| [`docs/rfc.md`](docs/rfc.md) | The design: assumptions, API, data model, dispatch, security threat assessment, metrics, acceptance criteria, decisions, open questions |
| [`docs/tasks.md`](docs/tasks.md) | How it was built, one task per PR (T0–T11) |
| [`src/main/resources/openapi/notifications-service.yaml`](src/main/resources/openapi/notifications-service.yaml) | The API contract (OpenAPI 3.1), source of the generated interfaces and DTOs |
| [`CLAUDE.md`](CLAUDE.md) | Conventions and architecture rules for contributors |

**Stack:** Java 25 · Spring Boot 4.1 · Liquibase 5 · PostgreSQL 18 · Testcontainers · Maven wrapper.

## Run it locally

Requires Java 25 and Docker.

```bash
docker compose up -d                                      # postgres:18 on 5432, Mailpit (SMTP 1025, UI http://localhost:8025)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # http://localhost:8080
```

The `local` profile validates JWTs against a dev key pair committed for this purpose (the private key lives only under `src/test/resources/keys`). Print 12-hour tokens for each role:

```bash
./mvnw -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.oriolvc.notificationsservice.LocalTokens > tokens.txt

token() { awk -v label="# $1" '$0 == label { getline; print }' tokens.txt; }
PUBLISHER=$(token "NOTIFICATION_PUBLISHER (detection process)")
ADMIN_101=$(token "DISTRIBUTION_LIST_ADMIN of tenant 101")
VIEWER_101=$(token "NOTIFICATION_VIEWER of tenant 101")
```

`tokens.txt` also has a list admin and a viewer for tenant 102.

### Walkthrough

1. A customer admin (tenant 101) creates its list and adds a member:

   ```bash
   curl -s -X POST localhost:8080/distribution-lists -H "Authorization: Bearer $ADMIN_101" \
     -H 'Content-Type: application/json' \
     -d '{"name": "Compliance team", "notificationType": "REGULATORY_DOCUMENT_MATCHED", "channel": "EMAIL"}'
   # → 201 {"id": 1, ..., "version": 0}

   curl -s -X POST localhost:8080/distribution-lists/1/members -H "Authorization: Bearer $ADMIN_101" \
     -H 'Content-Type: application/json' -d '{"address": "alice@customer101.example"}'
   ```

2. The detection process publishes an event for customers 101 and 102:

   ```bash
   curl -s -X POST localhost:8080/notifications -H "Authorization: Bearer $PUBLISHER" \
     -H 'Content-Type: application/json' -d '{
       "type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": [101, 102],
       "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                      "authorityName": "FDA", "documentType": "WARNING_LETTER",
                      "issueDate": "2026-06-12", "confidence": 80}}'
   # → 202 {"eventKey": "cb35…", "results": [
   #          {"customerId": 101, "notificationId": 1, "outcome": "ACCEPTED", "deliveries": 1},
   #          {"customerId": 102, "notificationId": 2, "outcome": "NO_RECIPIENTS", "deliveries": 0}]}
   ```

   Posting the same event again returns 200 with every customer `DUPLICATE`.

3. The email arrives in Mailpit (http://localhost:8025) within seconds:

   > FDA warning letter – Qualifyze Spain
   >
   > Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026. [80%]

4. A customer user tracks it: recipients, rendered content, status and every attempt.

   ```bash
   curl -s localhost:8080/notifications/1 -H "Authorization: Bearer $VIEWER_101"
   curl -s "localhost:8080/notifications?eventKey=<eventKey>" -H "Authorization: Bearer $PUBLISHER"
   ```

   With the tenant 102 viewer, notification 1 is a 404.

5. Metrics, for publishers (the scraper's service token):

   ```bash
   curl -s localhost:8080/actuator/prometheus -H "Authorization: Bearer $PUBLISHER" | grep -E '^notification'
   ```

**Swagger UI:** http://localhost:8080/swagger-ui.html renders the contract. Use **Authorize** with one of the tokens.

To see retries, stop Mailpit (`docker compose stop mailpit`), publish an event with other parameters, and start it again. The delivery stays `PENDING` with a `RETRYABLE_ERROR` attempt and is sent on the next try, after a 1-minute backoff.

## API

| Endpoint | Roles | |
|---|---|---|
| `POST /notifications` | `NOTIFICATION_PUBLISHER` | Publish an event for a set of customers |
| `GET /notifications?eventKey=&customerId=` | publisher, list admin, viewer | Search, newest first (at most 100) |
| `GET /notifications/{id}` | publisher, list admin, viewer | One notification with its deliveries and attempts |
| `POST /distribution-lists`, `GET`, `GET /{id}`, `PATCH /{id}`, `DELETE /{id}` | list admin (reads: also viewer) | The caller's lists |
| `POST /distribution-lists/{id}/members`, `GET`, `DELETE /{memberId}` | list admin (reads: also viewer) | Members of a list |
| `GET /actuator/prometheus` | `NOTIFICATION_PUBLISHER` | Metrics (RFC §13) |
| `GET /actuator/health` | public | |

Tenant users only ever see their own tenant, taken from the JWT; another tenant's resource is a 404. Errors are RFC 9457 Problem Details (400 malformed, 422 semantic validation, 409 conflict or duplicate, 404 not found).

## Test

```bash
./mvnw verify      # all tests (Docker needed for Testcontainers), Spotless, Checkstyle, JaCoCo ≥ 90 % lines
```

- Unit tests cover the domain and use cases with hand-written fakes.
- Integration tests run the real HTTP API against `postgres:18` (Testcontainers) and an in-process SMTP server (GreenMail). SMTP outages and 4xx/5xx replies are simulated.
- Every request and response is validated against the OpenAPI spec, and the security rules are checked against the spec's `x-required-roles`.
- GitHub Actions runs `verify` on every push (`.github/workflows/ci.yml`).

## Design in brief

- **Hexagonal, two bounded contexts:**
  - *distribution* decides who receives what;
  - *notification* covers types, templates, notifications, deliveries and dispatch.
  - The notification context reads recipients only through a port (RFC §4).
- **Contract-first:** the OpenAPI spec generates the controllers' interfaces and DTOs. Each notification type's parameters are a versioned JSON Schema, shared by the spec and the database seed (RFC §6.3).
- **Deduplication:** an event key is computed from the type and the normalized parameters, and a unique constraint per customer guarantees the same event is never notified twice, even under concurrent requests (RFC §7.3).
- **Dispatch:** the database is the queue (`FOR UPDATE SKIP LOCKED`), with no broker at this volume. Each delivery is sent in its own transaction, retried with a 1/5/15/60-minute backoff and failed after 5 attempts (RFC §9).
- **Audit:**
  - every delivery stores the address and the rendered content it was sent with;
  - every attempt is stored;
  - list changes are versioned by Envers with the user id (RFC §11).
- **Security threat assessment:** RFC §12 (STRIDE).
- **Metrics:** RFC §13 covers adoption and performance.

### Assumptions (RFC §3)

- The detection process sends the affected customer ids and only events from its current cycle. This service never re-notifies the same event to the same customer, and members added later receive no backfill.
- A gateway authenticates callers, and the service still validates the JWT (`tenantId`, `userId`, `roles`). The detection process is a cross-tenant service account.
- Customers manage their own lists. A member is just an address, and there is at most one active list per customer, notification type and channel.
- Confidence is always shown and never filters. A change in any parameter, confidence included, is a new event.
- Email is plain text, one message per recipient. The message follows the FR.1.2 template, and the subject is `{authority} {document type} – {site}`.

Open questions for the product owner, each with its current answer, are in RFC §17.

### Configuration outside `local`

| Variable | |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP relay and sender |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES`, `NOTIFICATIONS_SECURITY_JWT_ISSUER` | JWT validation |

## Acceptance criteria → tests

The ACs are defined in RFC §14. Test classes are under `src/test/java/com/oriolvc/notificationsservice/`, and each test carries its AC id in `@DisplayName`.

| AC | What | Test |
|---|---|---|
| AC-1 | Notify affected customers | `notification/infrastructure/web/CreateNotificationIntegrationTest#createsPendingDeliveries`, `notification/application/DispatchIntegrationTest#notifiesAffectedCustomers` |
| AC-2 | Exact message | `notification/domain/TemplateRendererTest#rendersTheSampleExactly` (and the email body in `DispatchIntegrationTest#notifiesAffectedCustomers`) |
| AC-3 | List management, audited | `distribution/infrastructure/web/MembersIntegrationTest#listManagementIsAuditedWithTheUserId`, `DistributionListsIntegrationTest#everyChangeIsAuditedWithTheUserId` |
| AC-4 | Tracking | `notification/infrastructure/web/TrackingIntegrationTest#tracksANotification` |
| AC-5 | Same event twice | `CreateNotificationIntegrationTest#sameEventTwice` |
| AC-6 | Same event, extra customer | `CreateNotificationIntegrationTest#extraCustomer` |
| AC-7 | Extra whitespace | `CreateNotificationIntegrationTest#whitespaceIsTheSameEvent`, `notification/domain/EventKeyTest#whitespaceDoesNotChangeTheKey` |
| AC-8 | Different confidence | `CreateNotificationIntegrationTest#confidenceChangeIsANewEvent`, `EventKeyTest#confidenceChangesTheKey` |
| AC-9 | Parallel identical requests | `CreateNotificationIntegrationTest#parallelRequests` |
| AC-10 | No list / inactive list / no members | `CreateNotificationIntegrationTest#customersWithoutRecipients` |
| AC-11 | Unknown or inactive type | `CreateNotificationIntegrationTest#unknownType`, `#inactiveType` |
| AC-12 | Invalid parameters | `CreateNotificationIntegrationTest#invalidParameters`, `notification/infrastructure/validation/JsonSchemaParametersValidatorTest#reportsEveryViolation` |
| AC-13 | SMTP down, then recovers | `DispatchIntegrationTest#smtpOutageThenRecovery` |
| AC-14 | SMTP keeps failing / 5xx | `DispatchIntegrationTest#failsAfterFiveAttempts`, `#permanentErrorFailsImmediately` |
| AC-15 | Member removed before dispatch | `DispatchIntegrationTest#memberRemovedBeforeDispatch` |
| AC-16 | Member added later | `CreateNotificationIntegrationTest#memberAddedLater` |
| AC-17 | Customer user publishes | `shared/security/JwtAuthenticationIntegrationTest#customerUserCannotPublish` |
| AC-18 | Cross-tenant access | `DistributionListsIntegrationTest.CrossTenant`, `TrackingIntegrationTest#anotherTenantsNotificationIsNotFound`, `#anotherTenantIsAnEmptyResult` |
| AC-19 | Invalid / already-active address | `MembersIntegrationTest#invalidAddressIsABadRequest`, `#alreadyActiveAddressIsAConflict` |
| AC-20 | Stale version | `DistributionListsIntegrationTest#staleVersionIsAConflict` |
| AC-21 | Second active list | `DistributionListsIntegrationTest#secondActiveListIsAConflict` |

Metrics (RFC §13) are covered by `shared/MetricsIntegrationTest`.
