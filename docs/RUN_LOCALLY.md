# Run VMS locally (backend + frontend)

Two projects, two terminals:

| Project | Folder | Runs on |
|---|---|---|
| Backend (Spring Boot) | `~/Code/vms-backend` | http://localhost:8080 |
| Frontend (Flutter Web) | `~/Code/vms-frontend` | opens in your browser |

## One-time setup

### 1. Java 21 in every terminal
The project's JDK lives in `~/.local/jdk`. Add this to the end of `~/.bashrc`, then open a new terminal:

```bash
export JAVA_HOME="$HOME/.local/jdk/jdk-21.0.13+9"
export PATH="$JAVA_HOME/bin:$PATH"
```

Check: `java -version` shows 21. (Flutter is already on your PATH.)

### 2. PostgreSQL with an empty `vms` database
Install and start PostgreSQL (needs your sudo password):

```bash
sudo apt update && sudo apt install -y postgresql
sudo systemctl enable --now postgresql
sudo -u postgres psql
```

Inside `psql`, choose your own database password and run:

```sql
CREATE ROLE vms_app LOGIN PASSWORD 'pick-a-db-password';
CREATE DATABASE vms OWNER vms_app;
\q
```

The app creates its own tables on first start.

### 3. Your private settings file (`.env`)
```bash
cd ~/Code/vms-backend
cp .env.example .env
nano .env
```

Fill in the blanks. `.env` is git-ignored, so nothing here is ever committed.

| Setting | What to put |
|---|---|
| `DB_PASSWORD` | the database password from step 2 |
| `JWT_SECRET` | run `openssl rand -base64 48` and paste the result (at least 32 characters) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | the login for your first account (password 12 to 72 characters) |
| `REGISTRATION_CODE` | a code (at least 12 characters) people must enter to sign up. Leave empty to switch sign-up off |

`DB_HOST`, `DB_PORT`, `DB_NAME` and `DB_USERNAME` are already right for the steps above.

## Every time you want to test

**Terminal 1: backend**
```bash
cd ~/Code/vms-backend
./mvnw spring-boot:run
```
Ready when you see `Started VmsBackendApplication`. Quick check: http://localhost:8080/actuator/health shows `"status":"UP"` and `"db":{"status":"UP"}`.

**Terminal 2: frontend**
```bash
cd ~/Code/vms-frontend
flutter run -d chrome
```
The browser opens on the sign-in screen. (No setting needed: in debug mode the app talks to `http://localhost:8080`.) If the browser does not open, use `flutter run -d web-server --web-port 5000` and open http://localhost:5000 yourself.

**Sign in** with `ADMIN_EMAIL` and `ADMIN_PASSWORD` from your `.env`, or choose *Sign up* and enter a new email, password and your `REGISTRATION_CODE`.

**Stop:** press `Ctrl+C` in each terminal. In the Flutter terminal, `r` reloads code changes and `R` restarts the app.

## Run the tests
```bash
cd ~/Code/vms-backend  && ./mvnw verify        # needs no database
cd ~/Code/vms-frontend && flutter analyze && flutter test
```
One test class covers just the duplicate vehicle number rule: `./mvnw test -Dtest=VehicleNumberUniquenessTest`.

## Look at the data
```bash
sudo -u postgres psql -d vms -c "select id, vehicle_number, company, status from vehicles;"
sudo -u postgres psql -d vms -c "select id, email, role from app_users;"
```

## When something goes wrong

| What you see | Cause and fix |
|---|---|
| Backend stops with `Missing required database configuration: DB_PASSWORD` (or `JWT_SECRET`) | That value is empty in `.env`. Run the backend from the `vms-backend` folder, because it reads `.env` from the current folder. |
| `JWT_SECRET is too short` | Use `openssl rand -base64 48`. |
| `password authentication failed for user "vms_app"` | `DB_PASSWORD` in `.env` differs from the password you gave `CREATE ROLE`. Fix it with `sudo -u postgres psql -c "ALTER ROLE vms_app PASSWORD 'new'"` and update `.env`. |
| `Connection refused` to port 5432 | PostgreSQL is not running: `sudo systemctl start postgresql`. |
| `Port 8080 was already in use` | An old backend is still running. Find it with `ss -ltnp \| grep 8080` and stop it. |
| App says "Cannot reach the server" | The backend is not running, or it is not on port 8080. |
| "Incorrect email or password" for the admin | The admin is created only while the user table is empty. If you changed `ADMIN_PASSWORD` later, it has no effect (see below). |
| Sign-up says "Sign-up is not available right now" | `REGISTRATION_CODE` is empty in `.env`. Set it and restart the backend. |
| You are signed out after refreshing the page | By design: the login token is kept in memory only, never in browser storage. |
| "Your session has expired" | Tokens last 30 minutes. Sign in again. |

## Start over
Forgot the admin password, or want a clean slate? Stop the backend, then:

```bash
sudo -u postgres psql -d vms -c "delete from app_users;"     # accounts only
sudo -u postgres psql -d vms -c "delete from vehicles;"      # vehicles only
```
On the next start, the admin is created again from `ADMIN_EMAIL` and `ADMIN_PASSWORD`.
To drop everything: `sudo -u postgres psql -c "DROP DATABASE vms;" -c "CREATE DATABASE vms OWNER vms_app;"`.
