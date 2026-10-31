# RFC: Customer Notification Service

| | |
|---|---|
| Author | Oriol Vaquer |
| Status | Draft v2 (supersedes `initial_rfc.md`) |
| Date | 2026-10-30 |
| Stack | Java 25 · Spring Boot 4 · Liquibase 5 · PostgreSQL 18 |

---

## 1. Context

A process already exists that detects, from public sources, new **warning letters, OAIs and non-compliance reports** and matches them to manufacturing sites. What is missing is the system that informs the affected customers.

This RFC describes a new **notification service** that:
- receives events from the detection process through a REST API,
- delivers a templated message to each affected customer's dedicated distribution list (email first),
- keeps a complete, auditable record of every delivery, failure and configuration change (ISO 27001 / SOC 2 Type II),
- is generic enough to add new notification types and channels (Slack, WhatsApp) without redesign.

**Volume (FR.1.5):** 1–5 events/week × 10–25 customers × a handful of members ≈ **a few hundred emails per week**. This drives most simplicity decisions below (no message broker, DB-backed dispatch queue, synchronous SMTP).

## 2. Goals and non-goals

**Goals**
- Notify every member of each affected customer's distribution list, exactly following the FR.1.2 template.
- Never notify the same event twice to the same customer.
- Store every dispatch attempt (success and error) and every configuration change.
- Generic notification types (parameter schema + per-channel template) and pluggable channels.

**Non-goals (out of scope)**
- Bounce / delivery-receipt handling (`SENT` = accepted by the SMTP server).
- Unsubscribe flows, recipient preferences, i18n of templates.
- Admin API for notification types and templates (they are managed through Liquibase migrations; see D-6).
- Channels other than email (only the extension point is built).
- Data retention / purge policy.

## 3. Assumptions

| # | Assumption |
|---|---|
| A-1 | The detection process knows **which customers are affected** by a matched site and sends their IDs. This service does not maintain site subscriptions. |
| A-2 | An API gateway authenticates requests and forwards a signed JWT. The service still **validates the JWT** (signature, issuer, audience, expiry) as an OAuth2 resource server. |
| A-3 | User JWTs carry `tenantId` (= `customerId`), `userId` and `roles`. The detection process calls with a **service identity** (client credentials) and the `NOTIFICATION_PUBLISHER` role; it is not bound to a tenant. |
| A-4 | Distribution lists are **self-managed by customer users** within their tenant. |
| A-5 | Distribution list members **are not platform users**; a member is just an address. |
| A-6 | Multi-tenancy is a shared schema with a `customer_id` discriminator, enforced in the application layer. |
| A-7 | **FR.1.4 (current cycle only):** the detection process sends only events detected since its last run. This service guarantees an event is never re-notified to the same customer (event key, §7.3), and a member added later never receives past events (no backfill). |
| A-8 | **FR.1.3:** confidence is always shown and never used to filter. Curation happens upstream. |
| A-9 | A change in any parameter (including confidence) produces a **new event** and therefore a new notification (see §7.3 and Q-2). |
| A-10 | Email is sent as **plain text**, one message per recipient (no shared `To:`/`CC:`). |
| A-11 | Message format follows the FR.1.2 template, not the sample. Known differences in the sample ("Site" prefix, missing comma, "a OAI") are template content and can be fixed by a migration (Q-3). Dates render as `d MMMM yyyy`, English locale. |
| A-12 | Email subject is not specified; we use `{{authorityName}} {{documentType}} – {{siteName}}`. |
| A-13 | A customer has at most **one active list per (notification type, channel)** ("a customer dedicated distribution list"). |

## 4. Solution overview

```
 Detection process                     Notification service                              SMTP
 ────────────────                      ─────────────────────────────────────────         ────
 POST /notifications ───────────────▶  1. validate params (JSON Schema of type)
 {type, customerIds, parameters}       2. eventKey = SHA-256(type + canonical params)
                                       3. per customer: skip if already notified
                                       4. resolve active list + members
                                       5. render template per channel
                                       6. persist notification + PENDING deliveries  (1 tx)
          ◀──── 202 {eventKey, results}

                                       Dispatcher (scheduled + after-commit trigger)
                                       7. lock PENDING delivery (SKIP LOCKED)
                                       8. send via ChannelSender ─────────────────────▶  send
                                       9. record attempt; SENT / retry / FAILED
```

Single deployable, **hexagonal architecture**, two bounded contexts:

| Context | Responsibility | Aggregates |
|---|---|---|
| **Distribution** | Configure who receives what, per customer and channel | `DistributionList` (root) → `Member` |
| **Notification** | Accept events, render, dispatch, track | `NotificationType` (+ templates), `Notification` (root) → `Delivery` → `DeliveryAttempt` |

The Notification context reads recipients through a port (`RecipientResolver`) implemented by an adapter over the Distribution context's application service. There is no direct repository access across contexts.

Package layout per context: `domain` (entities, value objects, ports) · `application` (use cases) · `infrastructure` (web, persistence, email, scheduling).

Channel extension point:

```java
public interface ChannelSender {
    Channel channel();                       // EMAIL, later SLACK, WHATSAPP
    SendResult send(Delivery delivery);      // SUCCESS | RETRYABLE_ERROR | PERMANENT_ERROR
}
public interface AddressValidator {
    Channel channel();
    void validate(String address);           // e.g. RFC 5322 email for EMAIL
}
```

Adding a channel means adding a `Channel` enum value, a `ChannelSender`, an `AddressValidator` and a template row. No change to use cases or schema.

## 5. Roles

| Role | Who | Scope | Allowed |
|---|---|---|---|
| `NOTIFICATION_PUBLISHER` | Detection process (service account) | All tenants | `POST /notifications`, `GET /notifications…` |
| `DISTRIBUTION_LIST_ADMIN` | Customer user | Own tenant | Manage lists and members, read notifications |
| `NOTIFICATION_VIEWER` | Customer user | Own tenant | Read lists, members, notifications, deliveries |

Accessing a resource of another tenant returns **404**, so the response does not reveal that the resource exists.

## 6. API

**Contract-first:** an OpenAPI 3.1 spec checked into the repo is the source of the API. Server interfaces and DTOs are generated from it. Errors use `application/problem+json` (RFC 9457).

### 6.1 Notifications

**`POST /notifications`** (`NOTIFICATION_PUBLISHER`)

The request is a `oneOf` with one variant per notification type, discriminated by `type`. Each variant's `parameters` references the **same JSON Schema file** that is loaded into `notification_types.parameters_schema` (§6.3):

```yaml
CreateNotificationRequest:
  oneOf:
    - $ref: '#/components/schemas/RegulatoryDocumentMatchedRequest'
  discriminator:
    propertyName: type
    mapping:
      REGULATORY_DOCUMENT_MATCHED: '#/components/schemas/RegulatoryDocumentMatchedRequest'

RegulatoryDocumentMatchedRequest:
  type: object
  additionalProperties: false
  required: [type, customerIds, parameters]
  properties:
    type:        { const: REGULATORY_DOCUMENT_MATCHED }
    customerIds: { $ref: '#/components/schemas/CustomerIds' }
    parameters:  { $ref: '../schemas/regulatory-document-matched.v1.json' }

CustomerIds:
  type: array
  minItems: 1
  maxItems: 100
  uniqueItems: true
  items: { type: integer, format: int64 }
```

Example:

```json
{
  "type": "REGULATORY_DOCUMENT_MATCHED",
  "customerIds": [101, 102, 103],
  "parameters": {
    "siteName": "Qualifyze Spain",
    "siteAddress": "Passeig de Gracia 19 Barcelona",
    "authorityName": "FDA",
    "documentType": "WARNING_LETTER",
    "issueDate": "2026-06-12",
    "confidence": 80
  }
}
```

Response **202 Accepted** (at least one new notification) or **200 OK** (all customers already notified):

```json
{
  "eventKey": "9f2c…e1",
  "results": [
    { "customerId": 101, "notificationId": 501, "outcome": "ACCEPTED",      "deliveries": 4 },
    { "customerId": 102, "notificationId": 502, "outcome": "NO_RECIPIENTS", "deliveries": 0 },
    { "customerId": 103, "notificationId": 377, "outcome": "DUPLICATE",     "deliveries": 3 }
  ]
}
```

| Status | When |
|---|---|
| 400 | Malformed body, empty `customerIds`, more than 100 customers |
| 401 / 403 | Missing/invalid token, missing role |
| 422 | Unknown or inactive `type`; `parameters` do not satisfy the type schema |

Duplicated `customerIds` are not an error: the generated DTO binds `uniqueItems` to a `Set`, so a customer listed twice is processed once.

**`GET /notifications/{id}`**: notification, rendered content and per-recipient delivery status with attempts.
**`GET /notifications?eventKey=…&customerId=…`**: search. Tenant users are always filtered to their own tenant.

### 6.2 Distribution lists (tenant from JWT)

| Method | Path | Notes |
|---|---|---|
| POST | `/distribution-lists` | `{name, notificationType, channel}` → 201. 409 if an active list already exists for (type, channel) |
| GET | `/distribution-lists` | Own tenant only |
| GET | `/distribution-lists/{id}` | Includes `version` |
| PATCH | `/distribution-lists/{id}` | `{name?, active?, version}` → 409 on version mismatch (optimistic locking) |
| DELETE | `/distribution-lists/{id}` | **Soft delete** (`active=false`) → 204 |
| POST | `/distribution-lists/{id}/members` | `{address}` → 201. Address validated per channel. Re-adding a removed address reactivates it. 409 if already active |
| GET | `/distribution-lists/{id}/members` | |
| DELETE | `/distribution-lists/{id}/members/{memberId}` | Soft delete → 204 |

There is no member PATCH: changing an address is a remove plus an add. This keeps the audit trail unambiguous.
Limit: 100 active members per list. Adding a member beyond it → 422 (a business rule, not a conflict with another resource).
Reactivating a list (`PATCH active=true`) while another active list exists for the same (type, channel) → 409.

### 6.3 Parameter schemas: single source of truth

OpenAPI 3.1 uses JSON Schema 2020-12, so one file per type and version serves both the contract and runtime validation:

```
src/main/resources/
├── schemas/
│   └── regulatory-document-matched.v1.json   ← the only definition of the parameters
├── openapi/
│   └── notifications-service.yaml            ← $ref to the schema file
└── db/changelog/
    └── 002-seed-regulatory-document-matched.yaml
```

```yaml
# Liquibase changeset (relative path to schemas/ omitted)
- insert:
    tableName: notification_types
    columns:
      - column: { name: code, value: REGULATORY_DOCUMENT_MATCHED }
      - column: { name: name, value: Regulatory document matched to a site }
      - column: { name: parameters_schema, valueClobFile: regulatory-document-matched.v1.json }
```

- **Versioned files.** Liquibase checksums include the file content, so an applied file is never edited. A change means a new `…v2.json`, a new changeset updating the row, and the spec pointing to v2. Stored notifications keep their original parameters.
- **One validation path.** Generated DTOs map the per-type `parameters` schemas to a generic JSON type (openapi-generator `schemaMappings` → `JsonNode`). The web adapter builds the generic command (`type`, `customerIds`, `parameters`). The JSON Schema validator remains the only parameter validator, so every violation returns the same 422 response, not a mix of Jackson 400s and Bean Validation errors.
- **Generated `oneOf` (openapi-generator 7.26, `spring`).** With a single variant the generator flattens the `oneOf` into one concrete `CreateNotificationRequest` class (`type` enum, `customerIds`, `parameters: JsonNode`). From two variants on it generates an interface with `@JsonTypeInfo`/`@JsonSubTypes` and one class per variant. The web adapter only reads `type`, `customerIds` and `parameters`, so adding the second type changes that mapping only. An unknown `type` fails Jackson deserialization in both shapes; the error handler maps it to the same 422 as an unknown type in the catalog (AC-11).
- **Swagger UI.** The spec and the schema files are served as static resources (`/openapi/**`, `/schemas/**`) with the same relative layout, so Swagger UI resolves the external `$ref`. springdoc's Swagger UI points at the static spec. springdoc cannot disable its runtime spec generation without also disabling Swagger UI, so `/v3/api-docs` stays enabled but the security chain denies it; only `/v3/api-docs/swagger-config` is public.
- **Drift guard.** A test asserts that every active `notification_types` row has a matching discriminator mapping in the spec, and that its stored `parameters_schema` equals the referenced file.
- **Adding a type:** schema file + spec variant + Liquibase changeset (type and templates). This takes a release, which is consistent with D-6.

## 7. Use case: create notification

### 7.1 Flow

1. Load `NotificationType` by `code`. Missing or inactive → 422.
2. Validate `parameters` against the type's **JSON Schema** (`networknt/json-schema-validator`). Invalid → 422 with the list of violations.
3. Normalize parameters and compute the **event key** (§7.3).
4. For each `customerId`:
   1. Insert a `notification` row with `ON CONFLICT (notification_type_id, event_key, customer_id) DO NOTHING`. If nothing was inserted, the outcome is `DUPLICATE` (return the existing notification).
   2. Resolve the customer's active lists for the type (one per channel) and their active members.
   3. If there are no lists or no active members, the outcome is `NO_RECIPIENTS`. The notification row is still stored as an auditable record.
   4. Otherwise, for each list, pick the template for the list's channel, render it, and create one `PENDING` delivery per member with an **address snapshot** and the **rendered content**.
5. Commit everything in a single transaction. After commit, trigger the dispatcher.

The unique constraint, not a prior `SELECT`, is what guarantees deduplication under concurrent calls.

### 7.2 Parameter schema

`notification_types.parameters_schema` holds a JSON Schema (draft 2020-12), loaded from the same file the OpenAPI spec references (§6.3). For `REGULATORY_DOCUMENT_MATCHED` (`schemas/regulatory-document-matched.v1.json`):

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["siteName", "siteAddress", "authorityName", "documentType", "issueDate", "confidence"],
  "properties": {
    "siteName":      { "type": "string", "minLength": 1, "maxLength": 300 },
    "siteAddress":   { "type": "string", "minLength": 1, "maxLength": 500 },
    "authorityName": { "type": "string", "minLength": 1, "maxLength": 100 },
    "documentType": {
      "oneOf": [
        { "const": "WARNING_LETTER",        "title": "warning letter" },
        { "const": "OAI",                   "title": "OAI" },
        { "const": "NON_COMPLIANCE_REPORT", "title": "non-compliance report" }
      ]
    },
    "issueDate":  { "type": "string", "format": "date" },
    "confidence": { "type": "integer", "minimum": 0, "maximum": 100 }
  }
}
```

`additionalProperties: false` matters for the event key: unknown fields cannot change the hash.

### 7.3 Event key (deduplication)

```
eventKey = hex( SHA-256( typeCode + "\n" + canonicalJson(normalizedParameters) ) )
```

- **Normalization** (after validation): trim strings and collapse internal whitespace runs to a single space. Values are otherwise case-sensitive.
- **Canonical JSON:** RFC 8785 (JCS). Keys are sorted, there is no insignificant whitespace, and numbers use a canonical form.
- The **normalized** parameters are what gets stored and rendered.
- The event key is returned to the caller and stored on every notification. It is the **event reference** required by FR.1.6.
- Deduplication is **permanent** (no time window): `unique (notification_type_id, event_key, customer_id)`.
- Deduplication is **per customer**. If the same event is re-sent with an extra customer, only the new customer is notified.

## 8. Templates and rendering

- One template per **(notification type, channel)**. `subject` is required for EMAIL and null for channels without one.
- Placeholder syntax `{{name}}`, implemented with a minimal renderer (no logic, no includes).
- **Formatting is driven by the schema**, so new types need no code:
  - `format: date` → `d MMMM yyyy`, `Locale.ENGLISH` (`2026-06-12` → `12 June 2026`)
  - `oneOf` with `const`/`title` → the `title` (`WARNING_LETTER` → `warning letter`)
  - anything else → its string value
- **Startup validation:** every placeholder in every template must exist in its type's schema; otherwise the application fails to start.
- **Injection safety:** plain-text body; CR/LF removed from values rendered into the subject (header injection).

Seeded template for `REGULATORY_DOCUMENT_MATCHED` / `EMAIL`:

```
subject: {{authorityName}} {{documentType}} – {{siteName}}
body:    {{siteName}}, located at {{siteAddress}}, received a {{authorityName}} {{documentType}} on {{issueDate}}. [{{confidence}}%]
```

Result:
`Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026. [80%]`

## 9. Dispatch

- `notification_deliveries` with `status = PENDING` is the **work queue**. The deliveries are written in the same transaction as the notification, so no event can be lost between acceptance and sending.
- **Dispatcher:** runs `@Scheduled(fixedDelay = 10s)` and is also triggered after each notification commit (`@TransactionalEventListener(AFTER_COMMIT)`) for low latency. Each delivery is processed in its own transaction:
  ```sql
  SELECT * FROM notification_deliveries
  WHERE status = 'PENDING' AND next_attempt_at <= now()
  ORDER BY next_attempt_at
  LIMIT 1 FOR UPDATE SKIP LOCKED;
  ```
  `SKIP LOCKED` makes it safe with several instances. If an instance crashes, its transaction rolls back and the lock is released, so no stuck-delivery detection is needed.
- **Send:** `EmailChannelSender` (Spring `JavaMailSender`, SMTP), one message per recipient. `Message-ID` is stored as `provider_message_id`.
- **Outcome handling:** every attempt is appended to `delivery_attempts`.
  - Success → `SENT`, `sent_at`.
  - Retryable error (connection error, timeout, SMTP 4xx) → stays `PENDING`, `attempt_count++`, `next_attempt_at` = now + backoff (1, 5, 15, 60 min).
  - Permanent error (SMTP 5xx, e.g. unknown mailbox) or 5 attempts reached → `FAILED`.
- **Guarantee:** at-least-once. If the process dies after the SMTP server accepts the message but before commit, the email can be sent twice. This is accepted at this volume and documented.
- Holding a DB transaction during an SMTP call (≈100 ms–1 s) is acceptable for a few hundred messages a week. Revisit if volume grows (D-3).

## 10. Data model

Conventions: `bigint` identity PKs. All mutable tables have `created_at`, `created_by`, `updated_at`, `updated_by` (Spring Data auditing; `AuditorAware` = `userId` or service client id from the JWT) and `version` for optimistic locking. `channel` is `text` with a `CHECK (channel IN ('EMAIL'))` constraint, widened by migration when a channel is added.

```
Table notification_types {               // seeded by Liquibase
  id bigint [pk, increment]
  code text [not null, unique]           // 'REGULATORY_DOCUMENT_MATCHED'
  name text [not null]
  parameters_schema jsonb [not null]     // JSON Schema loaded from schemas/*.vN.json, §6.3
  active boolean [not null, default: true]
  created_at, created_by, updated_at, updated_by
}

Table notification_templates {           // seeded by Liquibase
  id bigint [pk, increment]
  notification_type_id bigint [not null, ref: > notification_types.id]
  channel text [not null]
  subject text                           // CHECK (channel <> 'EMAIL' OR subject IS NOT NULL)
  body text [not null]
  created_at, created_by, updated_at, updated_by
  indexes { (notification_type_id, channel) [unique] }
}

Table distribution_lists {               // audited (Envers)
  id bigint [pk, increment]
  customer_id bigint [not null]          // tenant
  name text [not null]
  notification_type_id bigint [not null, ref: > notification_types.id]
  channel text [not null]
  active boolean [not null, default: true]
  version bigint [not null, default: 0]
  created_at, created_by, updated_at, updated_by
  indexes {
    (customer_id, name) [unique]
    (customer_id, notification_type_id, channel) [unique]   // partial: WHERE active
  }
}

Table distribution_list_members {        // audited (Envers)
  id bigint [pk, increment]
  distribution_list_id bigint [not null, ref: > distribution_lists.id]
  address text [not null]                // email today; validated per channel
  active boolean [not null, default: true]
  version bigint [not null, default: 0]
  created_at, created_by, updated_at, updated_by
  indexes { (distribution_list_id, lower(address)) [unique] }
}

Table notifications {                    // one per (event, customer); immutable
  id bigint [pk, increment]
  notification_type_id bigint [not null, ref: > notification_types.id]
  event_key char(64) [not null]          // SHA-256 hex, §7.3
  customer_id bigint [not null]
  parameters jsonb [not null]            // normalized
  outcome text [not null]                // ACCEPTED | NO_RECIPIENTS
  created_at, created_by
  indexes {
    (notification_type_id, event_key, customer_id) [unique]  // deduplication
    (customer_id, created_at)
  }
}

Table notification_deliveries {          // one per (notification, member); work queue
  id bigint [pk, increment]
  notification_id bigint [not null, ref: > notifications.id]
  distribution_list_id bigint [not null, ref: > distribution_lists.id]
  distribution_list_member_id bigint [not null, ref: > distribution_list_members.id]
  channel text [not null]
  address text [not null]                // snapshot at acceptance
  template_id bigint [not null, ref: > notification_templates.id]
  subject text
  body text [not null]                   // exact content sent
  status text [not null]                 // PENDING | SENT | FAILED
  attempt_count int [not null, default: 0]
  next_attempt_at timestamptz [not null]
  sent_at timestamptz
  provider_message_id text
  version bigint [not null, default: 0]
  created_at, updated_at
  indexes {
    (notification_id, distribution_list_member_id) [unique]
    (next_attempt_at)                    // partial: WHERE status = 'PENDING'
  }
}

Table delivery_attempts {                // append-only
  id bigint [pk, increment]
  delivery_id bigint [not null, ref: > notification_deliveries.id]
  attempt_number int [not null]
  attempted_at timestamptz [not null]
  outcome text [not null]                // SUCCESS | RETRYABLE_ERROR | PERMANENT_ERROR
  provider_message_id text
  error_code text
  error_message text
  indexes { (delivery_id, attempt_number) [unique] }
}

// Envers
Table revinfo { rev bigint [pk], rev_timestamp timestamptz, user_id text }
Table distribution_lists_aud { ... + rev, revtype }
Table distribution_list_members_aud { ... + rev, revtype }
```

All tables, including the Envers ones, are created by Liquibase (no `ddl-auto`). Lists and members are never hard-deleted, so FKs from deliveries remain valid.

## 11. Auditing and tracking (FR.1.6)

| What | Where |
|---|---|
| Distribution list / member changes (who, when, before/after) | Envers `_aud` tables + `revinfo.user_id` |
| Notification type / template changes | Liquibase changelog + Git history (they only change through migrations) |
| Event received, per customer | `notifications` (event key, parameters, outcome, `created_by`) |
| Successful communication (recipient, timestamp, event reference, status) | `notification_deliveries` (`SENT`, `sent_at`, `address`, `body`) + `delivery_attempts` |
| Every error | `delivery_attempts` (one row per failed attempt) + `NO_RECIPIENTS` notifications |
| Request tracing | Structured logs with correlation id (`traceparent`). PII is masked. |

**Production hardening (not in the exercise):** grant the application DB user only `INSERT, SELECT` on `notifications`, `delivery_attempts` and the `_aud` tables, so the audit trail is append-only.

## 12. Security threat assessment (STRIDE)

| Threat | Risk in this service | Mitigation |
|---|---|---|
| **Spoofing** | Forged caller publishes fake regulatory alerts to customers; forged sender address | JWT validated in-service (signature/JWKS, `iss`, `aud`, `exp`); publisher uses client credentials; SPF, DKIM and DMARC on the sending domain |
| **Tampering** | Altered payload or audit records; template/header injection from untrusted public-source data | TLS everywhere; JSON Schema validation with `additionalProperties:false`; plain-text email; CR/LF stripped in subject; optimistic locking; append-only audit tables (hardening) |
| **Repudiation** | "We never received it" / "I didn't change that list" | Envers with user id; per-attempt records with `provider_message_id`; stored rendered content; `created_by` everywhere |
| **Information disclosure** | **Highest risk:** a customer's supplier-compliance alert goes to another customer; recipients see each other's addresses; PII in logs | Tenant from JWT, never from the request, for user endpoints; cross-tenant access → 404; recipients resolved only from the customer's own list; one email per recipient; PII masking in logs; SMTP credentials in a secret store |
| **Denial of service** | Request floods; using the service to mail-bomb | Gateway rate limiting; ≤100 customers per request; ≤100 members per list; request size limit; retry cap with backoff |
| **Elevation of privilege** | Customer user publishes notifications or edits another tenant's list (IDOR) | Role checks (`@PreAuthorize`) per endpoint; every list/member query scoped by `customer_id`; tests for cross-tenant access |

## 13. Metrics

Exposed with Micrometer through `/actuator/prometheus`.

### 13.1 Adoption

| Metric | Type | Why |
|---|---|---|
| `notification.customers.reached.ratio`: ACCEPTED / (ACCEPTED + NO_RECIPIENTS) per event | gauge/summary | Main adoption signal: how many affected customers actually receive alerts |
| `distribution.lists.active` by type, channel | gauge | Configured customers |
| `distribution.list.members` per list | distribution summary | Reach per customer |
| `notifications.created` by type, outcome | counter | Usage per week |
| `distribution.config.changes` by operation | counter | Customer engagement with self-service |

### 13.2 Performance

| Metric | Type | Proposed target |
|---|---|---|
| `http.server.requests` for `POST /notifications` | timer | p95 < 300 ms |
| `notification.delivery.latency` (`sent_at − notification.created_at`) | timer | p99 < 5 min |
| `notification.deliveries` by channel, status, error_code | counter | Success rate ≥ 99 % |
| `notification.delivery.attempts` per delivery | distribution summary | Mostly 1 |
| `notification.deliveries.pending` / `…pending.oldest.age` | gauge | Alert if oldest > 30 min |
| `notification.channel.send` by channel | timer | Provider health |

## 14. Acceptance criteria

### 14.1 Happy paths

- **AC-1 Notify affected customers.** Given customers 101 and 102 each have an active EMAIL list with 2 active members, when the publisher posts a valid event for [101, 102], then the response is 202 with an event key and two `ACCEPTED` results. 4 `PENDING` deliveries are stored, and after dispatch each member receives exactly one email whose body equals the rendered template, and all 4 deliveries are `SENT` with one `SUCCESS` attempt each.
- **AC-2 Exact message.** Given the sample parameters, the body is exactly `Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026. [80%]`.
- **AC-3 List management.** A list admin can create a list, add/remove members and deactivate the list. Each change creates an Envers revision carrying their user id.
- **AC-4 Tracking.** `GET /notifications/{id}` returns recipients, timestamps, event key, status and attempts.

### 14.2 Edge cases

| # | Given / When | Then |
|---|---|---|
| AC-5 | Same event posted twice | Second call → 200, all `DUPLICATE`, no new deliveries |
| AC-6 | Same event re-posted with an extra customer | Only the new customer gets `ACCEPTED`; others `DUPLICATE` |
| AC-7 | Same event with extra whitespace in `siteName` | Same event key → `DUPLICATE` |
| AC-8 | Same event with different confidence | New event key → new notification (A-9) |
| AC-9 | Two identical requests in parallel | Exactly one notification per customer (unique constraint) |
| AC-10 | Customer with no list / inactive list / no active members | `NO_RECIPIENTS` stored and returned; other customers unaffected |
| AC-11 | Unknown or inactive type | 422, nothing stored |
| AC-12 | Missing parameter, unknown parameter, `confidence` = 101, invalid `documentType`, invalid date | 422 listing the violations, nothing stored |
| AC-13 | SMTP unavailable, then recovers | Delivery stays `PENDING`, one `RETRYABLE_ERROR` attempt per try, eventually `SENT` |
| AC-14 | SMTP keeps failing / returns 5xx | `FAILED` after 5 attempts / immediately; every attempt stored |
| AC-15 | Member removed after acceptance, before dispatch | Delivery still sent to the snapshot address |
| AC-16 | Member added after an event was notified | Never receives that event |
| AC-17 | Customer user calls `POST /notifications` | 403 |
| AC-18 | User of tenant A reads/edits a list of tenant B | 404, no change |
| AC-19 | Adding an invalid email / an already-active address | 400 / 409 |
| AC-20 | Concurrent update of a list with a stale `version` | 409 |
| AC-21 | Second active list for same (customer, type, channel) | 409 |

## 15. Testing strategy

- **Unit (domain/application):** schema validation, normalization and event key, renderer and formatting, retry/backoff policy, aggregate invariants, use cases with fake ports.
- **Integration:** `@SpringBootTest` + **Testcontainers `postgres:18`** + **GreenMail** (SMTP, including failure simulation) + `spring-security-test` JWTs. Covers each AC above end to end through the HTTP API.
- **Contract:** the drift guard from §6.3 (DB types ↔ spec variants ↔ schema files). Request/response validation against the OpenAPI spec in integration tests (e.g. `swagger-request-validator`).
- **Fixtures:** the notification type and template are seeded by the main Liquibase changelog. Test data (customers' lists and members) lives in a Liquibase `test` context plus `@Sql` scripts under `src/test/resources/fixtures`, so tests run on a clean database.
- **Coverage:** JaCoCo `check` goal, **line coverage ≥ 90 %**; the build fails below it. Generated OpenAPI DTOs are excluded.
- **Determinism:** `Clock` is injected; the dispatcher can be triggered synchronously in tests.

## 16. Decisions

| # | Decision | Alternatives / rationale |
|---|---|---|
| D-1 | Notification types are data (JSON Schema + per-channel templates); each type's schema is a versioned file shared by the OpenAPI spec and the DB seed (§6.3) | **Typed Java records per type**: compile-time safety, but every type adds code to the use case. **Spec generated at runtime from the DB** (springdoc customizer): no release needed for new types, but it requires code-first and the contract changes without a version, so consumers' clients go stale silently. **Generic `parameters: object` + schema discovery endpoint**: simplest, but no typed contract. The chosen option keeps the use case generic (FR.1.7) and gives consumers a typed, versioned contract with no drift. Since types only change through migrations (D-6), it costs nothing in flexibility. |
| D-2 | Permanent deduplication with a parameter hash, per customer | Time-window or "same customers + same params" checks are racy, miss partial overlaps and are arbitrary. A caller-supplied reference would be equally valid, but a derived key needs no contract with the caller. |
| D-3 | DB-backed work queue (`SKIP LOCKED`), no broker | At a few hundred messages a week a broker adds operations cost with no benefit. Writing deliveries in the same transaction gives outbox-style guarantees. Move to a broker if volume or the number of channels grows. |
| D-4 | Distribution list per (customer, type, channel); member = single address | Columns per channel (`email`, `phone`, `slack_id`) on members would be mostly null and need cross-table validation. Per-channel lists keep validation local (`AddressValidator`). |
| D-5 | Soft delete for lists and members | Hard delete would break delivery FKs and the audit trail. |
| D-6 | No admin API for types and templates | Not needed by the requirements. Changes go through reviewed migrations, which are also their audit trail. Can be added later without schema changes (Envers would then cover them). |
| D-7 | Rendered content stored per delivery | Proves exactly what each recipient received, even if the template changes later. The storage cost is negligible. |
| D-8 | Plain-text email, one per recipient | Avoids HTML injection and address disclosure. |
| D-9 | 202 Accepted with per-customer outcome | Delivery is asynchronous. 200 when nothing new was created, so retries by the caller are safe. |

## 17. Open questions (for the product owner)

- **Q-1** Who manages distribution lists: customers (assumed) or Qualifyze staff on their behalf?
- **Q-2** Should a confidence change for an already-notified match trigger a new notification? Currently yes (A-9). The alternative is to mark identity fields in the schema and exclude `confidence` from the hash.
- **Q-3** Exact wording: should the message follow the template or the sample ("Site …" prefix), and should "a OAI" be "an OAI"?
- **Q-4** Is a subject format / sender address defined by brand guidelines?
- **Q-5** Should repeated `FAILED` deliveries for a member alert the customer admin?
- **Q-6** Retention period for notifications and audit data.

## 18. Requirements traceability

| Requirement | Covered in |
|---|---|
| FR.1.1 REST API for detection process | §6.1, §7 |
| FR.1.2 Channel and template | §8, §9 |
| FR.1.3 Confidence | A-8, §7.2, §8 |
| FR.1.4 Current cycle only | A-7, §7.3 |
| FR.1.5 Volume | §1, D-3 |
| FR.1.6 Historicization and tracking | §10, §11 |
| FR.1.7 Reusability | §4 (`ChannelSender`), §6.3, D-1, D-4 |
| Security threat assessment | §12 |
| Adoption and performance metrics | §13 |
| Fixtures, coverage > 90 % | §15 |
| Acceptance criteria | §14 |
