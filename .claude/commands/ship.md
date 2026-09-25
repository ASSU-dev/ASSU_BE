---
description: Run the full develop-based pipeline for a change - create a GitHub issue, branch off develop per convention, commit, and open a PR to develop.
argument-hint: "<type> <issue or change summary>"
allowed-tools: Bash(git status --short), Bash(git branch --show-current), Bash(git stash push -- *), Bash(git stash pop), Bash(git checkout develop), Bash(git pull origin develop --ff-only), Bash(git switch -c *), Bash(git diff --staged), Bash(git diff --stat), Bash(git add *), Bash(git commit -m *), Bash(git push -u origin *), Bash(gh issue create *), Bash(gh issue edit *), Bash(gh label list *), Bash(gh pr create *)
---

You are running this repository's full "issue → branch → commit → PR" pipeline for a single change, based on `develop`.

Use Korean for all created content (issue, commit message, PR body).

Input from the user:

```text
$ARGUMENTS
```

## Process

1. **Identify the type and issue template.**
   Allowed types: `feat`, `fix`, `refactor`, `chore`, `hotfix` (matches branch/commit convention types; `docs`/`style`/`test`/`merge` have no dedicated issue template — fall back to `fix.md`'s structure for those if needed).
   Map type → template:
   - `feat` → `.github/ISSUE_TEMPLATE/feature.md`, label `:sparkles: feature`
   - `fix` → `.github/ISSUE_TEMPLATE/fix.md`, label `:bug: bug`
   - `refactor` → `.github/ISSUE_TEMPLATE/refactor.md`, label `:recycle: refactor`
   - `chore` → `.github/ISSUE_TEMPLATE/chore.md`, label none unless one already fits
   - `hotfix` → `.github/ISSUE_TEMPLATE/hotfix.md`, label `:bug: bug`
   If the type is unclear from `$ARGUMENTS`, ask before proceeding.

2. **Create the GitHub issue.**
   - Write the issue body in Korean following the mapped template's structure (see the corresponding `<type>-issue` command for the exact section layout if one exists).
   - Create it with `gh issue create --title "[TYPE] ..." --label "<label>" --body "..."`.
   - Extract the issue number from the returned URL: `issue_number=$(basename "$issue_url")`.
   - Immediately rename the title to include the real number: `gh issue edit "$issue_number" --title "[TYPE/#$issue_number] ..."`. Never leave a literal `<number>` placeholder.

3. **Protect unrelated working-tree state before switching branches.**
   - Run `git status --short` and `git branch --show-current` first.
   - If there are uncommitted changes that belong to the current task (e.g. the fix already applied on the wrong branch), stash *only those specific paths* with `git stash push -m "<label>" -- <path...>` — never a bare `git stash` that could sweep up unrelated in-progress work (submodule pointer diffs, other WIP files). Leave anything unrelated untouched in the working tree.
   - Never run destructive commands (`git reset --hard`, `git clean -f`, `git checkout -- .`) as part of this flow.

4. **Branch off develop.**
   - `git checkout develop`
   - `git pull origin develop --ff-only` (never merge/rebase over local develop; if this fails, stop and report — do not force)
   - `git switch -c <type>/#<issue-number>-<brief-english-kebab-case-description>` following the repo's branch convention (see the `branch` command for the exact rules).
   - If a stash was created in step 3, `git stash pop` to bring the change back onto the new branch.

5. **Commit.**
   - Stage only the files relevant to this change — never `git add -A` or `git add .`. Review `git status --short` before committing.
   - Write the commit message following the repo's commit convention (see the `commit` command): `[TYPE/#issue-number] 한글 요약` subject, blank line, Korean bullet body.
   - Commit.

6. **Push and open the PR.**
   - `git push -u origin <branch-name>`
   - Draft the PR body in Korean following `.github/PULL_REQUEST_TEMPLATE.md` (see the `pr` command for the exact section layout), with `close #<issue-number>` filled in.
   - `gh pr create --base develop --head <branch-name> --title "[TYPE/#issue-number] 한글 요약" --body "..."`.
   - Report the final issue URL and PR URL to the user.

## Rules

- Base branch is always `develop` unless the user explicitly says otherwise.
- Every git/gh action in this flow is state-changing (issue creation, branch push, PR creation) — narrate each step briefly as you go so the user can interrupt if something looks wrong, but do not stop for confirmation between steps unless something is ambiguous or a command fails.
- If `git pull --ff-only` fails, or the working tree has unrelated conflicts, stop and explain rather than forcing past it.
- Before including any logs, error messages, or screenshots in the issue/PR body, mask sensitive information: passwords, API keys, tokens, cookies, Authorization headers, and personal data (email, phone, name) with `[MASKED]`.
- Do not bundle unrelated changes (submodule pointer bumps, stray untracked files) into the commit or PR just because they were sitting in the working tree.
