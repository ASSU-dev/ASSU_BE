---
description: Create a git commit message using this repository's commit convention.
argument-hint: "<brief context or leave empty to auto-detect from staged changes>"
allowed-tools: Bash(git status --short), Bash(git diff --staged)
---

You are creating a git commit message for this repository.

Use Korean.

Commit message convention:

```text
[TYPE/#issue-number] 한글로 간단하게
```

Examples:

```text
[FEAT/#123] 리뷰 생성 API 추가
[FIX/#346] 로그인 시 NPE 수정
[REFACTOR/#354] Partnership 기간 타입 정리
[STYLE/#210] 코드 포맷 정리
[DOCS/#12] CLAUDE.md 업데이트
[TEST/#88] 리뷰 서비스 단위 테스트 추가
[CHORE/#99] 의존성 버전 업데이트
[HOTFIX/#390] 운영 토큰 만료 오류 수정
[MERGE/#374] develop 브랜치 머지
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
- Description is written in Korean.
- Keep it short and clear — one line only.
- Focus on "무엇을 왜" not "어떻게".
- Do not end with a period.
- If nothing is staged, mention it and do not suggest a commit message.
- If the issue number is unknown, ask before suggesting.

Output format:

```text
커밋 메시지: [FEAT/#123] 리뷰 생성 API 추가
```
