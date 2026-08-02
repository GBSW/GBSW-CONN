# Repository Guidelines

## Project Structure & Module Organization

- `backend/` contains the Spring Boot API. Java sources live under `src/main/java/kr/hs/gbsw/communication`, grouped by feature (`auth`, `proposal`, `moderation`, `user`). Flyway migrations are in `src/main/resources/db/migration`; tests mirror the package structure under `src/test/java`.
- `frontend/` is a Next.js App Router application. Routes are in `app/`, feature components in `components/`, generated OpenAPI types and API helpers in `lib/`, static assets in `public/`, and Playwright flows in `e2e/`.
- `docs/` records architecture, permissions, UX, data, and security decisions. Root Compose files provide local and isolated E2E MySQL environments.

## Build, Test, and Development Commands

```bash
cp .env.example .env && docker compose up -d mysql
cd backend && set -a && source ../.env && set +a && ./gradlew bootRun
cd frontend && npm ci && npm run dev
```

Use `./gradlew test` for backend tests, `npm run check` for ESLint, TypeScript, and a production build, and `npm run e2e` for the full MySQL/Spring/Next/Chromium workflow. With the backend running, `npm run generate:api` refreshes `frontend/lib/api-schema.d.ts` from OpenAPI.

## Coding Style & Naming Conventions

`.editorconfig` requires UTF-8, LF endings, final newlines, two-space indentation, and four spaces for Java. Use `PascalCase` for Java types and React components, `camelCase` for methods and variables, and kebab-case TypeScript filenames. Name API records with `Request` or `Response` suffixes and migrations like `V8__describe_change.sql`.

Frontend work must follow `frontend/AGENTS.md` and `DESIGN.md`: prefer Astryx components and tokens, avoid hand-built layout elements and raw colors or spacing. Do not edit generated API types manually.

## Testing Guidelines

JUnit 5, Spring Boot Test, and Testcontainers validate backend behavior against MySQL; tests use `*Test.java`. Playwright scenarios use `*.spec.ts`. Add regression coverage for permission, session, migration, concurrency, and role-specific UI changes. There is no numeric coverage threshold, but all affected suites must pass.

## Commit & Pull Request Guidelines

History is intentionally concise (`First Setup`, `chore: initialize ...`). Prefer short imperative Conventional Commit messages, such as `feat: add proposal comments` or `fix: isolate admin scrolling`. Do not add AI/tool co-author or attribution trailers.

Follow the PR template: explain motivation, link issues, summarize implementation, list verification, and assess security/privacy impact. Include screenshots for UI changes and document migrations, OpenAPI updates, or operational consequences.

## Security & Configuration

Never commit `.env`, activation material, credentials, identity plaintext, or production logs. Enforce authorization in backend services, keep applied Flyway migrations immutable, and consult `SECURITY.md` and `docs/security-model.md` for sensitive changes.
