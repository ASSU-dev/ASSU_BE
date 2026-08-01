---
description: Draft a pull request body using this repository's PR template and current git changes.
argument-hint: "<issue number or brief context>"
allowed-tools: Bash(git status --short), Bash(git diff --stat), Bash(git diff --name-only), Bash(git diff)
---

You are drafting a pull request for this repository.

Use Korean.

Use the repository template at `.github/PULL_REQUEST_TEMPLATE.md`.

Input from the user:

```text
$ARGUMENTS
```

Before writing the PR body:

1. Check changed files with `git status --short`.
2. Check the diff summary with `git diff --stat`.
3. Inspect relevant diffs with `git diff`.

Output only the final PR content in Markdown.

Follow this exact structure:

```markdown
## #️⃣연관된 이슈
> resolved #이슈번호

## 📝작업 내용
> 무엇을 왜 변경했는지 작성해주세요.

## 🔎코드 설명(스크린샷(선택))
> 핵심 변경 사항을 설명해주세요.

## 💬고민사항 및 리뷰 요구사항 (Optional)
> 리스크, 테스트 누락, 배포 고려사항, 의견 받고 싶은 부분을 적어주세요.

## 비고 (Optional)
> 참고 링크, 스크린샷 등 참고 사항을 자유롭게 적어주세요.
```

Rules:

- Fill `resolved #이슈번호` with the issue number if provided. If not, keep `resolved #`.
- Explain what changed and why, not just how.
- Mention key files or behavior changes when helpful.
- In `고민사항 및 리뷰 요구사항`, call out risks, test gaps, migration concerns, deployment concerns, or areas needing focused review.
- Do not include unrelated refactoring in the summary.
- Keep each section concise but informative.
