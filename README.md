# BiletFlow

BiletFlow is a self-service event ticketing platform for Kazakhstan.

The backend is a Java / Spring Boot modular monolith organized using DDD bounded contexts. The project was originally generated with JHipster 9.3.0 and has since been adapted to the BiletFlow architecture.

## Requirements

- Java 21
- Docker
- Docker Compose

## Local Development

Start the backend dependencies:

```bash
docker compose -f src/main/docker/services.yml up -d
```

Start the application:

```bash
./gradlew
```

The Gradle process remains running while the Spring Boot application is running. Seeing Gradle remain at an `EXECUTING` percentage is normal.

Backend:

```text
http://localhost:8080
```

API:

```text
http://localhost:8080/api
```

Health check:

```bash
curl http://localhost:8080/management/health
```

Stop backend dependencies with:

```bash
docker compose -f src/main/docker/services.yml down
```

## Frontend Development

Frontend applications should use:

```text
http://localhost:8080/api
```

as the local API base URL.

Authenticated requests use JWT bearer authentication:

```text
Authorization: Bearer <JWT>
```

Authentication endpoint:

```text
POST /api/authenticate
```

## Development Email

Local development uses Mailpit so email does not require an external SMTP server.

Mailpit inbox:

```text
http://localhost:8025
```

Registration activation and password-reset emails can be opened there.

## Testing

Run backend tests with:

```bash
./gradlew test integrationTest jacocoTestReport
```

## Production Build

Build the production JAR with:

```bash
./gradlew -Pprod clean bootJar
```

Run it with:

```bash
java -jar build/libs/*.jar
```

## Project Structure

Backend source:

```text
src/main/java
src/main/resources
```

Development Docker configuration:

```text
src/main/docker
```

The backend is divided into bounded contexts such as:

```text
iam
eventmanagement
ticketinventory
```

Bounded contexts communicate synchronously through Open Host Services (OHS).

## Optional Development Tools

JHipster generated additional tooling under `src/main/docker/`. These tools are not required to run BiletFlow.

### SonarQube

SonarQube can be used for optional static analysis and code-quality checks:

```bash
docker compose -f src/main/docker/sonar.yml up -d
```

### Swagger Editor

A local Swagger Editor is available if API-first/OpenAPI development is needed:

```bash
docker compose -f src/main/docker/swagger-editor.yml up -d
```

It is not required for normal backend or frontend development.

## JHipster

BiletFlow was generated with JHipster 9.3.0.

JHipster documentation:

https://www.jhipster.tech/documentation-archive/v9.3.0
