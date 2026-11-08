---
name: create-issue
description: 'Create a new issue the GitHub-first way — first the GitHub issue (GitHub assigns the number), then the detailed write-up in docs/issues/NNN-*.md. Use when the user wants to create or file an issue, feature request or bug. Examples: "založ issue …", "/create-issue move SyntheticPdfSvg to src/test/java", "vytvoř issue na …", "create an issue for …".'
argument-hint: [short issue description]
allowed-tools: Read, Glob, Grep, Bash, AskUserQuestion, Write, Edit
user-invocable: true
---

# Create an issue (GitHub-first)

Create a new issue in the portunix-table-detector repository.

Communicate with the user in Czech. Issue titles, bodies and files stay in English.

**Argument:** $ARGUMENTS

## Principle

**GitHub is the single source of truth for the issue list** (status, assignee, labels). GitHub
assigns the number on creation. The GitHub issue holds the **basics** (title, short description,
link to the detail file). The details live in `docs/issues/NNN-*.md`, where `NNN` is the
GitHub issue number, zero-padded to three digits (`#1` → `001`).

**Do not add any overview tables** to a README — they cause merge conflicts.

## Instructions

### STEP 1: Gather the inputs

From the argument and context (or via `AskUserQuestion` if missing) determine:

- **Title** — short, descriptive, in English (no number; GitHub adds it)
- **Slug** — kebab-case for the file name, derived from the title (e.g.
  `move-synthetic-pdf-svg-to-test-sources`)
- **Type**: Feature | Enhancement | Bug Fix | Security | Refactoring | Documentation
- **Priority**: Critical | High | Medium | Low
- **Component** — `table-detector-api`, `table-detector-core`, `table-detector-pdf`, build/CI or
  the whole repository
- **Short description** (1–3 sentences) — goes into the GitHub issue body
- **Detailed content** — goes into the local `.md` (problem, proposal, acceptance criteria,
  tests)

Before writing, explore the relevant code so the issue names real files, classes and methods.
All texts in **English** (project convention).

### STEP 2: Preflight — check `gh`

```bash
gh --version && gh auth status
gh issue list --state all --search "<keywords>"
gh label list
```

If `gh` is not in `PATH`, try `"/c/Program Files/GitHub CLI/gh.exe"`. If not logged in, stop and
ask the user to run `gh auth login` (e.g. with the `!` prefix). If an existing issue already
covers the topic, show it and ask whether to continue.

### STEP 3: Create the GitHub issue (GitHub assigns the number)

Keep the body **minimal** — short description plus a link placeholder (completed in STEP 5).
Map the type to an existing label (`enhancement`, `bug`, `documentation`, ...):

```bash
REPO="cassandragargoyle/portunix-table-detector"
gh issue create --repo "$REPO" \
  --title "<TITLE>" \
  --body "<SHORT DESCRIPTION>

📄 Full details: docs/issues/<NNN>-<slug>.md" \
  [--label <type>]
```

`gh issue create` prints the URL — take the **number** from it (`.../issues/7` → `NNN=007`).
Use only labels that already exist on GitHub, never create them automatically.

### STEP 4: Write the detail file

Create `docs/issues/<NNN>-<slug>.md` with the full content, following
[MARKDOWN-STYLE.md](../../../docs/contributing/MARKDOWN-STYLE.md). Recommended skeleton:

```markdown
# Issue #<N>: <Title>

## Summary

<1–3 sentences>

**Status**: 📋 Open
**Priority**: <priority>
**Component**: <component>
**Created**: <YYYY-MM-DD>
**GitHub**: #<N>
**Related**: #<...>

## Problem Description

...

## Proposal

...

## Acceptance Criteria

1. `mvn clean verify` passes
1. ...

## Testing

...
```

Run `markdownlint-cli2` on the file if available.

### STEP 5: Put the exact link into the GitHub issue

Now that `NNN` and the file name are known, update the body with a working link (blob URL on
`main`):

```bash
URL="https://github.com/$REPO/blob/main/docs/issues/<NNN>-<slug>.md"
gh issue edit <N> --repo "$REPO" \
  --body "<SHORT DESCRIPTION>

📄 [Full details](${URL})"
```

> The link starts working once the file is on `main`. Until then it points to the target path.

The detail file is committed with the implementation (first commit of the feature branch) or
separately as `docs(<NNN>): Add issue write-up` — ask the user.

### STEP 6: Do not edit README tables

The issue list lives on GitHub. **No tables are edited.**

### STEP 7: Summary

Show the user:

```text
Issue #<N> created.

GitHub:  https://github.com/cassandragargoyle/portunix-table-detector/issues/<N>
Detail:  docs/issues/<NNN>-<slug>.md
Type/Priority: <type> / <priority>

Next steps:
  /implement-issue <N>
  # branch:  feature/<N>-<slug>
  # commit:  feat(<N>): <Title>   (closes via: Closes #<N>)
```

## Notes

- **Never invent the number** — GitHub assigns it. Issues and PRs share one counter, so numbers
  may skip.
- Archiving a finished issue: `gh issue close <N>` +
  `git mv docs/issues/<NNN>-*.md docs/issues/done/`.
- Full methodology: [docs/issues/README.md](../../../docs/issues/README.md).
- Do not add AI attribution to the issue body.
