# Backend — Clean Architecture and Domain-Driven Design

This Maven multi-module backend targets Java 25, Spring Boot 4.1.0, and PostgreSQL.

## Dependency rule

```text
api ────────────────┐
                   ├──> application ──> domain
infrastructure ────┘
```

- `domain`: pure Java business model, value objects, domain events, and repository ports.
- `application`: CQRS use cases and outbound ports; it depends only on `domain`.
- `infrastructure`: persistence and external-system adapters.
- `api`: HTTP entry point and composition root.

The compiler enforces module boundaries. Architecture tests additionally prevent forbidden package dependencies and persistence details from leaking into the domain.

## Build and run

Start the local database:

```powershell
C:\Proyect\.tools\db.ps1 start
```

Activate the local toolchain, build, and run:

```powershell
. C:\Proyect\.tools\env.ps1
mvnw.cmd clean install
java -jar api\target\backend-api-0.0.1-SNAPSHOT.jar
```

Health endpoint: <http://localhost:8080/actuator/health>

## Configuration

Without `spring.profiles.active`, the application uses the `dev` profile.

| Variable | Development default |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:postgresql://localhost:5433/proyectdb` |
| `DB_USERNAME` | `proyect` |
| `DB_PASSWORD` | `proyect` |

The `prod` profile provides no defaults. Missing database variables prevent startup rather than silently connecting to a development database.

Authentication also requires `JWT_HMAC_SECRET`: a Base64-encoded secret containing at least 64 random bytes. Password hashes are verified with Argon2id; access tokens are short-lived HS512 JWTs and refresh tokens are random, opaque values persisted only as SHA-256 digests.

## Invitation email delivery

Loan invitations are single-use. The database retains only the SHA-256 digest of
the token; the complete delivery request (recipient and raw token) is AES-256-GCM
encrypted before it enters the transactional outbox. A scheduled worker sends it
using standard SMTP and marks the outbox event as published only after SMTP accepts
the message. Delivery is at-least-once, which is safe because the link can only be
redeemed once.

The onboarding link defaults to:

```text
http://localhost:5173/onboarding/payer#invitationToken=<one-time-token>
```

The token is deliberately in the URL fragment, so browsers do not send it to the
frontend server or in HTTP referrer headers. The frontend must read the fragment,
remove it with `history.replaceState`, and submit it only in the JSON body to
`POST /api/v1/onboarding/payer`.

Generate the required local encryption key once per environment:

```powershell
$bytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Set its result as `INVITATION_OUTBOX_ENCRYPTION_KEY`. It is intentionally separate
from `JWT_HMAC_SECRET` and must never be committed.

### Local SMTP test sink

The development defaults use SMTP at `localhost:1025`, compatible with Mailpit.
For example, with Docker installed:

```powershell
docker run --rm --name project-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Start the backend after setting `JWT_HMAC_SECRET` and
`INVITATION_OUTBOX_ENCRYPTION_KEY`, create a loan, then inspect the received
message at <http://localhost:8025>. Set `INVITATION_PUBLIC_BASE_URL` if the local
frontend uses a port other than `5173`.

### Brevo free tier / deployed SMTP

Brevo is the recommended provider for this project because its current free tier
includes transactional email with a 300-email daily limit. Authenticate the sender
domain (DKIM), register the sender, create an SMTP key, and configure:

```text
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=<Brevo SMTP login email>
MAIL_PASSWORD=<Brevo SMTP key, not the API key>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
INVITATION_EMAIL_FROM=verified-sender@example.com
INVITATION_EMAIL_SENDER_NAME=Project
INVITATION_PUBLIC_BASE_URL=https://app.example.com
INVITATION_OUTBOX_ENCRYPTION_KEY=<Base64 32-byte AES-256 key>
```

The `prod` profile requires the mail host, credentials, verified sender and public
application URL instead of inheriting the localhost defaults.

## Financial integrity

An approved payment is persisted in two complementary forms: the operational
payment/allocation record and an append-only, balanced financial journal. For an
approval, `CASH_CLEARING` is debited and the allocated principal, interest, and
fee receivables are credited. A reversal writes the exact compensating journal;
it never edits or deletes the approval journal.

Payment approvals and reversals take a PostgreSQL row lock on the loan. This
serializes financial changes for that loan, preventing concurrent approvals from
spending the same unpaid installment component. The backend also rejects:

- allocations to installments outside the loan's current payment plan;
- more than the unpaid principal, interest, or fee of an installment;
- total approved principal greater than the contractual original principal; and
- repeated components in one payment allocation.

`PAYOFF` is deliberately rejected for now. The previous implementation accepted
it without atomically closing the loan and payment plan, which is not a valid
financial settlement. It will be enabled only with that complete workflow.

When an approved `CAPITAL_PREPAYMENT` changes the plan, the backend first takes a
locked snapshot of every unpaid component in the current plan. The replacement
plan starts from that unpaid principal, not from the original future installments.
Already-approved interest paid in advance is credited against recalculated future
interest; interest already due and every unpaid fee are carried forward. Reversed
payments are excluded from this snapshot. This prevents a recalculation from
charging a component that has already been paid.

The reporting endpoint now requires a UUID request header. Reuse the same key
when retrying a timeout, not when creating another payment:

```http
POST /api/v1/loans/{loanId}/payments
Idempotency-Key: 8e6c284d-1be7-493c-a2e0-312567f4fdc9
```

The same key with the same submission returns the original payment identifier and
status; reuse with different submission data returns `409 idempotency_conflict`.
The key is stored with the payment and protected by a database unique constraint.

Reconcile journals operationally with:

```sql
SELECT *
FROM loans.financial_journal_reconciliation
WHERE difference <> 0;
```

The result must always be empty. In production, use a distinct runtime database
role that can `SELECT` and `INSERT` ledger rows but cannot `UPDATE`, `DELETE`,
or alter their tables; do not run the application as the schema owner.

## Database-first schema and migrations

The initial PostgreSQL schema is managed through the idempotent database-first scripts in [`../.database`](../.database). Hibernate runs with `ddl-auto: validate`, so it validates mappings and never creates or changes tables.

For a new empty local database, run the base scripts once before starting the
application:

```powershell
. C:\Proyect\.tools\env.ps1
psql -h localhost -p 5433 -U proyect -d proyectdb -f C:\Proyect\.database\00_loans_schema.sql
psql -h localhost -p 5433 -U proyect -d proyectdb -f C:\Proyect\.database\01_reference_data.sql
```

After the base schema exists, Flyway applies the additive migrations under
`infrastructure/src/main/resources/db/migration` at startup. A nonempty legacy
Flyway schema is baselined at version `0`. `V1__financial_journal_and_integrity.sql`
creates the ledger and its reconciliation view. Back up production data and execute
`flyway validate` in the release pipeline before deployment. Do not add the initial
schema as a migration or let the application generate DDL.

## Adding a capability

1. Model rules and invariants in `domain`.
2. Define required repository ports in `domain.repository`.
3. Orchestrate the operation through a command or query in `application`.
4. Implement technical adapters in `infrastructure`.
5. Expose the use case from `api` with request/response DTOs and composition-root wiring.
