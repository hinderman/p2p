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

## Database-first schema

The initial PostgreSQL schema is managed through the idempotent database-first scripts in [`../.database`](../.database). Hibernate runs with `ddl-auto: validate`, so it validates mappings and never creates or changes tables.

The Flyway location is intentionally empty during the initial database-first setup. Do not add the initial schema as a migration or let the application generate DDL.

## Adding a capability

1. Model rules and invariants in `domain`.
2. Define required repository ports in `domain.repository`.
3. Orchestrate the operation through a command or query in `application`.
4. Implement technical adapters in `infrastructure`.
5. Expose the use case from `api` with request/response DTOs and composition-root wiring.
