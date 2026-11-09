# Issue #1: Move SyntheticPdfSvg tool from tools/ to src/test/java

## Summary

`table-detector-pdf/tools/SyntheticPdfSvg.java` lives outside the Maven source layout, so the
build never compiles it and API changes break it silently. Move it to the test sources.

**Status**: ✅ Implemented
**Priority**: Low
**Component**: `table-detector-pdf`
**Created**: 2026-11-08
**Closed**: 2026-11-09
**GitHub**: #1

## Problem Description

`table-detector-pdf/tools/SyntheticPdfSvg.java` renders the synthetic PDFs of
`PdfTableDetectorTest` to SVG with the detected bounding boxes. It is run as a single-file
source program:

```bash
java -cp <test classpath> tools/SyntheticPdfSvg.java <output dir>
```

Consequences:

- `mvn verify` never compiles it, so API changes in `PdfTableDetector` or the model classes
  (`BoundingBox`, `TableCell`, `TableModel`) break it silently
- It reaches the fixture factories of `PdfTableDetectorTest` (`createSyntheticPdf`,
  `createProsePdf`, `createProseWithMatrixPdf`) via reflection (`getDeclaredMethod` / `invoke`),
  so renames in the test are not caught either
- `tools/` inside a module is not a standard Maven location

The tool is not part of the production JAR today and must stay that way.

## Proposal

- Move the class to
  `table-detector-pdf/src/test/java/org/cassandragargoyle/portunix/tabledetector/pdf/SyntheticPdfSvg.java`
  with the proper `package` declaration
- Replace the reflection with direct calls to the `PdfTableDetectorTest` factory methods (same
  package)
- Update the usage comment, e.g. run via

  ```bash
  mvn -pl table-detector-pdf exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=org.cassandragargoyle.portunix.tabledetector.pdf.SyntheticPdfSvg \
    -Dexec.args="<output dir>"
  ```

  or from the IDE
- Add the architecture document `SyntheticPdfSvg.md` next to the source file (project convention)
- Remove the now empty `tools/` directory

## Acceptance Criteria

1. `mvn clean verify` compiles the tool together with the tests
1. The tool is not part of the production `table-detector-pdf` JAR
1. Running it produces the same SVG output as before
1. `SyntheticPdfSvg.md` exists next to the source with frontmatter warning and Mermaid diagram

## Testing

- `mvn clean verify`
- `jar tf table-detector-pdf/target/table-detector-pdf-*.jar | grep SyntheticPdfSvg` prints
  nothing
- Run the tool before and after the move into two directories and compare the SVG files
  (`diff -r`)
