---
warning: Only the code is the source of truth. This document is for humans and may lag behind the code.
---

# TableDetectorManager

Holds the table detection backends (`TableDetectorPlugin`) and routes each detection request to
the one that supports the input. Backends come from PF4J discovery (`initialize()`) or from
manual registration (`registerPlugin()`), the CLI `Main` and embedded users such as
text-extractor register `PdfTableDetectorPlugin` directly.

## Structure

```mermaid
classDiagram
    class TableDetectorManager {
        -List~TableDetectorPlugin~ plugins
        -PluginManager pluginManager
        -boolean initialized
        +initialize()
        +registerPlugin(TableDetectorPlugin)
        +detect(DetectionContext) DetectionResult
        +getPlugins() List~TableDetectorPlugin~
        +shutdown()
        -findPlugin(DetectionContext) TableDetectorPlugin
    }
    class TableDetectorPlugin {
        <<interface>>
        +supports(DetectionContext) boolean
        +detect(DetectionContext) DetectionResult
        +getPriority() int
    }
    TableDetectorManager o-- TableDetectorPlugin
    TableDetectorManager --> PluginManager : PF4J discovery
    TableDetectorPlugin <|.. PdfTableDetectorPlugin
```

## Detection flow

```mermaid
sequenceDiagram
    participant Caller
    participant M as TableDetectorManager
    participant P as TableDetectorPlugin
    Caller->>M: detect(context)
    M->>M: findPlugin(context)
    Note right of M: plugins that support the context,<br/>lowest priority value wins
    alt no plugin
        M-->>Caller: DetectionResult.failure
    else plugin found
        M->>P: detect(context)
        P-->>M: DetectionResult
        M->>M: drop tables below minConfidence
        M-->>Caller: DetectionResult with processing time
    end
```

## Logging

The logger comes from `LogFactory.getLogger` (`java.util.logging`). Messages use
`MessageFormat` placeholders `{0}`, `{1}`; SLF4J style `{}` is not substituted.

- `FINE`: number of plugins found by PF4J discovery
- `WARNING`: PF4J discovery failed, the manager continues with manually registered plugins
- `FINER`: each manually registered plugin
