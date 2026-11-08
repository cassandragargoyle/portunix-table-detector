# Portunix Table Detector Development Instructions

Instructions for AI coding agents (Claude Code, Codex, Copilot, ...) and contributors.
`CLAUDE.md` only imports this file. Contribution guidelines live in `docs/contributing/`.

## Project Information

- **Repository**: <https://github.com/cassandragargoyle/portunix-table-detector/> (GitHub — single
  source of truth for development and publication)
- **Project Name**: portunix-table-detector
- **Purpose**: table detection library (Maven artifacts) and standalone Portunix plugin
- **Primary Language**: Java 21 (multi-module Maven project, PF4J backends)
- **Platforms**: Windows, Linux (cross-platform)
- **Origin**: moved from `plugins/table-detector` in
  [portunix-plugins](https://github.com/cassandragargoyle/portunix-plugins) (issue #239); the
  earlier git history stays there

## Repository Layout

- `table-detector-api/` — `TableDetectorPlugin` extension point and shared models
- `table-detector-core/` — Picocli CLI (`Main`), `TableDetectorManager`, standalone JAR
- `table-detector-pdf/` — PDF backend (Apache PDFBox)
- `samples/` — sample documents
- `plugin.json` — Portunix plugin manifest

## Coding Guidelines & Development Instructions

### Strict Policies

- Code, code comments, and user messages must be in English
- If implementation of a request could lead to loss of implemented code (e.g., when manipulating
  git), highlight this prominently and always require confirmation
- Do not put "Generated with [Claude Code](https://claude.ai/code)" or any AI attribution into
  code or other files
- **Never add a `Co-Authored-By:` trailer** (Claude or any other AI agent) or similar attribution
  lines to git commit messages or pull request descriptions

### General Policies

- Follow existing conventions and styles in the project
- Always explore existing code first before creating new code
- Prefer editing existing files over creating new ones
- All texts in programs (messages, labels, constants) are in English
- Comments are in English and are not sentences (no period at the end)
- Each source file has an architecture document next to it with the same base name and a `.md`
  extension (frontmatter warning that only the code is the source of truth, Mermaid diagrams);
  update it in the same commit as the code

### Markdown

- Before writing or generating any `.md` file, read and follow
  [docs/contributing/MARKDOWN-STYLE.md](docs/contributing/MARKDOWN-STYLE.md)

### CLI Conventions

- **GNU/POSIX style**: Options BEFORE positional arguments
- Use short (`-o`) and long (`--output`) option variants
- Boolean flags don't require values: `--verbose`, `--dry-run`
- All CLI commands must support `--help` and `--version` flags
- Java CLIs use Picocli

### Build

```bash
mvn clean verify   # build and test all modules
make dist          # Portunix distribution package in dist/
```

`org-cassandragargoyle-api` is resolved from GitHub Packages, which needs a token with
`read:packages` in `~/.m2/settings.xml` (server id `github`), see [README.md](README.md).

### Versioning and Release

- The version lives in `pom.xml` (all four POMs, `-SNAPSHOT` during development) and in
  `plugin.json` (without `-SNAPSHOT`); keep them in sync
- Consumers: `text-extractor` in portunix-plugins and `vim-parser-table` in portunix-vim depend
  on `table-detector-{api,core,pdf}` by version
- Release: push a tag `vX.Y.Z` matching `pom.xml` and `plugin.json`; `publish.yml` deploys the
  Maven artifacts to GitHub Packages and attaches the plugin JAR and `plugin.json` to the GitHub
  Release

### Issue Tracking

- **GitHub is the single source of truth** for the issue list (status, assignee, labels)
- Before creating new features, check existing issues on GitHub (`gh issue list`)
- Long-form write-ups live in `docs/issues/NNN-name.md` (`NNN` = GitHub issue number), archived
  ones in `docs/issues/done/`, see [docs/issues/README.md](docs/issues/README.md)

### AI Assistant Skills and Roles

- Skills in `.claude/skills/`: `create-issue`, `implement-issue`, `finish-branch`,
  `bump-version`, `deploy-plugin`, `brainstorm`
- Roles in `.claude/roles/`, switched with `/role <name>`; the active role is set in the
  local-only `CLAUDE.local.md` (`@.claude/roles/developer.md`), `implement-issue` requires the
  Developer role

### GitHub Workflow

Feature branch → Pull Request → merge into `main`:

```bash
git switch -c feature/my-change
git commit -m "feat(NNN): short description"
git push -u origin feature/my-change
```

**Remote:** `origin -> github.com/cassandragargoyle/portunix-table-detector`

**Never commit secrets.** The following stay local-only (gitignored): `NOTES.md`,
`.claude/settings.local.json`, `CLAUDE.local.md`, `.env*`.
