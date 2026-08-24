# NeoForge migration status (feat/neoforge)

Last updated: 2026-08-24 (WIP #2). Compile state: `:neoforge:compileKotlin` = **728 errors**
(down from 1451 after WIP #1; fabric untouched and green).

## What was done

1. **gradle.properties**: `neoforge_version` 21.11.8 → **21.11.45** (21.11.8 never existed).
   Note: NeoForge has NO 1.21.9 release at all — series jumped 21.8.x → 21.10.x. The
   "modern" range can only ship NeoForge for 1.21.10 (`21.10.64`) and 1.21.11 (`21.11.45`).

2. **settings.gradle.kts**: moddev plugin 2.0.49-beta → **2.0.144**. The old version hits a
   fatal NeoForm `ZipException: STORED entry missing size...` bug.

3. **neoforge/build.gradle.kts**: rewritten — common-source wiring, jar manifest,
   version→NeoForge selection (1.21.10→21.10.64, 1.21.11→21.11.45).

4. **The root blocker**: all shared code was written in **Yarn** mappings; ModDevGradle only
   compiles against **Mojmap**. Fabric Loom hides this for fabric. Resolution chosen:
   per-loader source copies — `/common` stays Yarn (inlined by fabric), while
   `neoforge/src/mojmap-common/` holds a Mojmap copy of common, and `neoforge/src/**`
   sources were migrated Yarn→Mojmap by script.

5. **Migration toolchain** (`neoforge/migration/`, mappings cached in /tmp/opencode/mappings —
   re-downloadable):
   - `build_rename_map.py`: joins yarn tiny v2 + intermediary v2 + Mojang proguard txt via
     official namespace, overload/descriptor-aware. Produces rename.json: class renames +
     ~36k unambiguous member renames.
   - `apply_renames.py`: applies to Kotlin sources with guards:
     * class renames on imports + qualified refs + imported simple names
     * member renames only on `.member` access or bare calls, never definitions
     * GLOBAL_PROTECTED: our enum entries / ALL_CAPS consts are never renamed
   - Re-run procedure: restore `neoforge/src` (git) + recreate `mojmap-common` from
     `common/src/main/kotlin`, then run apply_renames.py over `neoforge/src`.
     WARNING: mojmap-common is untracked — `git checkout` does NOT restore it.

## What remains (~728 diagnostics, concentrated in ~10 files)

Top files: VersionCompatImpl.kt (96), AxionHotbarHud.kt (59), MagicSelectRule.kt (42),
AxionBlockTessellator.kt (41), PreviewDirectionArrowRenderer.kt (32). These are genuine
API-shape divergences no mapping table covers.

### Patterns already fixed (do not regress)
- `client.world` → `client.level`; entity `.world` → `.level()`
- `client.server` → `client.singleplayerServer`; `client.interactionManager` → `client.gameMode`
- `GLFW.PRESS/KEY_*SHIFT` → real LWJGL names (LWJGL is never remapped)
- fabric-only `Method.wasAccessibleSinceLastSave` assignments stripped (interface injection)
- package corruption repaired: `axion.common.history`, `blaze3d.vertex` (was addVertex)
- `Identifier` IS `net.minecraft.resources.Identifier` in 1.21.11 (Mojang renamed
  ResourceLocation → Identifier upstream!). Factories: `Identifier.of(ns, path)`,
  `.parse(s)`, `fromNamespaceAndPath`.
- `RegistryOps.of` → `RegistryOps.create`
- `.dimensions(` → `.bounds(`; `GuiGraphics.guiWidth/guiHeight`;
  Window keeps `guiScaledWidth`
- `ClipContext.ShapeType/FluidHandling` → `ClipContext.Block/Fluid`
- `gameRenderer.camera` → `mainCamera`; `client.mouse` → `mouseHandler`
- `ci.returnValue` (CallbackInfoReturnable) — beware bogus map entries
  (`returnValue→sum`, `sumOf→accumulate`, `currentTimeMillis→currentTimeMs`,
  `removeLast→discardLast`, `stack→DATA_ITEM`) — all reverted; if new bogus renames
  appear, check rename.json for stdlib/JDK collisions.
- MatrixStack.Entry import → `com.mojang.blaze3d.vertex.PoseStack.Pose`
- `font.getWidth(` → `font.width(`; Direction offsetX/Y/Z → stepX/Y/Z;
  VertexConsumer `.color/.normal` → `.setColor/.setNormal`

### Suggested next loop
1. `grep "^e:" errs | grep -oE "'[a-zA-Z.]+'" | sort | uniq -c | sort -rn`
2. Fix the top pattern with a scoped python rule or by hand.
3. Recompile, repeat. Each fixed pattern clears ~5-30 diagnostics.
VersionCompatImpl needs the most thought: it bridges text/item/registry APIs that changed
shape between mappings (EditBox.value, Font.width, ItemStack hints, drawItem → renderItem...).

## Build commands

```bash
# neoforge (Java 21 required for the toolchain steps)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :neoforge:compileKotlin

# fabric regression check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :fabric:compileKotlin
```

Gradle daemons may spawn with Java 26 (default JVM) which breaks NeoForm zip handling —
always pin `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` or export JAVA_HOME first.
