# SmartCollect - Production Deployment Instructions

These instructions are for anyone who receives this project as a zip file and needs to bring it up in a production environment for the first time.

All changes are made exclusively in:
```
src/main/resources/application.properties
```

---

## Prerequisites

Before starting the server, make sure the following are installed:

- **JDK 25** - check with:
  ```
  java -version
  ```
  If it does not report version 25, install JDK 25 (e.g. Eclipse Temurin: https://adoptium.net/temurin/releases/?version=25) before continuing.
- **Docker** - must be installed and running. The project uses Spring Boot's Docker Compose integration, which automatically starts the PostgreSQL container defined in `compose.yaml` when the server starts. There is no need to run `docker compose up` manually.

---

## How to Start the Server

**Option A - Terminal (recommended):**

From the project's root folder (the folder containing `gradlew`), on **any OS** (Windows, macOS, Linux):

```
./gradlew bootRun
```

This compiles the code (if needed) and starts the server. The server listens on port `8084`.

**Option B - From an IDE (e.g. IntelliJ, Eclipse/STS, VS Code):**

1. Open the project's root folder as a Gradle project.
2. Locate `src/main/java/ambient_invisible_intelligence/Application.java`.
3. Run its `main` method (right-click → Run, or use the IDE's Run/Play button next to the class).

Either option starts the same Spring Boot application; there is no functional difference between them.

---

## Background: DDL-Auto Modes

| Mode | What it does | When to use |
|---|---|---|
| `create-drop` | Drops and recreates all tables on every startup. All data is lost on shutdown. | Development / testing only |
| `create` | Creates tables on first startup. Does **not** drop them on shutdown. Data survives restart. | **First production run** - to initialize the schema |
| `update` | Updates the schema to match entities without dropping existing data. | Re-deployments when the schema may have changed |
| `validate` | Only checks that the schema matches the entities. Throws an error if there is a mismatch. Does not modify anything. | **Stable production** - once the schema is confirmed correct |

---

## Step 1 - First Run (create schema + seed demo data)

**No changes required.** The file as received is already configured for the first run:

```properties
spring.profiles.active=default,initDemoes
#spring.profiles.active=default

#spring.jpa.hibernate.ddl-auto=create-drop
#spring.jpa.hibernate.ddl-auto=update
spring.jpa.hibernate.ddl-auto=create
#spring.jpa.hibernate.ddl-auto=validate
```

- `initDemoes` is active → demo data will be seeded automatically on startup
- `create` is active → the database schema will be created on first startup and data will persist after shutdown

**Start the server.** Verify it started correctly and the data is present before continuing.

---

## Demo User Credentials (seeded by `initDemoes`)

The following users are created automatically on first run (see `SmartCollectDemoInit.java`):

| Role | Name | Email | Password |
|---|---|---|---|
| ADMIN | System Admin | `admin@sc.com` | `Admin1!` |
| OPERATOR | Operator One | `op1@sc.com` | `Oper1!7` |
| DRIVER (END_USER) | Dan Cohen | `dan@sc.com` | `Driver1!` |
| DRIVER (END_USER) | Noa Levi | `noa@sc.com` | `Driver1!` |
| DRIVER (END_USER) | Omer Bar | `omer@sc.com` | `Driver1!` |
| DRIVER (END_USER) | Maya Katz | `maya@sc.com` | `Driver1!` |
| DRIVER (END_USER) | Yossi Mizrahi | `yossi@sc.com` | `Driver1!` |

All 5 drivers share the same password (`Driver1!`); each has a distinct email.

---

## Step 2 - Subsequent Runs (preserve data, validate schema)

Once the server has run once and the schema and data are in place, make the following two changes in `application.properties` before restarting:

**1. Disable demo data initialization** (prevents re-seeding on every restart):
```properties
# Comment out this line:
#spring.profiles.active=default,initDemoes

# Uncomment this line:
spring.profiles.active=default
```

**2. Switch from `create` to `validate`** (schema is no longer modified on startup):
```properties
# Comment out this line:
#spring.jpa.hibernate.ddl-auto=create

# Uncomment this line:
spring.jpa.hibernate.ddl-auto=validate
```

After both changes the relevant section should look like this:
```properties
#spring.profiles.active=default,initDemoes
spring.profiles.active=default

#spring.jpa.hibernate.ddl-auto=create-drop
#spring.jpa.hibernate.ddl-auto=update
#spring.jpa.hibernate.ddl-auto=create
spring.jpa.hibernate.ddl-auto=validate
```

**Restart the server.** The data from Step 1 is preserved. The server will validate the schema on startup and refuse to start if there is a mismatch.

---

## When to use `update` instead of `validate`

Use `update` if you re-deploy a new version of the code that changes an entity (adds a column, renames a field, etc.). It will migrate the schema without dropping data. Once you have confirmed the migration is correct, switch back to `validate`.

---

## Summary

| Step | `spring.profiles.active` | `ddl-auto` | Purpose |
|---|---|---|---|
| First run | `default,initDemoes` | `create` | Create schema + seed data |
| Production | `default` | `validate` | Preserve data, verify schema |
| Re-deploy with schema change | `default` | `update` | Migrate schema, preserve data |
