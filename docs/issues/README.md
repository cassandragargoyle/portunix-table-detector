# Issues Documentation & Tracking

## Overview

This directory contains detailed write-ups for issues, feature requests and development planning
for portunix-table-detector.

## No overview table here

There is **no overview table** in this file on purpose. A shared Markdown table edited by
everyone is a constant source of merge conflicts.

The **canonical issue list lives on GitHub** — the single source of truth for status, assignee
and labels:

- **Open / active issues** → <https://github.com/cassandragargoyle/portunix-table-detector/issues>
- **Closed / done issues** →
  <https://github.com/cassandragargoyle/portunix-table-detector/issues?q=is%3Aissue+is%3Aclosed>

This directory keeps the **long-form write-up** for each issue as `NNN-name.md`, where `NNN` is
the **GitHub issue number** zero-padded to three digits (`#7` → `007`). Active issues sit
directly in this directory, archived ones in [`done/`](done/README.md).

A write-up is optional for small issues whose GitHub body already holds the whole description.

## Numbering

Issues are **GitHub-first**: the number is assigned by GitHub when the issue is created, and the
local file adopts that exact number. GitHub issues and pull requests share one counter, so
numbers may skip where a PR consumed one.

Issues of the original `plugins/table-detector` (e.g. `#239`) live in
[portunix-plugins](https://github.com/cassandragargoyle/portunix-plugins/issues).

## Creating a new issue

Use the `create-issue` skill (`/create-issue`), or do it by hand:

1. **Create the GitHub issue first** — GitHub assigns the number `N`:

   ```bash
   gh issue create --repo cassandragargoyle/portunix-table-detector \
     --title "Short descriptive title" \
     --body "Basic description. Full details: docs/issues/NNN-name.md"
   ```

   Keep the GitHub body minimal — a one-line summary plus a link to the detail file.

1. **Write the detail file** `docs/issues/NNN-name.md` with the full context, acceptance
   criteria and implementation notes.

1. **Reference the number** in branches and commits: `feature/N-name`, `feat(N): …`,
   `Closes #N`.

No table to update — GitHub is the list of record.

## Archiving a completed issue

When an issue reaches a terminal status (✅ Implemented, ❌ Closed, ➡️ Moved):

1. Set `**Status**` in the write-up and add the closing date.
1. Close the GitHub issue: `gh issue close N` (or let `Closes #N` in the PR do it).
1. Move the file: `git mv docs/issues/NNN-name.md docs/issues/done/NNN-name.md`.

The `finish-branch` skill (`/finish-branch`) performs these steps.
