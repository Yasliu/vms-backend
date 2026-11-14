# How to create a Spring Boot project (Spring Initializr)

## 1. Install prerequisites
- **JDK 17 or newer** (this project uses 21). Check with `java -version`.
- Nothing else is required for Maven: the generated project contains the Maven Wrapper (`mvnw`).

## 2. Generate the project

### Option A: website
1. Open https://start.spring.io
2. Choose **Project: Maven**, **Language: Java**, the default stable **Spring Boot** version (no SNAPSHOT/RC).
3. Fill in **Group** (e.g. `com.vms`), **Artifact** (e.g. `vms-backend`), **Packaging: Jar**, **Java: 21**.
4. Click **Add Dependencies** and add: *Spring Web*, *Spring Data JPA*, *Validation*, *PostgreSQL Driver*, *H2 Database*, *Spring Boot Actuator*. Add *Spring Security* only when you implement authentication.
5. Click **Generate**, unzip, and open the folder in your IDE.

### Option B: command line (what we did)
```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=21 -d packaging=jar \
  -d groupId=com.vms -d artifactId=vms-backend -d name=vms-backend \
  -d packageName=com.vms \
  -d dependencies=web,data-jpa,validation,postgresql,h2,actuator \
  -o vms.zip
unzip vms.zip -d vms-backend
```
- Omit `bootVersion` to get the current stable default. Do not copy ids ending in `.RELEASE` from the metadata endpoint into the POM; Maven Central versions look like `4.1.2`.
- List all dependency ids: `curl https://start.spring.io/metadata/client`.

### Option C: IDE
IntelliJ IDEA (Ultimate): *New Project > Spring Boot*. VS Code: "Spring Initializr" extension. Both use the same service.

## 3. Build and run
```bash
cd vms-backend
./mvnw verify                 # compile + run tests (Windows: mvnw.cmd verify)
./mvnw spring-boot:run        # start the app
```

## 4. Adding a dependency later
Find the starter id (e.g. Security) and add it to `pom.xml`:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```
Then run `./mvnw verify`. Versions are managed by the Spring Boot parent, so do not set them.

## 5. Recommended structure
Create packages under your base package: `controller`, `service`, `repository`, `entity`, `dto`, `validation`, `exception`, `config`. Controllers call services; services call repositories; entities stay inside the service layer and DTOs cross the API boundary.

## 6. Configure the database (without committing secrets)
1. In `application.properties` reference placeholders only, never real values:
   ```properties
   spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME}
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   spring.config.import=optional:file:.env[.properties]
   ```
2. Commit a template `.env.example` with fake values; copy it to `.env` locally and fill in the real ones.
3. Add `.env` (and `config/`) to `.gitignore` **before** creating the file. Verify with `git check-ignore -v .env`.
4. Run tests against an in-memory database (H2, `test` scope) in an `application-test.properties` profile so tests never touch your real database.
5. Check `/actuator/health` shows `"db": {"status": "UP"}`.

## Common problems
| Symptom | Fix |
|---|---|
| `java: command not found` | Install a JDK and set `JAVA_HOME` |
| `Non-resolvable parent POM ... .RELEASE` | Use version `X.Y.Z` in the POM parent, not `X.Y.Z.RELEASE` |
| `Port 8080 already in use` | `--server.port=8081` |
| Tests named `*IT` don't run | Surefire runs `*Test`; `*IT` needs the Failsafe plugin |
