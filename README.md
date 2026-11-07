# Table Detector Plugin

Universal table detection framework with pluggable format backends for the Portunix ecosystem.

## Overview

The Table Detector plugin detects and extracts tables from documents using a pluggable architecture. It currently supports PDF files via Apache PDFBox, with an extension point for additional format backends (DOCX, XLSX, HTML, etc.).

## Features

- **Pluggable architecture** - PF4J-based extension system for format-specific backends
- **PDF table detection** - Text-based table detection using Apache PDFBox 3.x
- **Running text aware** - Paragraphs are not reported as tables, several tables on one page are detected separately
- **Multiple output formats** - JSON (structured) and Markdown (human-readable)
- **Confidence scoring** - Configurable minimum confidence threshold for detected tables
- **Page selection** - Process all pages or a specific page
- **Bounding box data** - Spatial coordinates for each detected table and cell

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Portunix 1.10.0+

## Installation

Download `table-detector-<version>.jar` and `plugin.json` from
[GitHub Releases](https://github.com/cassandragargoyle/portunix-table-detector/releases) into one
directory and install it:

```bash
portunix plugin install /path/to/table-detector/
portunix plugin enable table-detector
```

## Using as a Maven Library

The thin JARs `table-detector-api`, `table-detector-core` and `table-detector-pdf` are published
to GitHub Packages:

```xml
<repositories>
	<repository>
		<id>github-table-detector</id>
		<url>https://maven.pkg.github.com/cassandragargoyle/portunix-table-detector</url>
	</repository>
</repositories>

<dependency>
	<groupId>org.cassandragargoyle.portunix</groupId>
	<artifactId>table-detector-pdf</artifactId>
	<version>1.0.3</version>
</dependency>
```

GitHub Packages requires authentication even for public packages. Add a personal access token
with the `read:packages` scope to `~/.m2/settings.xml` for every repository id used (here `github`
for `org-cassandragargoyle-api` and the id of the repository above). In GitHub Actions, the
workflow `GITHUB_TOKEN` with `packages: read` is enough.

```xml
<settings>
	<servers>
		<server>
			<id>github</id>
			<username>YOUR_GITHUB_LOGIN</username>
			<password>YOUR_TOKEN</password>
		</server>
	</servers>
</settings>
```

## Build

The build resolves `org-cassandragargoyle-api` from GitHub Packages, so the `github` server from
[Using as a Maven Library](#using-as-a-maven-library) must be configured.

```bash
# Build plugin JAR (without tests)
make build

# Run tests
make test

# Create distribution package
make dist
```

Or using Maven directly:

```bash
mvn clean verify
```

## Release

Set the release version in all `pom.xml` files (`X.Y.Z-SNAPSHOT`) and in `plugin.json`
(`X.Y.Z`), then push a tag `vX.Y.Z`. The `Publish` workflow checks the versions, deploys the
Maven artifacts to GitHub Packages and attaches the plugin JAR, `plugin.json` and checksums to
the GitHub Release.

## Usage

### CLI

```bash
# Detect tables in a PDF (JSON output)
table-detector detect document.pdf

# Markdown output
table-detector detect --format markdown document.pdf

# Specific page with verbose output
table-detector detect --page 3 --verbose document.pdf

# Custom confidence threshold
table-detector detect --min-confidence 0.7 document.pdf
```

### Via Portunix

```bash
portunix table-detector detect document.pdf
portunix table-detector detect --format markdown --page 2 report.pdf
```

### Options

| Option | Short | Description | Default |
| ------ | ----- | ----------- | ------- |
| `--page` | `-p` | Page number (0 = all pages) | 0 |
| `--min-confidence` | `-c` | Minimum confidence threshold (0.0-1.0) | 0.5 |
| `--format` | `-f` | Output format: `json`, `markdown` | json |
| `--verbose` | `-v` | Verbose output to stderr | false |

### JSON Output Example

```json
{
  "success": true,
  "tableCount": 2,
  "processingTimeMs": 145,
  "tables": [
    {
      "id": "table-p1-0",
      "page": 1,
      "confidence": 0.85,
      "rowCount": 4,
      "columnCount": 3,
      "cells": [...]
    }
  ]
}
```

### Markdown Output Example

```markdown
| Name | Age | City |
| --- | --- | --- |
| Alice | 30 | Prague |
| Bob | 25 | Brno |
```

## Architecture

```text
portunix-table-detector/
├── table-detector-api/     # Extension point interfaces and models
├── table-detector-core/    # CLI entry point, manager, output formatting
├── table-detector-pdf/     # PDF backend (Apache PDFBox)
├── samples/                # Sample documents for testing
├── dist/                   # Distribution package (make dist)
├── .github/workflows/      # Build (PR, main) and Publish (vX.Y.Z tag)
├── plugin.json             # Portunix plugin manifest
├── pom.xml                 # Parent POM
└── Makefile                # Build commands
```

### Modules

| Module | Description |
| ------ | ----------- |
| `table-detector-api` | `TableDetectorPlugin` extension point, `DetectionContext`, `DetectionResult`, `TableModel`, `TableCell`, `BoundingBox` |
| `table-detector-core` | `Main` (Picocli CLI), `TableDetectorManager` (plugin registry and dispatch) |
| `table-detector-pdf` | `PdfTableDetector` (PDFBox-based text row analysis), `PdfTableDetectorPlugin` (PF4J registration) |

### Adding a New Format Backend

Implement `TableDetectorPlugin` from `table-detector-api`:

```java
public class DocxTableDetectorPlugin implements TableDetectorPlugin {
    public String getId() { return "docx"; }
    public String[] getSupportedMimeTypes() { return new String[]{"application/vnd.openxmlformats-officedocument.wordprocessingml.document"}; }
    public boolean supports(DetectionContext ctx) { /* ... */ }
    public DetectionResult detect(DetectionContext ctx) { /* ... */ }
    public int getPriority() { return 100; }
}
```

Register in `Main.java` or use PF4J auto-discovery via `extensions.idx`.

## Dependencies

| Library | Version | Purpose |
| ------- | ------- | ------- |
| Apache PDFBox | 3.0.7 | PDF parsing and text extraction |
| PF4J | 3.15.0 | Plugin framework for format backends |
| Picocli | 4.7.7 | CLI argument parsing |
| Jackson | 2.22.0 | JSON serialization |
| CassandraGargoyle API | 1.1.0 | Logging (`LogFactory`, java.util.logging) |

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Related Plugins

- [text-extractor](https://github.com/cassandragargoyle/portunix-plugins/tree/main/plugins/text-extractor) - Text extraction from documents (consumes this library)
- [docgen](https://github.com/cassandragargoyle/portunix-plugins/tree/main/plugins/docgen) - Document generation from Markdown
- [vim-parser](https://github.com/cassandragargoyle/portunix-vim) - Semantic extraction from documents (`vim-parser-table` consumes this library)
