# Authentication contract

Stateless JWT bearer tokens. The server keeps no session and sets no cookies.

## Sign in

`POST /api/v1/auth/login` (public)

Request `application/json`:

```json
{ "email": "admin@example.com", "password": "..." }
```

`email` is trimmed and compared case-insensitively. Both fields are required (`email` max 254, `password` max 128 characters).

**200 OK**

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "email": "admin@example.com"
}
```

`expiresIn` is the token lifetime in seconds (default 30 minutes, `JWT_TTL`). The response has `Cache-Control: no-store`.

**401 Unauthorized** (wrong password *or* unknown email, deliberately identical)

```json
{ "type": "about:blank", "title": "Authentication failed", "status": 401,
  "detail": "Invalid email or password.", "instance": "/api/v1/auth/login" }
```

**400 Bad Request**: a field is blank/too long or the body is not valid JSON (`errors[]` lists `field` and `message`).

## Sign up

`POST /api/v1/auth/register` (public, but gated by a shared registration code)

```json
{ "email": "new.user@example.com", "password": "at least 12 characters", "registrationCode": "..." }
```

- `password`: 12 to 72 characters (72 bytes, the BCrypt limit). `email` must be a valid address, max 254.
- Sign-up is **off unless `REGISTRATION_CODE` is set** on the server (at least 12 characters; startup fails with a clear message if it is shorter). The code is compared in constant time and is never logged or echoed.
- The code is checked **before** anything else, so a caller without it cannot learn which emails already have accounts.
- New accounts get the `ADMIN` role, the same access every signed-in user has.

| Result | Meaning |
|---|---|
| **201** `{accessToken, tokenType, expiresIn, email}` | Account created and signed in (same body as login). |
| **400** `errors[]` | A field is invalid (`email`, `password`, `registrationCode`). |
| **403** `Registration not allowed` | Wrong code (`That registration code is not valid.`) or sign-up is off (`Sign-up is not available right now.`). |
| **409** `Duplicate email` | `An account with this email already exists.` (case-insensitive). |

## Calling protected endpoints

Everything under `/api/` except the login and register endpoints requires:

```
Authorization: Bearer <accessToken>
```

| Situation | Response |
|---|---|
| Missing, malformed, wrongly signed, tampered or unsigned token | **401**, header `WWW-Authenticate: Bearer`, detail `Authentication is required to access this resource.` |
| Expired token | **401**, same header, detail `Your session has expired. Please sign in again.` |
| Valid token without permission (future roles) | **403**, detail `You do not have permission to do this.` |

All security errors are `application/problem+json`. Credentials in the query string or Basic auth are not accepted. `OPTIONS` preflights, login, register and `GET /actuator/health` are public; every other path is denied.

## Token

HS256 JWT signed with `JWT_SECRET`. Claims: `iss` (`vms-backend`), `sub` (the email), `iat`, `exp`, `roles` (`["ADMIN"]`). It never contains the password or its hash.

## Accounts and secrets

- Users live in `app_users` (`email` unique, `password_hash`, `role`). Passwords are stored only as BCrypt hashes (`{bcrypt}` prefix).
- Further accounts are created through sign-up (above), or the first admin at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD` (12 to 72 characters), only while the table is empty. Remove both variables afterwards.
- `JWT_SECRET` must be at least 32 characters (`openssl rand -base64 48`). The app refuses to start without it. Changing it signs everybody out.
- Passwords, hashes, tokens and the secret are never logged or returned in error messages.
