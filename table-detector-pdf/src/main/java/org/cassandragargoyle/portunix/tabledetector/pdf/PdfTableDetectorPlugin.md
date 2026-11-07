---
warning: Only the code is the source of truth. This document is for humans and may lag behind the code.
---

# PdfTableDetectorPlugin

PF4J extension (`@Extension`) implementing `TableDetectorPlugin` for PDF input. It loads the
document with PDFBox, runs `PdfTableDetector` on the requested page (or on all pages) and
numbers the tables across the whole document.

- **Id**: `pdf`
- **MIME types**: `application/pdf`, with the `.pdf` file name extension as a fallback
- **Priority**: 10

## Structure

```mermaid
classDiagram
    class TableDetectorPlugin {
        <<interface>>
    }
    class PdfTableDetectorPlugin {
        +getId() String
        +getSupportedMimeTypes() String[]
        +supports(DetectionContext) boolean
        +detect(DetectionContext) DetectionResult
        +getPriority() int
    }
    TableDetectorPlugin <|.. PdfTableDetectorPlugin
    PdfTableDetectorPlugin ..> PdfTableDetector : one per request
    PdfTableDetectorPlugin ..> PDDocument : Loader.loadPDF
```

## Detection flow

```mermaid
flowchart TD
    A[detect context] --> B{content empty?}
    B -- yes --> F[DetectionResult.failure]
    B -- no --> C[Loader.loadPDF]
    C --> D[page range: one page or all pages]
    D --> E[PdfTableDetector.detectTables per page]
    E --> G[set id table-N across the document and page number]
    G --> H[DetectionResult.success with table_count metadata]
    C -. exception .-> X[log SEVERE with stack trace]
    E -. exception .-> X
    X --> F
```

Table ids from `PdfTableDetector` are per page; the plugin overwrites them with a counter that
runs through the whole document.

## Logging

The logger comes from `LogFactory.getLogger` (`java.util.logging`). A failed detection is logged
at `SEVERE` with the exception, so the stack trace is kept, and returned as
`DetectionResult.failure` with the exception message.
