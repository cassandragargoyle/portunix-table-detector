# Issue #3: SyntheticPdfSvg ignores pdftocairo failures and lacks argument checks

## Summary

`SyntheticPdfSvg` does not check the exit code of `pdftocairo`: on failure it exits with 0 and
appends a second overlay to a stale SVG, which can then be copied into the committed pictures. A
missing argument or output directory ends with a raw stack trace.

**Status**: 📋 Open
**Priority**: Medium
**Component**: `table-detector-pdf` (test sources)
**Created**: 2026-11-09
**GitHub**: #3
**Related**: #1

## Problem Description

Found while testing #1 (move of the tool to
`table-detector-pdf/src/test/java/org/cassandragargoyle/portunix/tabledetector/pdf/SyntheticPdfSvg.java`).
The behaviour is the same as in the original `tools/SyntheticPdfSvg.java`, so it is not a
regression of #1.

### pdftocairo failure is ignored

`main` runs

```java
new ProcessBuilder("pdftocairo", "-svg", pdf.getPath(), svg.getPath()).inheritIO().start().waitFor();
```

and ignores the return value of `waitFor()`. Then it reads `<variant>.svg` and appends the
overlay.

Reproduction: put a fake `pdftocairo` that prints an error and exits with 1 first in `PATH`, fill
the output directory with SVGs from a previous run and run the tool:

- the tool exits with **0**
- every stale SVG gets a **second** `<g id="detected">` group (overlay counted twice)
- without stale SVGs it fails later with `NoSuchFileException`, which hides the real cause

If the output is then copied into `synthetic-pdf/` as described in `PdfTableDetectorTest.md`, the
corrupted pictures end up in the repository.

### Missing argument and output directory

| Input | Current result |
| ----- | ----- |
| no argument | `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`, exit 1 |
| output directory does not exist | `FileNotFoundException: <dir>/synthetic-a4.pdf`, exit 1 |
| `pdftocairo` not in `PATH` | `IOException: Cannot run program "pdftocairo"`, exit 1, one PDF left behind |

## Proposal

- Check the exit code of `pdftocairo` and fail with an `IOException` naming the variant and the
  exit code; do not touch the SVG in that case
- Without exactly one argument print `Usage: SyntheticPdfSvg <output dir>` to stderr and exit
  with 2
- Create the output directory with `Files.createDirectories`
- Report a missing `pdftocairo` with a short hint (`pdftocairo not found, install poppler-utils`)
  instead of the raw `IOException`
- Update `SyntheticPdfSvg.md` (flow diagram with the error branches)

## Acceptance Criteria

1. A failing `pdftocairo` makes the tool exit with a non-zero code and leaves no SVG with a
   duplicated overlay
1. Running without an argument prints the usage and exits with 2
1. A non-existing output directory is created
1. A missing `pdftocairo` gives a one-line hint and a non-zero exit code
1. A normal run still produces SVGs identical to the committed ones in `synthetic-pdf/`
1. `mvn clean verify` passes

## Testing

- Normal run as in `PdfTableDetectorTest.md`, compare with `cmp` against `synthetic-pdf/*.svg`
- Fake failing `pdftocairo` first in `PATH` with stale SVGs in the output directory → non-zero
  exit, `grep -c '<g id="detected"'` stays 1
- `PATH` without `pdftocairo` → hint and non-zero exit
- No argument → usage and exit 2
- Non-existing output directory → created and filled
