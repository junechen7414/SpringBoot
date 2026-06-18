# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Authoritative docs

Detailed guidance already lives in **`AGENTS.md`**, which `@`-imports the modular docs under **`docs/agents/`** (overview, setup, git workflow, branch cleanup, code standards, architecture, dependency-check, testing, monitoring, troubleshooting). Read those for depth; this file is the fast-start summary. When project conventions change, keep `AGENTS.md`, `docs/agents/*`, and `.github/instructions/Global.instructions.md` in sync (the code-standards doc requires it).

## Conventions that affect every task

- **Respond in Traditional Chinese** (繁體中文); keep technical terms in English.
- **Containers: use `podman`, not `docker`.** All compose/build commands assume `podman compose`.
- **Detect the shell before running CLI commands.** PowerShell chains with `;` and calls `./gradlew`; CMD chains with `&&` and calls `gradlew`. Don't mix syntaxes. (This session's shell is Git Bash on Windows — use `./gradlew`.)
- Single-module Gradle project (`settings.gradle` defines one project). Java 21 toolchain. Base package `com.ibm.demo`.

## Common commands

Build / run (from repo root):

```bash
./gradlew build                       # compile + test + assemble
./gradlew bootRun                     # run app (needs Oracle DB reachable)
./gradlew generateOpenApiDocs         # -> build/docs/swagger.json (uses 'openapi' profile)
podman compose up -d                  # app + Oracle + Alloy + Prometheus + Grafana
podman compose up oracle-db -d        # DB only, then run DemoApplication.java from IDE
```

App listens on **http://localhost:8787**. Requires a `.env` with `ORACLE_DEV_USERNAME` / `ORACLE_DEV_PASSWORD` (see `.env.example`).

Tests:

```bash
./gradlew test                                              # full suite (integration tests start Oracle via Testcontainers)
./gradlew test -Djunit.platform.exclude.tags=SanityTest     # exclude a JUnit tag (see build.gradle wiring)
./gradlew test --tests "*IntegrationTest"                   # only integration tests
./gradlew test --tests "com.ibm.demo.order.OrderServiceTest"  # single test class
```

Notes:
- Integration tests extend `BaseIntegrationTest` (auto-starts a `gvenzl/oracle-free` container via `@ServiceConnection`, profile `integration-test`); first run downloads the image and startup can take minutes.
- `test` task forces `maxParallelForks = 1` to avoid Oracle container contention — don't expect parallel test execution.
- Tag filtering is driven by the `junit.platform.exclude.tags` system property (wired in `build.gradle`).

## Architecture big picture

Layered, feature-sliced by domain. The three domains — **account**, **product**, **order** — each contain their own Controller / Service / Repository / Entity / DTO under `com.ibm.demo.<domain>`. Cross-cutting concerns live in dedicated packages.

Request flow: `Client → Controller → Service → Repository → Entity`, with shared infrastructure in `util/`.

- **Cross-module calls go through `*Client` classes** (e.g. `AccountClient`, `ProductClient`, `OrderClient`) backed by Spring `RestClient` (configured in `config/RestClientConfig.java`), **not** by calling another domain's service directly. This is what lets the app treat modules as if they were separate services.
- **`OrderService` vs `OrderTransactionalService`**: order creation spans account + product; the transactional service isolates the DB transaction boundary from orchestration/remote-call logic. Preserve this split when editing order flows.
- **Soft delete + auditing** are centralized: entities extend `util/BaseEntity` (audit fields, `@Version` optimistic locking), repositories that need soft delete extend `util/SoftDeleteRepository`, and Hibernate `@SQLRestriction` filters out deleted/inactive rows at the query level. Don't hand-roll `deleted = false` filters. (`BaseEntity` is marked `@Deprecated(forRemoval=true)` — check before extending it for new entities.)
- **Pagination is uniform**: list endpoints accept `Pageable` and return `util/PageResponse<T>` (default `page=0, size=20`). There are intentionally no non-paged list endpoints.
- **Errors**: domain logic throws subclasses of `exception/BusinessLogicCheck/BusinessException`; `GlobalExceptionHandler` (`@RestControllerAdvice`) maps them to `ApiErrorResponse` using `util/ErrorCode`. Add new failure modes as `BusinessException` subclasses, not ad-hoc responses.
- **Resilience4j** (`config/Resilience4jConfig.java`, `application.yml`) provides Bulkhead (fail-fast, `max-wait-duration: 0`), CircuitBreaker, and RateLimiter via annotations on services. Config keys live under `resilience4j.*`.
- **Observability**: app exports metrics over OTLP (Micrometer) → Grafana Alloy → Prometheus → Grafana. Relevant infra files: `docker-compose.yml`, `config.alloy`, `prometheus.yml`.
- **DB migrations**: Flyway, scripts in `src/main/resources/db/migration` (Oracle). H2 is used for tests and OpenAPI doc generation.

## Profiles

Config is layered (env/system props > `application-{profile}.yml` > `application.yml`). Profiles: `dev`, `unit-test`, `integration-test`, `e2e`, `openapi`. Test resources hold the `-unit-test`, `-integration-test`, and `-e2e` variants.

## Git workflow (summary)

**Trunk-based, solo project.** Default to committing small steps **directly on `main`** and pushing — CI (`.github/workflows/image-publish.yml`) runs the test gate on both push-to-`main` and PRs, so a PR is not needed just to trigger tests. `main` has no branch protection; if it breaks, fix-forward or `git revert`.

- **Before pushing, tests must pass.** A pre-push hook (`.githooks/pre-push`, enable once with `git config core.hooksPath .githooks`) runs the same command as the CI gate (`./gradlew test -Djunit.platform.exclude.tags=SanityTest`) whenever the push includes `main` — "green locally = green in CI".
- **Pushing `main` has side effects**: it publishes the `latest` image to ghcr.io, triggers downstream E2E, and regenerates swagger.json. The test gate runs before the image build, so test-caught failures don't ship a bad image.
- **Open a branch + PR only for high-risk changes**: CI workflow edits, DB migrations, cross-domain refactors / large features — anything you want CI to validate before it reaches `main`.
- **When branching**, use `feature/ fix/ hotfix/ refactor/ config/ docs/ test/ chore/` prefixes (lowercase, `-` separated), branch off the latest `main`, keep it short-lived. Commits follow Conventional Commits (`type(scope): subject`, imperative, lowercase, no trailing period).

Full rules, the pre-push hook setup, PR labels, and cleanup steps are in `docs/agents/03-git-workflow.md` and `04-git-branch-cleanup.md`.
