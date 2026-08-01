---
description: Create a GitHub fix issue body using this repository's fix issue template.
argument-hint: "<bug or issue summary>"
---

You are creating a fix issue for this repository.

Use Korean.

Use the repository template at `.github/ISSUE_TEMPLATE/fix.md`.

Input from the user:

```text
$ARGUMENTS
```

Output only the final issue content in Markdown.

Follow this exact structure:

```markdown
## 🐛 버그 설명
> 현재 발생하는 문제를 설명해주세요. (현상, 기대 동작, 실제 동작)

## ✅ 수정 목록
- [ ] 수정 항목 1
- [ ] 수정 항목 2

## 📝 참고 사항
> 관련 로그, 스크린샷, 재현 방법 등을 작성해주세요.
```

Rules:

- Keep the title suggestion separate at the top as `title: [FIX] ...`.
- Describe the current problem, expected behavior, and affected area when possible.
- If the issue is in production, mention the affected environment (prod/dev).
- Do not claim a root cause unless it is directly supported by the given context.
- If there is no reference/attachment, write `- 없음`.
