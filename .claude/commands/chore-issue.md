---
description: Create a GitHub chore issue body using this repository's chore issue template.
argument-hint: "<chore task summary>"
---

You are creating a chore issue for this repository.

Use Korean.

Use the repository template at `.github/ISSUE_TEMPLATE/chore.md`.

Input from the user:

```text
$ARGUMENTS
```

Output only the final issue content in Markdown.

Follow this exact structure:

```markdown
## 🔩 작업 내용
> 수행할 작업을 설명해주세요. (의존성 업데이트, 빌드 설정, CI/CD 변경 등)

## ✅ 작업 목록
- [ ] 작업 항목 1
- [ ] 작업 항목 2

## 📝 참고 사항
> 관련 문서, 참고 자료 등을 작성해주세요.
```

Rules:

- Keep the title suggestion separate at the top as `title: [CHORE] ...`.
- GitHub issue titles in this repo follow `[TYPE/#issue-number] description`, matching the commit convention — but the issue number is unknown until after creation. `gh issue create` prints the created issue's URL (e.g. `https://github.com/OWNER/REPO/issues/416`); extract the trailing number from that URL (e.g. `issue_number=$(basename "$issue_url")`) and immediately run `gh issue edit "$issue_number" --title "[CHORE/#$issue_number] ..."` so the number is never missing. Do not treat `<number>` as literal text — it must be the real number captured from the command output.
- Chore covers: dependency updates, build/CI configuration, tooling, environment setup.
- Do not include feature or bug fix work in chore scope.
- If there is no reference/attachment, write `- 없음`.
