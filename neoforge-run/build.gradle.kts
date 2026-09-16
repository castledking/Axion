// Dev launcher for NeoForge clients, used by ./run-axion.sh.
//
// The :neoforge sources only compile against the 1.21.11 API; the 1.21.6-1.21.10
// jars are produced by rewriting the compiled 1.21.11 jar
// (neoforge/migration/build_1_21_10_jar.py). A source run can therefore not
// target those versions, so this project has no sources of its own: it starts
// a plain NeoForge client of the requested version, and run-axion.sh places the
// built (and, for older versions, rewritten) Axion jar in the run directory's
// mods/ folder. 1.21.11 goes through the same path so every version tests the
// jar that actually ships.
//
// Nothing is configured unless run-axion.sh passes -Paxion_neoforge_run_version,
// so ordinary builds never resolve a NeoForge distribution for this project.

plugins {
    id("net.neoforged.moddev")
}

val runNeoForgeVersion = (findProperty("axion_neoforge_run_version") as String?)?.takeIf { it.isNotBlank() }

if (runNeoForgeVersion != null) {
    val runDir = (findProperty("axion_run_dir") as String?)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("-Paxion_run_dir is required for NeoForge client runs")

    neoForge {
        version = runNeoForgeVersion

        runs {
            register("client") {
                client()
                gameDirectory.set(file(runDir))
            }
        }
    }
}
