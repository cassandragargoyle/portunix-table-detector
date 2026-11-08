---
name: bump-version
description: 'Bump the version of portunix-table-detector (patch/minor/major), keeping all four pom.xml files, plugin.json (version and binary) and the README dependency snippet in sync, optionally preparing the vX.Y.Z release tag. Use when the user wants to bump, raise or change the version or prepare a release (e.g. "zvedni verzi", "bump version", "nová patch verze", "připrav release").'
argument-hint: [patch | minor | major | X.Y.Z]
allowed-tools: Read, Edit, Grep, Glob, Bash, AskUserQuestion
user-invocable: true
---

# Bump version

Communicate with the user in Czech. Code and commit messages stay in English.

**IMPORTANT: NEVER add a "Co-Authored-By:" trailer or any AI attribution to commits or files.**

## Where the version lives

| File | Value |
| ----- | ----- |
| `pom.xml` | project `<version>X.Y.Z-SNAPSHOT</version>` |
| `table-detector-api/pom.xml` | `<parent>` `<version>X.Y.Z-SNAPSHOT</version>` |
| `table-detector-core/pom.xml` | `<parent>` `<version>X.Y.Z-SNAPSHOT</version>` |
| `table-detector-pdf/pom.xml` | `<parent>` `<version>X.Y.Z-SNAPSHOT</version>` |
| `plugin.json` | `"version": "X.Y.Z"` and `"binary": "table-detector-X.Y.Z.jar"` |
| `README.md` | Maven dependency snippet `<version>X.Y.Z</version>` (released version) |

Module dependencies inside the root `pom.xml` use `${project.version}`, do not touch them. The
`Makefile` reads the versions from `pom.xml` and `plugin.json`, it needs no change.

**`plugin.binary` must change together with `version`**, otherwise Portunix reports
`Unable to access jarfile ...` after deployment.

## STEP 1: Read the current version

```bash
grep -n -m1 '<version>' pom.xml
grep -n -A1 '<artifactId>table-detector-parent</artifactId>' */pom.xml | grep version
grep -n -e '"version"' -e '"binary"' plugin.json
grep -n -B2 '<version>' README.md | grep -A2 'table-detector'
git tag --sort=-v:refname | head -5
```

If the files are out of sync, report it before proposing anything.

## STEP 2: Propose the new version

Use the argument if given, otherwise ask:

- **patch** (default): `X.Y.Z` → `X.Y.(Z+1)` — bug fixes, small changes
- **minor**: `X.Y.Z` → `X.(Y+1).0` — new features, backward compatible
- **major**: `X.Y.Z` → `(X+1).0.0` — incompatible API changes in `table-detector-api`

Show the current and proposed version and the list of files that will change.

**STOP** — wait for the user's approval.

## STEP 3: Apply

Edit all files from the table above. Keep `-SNAPSHOT` in the POMs, never in `plugin.json`.
Update the README snippet only when the new version is going to be released (STEP 5).

If the repository contains `CHANGELOG.md`, ask whether to add a new section
`## [X.Y.W] - <today YYYY-MM-DD>` in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
format.

Verify:

```bash
mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout; echo
mvn clean verify
```

Commit on the current feature branch (or a `chore/bump-X.Y.Z` branch):

```text
chore: Bump version to X.Y.Z
```

## STEP 4: Consumers

After a release, the consumers must be bumped separately in their repositories:

- `text-extractor` in portunix-plugins — property `portunix.table-detector.version`
- `vim-parser-table` in portunix-vim

Only mention it, do not edit other repositories unless the user asks.

## STEP 5: Release (optional)

Ask whether to release. Release happens from `main` after the bump is merged:

```bash
git switch main && git pull --ff-only
git tag vX.Y.Z
git push origin vX.Y.Z
```

The `Publish` workflow strips `-SNAPSHOT`, fails if the tag does not match `pom.xml` and
`plugin.json`, deploys the Maven artifacts to GitHub Packages and creates the GitHub Release.

**STOP** — pushing a tag publishes artifacts, always ask for explicit confirmation first.

## STEP 6: Confirmation

```text
table-detector version bumped: X.Y.Z → X.Y.W
Updated: pom.xml (4x), plugin.json (version, binary), README.md
Build: passed
Release: tag vX.Y.W pushed / not yet
```
