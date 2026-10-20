# Project conventions

BiletFlow is a Java 21 / Spring Boot modular monolith organized by DDD bounded contexts. These conventions guide new code and changes to existing features. Prefer the established pattern in the surrounding context over introducing another abstraction or library.

## Packages and responsibilities

Business code lives under `com.biletflow.biletflow.<boundedcontext>`. Organize by feature or aggregate within each layer when a context grows.

| Package                                           | Responsibility                                                                                       |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `domain`                                          | Aggregates, value objects, business rules, domain exceptions, and aggregate repository contracts.    |
| `application`                                     | Use cases, authorization, transaction boundaries, and coordination through domain objects and ports. |
| `application/.../command`, `application/.../view` | Use-case inputs and read results.                                                                    |
| `ohs`                                             | The context's public Java API for other contexts, with its own commands and result types.            |
| `external`                                        | Adapters translating another context's OHS into local application ports.                             |
| `persistence/<aggregate>`                         | JPA repository adapters and persistence mappers. Put mapped entities in its `entity` subpackage.     |
| `infrastructure`, `config`                        | Provider clients, scheduling, serialization, encryption, and Spring wiring.                          |
| `rest`                                            | HTTP endpoints, request/response mapping, validation, and exception translation.                     |

Some existing contexts use different package layouts. Keep related changes coherent; avoid unrelated package moves just to normalize names.

## Domain and application boundaries

- Keep domain objects independent of Spring, JPA, HTTP, provider SDKs, and application-layer types.
- Keep aggregate state private. Expose named operations such as `requestRefund` or `expire` that enforce valid transitions. Use immutable IDs and value objects, enums for finite states, and controlled collection access.
- Application services load aggregates, check the caller's permissions, invoke domain behavior, save changes, and coordinate external effects through ports. Business decisions belong in domain methods or domain services; SQL, JSON, cryptography, and provider protocols belong in adapters.
- Transport validation checks request shape. Domain objects still enforce business invariants when invoked outside HTTP.
- Use constructor injection and validate required dependencies. Prefer records for immutable commands and views. Do not expose aggregates or JPA entities as HTTP responses.
- Add an interface or service when it represents a meaningful boundary or responsibility. Avoid pass-through layers and unused extension points.

## Context boundaries and shared code

- Access another context through its OHS, not its repositories, persistence entities, or internal services. Translate foreign concepts and exceptions in an adapter where necessary.
- Put shared primitives and cross-cutting infrastructure in `common`; keep context-specific policies in their owning context. A second caller alone is not a reason to move business logic into `common`.
- Use `CurrentActor` for the authenticated actor and enforce use-case authorization in application code. Endpoint security complements those checks.

## Persistence and transactions

- Persist aggregates using JPA entities and repositories. Keep the domain model separate from the relational model; do not store whole aggregate snapshots as JSON.
- Follow the mapper operations `toDomain`, `toNewEntity`, and `updateEntity` where applicable. Update managed entities without replacing their identity or immutable relationships.
- Use private entity fields and the access mode established by their JPA annotations. With field access, add getters only when a consumer needs them; retain the no-argument constructor required by JPA.
- Prefer Spring Data queries or JPQL. Native SQL is appropriate for a documented database-specific need, such as transaction locks or durable worker coordination; keep it inside infrastructure and persistence adapters.
- Use read-only transaction defaults for query-oriented services and repositories, with explicit write transactions. Keep remote provider calls outside long-running database transactions. Coordinate uncertain outcomes through persisted workflow state and reconciliation.
- Schema changes belong in Liquibase under `src/main/resources/config/liquibase`. Generate a diff where practical, review it, and include the changeset in the master changelog. Add new changesets rather than rewriting migrations already applied to shared databases. See [the migration guide](db-migrations-guide.md).
- Treat enum storage values and persisted operation codes as compatibility contracts. Renaming a Java constant must not silently change the meaning of existing rows.

## HTTP contracts and external effects

- Use dedicated request DTOs with Bean Validation, including nested validation. Map them to application commands. Preserve existing field names, status codes, and idempotency behavior unless intentionally changing the API.
- Follow the context's `ProblemDetail` exception handler. Use shared authentication, authorization, and not-found exceptions where applicable. Translate provider failures into application-facing errors without exposing provider internals or secrets.
- Document endpoints and relevant error responses with the existing OpenAPI annotations.
- Assume callbacks and event deliveries may repeat. Verify provider evidence, make handlers idempotent, and retain enough durable state to recover after failure. A timeout does not prove that a payment failed.
- Publish durable integration events within the database transaction required by `EventBus`; consumers must tolerate redelivery. Do not replace durable delivery with an in-memory callback for financial workflows.

## Tests, formatting, and documentation

- Test observable business behavior, authorization, failure recovery, and persistence guarantees. Avoid tests that merely repeat getters/setters or inspect source text for particular strings.
- Use JUnit tests named `*Test` for isolated tests and `*IT` for integration tests so Gradle routes them correctly. Use PostgreSQL/Testcontainers for database behavior that depends on PostgreSQL; H2 checks do not establish migration or locking correctness there.
- Run `./gradlew test` for unit tests and `./gradlew integrationTest` for integration tests with Docker available. Report skipped checks accurately.
- Format changed files using the repository's Prettier configuration and plugins: four-space Java indentation and a 140-character print width. For a specific file, use `npx prettier --write <path>` with the project's installed dependencies. Remove unused imports separately.
- Keep changes focused. Remove superseded implementations and obsolete test fixtures when replacing a feature, while distinguishing unused code from deliberately unfinished functionality.
- Write documentation for future contributors: stable architecture, contracts, setup, and operational behavior. Keep patch-application instructions, individual debugging history, and temporary verification notes out of project documentation.
