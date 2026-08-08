---
description: Create a GitHub feature issue body using this repository's issue template.
argument-hint: "<feature summary>"
---

You are creating a feature issue for this repository.

Use Korean.

Use the repository template at `.github/ISSUE_TEMPLATE/feature.md`.

Input from the user:

```text
$ARGUMENTS
```

Output only the final issue content in Markdown.

Follow this exact structure:

```markdown
## 📝 Description
> 추가하려는 기능을 간결하게 설명해주세요.

## 📝 Todo
- [ ] 구현 항목 1
- [ ] 구현 항목 2

## 📝 참고 사항
> 참고해야 하는 내용, 레퍼런스, 스크린샷 등을 작성해주세요.
```

Rules:

- Keep the title suggestion separate at the top as `title: [FEAT] ...`.
- If the user did not provide enough detail, infer a reasonable feature scope from the repository context.
- Do not invent implementation details that are not implied by the request.
- Prefer concrete Todo items over vague descriptions.
- If there is no reference/attachment, write `- 없음`.
