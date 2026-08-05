# Assu Backend — Agent Guidelines

## Project Overview

Spring Boot 3.5.3 / Java 17 backend for Assu, a university partnership benefit platform for Soongsil University.
Application code lives under `src/main/java/com/assu/server`, grouped into `domain/`, `global/`, and `infra/`.

Domains: `admin`, `appreview`, `auth`, `backoffice`, `certification`, `chat`, `common`, `deviceToken`,
`inquiry`, `map`, `member`, `notification`, `partner`, `partnership`, `qr`, `report`, `review`, `store`, `student`, `suggestion`

Runtime configuration is in `src/main/resources` with profile files `application-local.yml`, `application-dev.yml`, `application-prod.yml`.

## Build, Test, and Development Commands

- `./gradlew build`: compile, test, and build the application jar.
- `./gradlew test`: run the full JUnit test suite.
- `./gradlew test --tests "com.assu.server.backoffice.BackofficeAuditAspectTest"`: run a single test class.
- `./gradlew bootRun --args='--spring.profiles.active=local'`: run the server locally.

## Coding Style & Conventions

Use Java 17 with 4-space indentation. Do not use wildcard imports.

- **DTO**: always use `record`. Class name follows `~~DTO`. Include `@Schema(description, example)` on every field.
- **Converter**: no separate Converter class. Put static factory methods directly in the DTO record.
- **Service**: annotate the implementation class with `@Transactional`. Use `readOnly = true` for read-only services.
- **Enum**: place domain-specific enums in `<domain>/entity/enums/` package.
- **Field naming**: names must reveal intent. e.g., `hashedPassword` not `password`.
- **No unused code**: remove all unused imports, log statements, methods, and files.
- **No unnecessary comments**: only add a comment when the WHY is non-obvious.
- **@BackofficeAudited**: apply only to CUD (Create/Update/Delete) handler methods in backoffice controllers. Not for GET.

## Commit Convention

```
[TYPE/#issue-number] 한글로 간결하게

- 작업 내용 상세 1
- 작업 내용 상세 2
```

Types: `FEAT`, `FIX`, `REFACTOR`, `STYLE`, `DOCS`, `TEST`, `CHORE`, `HOTFIX`, `MERGE`

Example:
```
[FEAT/#123] 리뷰 생성 API 추가

- ReviewController에 POST /reviews 엔드포인트 추가
- ReviewServiceImpl에 createReview 메서드 구현
```

## Branch Convention

```
<type>/#<issue-number>-<brief-english-description>
```

Example: `feat/#123-add-review-api`

## Pull Request Guidelines

Follow `.github/PULL_REQUEST_TEMPLATE.md`. Link the issue with `close #`, summarize what changed and why, and include reviewer notes for risks, test gaps, or deployment concerns.

## Agent-Specific Workflow

For every request, analyze before implementing:
1. Explain the relevant process and current structure.
2. Propose a direction and state key trade-offs.
3. Do NOT write or modify code unless the user explicitly requests with wording like "개발해줘", "작성해줘", "구현해줘", or an equivalent.
4. If the request is exploratory or ambiguous, provide analysis and recommendations only.
5. When implementing from an issue with multiple items, process one item at a time with full analysis before moving to the next.

## Security & Configuration

Never commit secrets, tokens, or credentials. Keep local configuration in `application-local.yml` or `application-secret.yml` (git-ignored). Be careful with deployment changes as the production environment runs on EC2.
