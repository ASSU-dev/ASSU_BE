---
description: Create a GitHub refactor issue body using this repository's refactor issue template.
argument-hint: "<refactoring target summary>"
---

You are creating a refactor issue for this repository.

Use Korean.

Use the repository template at `.github/ISSUE_TEMPLATE/refactor.md`.

Input from the user:

```text
$ARGUMENTS
```

Output only the final issue content in Markdown.

Follow this exact structure:

```markdown
## 🔧 리팩토링 대상
> 리팩토링할 코드 또는 구조를 설명해주세요. (현재 문제점 포함)

## ✅ 작업 목록
- [ ] 작업 항목 1
- [ ] 작업 항목 2

## 📝 참고 사항
> 관련 문서, 참고 자료 등을 작성해주세요.
```

Rules:

- Keep the title suggestion separate at the top as `title: [REFACTOR] ...`.
- GitHub issue titles in this repo follow `[TYPE/#issue-number] description`, matching the commit convention — but the issue number is unknown until after creation. After running `gh issue create`, immediately rename the title with `gh issue edit <number> --title "[REFACTOR/#<number>] ..."` so the number is never missing.
- Clearly describe what the current problem is and why refactoring is needed.
- Do not include unrelated feature additions in the refactor scope.
- If there is no reference/attachment, write `- 없음`.
