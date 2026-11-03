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

3. **Never switch branches in the main working tree.** Other sessions may be working there. Do not run `git checkout`/`git switch`/`git stash` in it, and never run destructive commands (`git reset --hard`, `git clean -f`, `git checkout -- .`).
   - If the change was already applied in the main working tree, save it as a patch of *only those paths* (`git diff -- <path...> > <scratch>/change.patch`) to apply in the worktree in step 4. Leave the main working tree untouched.

4. **Create a worktree off develop.**
   - Branch name: `<type>/#<issue-number>-<brief-english-kebab-case-description>` (see the `branch` command for the exact rules). Worktree dir: `.claude/worktrees/<branch name with "/#" replaced by "-">`.
   - `git fetch origin develop`
   - `git worktree add -b <branch-name> .claude/worktrees/<dir> origin/develop`
   - In the worktree: `git submodule update --init config` (needed for build).
   - If a patch was saved in step 3, `git apply <scratch>/change.patch` inside the worktree. If it fails, stop and report.
   - Run steps 5–6 from inside the worktree directory.

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
