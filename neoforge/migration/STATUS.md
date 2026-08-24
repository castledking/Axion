# NeoForge migration status (feat/neoforge)

Last updated: 2026-08-24. Compile state: `:neoforge:compileKotlin` = **1451 errors** (down
from "nothing resolves"); fabric build untouched and green.

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

## What remains (~1451 diagnostics, concentrated in ~12 files)

Top files: ClientModeController.kt (208), VersionCompatImpl.kt (173),
PulsingCuboidRenderer.kt (163), MagicSelectRule.kt (163), SelectionBounds.kt (125),
LocalEntityCloneService.kt (115). These are genuine API-shape divergences no mapping table
covers, e.g.:

| Yarn idiom | Mojmap equivalent |
|---|---|
| `client.world` (field, nullable) | `minecraft.level` (field, non-null) |
| `player.world` / `entity.world` | `player.level()` / `entity.level()` (method!) |
| `drawContext.textRenderer` | `guiGraphics.getFont()` / `.font` per receiver |
| `Button.builder(msg){}.dimensions(x,y,w,h)` | `Button.builder(msg).bounds(x,y,w,h).build()` with `.onPress{}` |
| `BufferBuilder.vertex(...)` | `addVertex(...)` (present but receiver-typed) |
| ambiguous `camera`, `pos`, `dimensions`, `INSTANCE` | depends on owner class |

Suggested loop for the remaining work: compile → group errors by message+file → fix the
top pattern → repeat. Each fixed pattern typically clears 20–60 diagnostics.

## Build commands

```bash
# neoforge (Java 21 required for the toolchain steps)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :neoforge:compileKotlin

# fabric regression check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :fabric:compileKotlin
```

Gradle daemons may spawn with Java 26 (default JVM) which breaks NeoForm zip handling —
always pin `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk` or export JAVA_HOME first.
