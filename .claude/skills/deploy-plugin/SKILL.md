---
name: deploy-plugin
description: 'Local deployment of the table-detector plugin into Portunix — make dist, (re)install from dist/, enable and verify. Use when the user wants to deploy or install table-detector into the local Portunix. Examples: "nasaď plugin", "/deploy-plugin", "deploy table-detector", "nainstaluj table-detector do portunixu".'
allowed-tools: Read, Glob, Grep, Bash, AskUserQuestion
user-invocable: true
---

# Deploy the plugin into Portunix

Local deployment of table-detector into Portunix. Communicate with the user in Czech.

The plugin is a `helper` Java plugin (see `plugin.json`): a standalone JAR
`table-detector-<version>.jar` with the `detect` command.

## STEP 1: Preflight

```bash
portunix --version
java -version
grep -n -e '"version"' -e '"binary"' plugin.json
grep -n -m1 '<version>' pom.xml
```

If `portunix` is not in `PATH`, **STOP** and tell the user. The `plugin.json` version must equal
the `pom.xml` version without `-SNAPSHOT` and `plugin.binary` must name
`table-detector-<version>.jar`; otherwise report it (see `/bump-version`).

## STEP 2: Build

```bash
make dist
```

`make dist` runs `mvn clean package -DskipTests`, then copies the standalone JAR, `plugin.json`
and `README.md` to `dist/`. If the build fails, show the error and **STOP**.

Verify that `dist/plugin.json` has `"name": "table-detector"` and that the JAR named in
`plugin.binary` exists in `dist/`.

## STEP 3: Install

Install directly from `dist/` using an **absolute path** (a relative one fails with
"plugin.json not found"). If the plugin is already installed, uninstall it first:

```bash
DIST_DIR="$(realpath dist)"
portunix plugin list | grep -q table-detector && echo 'y' | portunix plugin uninstall table-detector
portunix plugin install "$DIST_DIR/"
```

On Windows (Git Bash) use PowerShell with an absolute Windows path:

```powershell
powershell.exe -Command "echo 'y' | portunix plugin uninstall table-detector"
powershell.exe -Command "portunix plugin install 'C:\absolute\path\to\dist\'"
```

## STEP 4: Enable and verify

```bash
portunix plugin enable table-detector
portunix plugin list
portunix plugin health table-detector
```

Optionally run a smoke test on a sample document from `samples/`:

```bash
java -jar dist/table-detector-<version>.jar detect --help
```

## STEP 5: Summary

```text
Plugin 'table-detector' deployed.

Build:   make dist
Source:  <absolute dist path>
Version: <version>
Health:  <result>
```

## Troubleshooting

### Missing org-cassandragargoyle-api

```text
Could not find artifact org.cassandragargoyle:org-cassandragargoyle-api:jar:X.X.X
```

The artifact comes from GitHub Packages and needs a token with `read:packages` in
`~/.m2/settings.xml` (server id `github`), see `README.md`.

Alternatively install a local checkout of the API project into the local Maven repository:

1. Check whether the API project exists: `../../Api` (relative to portunix-table-detector)
2. If it exists, install it:

   ```bash
   cd ../../Api && mvn install -DskipTests
   ```

3. Run the plugin build again

If the API project does not exist locally, clone it first:

```bash
git clone <your-Api-repo-url> ../../Api
cd ../../Api && mvn install -DskipTests
```

### Unable to access jarfile

`plugin.binary` in `plugin.json` does not match the built JAR name. Fix the versions with
`/bump-version` and run `make dist` again — `make dist` copies `plugin.json` into `dist/`, so a
change after the build needs a rebuild.

## Version bump checklist

When the version changes, **all** places that define it must be updated, otherwise e.g.
`Unable to access jarfile table-detector-1.0.4.jar` occurs because `plugin.json` points to an old
JAR:

| File | What to update |
| ----- | ----- |
| `pom.xml` (parent + all three modules) | `<version>X.Y.Z-SNAPSHOT</version>` |
| `plugin.json` | `"version"` + `"binary"` (JAR file name) |
| `Makefile` | nothing — `PLUGIN_VERSION` and `MAVEN_VERSION` are read from `plugin.json` and `pom.xml` |
| `*/src/main/resources/plugin.properties` | `plugin.version=` (PF4J plugin version), if such a file is added |

## Notes

- The plugin needs a valid `plugin.json` manifest
- `make dist` creates the standalone JAR in `dist/`
- Installation is done directly from `dist/` (not by copying)
- Installation requires a working Portunix in `PATH`
- Installed plugins live in `~/.portunix/plugins/`
