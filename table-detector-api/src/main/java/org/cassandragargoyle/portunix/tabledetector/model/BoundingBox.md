---
warning: Only the code is the source of truth. This document is for humans and may lag behind the code.
---

# BoundingBox

Axis-aligned rectangle used for table and cell geometry (`TableModel`, `TableCell`). It is
serialized to JSON with Jackson as `x`, `y`, `width`, `height`.

## Coordinate convention

Decided in issue #172:

- Units are PDF points (1/72 inch)
- The origin is the **top-left** corner of the page (media box), the same space as PDFBox
  `TextPosition.getX` / `getY`
- `x` grows to the right, `y` grows **downward**
- `y` is the **top edge** of the box and `getBottom()` (`y + height`) the bottom edge
- `y` is not the text baseline; for text the box spans `baseline - height .. baseline`

## Skewed text

The box has no angle. For text at a non-right angle it is the axis-aligned envelope of the turned
cell rectangle (issue #208): it covers the glyphs of the cell, but boxes of neighbouring cells may
overlap and contain glyphs of each other. `horizontallyOverlaps` and `isInSameColumn` are therefore
not reliable for skewed tables, the row and column indices of `TableCell` are.

```mermaid
flowchart TB
    subgraph Page["page, origin (0, 0) top-left"]
        direction TB
        T["top edge: y"] --> G["glyphs"] --> B["bottom edge: getBottom() = y + height = baseline"]
    end
```

## Structure

```mermaid
classDiagram
    class BoundingBox {
        -float x
        -float y
        -float width
        -float height
        +getRight() float
        +getBottom() float
        +horizontallyOverlaps(BoundingBox) boolean
        +isInSameColumn(BoundingBox, float) boolean
        +expandToInclude(BoundingBox)
    }
    TableModel --> BoundingBox
    TableCell --> BoundingBox
```

## Flow

```mermaid
flowchart TD
    A[expandToInclude other] --> B[x = min of left edges]
    B --> C[y = min of top edges]
    C --> D[right = max of right edges]
    D --> E[bottom = max of bottom edges]
    E --> F[width = right - x, height = bottom - y]
```
