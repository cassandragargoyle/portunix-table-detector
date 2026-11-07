# table-detector-pdf

PDF table detection backend for the Table Detector plugin using Apache PDFBox heuristic text position analysis.

## How it works

The detector uses PDFBox's `PDFTextStripper` to extract text positions from PDF pages, then applies heuristic analysis to identify table structures:

1. **Text grouping** — Groups text elements by Y-position into rows (using configurable tolerance)
2. **Column detection** — Detects column boundaries by analyzing gaps between text elements within rows
3. **Table construction** — Builds a table model with rows, columns, and cell content
4. **Confidence scoring** — Calculates confidence based on row count, column count, and structural consistency

Minimum thresholds: 2 rows, 2 columns.

## Module Structure

This is one of three modules in the `table-detector` plugin:

```text
table-detector/
├── table-detector-api/    # Shared interfaces (TableDetectorPlugin, DetectionResult)
├── table-detector-core/   # Core orchestrator and CLI
└── table-detector-pdf/    # PDF backend (this module)
```

## Key Classes

| Class | Description |
|-------|-------------|
| `PdfTableDetectorPlugin` | PF4J plugin extension, entry point for detection |
| `PdfTableDetector` | Core detection engine (extends PDFTextStripper) |

## Dependencies

- Apache PDFBox 3.0.5
- PF4J 3.12.0 (plugin framework)
- CassandraGargoyle API (logging)

## Build

```bash
# From table-detector root
mvn clean package
```
