---
description: Create or suggest a git branch name using this repository's branch convention.
argument-hint: "<type> #<issue-number> <brief Korean or English summary>"
allowed-tools: Bash(git status --short), Bash(git branch --show-current), Bash(git fetch origin develop), Bash(git worktree add *), Bash(git submodule update --init config)
---

You are creating or suggesting a git branch for this repository.

Use Korean.

Branch naming convention:

```text
<type>/#<issue-number>-<brief-english-description>
```

Examples:

```text
feat/#123-add-review-api
fix/#346-fix-null-pointer-login
refactor/#354-cleanup-partnership-period
hotfix/#390-fix-prod-token-expiry
docs/#12-update-claude-md
```

Input from the user:

```text
$ARGUMENTS
```

Process:

1. Identify the git commit type.
2. Identify the issue number.
3. Convert the feature/fix summary into short English kebab-case.
4. Check current branch and working tree before creating.
5. If the user only asks for a branch name, suggest the branch name only.
6. If the user explicitly asks to create the branch, create it as a worktree (never `git switch` in the main working tree — other sessions may be using it):
   `git fetch origin develop && git worktree add -b <branch-name> .claude/worktrees/<dir> origin/develop`, then `git submodule update --init config` inside it. `<dir>` is the branch name with `/#` replaced by `-`.

Allowed commit types:

```text
feat, fix, refactor, style, docs, test, chore, hotfix
```

Rules:

- Always include `#` before the issue number.
- Use lowercase English in the description.
- Use kebab-case (not spaces, underscores, or Korean).
- Keep the description short, usually 2-6 words.
- If the issue number is missing, ask for it before creating a branch.
- If the type is unclear, choose the most conservative type and explain briefly.
- Before creating a branch, run `git status --short` and `git branch --show-current`.
- If there are uncommitted changes, mention that they stay in the main working tree and are not carried into the new worktree.

Output when suggesting:

```text
추천 브랜치명: feat/#123-add-review-api
```

Output when creating:

```text
생성할 브랜치명: feat/#123-add-review-api
```

Then run:

```bash
git fetch origin develop
git worktree add -b feat/#123-add-review-api .claude/worktrees/feat-123-add-review-api origin/develop
git -C .claude/worktrees/feat-123-add-review-api submodule update --init config
```