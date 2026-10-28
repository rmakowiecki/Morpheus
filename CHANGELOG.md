# Changelog

## Unreleased

### Changed

- `:morpheus` draws every layer's edges and dots in point batches, one native draw call per batch, on every
  platform and whether or not frame stats are attached. Halos are still drawn one by one. Next to drawing each
  element on its own, as before:
  - edges are drawn in order of their 8-bit alpha level, so a pixel where edges overlap can differ by up to
    three levels out of 255;
  - a dot is drawn up to two levels more opaque, and at the center radius of its size bin, one of eight
    between the smallest and largest radius the layer's spec allows;
  - where dots overlap, their alphas add up instead of the later dot replacing the earlier one;
  - everything else is within two levels.
- `MorpheusGraphDrawingApproach` and `MorpheusGraphFrameStats.drawingApproach`, both experimental, are removed.
  Draw-call counts in `MorpheusGraphLayerBreakdown` count each batch as one call, so they don't compare with
  earlier readings.
