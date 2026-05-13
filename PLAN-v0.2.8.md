# Axion v0.2.8 Plan

## Status Update

The originally planned 1.21.5 and 1.21.6-1.21.8 chunked GPU preview work was pulled into v0.2.7:

- `./build-axion.sh 1.21.5` passes.
- `./build-axion.sh legacy` passes.
- 1.21.5 uses its older direct-uniform GPU API.
- 1.21.6-1.21.8, compiled against 1.21.7, uses cached section GPU buffers with manual per-section draws.
- 1.21.7 keeps `drawMultipleIndexedPreview(...)` disabled because its uniform upload API is not compatible with the 1.21.11 batching path.

## Primary Focus

v0.2.8 should shift back to holistic version support and UI cleanup.

## Goals

1. Add Minecraft 1.21.4 support for Paper and Fabric mod builds.
2. Investigate support below 1.21.4 after the 1.21.4 API gaps are mapped.
3. Add 26.1.x Fabric dedicated server support.
4. Improve the tool HUD so keybinds, tool states, offsets, and selection details use less screen space.
5. Reduce drift between the four preview render implementations by extracting shared chunked-preview logic where the version APIs allow it.

## GPU Renderer Follow-Up

The v0.2.7 legacy GPU port should still get runtime validation before cutting the release:

- large terrain clone preview, at least 25k blocks,
- move-origin glass overlay on a large selection,
- stack preview above the old CPU preview threshold,
- smear preview while changing camera direction,
- camera pan/turn around preview geometry,
- disconnect/reconnect to confirm GPU buffers close,
- `./build-axion.sh all`.

## Refactor Candidates

- Keep version-specific code for buffer upload, texture binding, uniform writing, and draw submission.
- Extract shared session/cache behavior from `ChunkedPreviewSession` only after the runtime behavior is proven on 1.21.5, 1.21.7, 1.21.11, and 26.1.x.
- Normalize diagnostic log prefixes across GPU drawers:
  - `[Axion GPU 1.21.5]`
  - `[Axion GPU 1.21.7]`
  - `[Axion GPU 1.21.11]`
  - `[Axion GPU 26.1]`

## Risks

- 1.21.4 may require another namespace/API split, especially around NBT codecs, entity loading, HUD hooks, and render pipeline classes.
- Fabric dedicated server support for 26.1.x may need server-only shims instead of reusing client official-namespace adapters.
- Preview renderer consolidation is only worth doing after the v0.2.7 release branch is stable.
