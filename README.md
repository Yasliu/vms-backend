# VMS Backend

Spring Boot REST API for the Vehicle Management System. It owns all business rules; the Flutter Web frontend is a separate project and is not connected yet.

**Stack:** Java 21, Spring Boot 4.1.2, Maven (wrapper), Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL driver, H2 (tests only), Actuator.

> **Just want to run everything?** See [`docs/RUN_LOCALLY.md`](docs/RUN_LOCALLY.md): one-time setup, the two commands to start, and troubleshooting.

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
| `JWT_SECRET` | yes | | Token signing key, at least 32 characters (`openssl rand -base64 48`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first start | | Creates the first admin while no users exist (password 12-72 chars) |
| `REGISTRATION_CODE` | no | unset (sign-up off) | Shared code people must enter to sign up (at least 12 characters) |
| `JWT_TTL` | no | `30m` | Access token lifetime |
| `DB_HOST` | no | `localhost` | Host |
| `DB_PORT` | no | `5432` | Port |
| `DB_POOL_SIZE` | no | `10` | Hikari max pool size |
| `JPA_DDL_AUTO` | no | `update` | Hibernate schema mode (see note below) |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:*,http://127.0.0.1:*` | Comma-separated browser origins allowed to call `/api/**`. Set to the real web app origin outside local development |

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

## Authentication

`POST /api/v1/auth/login` with `{"email","password"}` returns `{"accessToken","tokenType":"Bearer","expiresIn","email"}`. `POST /api/v1/auth/register` with `{email, password, registrationCode}` creates an account (only when `REGISTRATION_CODE` is set) and returns the same token body. Send `Authorization: Bearer <accessToken>` on every other `/api/**` call; missing, invalid or expired tokens get 401 `problem+json`. Full contract, error shapes and secret handling: [`docs/AUTH_CONTRACT.md`](docs/AUTH_CONTRACT.md).

## Vehicles API (`/api/v1/vehicles`, requires a bearer token)

Fields: `id` (generated), `vehicleNumber`, `company`, `type`, `brand`, `model`, `status` (`ACTIVE`, `INACTIVE`, `UNDER_MAINTENANCE`; defaults to `ACTIVE` on create).

| Method | Path | Notes |
|---|---|---|
| POST | `/` | 201 + `Location` |
| GET | `/?status=&q=&page=&size=&sort=` | Paged (default 20, max 100, sorted by `id`); `sort=vehicleNumber,desc`; `q` searches number, company, type, brand, model |
| GET | `/{id}` | 404 if unknown |
| PUT | `/{id}` | Full update of the editable fields |
| DELETE | `/{id}` | 204 |

**Business rule:** `vehicleNumber` is unique. It is normalised (trimmed, whitespace collapsed, upper-cased) before comparison, so `dhaka 1` and `DHAKA  1` clash. Enforced in `VehicleService` and backed by a database unique constraint. Violations return **409** with `DuplicateVehicleNumberException`'s message.

Every error is `application/problem+json` (`status`, `title`, `detail`, `instance`): 400 validation (with per-field `errors`), malformed JSON, bad id/status/sort; 404 not found; 405 method not allowed; 409 duplicate vehicle number; 415 wrong content type; 500 generic message only (details are logged, never returned).

POST/PUT bodies use request DTOs and responses use `VehicleResponse`; JPA entities are never serialised.

## Not done yet

- **Roles beyond ADMIN, login rate limiting / lockout, token refresh and revocation.** Tokens are valid until they expire.
- **DB migrations:** Flyway + `JPA_DDL_AUTO=validate` before production.
