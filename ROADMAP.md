# Axion Roadmap

## Rendering Compatibility

### Shader-compatible GPU previews

Current Axion GPU previews render through vanilla/Fabric world render events and custom version compatibility code. This works without shader packs, but active Iris shader packs can break or hide the direct GPU preview path. The current fallback disables direct GPU preview when an Iris shader pack is active, which avoids broken output but does not provide true shader-compatible GPU previews.

Findings from Axiom 5.3.0:

- Axiom does not rely only on vanilla `RenderLayer`/`VertexConsumerProvider` layers for its editor overlays.
- Axiom has a custom `core_rendering` pipeline system around `RenderPipeline`, `RenderPass`, `GpuTextureView`, custom draw buffers, and explicit pipeline depth/blend/cull settings.
- Axiom mixes into `LevelRenderer`, `GameRenderer`, `RenderTarget`, and `LightTexture`, plus Sodium chunk renderer classes.
- Axiom detects active Iris shader packs through `net.irisshaders.iris.api.v0.IrisApi`.
- Without shaders, Axiom injects pre/post world rendering through frame graph passes.
- With shaders enabled, Axiom moves its render hook to the end of `LevelRenderer.renderLevel`, after the shader-managed world path, and runs both pre/post overlay rendering there.
- Axiom maintains a separate selection-outline render target, can copy the main depth buffer into it, renders overlays into that target, then blits it back with a custom no-depth selection outline pipeline.
- Axiom's chunk render override path is disabled while Iris shaders are active, so shader compatibility is handled through late overlay rendering rather than by forcing shader packs to accept custom terrain chunk draws.

Planned Axion work:

- Replace shader-pack GPU fallback with a shader-aware render path instead of disabling GPU previews.
- Split Axion's GPU preview work into terrain-like chunk preview rendering and late overlay rendering, since shader packs may require different paths for each.
- Add a small Axion render pipeline abstraction for the overlay cases that cannot be represented reliably through `RenderLayer`.
- Move GPU preview draw scheduling to a render point that is valid under Iris, likely mirroring Axiom's shader-active late `LevelRenderer` hook.
- Add an optional outline/overlay render target for selection outlines and through-block editor overlays.
- Keep CPU preview fallback as a recovery path only, not the default shader-pack behavior.
- Validate with Iris on/off, Sodium on/off, and 1.21.5, 1.21.6-1.21.8, 1.21.9-1.21.11, and 26.1.x.

### Symmetry node through-block visibility

The current Axion attempt uses a custom `xrayQuads` layer plus temporary GL depth-state changes around buffered drawing. This is not robust because the buffered `RenderLayer` draw can be flushed later through a `RenderPipeline` whose own depth state wins over the immediate GL state.

Findings from Axiom 5.3.0+:

- Axiom's `BuildSymmetry.renderWorld` draws the node mesh twice.
- First pass: `AxiomRenderPipelines.POSITION_COLOR_IGNORE_DEPTH`, alpha-tinted, depth test disabled.
- Second pass: `AxiomRenderPipelines.POSITION_COLOR`, full-color, normal depth behavior.
- Rotation guide lines use `LINES_WITHOUT_WRITE_DEPTH`, not vanilla lines.
- Axiom's `AxiomRenderPipeline` owns `depthTest`, `depthWrite`, `depthFunc`, cull, blend, fog, and polygon offset.
- `depthTest = false` becomes `DepthTestFunction.NO_DEPTH_TEST` when the `RenderPipeline` is built.

Planned Axion work:

- Stop trying to make the symmetry node X-ray through immediate GL state.
- Render the node with an explicit no-depth GPU pipeline, followed by a normal-depth pass.
- Keep existing geometry generation if possible, but upload/draw it through a direct render pass instead of a vanilla buffered `RenderLayer`.
- Add a small compatibility implementation per supported rendering API generation:
  - 1.21.5 direct uniform path.
  - 1.21.6-1.21.8 GPU buffer path.
  - 1.21.9-1.21.11 modern `GpuBufferSlice`/`RenderPipeline` path.
  - 26.1.x official namespace path.
- Verify through-block visibility with opaque blocks between camera and node, both with and without Iris shaders.

## Server Edit Performance

### Bulk Paper edit pipeline with deferred relighting

Large Axion edits still use Bukkit/Paper block APIs in the Paper plugin. This is correct and safe, but it pays per-block update costs and does not match Axiom's large-edit performance.

Findings from Axiom Paper 5.0.4+:

- Axiom's large edits use a `SetBlockBufferOperation` style path.
- It writes directly into chunk sections instead of calling Bukkit `Block#setType` per block.
- It tracks old/new block states, updates heightmaps, POIs, and block entities, marks chunks dirty, and queues chunk sends.
- It detects whether lighting properties changed, sends chunk data, and defers chunk relighting through a queued chunk-level relight path.
- Axiom has configurable send/relight budgets; `0` means unlimited per tick.

Planned Axion work:

- Add an opt-in Paper NMS bulk writer for large operations.
- Keep the current Bukkit path for small edits and compatibility fallback.
- Group planned writes by chunk section before applying.
- Preserve Axion's undo/diff model by snapshotting before/after state at the operation boundary.
- Support block entity copy/paste payloads in the bulk writer.
- Queue chunk sends and chunk relights after the write phase.
- Add config flags for bulk writer enablement and per-tick send/relight budgets.
- Add audit fields for write path, chunks touched, relight queued, relight completed, and chunk send count.

## Version Support

### 0.2.8 focus

- Broaden supported versions downward, starting with 1.21.4 Paper/mod support.
- Continue hardening 26.1.x Fabric dedicated server support.
- Keep run script coverage for every advertised version.
- Reduce duplicated compatibility code where the API surface is genuinely shared, but avoid merging versions when it hides runtime-specific behavior.

## UI Polish

### Tool hint system

- Replace the large on-screen keybind block with compact mouse/key hints.
- Use the new `assets/axion/assets/mouse/` textures for left click, right click, and scroll affordances.
- Show primary click hints under the crosshair without a black backing panel.
- Move secondary keybind hints to the lower-right corner.
- Show smear node offset above the hotbar during smear-node adjustment.
