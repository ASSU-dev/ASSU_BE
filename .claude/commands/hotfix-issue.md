---
description: Create a GitHub hotfix issue body using this repository's hotfix issue template.
argument-hint: "<critical production issue summary>"
---

You are creating a hotfix issue for this repository.

Use Korean.

Use the repository template at `.github/ISSUE_TEMPLATE/hotfix.md`.

Input from the user:

```text
$ARGUMENTS
```

Output only the final issue content in Markdown.

Follow this exact structure:

```markdown
## 🚨 긴급 수정 내용
> 운영 환경에서 발생한 문제를 설명해주세요. (현상, 영향 범위, 발생 시점)

## ✅ 수정 목록
- [ ] 수정 항목 1
- [ ] 수정 항목 2

## 📝 영향 범위
> 해당 버그로 영향받는 기능, API, 사용자 역할을 명시해주세요.

## 📝 참고 사항
> 관련 로그, 알림, 스크린샷 등을 첨부해주세요.
```

Rules:

- Keep the title suggestion separate at the top as `title: [HOTFIX] ...`.
- GitHub issue titles in this repo follow `[TYPE/#issue-number] description`, matching the commit convention — but the issue number is unknown until after creation. After running `gh issue create`, immediately rename the title with `gh issue edit <number> --title "[HOTFIX/#<number>] ..."` so the number is never missing.
- Hotfix is reserved for critical production issues requiring immediate resolution.
- Clearly describe the impact scope and urgency.
- Include any relevant logs or error messages if available.
- If there is no reference/attachment, write `- 없음`.
- Before including any logs, error messages, or screenshots in the output, mask or remove sensitive information: passwords, API keys, tokens, cookies, Authorization headers, and personal data (email, phone, name). Replace with `[MASKED]`.
