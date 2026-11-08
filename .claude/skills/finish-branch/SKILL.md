---
name: finish-branch
description: 'Finish work on a feature branch — README, plugin.json and version check, mvn verify, Pull Request or direct merge into main, branch clean-up and optional issue status update. Use when the user wants to finish, merge or close a feature branch. Examples: "finish the branch", "/finish-branch", "merge the feature branch into main", "dokonči branch".'
allowed-tools: Read, Glob, Grep, Bash, AskUserQuestion, Edit
user-invocable: true
---

# Finish a feature branch

Finish work on a feature branch — README check, merge into the main branch, branch clean-up and
optional issue status update.

@.claude/prompts/assistant-programming.md

<!-- This file is authoritative. SKILL.cs.md is a Czech translation for humans. -->

**IMPORTANT: NEVER add a "Generated with Claude Code" or "Co-Authored-By:" trailer (Claude or
any other AI agent) to code files, git commits or pull request descriptions.**

## Instructions

### STEP 1: Establish the context

Ask for (or take from the current state):

1. The name of the feature branch (default: the current branch)
2. The target branch for the merge (default: `main`)

Verify that the branch exists, contains commits and the working tree is clean:

```bash
git status --short
git log --oneline <target-branch>..HEAD
```

Take the issue number `N` from the branch name (`feature/<N>-...`) or the commit scopes. If there
are uncommitted changes, **STOP** and ask whether to commit them first.

### STEP 2: Update the README and plugin.json

Before merging the branch, verify that the documentation matches the state after the change:

```bash
git diff --name-only <target-branch>...HEAD
```

1. **`README.md`**:
   - new or changed CLI options of `detect` are in the usage section and in the examples,
   - the list of supported formats, backends and features matches reality,
   - notes about dependencies (GitHub Packages token) and limitations are not outdated,
   - the Maven dependency snippet shows the latest released version.
2. **`plugin.json`**:
   - `commands[].options` match the Picocli options in `table-detector-core`,
   - `description` and `ai_integration` say what the plugin does today.
3. **Versions in sync** — all four `pom.xml` files (`X.Y.Z-SNAPSHOT`) and `plugin.json`
   (`version` = `X.Y.Z`, `plugin.binary` = `table-detector-X.Y.Z.jar`):

   ```bash
   grep -n -m1 '<version>' pom.xml
   grep -n -A1 '<artifactId>table-detector-parent</artifactId>' */pom.xml | grep version
   grep -n -e '"version"' -e '"binary"' plugin.json
   ```

4. **Architecture docs** — every changed source file has its matching `.md` updated in the
   branch.

If something does not match, fix it and commit it to the feature branch before the merge:

```text
docs(<N>): Update README and plugin.json
```

If everything matches, skip the step and say so in the summary.

### STEP 3: Verify the build

```bash
mvn clean verify
```

If it fails, **STOP** and report the output.

### STEP 4: Merge and clean-up

> **What is a PR (Pull Request)?** A request to integrate changes from a feature branch into the
> main branch. Used for code review in teams and runs the `Build` workflow. For solo development a
> direct merge is possible.

Ask the user for the preferred procedure.

#### Option A: Pull Request (project default, see `AGENTS.md`)

Prepare the PR description:

- **Summary**: What this PR does
- **Motivation**: Why (link to the issue)
- **Changes**: List of changes
- **Testing**: Steps to verify

```bash
git push -u origin <branch>
gh pr create --base <target-branch> --title "<type>(<N>): <Summary>" --body "$(cat <<'EOF'
## Summary

...

## Motivation

Closes #<N>

## Changes

- ...

## Testing

- `mvn clean verify`
EOF
)"
```

`Closes #<N>` closes the GitHub issue on merge. Merge only when the user asks
(`gh pr merge <PR> --squash --delete-branch` or the method they choose), then:

```bash
git switch <target-branch> && git pull --ff-only
git branch -d <branch>
```

#### Option B: Direct merge (solo development)

```bash
git switch <target-branch>
git pull --ff-only
git merge <branch>
git push origin <target-branch>
git branch -d <branch>
git push origin --delete <branch>   # only if the branch was pushed
```

**STOP** - Wait for confirmation before merging and before pushing to `main`.

### STEP 5: Issue update (optional)

Ask the user: "Is the issue completely finished by this?"

#### If YES

If a detail file exists in `docs/issues/`, edit it:

- Change `**Status**: 📋 Open` to `**Status**: ✅ Implemented`
- Add the closing date (`**Closed**: YYYY-MM-DD`)

Move the file to the archive and close the GitHub issue (unless the PR already closed it) —
there are no README tables, the list lives on GitHub:

```bash
git mv docs/issues/<NNN>-*.md docs/issues/done/
gh issue close <N>
```

Commit the issue status change (on the feature branch before the merge, or on `main` after it):

```text
docs(<N>): Close issue - implementation complete
```

#### If NO

Skip the status update. The issue stays open for further work.

### STEP 6: Summary

Show a summary:

- README / plugin.json updated: yes/no (not needed)
- Build: passed
- Merge performed into branch: `<name>` (PR URL or direct merge)
- Feature branch deleted: yes/no
- Issue status updated: yes/no/skipped
- Next steps (if any, e.g. `/bump-version` and a release tag)
