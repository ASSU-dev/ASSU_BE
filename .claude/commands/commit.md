---
description: Create a git commit message using this repository's commit convention.
argument-hint: "<brief context or leave empty to auto-detect from staged changes>"
allowed-tools: Bash(git status --short), Bash(git diff --staged)
---

You are creating a git commit message for this repository.

Use Korean.

Commit message convention:

```text
[TYPE/#issue-number] 한글로 간결하게

- 작업 내용 상세 1
- 작업 내용 상세 2
```

Examples:

```text
[FEAT/#123] 리뷰 생성 API 추가

- ReviewController에 POST /reviews 엔드포인트 추가
- ReviewServiceImpl에 createReview 메서드 구현
- 리뷰 이미지 S3 업로드 로직 연동
```

```text
[FIX/#346] 로그인 시 NPE 수정

- JwtAuthFilter에서 null 토큰에 대한 방어 처리 추가
```

```text
[REFACTOR/#354] Partnership 기간 타입 정리

- Period 타입을 LocalDate에서 LocalDateTime으로 변경
- 관련 DTO 및 서비스 로직 일괄 수정
```

```text
[CHORE/#99] 의존성 버전 업데이트

- Spring Boot 3.5.2 → 3.5.3 업그레이드
- springdoc-openapi 2.6.0 → 2.7.0 업그레이드
```

Input from the user:

```text
$ARGUMENTS
```

Process:

1. Run `git status --short` to check staged files.
2. Run `git diff --staged` to inspect actual changes.
3. Identify the appropriate commit type from the changes.
4. Identify the issue number from context or ask if unclear.
5. Write a short Korean description summarizing what changed and why.

Allowed commit types:

```text
FEAT, FIX, REFACTOR, STYLE, DOCS, TEST, CHORE, HOTFIX, MERGE
```

Rules:

- TYPE is uppercase.
- Subject line is written in Korean, short and clear.
- Body lists specific changes as bullet points (Korean).
- Focus on "무엇을 왜" not "어떻게".
- Do not end lines with a period.
- Blank line between subject and body is mandatory.
- Body should have at least 1 bullet point; add more if multiple files or features changed.
- If nothing is staged, mention it and do not suggest a commit message.
- If the issue number is unknown, ask before suggesting.

Output format:

```text
커밋 메시지:
[FEAT/#123] 리뷰 생성 API 추가

- ReviewController에 POST /reviews 엔드포인트 추가
- ReviewServiceImpl에 createReview 메서드 구현
```
