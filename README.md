# VMS Backend

Spring Boot REST API for the Vehicle Management System. It owns all business rules; the Flutter Web frontend is a separate project and is not connected yet.

**Stack:** Java 21, Spring Boot 4.1.2, Maven (wrapper), Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL driver, H2 (tests only), Actuator.

## Database setup (PostgreSQL)

Credentials are never stored in git. Provide them either as **environment variables** or in a local **`.env`** file (git-ignored; real environment variables win over it).

1. Create the database and a dedicated user (as a PostgreSQL admin, e.g. `psql -U postgres`):
   ```sql
   CREATE ROLE vms_app LOGIN PASSWORD 'choose-a-strong-password';
   CREATE DATABASE vms OWNER vms_app;
   ```
2. Copy the template and fill in your values:
   ```bash
   cp .env.example .env
   ```

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `DB_NAME` | yes | | Database name |
| `DB_USERNAME` | yes | | Database user |
| `DB_PASSWORD` | yes | | Database password |
| `DB_HOST` | no | `localhost` | Host |
| `DB_PORT` | no | `5432` | Port |
| `DB_POOL_SIZE` | no | `10` | Hikari max pool size |
| `JPA_DDL_AUTO` | no | `update` | Hibernate schema mode (see note below) |

If a required variable is missing the app stops at startup and names the missing variables. `.env`, `.env.*` (except `.env.example`), `config/`, `application-local*.properties`, `*.pem` and `*.key` are git-ignored.
Alternative to `.env`: a git-ignored `config/application.properties` is also read by Spring Boot automatically.

Check connectivity: `GET /actuator/health` returns `"db": {"status": "UP"}` when the database is reachable (no connection details are exposed).

**Schema note:** `JPA_DDL_AUTO=update` lets Hibernate create/alter tables for local development. Move to Flyway migrations and `validate` before production. If you ran an earlier version of this project against your database, drop the old `vehicles` table first; its columns were different.

## Run

```bash
./mvnw spring-boot:run     # needs the DB variables above
./mvnw verify              # compile + all tests (uses in-memory H2 only, never your real database)
```

## Architecture (`com.vms`)

| Package | Responsibility |
|---|---|
| `controller` | HTTP only: map requests/responses, trigger validation |
| `service` | Business rules and transactions |
| `repository` | Spring Data JPA access (+ `Specification`s for search) |
| `entity` | JPA entities and enums |
| `dto` | Request/response records with Bean Validation annotations (entities never leave the API) |
| `exception` | Domain exceptions + `GlobalExceptionHandler` (RFC 7807 `application/problem+json`) |
| `config` | Configuration, including the startup database-settings check |

## Vehicles API (`/api/v1/vehicles`)

Fields: `id` (generated), `vehicleNumber`, `company`, `type`, `brand`, `model`, `status` (`ACTIVE`, `INACTIVE`, `UNDER_MAINTENANCE`; defaults to `ACTIVE` on create).

| Method | Path | Notes |
|---|---|---|
| POST | `/` | 201 + `Location` |
| GET | `/?status=&q=&page=&size=&sort=` | Paged; `q` searches number, company, type, brand, model |
| GET | `/{id}` | 404 if unknown |
| PUT | `/{id}` | Full update of the editable fields |
| DELETE | `/{id}` | 204 |

**Business rule:** `vehicleNumber` is unique. It is normalised (trimmed, whitespace collapsed, upper-cased) before comparison, so `dhaka 1` and `DHAKA  1` clash. Enforced in `VehicleService` and backed by a database unique constraint. Violations return **409** with `DuplicateVehicleNumberException`'s message.

Errors (`application/problem+json`): 400 validation (with per-field `errors`), 404 not found, 409 duplicate.

## Not done yet

- **Authentication/authorization:** add `spring-boot-starter-security` (+ `spring-security-test`) when we implement it.
- **DB migrations:** Flyway + `JPA_DDL_AUTO=validate` before production.
- CORS is intentionally not configured until the frontend is connected.
