# Axion API Compatibility Map

This document maps API differences across supported Minecraft versions and documents the VersionCompat abstraction layer.

## Version Support

- **1.21.5 - 1.21.7**: Primary support with full feature parity
- **1.21.8 - 1.21.11**: Extended support with GPU rendering enhancements
- **26.1.x**: Official (Mojang) namespace, no remap — `src/compat-26_1`
- **26.2.x**: Latest Minecraft version — `src/compat-26_2`

26.1 and 26.2 ship as separate jars and do not share a rendering API: 26.2
deleted `MultiBufferSource` and `ShapeRenderer` outright and moved to the
submit-node system. The paths cited below are 26.1's; `src/compat-26_2` mirrors
the same layout. See `docs/porting/26.2.md` for the full delta.

## VersionCompat Interface Methods

The `VersionCompat` interface provides a common abstraction for operations that differ between Minecraft versions. Implementations exist in:
- `src/compat-1_21_5/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.5)
- `src/compat-1_21_6/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.6 exact test/build target)
- `src/compat-1_21_7/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.6 - 1.21.8 release range base)
- `src/compat-1_21_8/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.8 exact test/build target)
- `src/compat-1_21_9/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.9 exact test/build target)
- `src/compat-1_21_10/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.10 exact test/build target)
- `src/compat-1_21_11/kotlin/axion/client/compat/VersionCompatImpl.kt` (1.21.9 - 1.21.11 release range base)
- `src/compat-26_1/kotlin/axion/client/compat/VersionCompatImpl.kt` (26.1.x)

### API Area: Registry Operations

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `getBlock(id)` | `Registries.BLOCK.get(id)` | `Registries.BLOCK.get(id)` | `Registries.BLOCK.getOptional(id).orElse(null)` | 26.1.x uses Optional API |
| `getItem(id)` | `Registries.ITEM.get(id)` | `Registries.ITEM.get(id)` | `Registries.ITEM.getOptional(id).orElse(null)` | 26.1.x uses Optional API |
| `getBlockId(block)` | `Registries.BLOCK.getId(block)` | `Registries.BLOCK.getId(block)` | `Registries.BLOCK.getKey(block)` | 26.1.x renamed to getKey |
| `getItemId(item)` | `Registries.ITEM.getId(item)` | `Registries.ITEM.getId(item)` | `Registries.ITEM.getKey(item)` | 26.1.x renamed to getKey |

### API Area: ResourceLocation/Identifier

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `parseIdentifier(id)` | `Identifier.of(parts[0], parts[1])` | `Identifier.of(parts[0], parts[1])` | `Identifier.parse(id)` | 26.1.x has built-in parser |
| `identifierOf(ns, path)` | `Identifier.of(namespace, path)` | `Identifier.of(namespace, path)` | `Identifier.fromNamespaceAndPath(namespace, path)` | 26.1.x renamed method |

### API Area: BlockState Operations

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `blockStateToString(state)` | `BlockArgumentParser.stringifyBlockState(state)` | `BlockArgumentParser.stringifyBlockState(state)` | `BlockStateParser.serialize(state)` | 26.1.x moved to command block-state parser |
| `stringToBlockState(str)` | `BlockArgumentParser.block(...).blockState()` | `BlockArgumentParser.block(...).blockState()` | `BlockStateParser.parseForBlock(...).blockState()` | 26.1.x requires a block `HolderLookup` and `StringReader` |

### API Area: NBT Serialization

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `itemStackToNbt(stack)` | `nbt.copyFromCodec(ItemStack.MAP_CODEC, ops, stack)` | `NbtCompound()` (stub) | `NbtCompound()` (stub) | 1.21.5+ uses Codec |
| `nbtToItemStack(nbt)` | `nbt.decode(ItemStack.MAP_CODEC, ops).orElse(EMPTY)` | `ItemStack.EMPTY` (stub) | `ItemStack.EMPTY` (stub) | 1.21.5+ uses Codec |

**26.1.x ItemStack Codec (Hotbar Save/Load):**
- Uses typed `ItemStack.STREAM_CODEC` through `RegistryByteBuf`.
- Requires a `DynamicRegistryManager` from `ClientWorld.registryAccess()`.
- `PlayerInventory.setStack(slot, stack)` must call `setChanged()` after `setItem(...)`; otherwise hotbar pages can decode correctly but fail to refresh visually.
- Implemented in `src/compat-26_1/kotlin/axion/client/compat/VersionCompatImpl.kt` and `src/compat-26_1/kotlin/axion/client/hotbar/CompatExtensions.kt`.

### API Area: Keybinding Handling

| Method | 1.21.5-1.21.7 | 1.21.8-1.21.11 | 26.1.x | Notes |
|--------|---------------|----------------|--------|-------|
| `shouldUseNonConsumingKeybind()` | `true` | `false` | `false` | 1.21.8+ fixed keybind conflicts with `wasPressed()` |

### API Area: Rendering Helpers (Block Tessellation)

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `getBlockRenderManager(client)` | Not implemented | `client.blockRenderManager` | `client` (stub) | 26.1.x needs implementation |
| `getBlockRenderType(state)` | Not implemented | `state.renderType` | `state` (stub) | 26.1.x needs implementation |
| `getRenderingSeed(state, pos)` | Not implemented | `state.getRenderingSeed(pos)` | `0L` (stub) | 26.1.x needs implementation |
| `matrixStackPush(stack)` | Not implemented | `stack.push()` | `stack` (stub) | 26.1.x needs implementation |
| `matrixStackPop(stack)` | Not implemented | `stack.pop()` | (stub) | 26.1.x needs implementation |

**Status:** These legacy `VersionCompat` rendering helpers are still stubbed in 26.1.x. The active 26.1.x preview path bypasses them and uses version-specific tessellators/renderers in `src/compat-26_1/kotlin/axion/client/render/`.

### API Area: Entity API

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `entityIsRemoved(entity)` | `entity.isRemoved` | `entity.isRemoved` | `entity.isRemoved()` | 26.1.x changed to method |
| `entityGetVehicle(entity)` | `entity.vehicle` | `entity.vehicle` | `entity.vehicle` | Same |
| `entityGetUuid(entity)` | `entity.uuid` | `entity.uuid` | `entity.uuid` | Same |
| `entityGetX/Y/Z(entity)` | `entity.x/y/z` | `entity.x/y/z` | `entity.x/y/z` | Same |
| `entityGetYaw(entity)` | `entity.yaw` | `entity.yaw` | `entity.getYRot()` | 26.1.x renamed |
| `entityGetPitch(entity)` | `entity.pitch` | `entity.pitch` | `entity.getXRot()` | 26.1.x renamed |
| `entityGetPassengerList(entity)` | `entity.passengerList` | `entity.passengerList` | `entity.passengers` | 26.1.x renamed |
| `entitySetUuid(entity, uuid)` | `entity.setUuid(uuid)` | `entity.setUuid(uuid)` | `entity.setUUID(uuid)` | 26.1.x renamed |
| `entityRefreshPositionAndAngles(entity)` | Not implemented | `entity.refreshPositionAndAngles()` | Manual implementation | 26.1.x method removed |
| `entityUpdatePassengerPosition(entity, passenger)` | Not implemented | `entity.updatePassengerPosition(passenger)` | Manual implementation | 26.1.x method removed |
| `entityTypeLoadEntityWithPassengers(...)` | Not implemented | `EntityType.loadEntityWithPassengers(...)` | `EntityType.loadEntityRecursive(...)` with `TagValueInput` | 26.1.x API changed |
| `worldSpawnNewEntityAndPassengers(...)` | Not implemented | `world.spawnNewEntityAndPassengers(entity)` | `world.addFreshEntityWithPassengers(entity)` | 26.1.x renamed |
| `worldGetOtherEntities(...)` | Not implemented | `world.getOtherEntities(entity, box)` | `world.getEntitiesOfClass(Entity::class, box)` | 26.1.x API changed |

### API Area: MinecraftClient API

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `clientGetServer(client)` | Not implemented | `client.server` | `client.getSingleplayerServer()` | 26.1.x renamed |
| `clientGetWorldRegistryKey(client)` | Not implemented | `client.world?.registryKey` | `client.level?.dimension()` | 26.1.x renamed |
| `serverExecute(server, task)` | Not implemented | `server.execute(task)` | `server.execute(task)` | Same |
| `serverGetWorld(server, registryKey)` | Not implemented | `server.getWorld(registryKey)` | `server.getLevel(registryKey)` | 26.1.x renamed |
| `playerSendMessage(player, message, overlay)` | Not implemented | `player.sendMessage(message, overlay)` | Same logic, different types | 26.1.x uses net.minecraft.network.chat package |

### API Area: Direction/BlockState API

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `directionGetVector(direction)` | Not implemented | `direction.vector` | `Vec3i(d.step().x.toInt(), ...)` | 26.1.x API changed significantly |
| `blockStateStringify(state)` | Not implemented | `state.toString()` | `BlockStateParser.serialize(state)` | 26.1.x must emit command-parseable block-state strings |

### API Area: Registry/BlockArgumentParser

| Method | 1.21.5-1.21.7 | 1.21.11 | 26.1.x | Notes |
|--------|---------------|---------|--------|-------|
| `worldGetRegistryManager(world)` | Not implemented | `world.registryManager` | `world.registryAccess()` | 26.1.x renamed |
| `blockArgumentParserBlock(registry, state)` | Not implemented | `BlockArgumentParser.block(registry, state, false)` | `BlockStateParser.parseForBlock(registry, StringReader(state), true)` | 26.1.x parser takes `HolderLookup<Block>` |

#### 26.1.x Block State Parser
- Class moved from Yarn-style `BlockArgumentParser` usage to official `net.minecraft.commands.arguments.blocks.BlockStateParser`.
- Serialization must use `BlockStateParser.serialize(state)`. `state.toString()` is not the correct protocol format for round-tripping through command parsing.
- Parsing uses `BlockStateParser.parseForBlock(holderLookup, StringReader(state), true).blockState()`.
- Client holder lookup can come from `MinecraftClient.getInstance().level?.registryAccess()?.lookupOrThrow(RegistryKeys.BLOCK)`.
- `ProtocolBlockStateCodec.decode(...)` delegates to `VersionCompat.INSTANCE.stringToBlockState(...)`, so remote history block changes can decode on 26.1.x.

## Additional Version-Specific Methods

### Client-Specific Methods (not in interface)

#### Sound Playback
- **1.21.5-1.21.7**: `world.playSoundClient(x, y, z, sound, category, volume, pitch, false)`
- **1.21.11**: Same as 1.21.5-1.21.7
- **26.1.x**: `world.playLocalSound(x, y, z, sound, category, volume, pitch, false)` - renamed method

#### Inventory Access
- **1.21.5-1.21.7**: `inventory.mainStacks`
- **1.21.11**: `inventory.mainStacks`
- **26.1.x**: `inventory.nonEquipmentItems` - renamed property

#### Mouse Scaling
- **1.21.5-1.21.7**: `client.mouse.getScaledX/Y(client.window)`
- **1.21.11**: Same as 1.21.5-1.21.7
- **26.1.x**: `client.mouseHandler.xpos/ypos() * client.window.guiScaledWidth/Height / client.window.screenWidth/Height` - completely different API

#### HUD Registration
- **1.21.5-1.21.7**: `HudRenderCallback.EVENT.register { context, tickCounter -> ... }`
- **1.21.11**: Same as 1.21.5-1.21.7
- **26.1.x**: `HudElementRegistry.attachElementAfter(hotbar, hudId, HudElement { extractor, deltaTracker -> ... })` - uses new registry API

#### GUI Texture Drawing
- **1.21.5-1.21.7**: `context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0.0f, 0.0f, width, height, width, height)`
- **1.21.11**: `context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)`
- **26.1.x**: `context.delegate.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height)` - use raw texture blitting for `assets/.../textures/...` resources

#### HUD Matrix Stack / GUI Transform Compatibility
- **1.21.5**: `DrawContext.matrices` exposes the older matrix stack API with `push()`, `pop()`, and three-argument `translate(...)`.
- **1.21.6 - 1.21.8**: `DrawContext.matrices` can expose JOML's 2D GUI stack API with `pushMatrix()`, `popMatrix()`, and two-argument `translate(Float, Float)`.
- **1.21.9 - 1.21.11**: Keep HUD code compatible with both matrix shapes; common HUD code should not directly assume only `push()`/`pop()`.
- **26.1.x**: `net.minecraft.client.gui.DrawContext` is an Axion shim over `GuiGraphicsExtractor`; its `matrices` property returns a small adapter that maps common HUD calls to the 26.1.x `Matrix3x2fStack`.
- `AxionHotbarHud` uses local matrix helpers (`pushMatrices`, `popMatrices`, `translateMatrices`) so saved-hotbar overlay z ordering compiles across both the old `PoseStack`-style API and the newer 2D JOML stack.

#### Camera Position
- **1.21.5-1.21.7**: `camera.pos`
- **1.21.11**: Reflection-based fallback to `camera.pos` or `camera.position()`
- **26.1.x**: `camera.position()` - method instead of property

#### 26.1.x Texture Resource Finding
- `GuiGraphicsExtractor.blitSprite(...)` expects sprite atlas ids, not raw `assets/<namespace>/textures/...` paths.
- The toolbox alt-menu icon uses `axion:textures/gui/toolbox.png`, so it must go through raw `blit(...)`.
- Using `blitSprite(...)` for that icon renders Minecraft's purple/black missing texture.
- Fixed in `src/compat-26_1/kotlin/net/minecraft/client/gui/DrawContext.kt` and `src/compat-26_1/kotlin/axion/client/compat/VersionCompatImpl.kt`.

#### GPU Rendering (1.21.5)
- `supportsChunkedPreview()`: Returns `true`
- `renderChunkedPreview()`: Uses `ChunkedPreviewLifecycle.acquire(...)`
- 1.21.5 does not expose the newer `DynamicUniforms`/`GpuBufferSlice` API shape used by 1.21.7+.
- `AxionPreviewBuffer` uses the older enum-style `GpuBuffer` construction and 2-argument indexed draw calls.
- `AxionPreviewBlockDrawer` sets shader uniforms directly with names such as `ModelViewMat` and `ColorModulator`.
- Texture binding uses `pass.bindSampler(name, GpuTexture)`, not the later `GpuTextureView`/sampler pair.
- Multi-draw batching is not used for 1.21.5; the version keeps a manual section draw path with CPU fallback.

#### GPU Rendering (1.21.6 - 1.21.8 / compiled against 1.21.7)
- `supportsChunkedPreview()`: Returns `true`
- `renderChunkedPreview()`: Uses `ChunkedPreviewLifecycle.acquire(...)`
- `AxionPreviewBuffer` persists uploaded `BuiltBuffer` vertex/index data in GPU buffers and reuses dirty section buffers.
- `DynamicUniforms.write(...)` takes five parameters, including `lineWidth`.
- Texture binding uses `pass.bindSampler(name, GpuTextureView)`.
- `drawMultipleIndexedPreview()`: Intentionally returns `false`; 1.21.7 lacks the compatible per-object uniform upload path used by 1.21.11.
- `AxionPreviewBlockDrawer` uses a manual per-section loop: write dynamic uniforms, set `DynamicTransforms`, draw the section buffer.
- Build verification: `./build-axion.sh legacy` passes with the chunked GPU preview path enabled.
- Build verification: `./build-axion.sh all` passes after adding common HUD matrix compatibility for the 1.21.6 - 1.21.8 range.

#### GPU Rendering (1.21.9 - 1.21.11, compiled against 1.21.11)
- `supportsChunkedPreview()`: Returns `true` when at least one texture-binding method is detected
- `renderChunkedPreview()`: Full implementation using `ChunkedPreviewLifecycle`
- `drawMultipleIndexedPreview()`: Uses `RenderPass.RenderObject` (1.21.11); catches `NoClassDefFoundError` on 1.21.9/1.21.10 and falls back to per-section `setUniform` + `drawIndexed` loop
- `getBlockAtlasTextureView()`: Uses `client.atlasManager?.getAtlasTexture()`; atlas sampler is detected reflectively via `getSampler()`
- `bindTextureToRenderPass()`: Runtime-adapts between `pass.bindSampler(name, GpuTextureView)` on 1.21.9/1.21.10 and `pass.bindTexture(name, GpuTextureView, GpuSampler)` on 1.21.11
- `getRenderPipeline()`: Uses `layer.renderPipeline`
- `getPreviewShellPipeline()`: Custom pipeline builder with shaders
- `writeDynamicUniforms()`: Runtime-adapts between the 1.21.9/1.21.10 five-argument `DynamicUniforms.write(..., lineWidth)` and the 1.21.11 four-argument form
- **Reflection strategy:** All GPU method lookups use name + arity matching (`methods.firstOrNull { name == X && parameterTypes.size == N }`) instead of `getMethod()` with exact parameter types. This is required because the compat module is compiled against 1.21.11, and `getMethod()` fails on 1.21.9/1.21.10 when runtime parameter types don't match the compile-time class identity (e.g., `GpuTextureView` class loaded at runtime vs compiled against)

#### GPU Rendering (26.1.x)
- `supportsChunkedPreview()`: Returns `true`
- `renderChunkedPreview()`: Implemented using the 26.1.x `ChunkedPreviewLifecycle`
- `drawMultipleIndexedPreview()`: Uses `RenderPass.Draw` entries and `pass.drawMultipleIndexed(...)`
- `writeDynamicUniforms()`: Uses `dynamicUniforms.writeTransform(mvMatrix, colorTint, zeroVec, normalMatrix)`; the `lineWidth` parameter is ignored because 26.1.x no longer uses the same dynamic uniform shape as 1.21.11
- `getBlockAtlasTextureView()`: Uses `client.atlasManager.getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS)`, then binds `atlas.textureView` with `atlas.sampler`
- GPU preview drawing must use `RenderSystem.getModelViewMatrix()` for the active model-view transform; using the matrix stack alone caused previews to render offset from the world and disappear when camera direction changed
- Chunked preview sessions exist in `src/compat-26_1/kotlin/axion/client/render/gpu/`

#### 26.1.x Preview Block Render View
- 26.1.x block tessellation needs a `BlockAndTintGetter`, not the older 1.21.x render-view shape.
- Required methods include `getLightEngine()`, `getBrightness(layer: LightType, pos: BlockPos): Int`, `cardinalLighting()`, `getBlockTint(...)`, `getHeight()`, and `getMinY()`.
- Preview render views intentionally return air outside the preview buffer. Returning real world blocks outside the buffer can make adjacent real-world geometry occlude or cull preview faces.
- Preview render views force brightness to `15` so selected underground dirt/stone does not preview as missing or black under grass and other top-lit blocks.
- `getBlockState(pos.above())` is treated specially during per-block tessellation: translucent blocks above the block being rendered are hidden as air to prevent overlay/culling artifacts.
- Implemented in `src/compat-26_1/kotlin/axion/client/render/PreviewBlockTessellator.kt` and `src/compat-26_1/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt`.

#### 26.1.x Move-Origin Glass Overlay
- The old CPU/legacy overlay path becomes very slow for large selections and can freeze the client for several seconds.
- Move-source glass overlays now force the chunked GPU preview path via `BlockPreviewPipeline.OverlayScene.forceChunked`.
- `PlacementPreviewRenderer` should set `forceChunked = true` for move-source overlays.
- Large overlays still use the same textured block preview route, so the glass overlay depends on the 26.1.x block atlas texture binding described above.
- Preview cache invalidation for 26.1.x is routed through `ChunkedPreviewLifecycle.closeAll()` so disconnect/reload paths release active GPU buffers.
- The 26.1.x `DrawContext` shim includes a `MatrixStackAdapter` for HUD overlays because `GuiGraphicsExtractor.pose()` returns `Matrix3x2fStack`, not the older 3D matrix stack.

#### 26.1.x Entity Serialization
- Entity capture uses `TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess())` and `entity.save(output)`.
- Entity recreation uses `TagValueInput.create(...)` plus `EntityType.loadEntityRecursive(...)` with an `EntityProcessor`.
- This restores local singleplayer entity clone/stack behavior when Copy Entities is enabled.

## Mixin Injection Points

Mixin targets are separated by version source sets:

### 1.21.x Mixins
- Located in `src/client/kotlin/axion/client/mixin/`
- Target 1.21.x client classes
- Examples: Mouse input, keybinding, rendering hooks

### 26.1.x Mixins
- Located in `src/compat-26_1/kotlin/axion/client/mixin/`
- Target 26.1.x client classes with official namespace
- Need to handle renamed packages (e.g., `net.minecraft.util.math` → `net.minecraft.core`)

## Type Aliases and Extension Functions

### BlockPos/Vec3i Compatibility (26.1.x)
Located in `src/compat-26_1/kotlin/net/minecraft/util/math/Aliases.kt` and `src/client/kotlin/axion/client/compat/BlockPosExtensions.kt`:

- `BlockPos.add()`: Extension function for vector addition
- `BlockPos.toImmutable()`: Extension function for immutability
- `Vec3i.add()`: Extension function for vector addition
- `MutableBlockPos`: Typealias to `net.minecraft.core.BlockPos.MutableBlockPos`
- `unpackLongX/Y/Z(packed)`: Top-level functions for packed long unpacking
- `blockPosIterate(min, max)`: Top-level function for iteration (replaces `BlockPos.Companion.iterate`)
- `blockPosOfFloored(pos)`: Top-level function for floored position (replaces `BlockPos.Companion.ofFloored`)
- `blockPosFromLong(packed)`: Top-level function for creating BlockPos from packed long (replaces `BlockPos.fromLong`)
- `ORIGIN`: Constant for `BlockPos(0, 0, 0)` (replaces `BlockPos.ORIGIN`)

**Reason:** Kotlin typealias limitations prevent companion object method bridging, so top-level functions are used instead.
The 26.1 alias surface is intentionally kept compact: prefer typealiases in `net.minecraft.*` shims and place helper/extension behavior in `axion.client.compat`.

## Known Issues and TODO

### 26.1.x Rendering
- **Status:** GPU preview and move-origin glass overlay are ported for the Fabric client.
- **Findings:**
  - Preserve raw GUI texture blitting for non-atlas resources; do not route `textures/gui/*.png` through `blitSprite(...)`.
  - Use `RenderSystem.getModelViewMatrix()` in 26.1.x GPU preview draw code to avoid world-space offset/camera culling issues.
  - Use `BlockAndTintGetter` implementations that return air outside preview state maps and brightness `15`.
  - Force large move-source glass overlays through the chunked GPU path.
  - GUI/HUD transforms are 2D `Matrix3x2fStack`-based; expose adapter methods in shims rather than leaking official 26.1.x GUI types into common HUD code.
- **Remaining Risk:** Fabric dedicated server support for 26.1.x is still planned work, and several older compatibility stubs remain outside the client preview path.

### 26.1.x BlockState Parsing
- **Status:** Implemented for the Fabric client.
- **Remaining Risk:** Server-side Fabric 26.1.x support is still planned, but its operation service already uses the same `BlockStateParser.parseForBlock(...)` API shape.

### 26.1.x Entity Loading
- **Status:** Implemented for Fabric client singleplayer clone/stack operations
- **Remaining Risk:** Dedicated Fabric server support for 26.1.x is still planned work

## Paper Plugin Compatibility

The Paper plugin has separate compatibility considerations:

- Protocol changes between Paper versions
- Registry manager access differences
- Block entity serialization differences

Currently, the plugin uses reflection-based approaches for version compatibility where needed.
