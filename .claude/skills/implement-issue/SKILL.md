---
name: implement-issue
description: 'Guide for implementing an issue — from reading the assignment through the claim check, creating a branch, implementing the code (with architecture docs next to the code) and mvn verify, up to committing. Use when the user wants to implement an issue, work on an issue from GitHub or docs/issues/, or process an issue according to the project methodology. Examples: "implement issue 1", "/implement-issue 1", "pusť se do issue #1", "udělej issue se SyntheticPdfSvg".'
argument-hint: [issue number or name]
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
user-invocable: true
---

# Implement an issue

@.claude/prompts/assistant-programming.md

<!-- The file above defines the base context (language, role). This file adds the workflow. -->

Argument: **$ARGUMENTS**

**IMPORTANT: NEVER add a "Generated with Claude Code" or "Co-Authored-By:" trailer (Claude or
any other AI agent) to code files, git commits or pull requests.**

## STEP 0: Role check

Check the current role in `CLAUDE.local.md` (line `@.claude/roles/<role>.md`). If the role is
not **Developer** (or `CLAUDE.local.md` sets no role):

1. Inform the user that implementation requires the Developer role
2. Offer to switch the role with `/role developer`
3. **STOP** — Do not continue until the Developer role is active

## STEP 1: Get the issue

If the argument contains an issue number or name, use it. Otherwise list the open issues and ask
for the number:

```bash
gh issue list --state open
```

## STEP 2: Claim the issue (status + assignee check)

Before doing any work, verify the issue is free to work on. **GitHub is the single source of
truth for status and assignee** (see [docs/issues/README.md](../../../docs/issues/README.md)).

The local detail file `docs/issues/NNN-*.md` is named after its GitHub issue number (zero-padded
to three digits). A small issue may have none and keep its whole description in the GitHub body.

Run the checks in order:

1. **Locate the file.** If the file lives in `docs/issues/done/`, the issue is already **done** →
   **STOP**. Tell the user it is archived as done and refuse to start work on it.

2. **Get the current GitHub user:**

   ```bash
   gh api user --jq .login
   ```

   If `gh` is not installed or not authenticated, **STOP**: the claim check cannot run without
   `gh`; ask the user to install/authenticate `gh` or to explicitly confirm skipping the check.

3. **Read the issue state and assignees:**

   ```bash
   gh issue view N --repo cassandragargoyle/portunix-table-detector \
     --json number,title,state,assignees,labels,body
   ```

4. **Status check.** If `state` is `CLOSED` → **STOP**, the issue is already closed on GitHub.

5. **Assignee check:**
   - assigned to **someone other than the current user** → **STOP**. Report who has it and ask
     that person to **release the issue** (unassign themselves) first
   - assigned to **the current user** already → continue
   - **unassigned** → claim it:

     ```bash
     gh issue edit N --repo cassandragargoyle/portunix-table-detector --add-assignee @me
     ```

     Confirm the assignment succeeded before continuing.

Only once the issue is not done and is assigned to the current user, continue to STEP 3.

## STEP 3: Confirm understanding

1. Read the detail file from `docs/issues/` (if any) and the GitHub issue body
2. Explore the affected code
3. Restate the issue in your own words
4. Explain the required code changes (files, classes, tests, docs)
5. Ask for confirmation

**STOP** — Wait for explicit confirmation before continuing.

## STEP 4: Create a branch

Start from an up-to-date `main` and create `feature/<N>-<short-description>` or
`fix/<N>-<short-description>`:

```bash
git switch main && git pull --ff-only
git switch -c feature/1-move-synthetic-pdf-svg
```

- Lowercase letters and hyphens for the description
- Show the git command before running it

## STEP 5: Implementation

- Make all code changes in a single continuous phase
- Do NOT ask questions unless it is genuinely ambiguous
- Modify only the relevant files
- Follow the project's coding style (tabs, Allman braces, see existing sources), prefer minimal
  diffs
- Add tests for new functionality (JUnit), see
  [JAVA-TESTING.md](../../../docs/contributing/JAVA-TESTING.md) and
  [JAVA-TESTING-BASICS.md](../../../docs/contributing/JAVA-TESTING-BASICS.md)
- Code comments in English, not sentences (no period at the end)
- CLI changes follow GNU/POSIX conventions with Picocli (options before positional arguments,
  short and long variants, `--help` and `--version`); keep `commands[].options` in `plugin.json`
  in sync
- If the public API of `table-detector-api` changes, point out the consumers (`text-extractor`
  in portunix-plugins, `vim-parser-table` in portunix-vim) in the summary

### Architecture documentation next to the code

Each source file has an architecture document next to it with the SAME base name and a `.md`
extension, e.g.

- `table-detector-pdf/src/main/java/org/cassandragargoyle/portunix/tabledetector/pdf/PdfTableDetector.java`
  → `.../pdf/PdfTableDetector.md`
- `table-detector-core/src/main/java/org/cassandragargoyle/portunix/tabledetector/Main.java`
  → `.../tabledetector/Main.md`

Rules:

- The document MUST start with a frontmatter block warning that only the code is the source of
  truth and that this file is meant for humans, e.g.:

  ```markdown
  ---
  warning: Only the code is the source of truth. This document is for humans and may lag behind the code.
  ---
  ```

- The document MUST contain Mermaid diagrams describing the structure and flow (e.g. class or
  package diagram and a sequence or flow diagram of the main path)
- Whenever you change a source file, update its matching `.md` document in the SAME commit
- If the source file has no matching `.md` document yet, create one as part of the change
- Follow [MARKDOWN-STYLE.md](../../../docs/contributing/MARKDOWN-STYLE.md) and run
  `markdownlint-cli2` on changed `.md` files if available
- Do NOT create architecture docs for generated code or build output (`target/`, `dist/`)

### Verify

```bash
mvn clean verify
```

If the build or tests fail, fix them before continuing; report failures honestly with the output.

At the end, provide a summary:

- Modified files
- Added methods/classes
- Removed code
- Key decisions
- Build and test result
- Rollback steps (see the Developer role)

Ask what the user wants:

- a) Accept as is
- b) Adjust specific parts
- c) Redo completely

**STOP** — Wait for approval before continuing.

## STEP 6: Commit

Prepare the commit message:

```text
<type>(<N>): <Short summary>

- key change 1
- key change 2
```

Allowed types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`. The summary starts with a
capital letter, as in the existing history (e.g. `fix(239): Use non-deprecated ...`).

Do not add a "Generated with Claude Code" or "Co-Authored-By:" trailer to the commit.

**STOP** — Ask the user to confirm the commit message before committing.

---

## Next steps

After the implementation is committed, use `/finish-branch` to:

- Open a Pull Request or merge the branch into `main`
- Delete the feature branch
- Update the issue status to ✅ Implemented, archive it into `docs/issues/done/` and close the
  GitHub issue
