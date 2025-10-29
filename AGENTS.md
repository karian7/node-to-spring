# Repository Guidelines

## Project Structure & Migration Context
Spring Boot code lives in `src/main/java/com/example/chatapp`, split into `controller`, `service`, `config`, `dto`, `validation`, `websocket`, and `repository`. Shared entities sit in `model`; cross-cutting handlers are under `exception`. Tests mirror the main tree inside `src/test/java/com/example/chatapp`. The legacy Node.js implementation in `/backend` is the canonical reference while we migrate—treat it as specification, not production code. Plans, parity notes, and spike outcomes belong in `Migration.md` and `docs/`.

## Migration Workflow & Traceability
- Track every Spring feature against its `/backend` counterpart; update `Migration.md` when a scope is completed.
- Before touching Java code, read the matching Node controller/service to capture required edge cases and response shapes.
- After implementation, run both stacks and compare payloads (status, headers, body schemas) using Postman or curl fixtures.
- Document any intentional deviations from Node behavior in the PR description and add acceptance evidence (logs, screenshots, test output).

## Build, Test, and Verification Commands
- `./mvnw clean install` — compile, run tests, and flag regressions early.
- `./mvnw spring-boot:run` — boot the Java API on port 8080 for manual verification.
- `./mvnw test` — execute unit and slice tests only.
- `docker-compose up -d mongo redis` — provide the backing stores shared by both stacks.
- `npm install && npm run dev` (inside `/backend`) — serve the Node baseline for regression comparisons.

## Coding Style & Naming Conventions
Target Java 17 with Google Java Style (4-space indent, UpperCamelCase types, lowerCamelCase members). Keep REST entry points in `controller`, business logic in `service`, persistence in `repository`, and reuse DTOs for transport contracts. Use constructor injection and Lombok consistently. Mirror Node route semantics, HTTP verbs, and field names unless the migration plan states otherwise. For legacy Node code touched during analysis, follow its ESLint/Prettier defaults and kebab-case filenames.

## Testing Guidelines
Spring tests rely on JUnit 5 plus embedded Mongo/Redis slices. Place new specs alongside their Java packages, suffixing classes with `*Test`. Cover API parity with MockMvc or WebTestClient assertions that mirror the Node responses. Add service-level tests for validation, error handling, and JWT flows. Keep tests deterministic by stubbing external API calls and verifying structured responses (see `dto.ApiResponse`).

## Commit & Pull Request Guidelines
Use conventional commits (`feat(room): port join endpoint`). Scope each commit to a self-contained migration step with passing tests and updated docs. Pull requests must describe the Node feature being ported, link tracking issues, list environment variables (`openai.api.key`, database URIs), and attach parity evidence (test output, curl diffs). Record schema changes or manual migration steps in `Migration.md` and reference them in the PR checklist.

## Security & Configuration Tips
Never commit secrets; supply credentials via environment variables or ignored `.env` files. Update `src/main/resources/application.properties` only with safe defaults, and note new configuration options in the PR. Ensure Redis session settings and JWT keys align with the Node baseline before shipping.
