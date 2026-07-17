#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MOD_VERSION="${MOD_VERSION:-$(awk -F= '/^mod_version=/{print $2}' gradle.properties)}"

# Multi-range release strategy:
#   range "mc1_21_0_1" → covers 1.21 .. 1.21.1, compiled against 1.21.1
#   range "mc1_21_2_3" → covers 1.21.2 .. 1.21.3, compiled against 1.21.3
#   range "mc1_21_4" → covers 1.21.4, compiled against 1.21.4
#   range "mc1_21_5" → covers 1.21.5, compiled against 1.21.5
#   range "legacy"   → covers 1.21.6 .. 1.21.8, compiled against 1.21.7
#   range "modern"  → covers 1.21.9 .. 1.21.11, compiled against 1.21.11
#   range "mc26_1_x" → covers 26.1 .. 26.1.2, compiled against 26.1
# Within each range, cross-version mixin compatibility is handled by `require = 0`
# dual-signature injections.
SUPPORTED_RANGES=(
    "mc1_21_0_1"
    "mc1_21_2_3"
    "mc1_21_4"
    "mc1_21_5"
    "legacy"
    "modern"
    "mc26_1_x"
)

resolve_compile_version() {
    case "$1" in
    "mc1_21_0_1") echo "1.21.1" ;;
    "mc1_21_2_3") echo "1.21.3" ;;
    "mc1_21_4") echo "1.21.4" ;;
    "mc1_21_5") echo "1.21.5" ;;
    "mc1_21_6") echo "1.21.6" ;;
    "mc1_21_7") echo "1.21.7" ;;
    "mc1_21_8") echo "1.21.8" ;;
    "mc1_21_9") echo "1.21.9" ;;
    "mc1_21_10") echo "1.21.10" ;;
    "mc1_21_11") echo "1.21.11" ;;
    "legacy") echo "1.21.7" ;;
    "modern") echo "1.21.11" ;;
    "mc26_1_x") echo "26.1" ;;
    *) return 1 ;;
    esac
}

resolve_yarn_mappings() {
    case "$1" in
    "1.21.1") echo "1.21.1+build.3" ;;
    "1.21.3") echo "1.21.3+build.2" ;;
    "1.21.4") echo "1.21.4+build.8" ;;
    "1.21.5") echo "1.21.5+build.1" ;;
    "1.21.6") echo "1.21.6+build.1" ;;
    "1.21.7") echo "1.21.7+build.1" ;;
    "1.21.8") echo "1.21.8+build.1" ;;
    "1.21.9") echo "1.21.9+build.1" ;;
    "1.21.10") echo "1.21.10+build.3" ;;
    "1.21.11") echo "1.21.11+build.4" ;;
    "26.1") echo "" ;;
    *) return 1 ;;
    esac
}

resolve_loader_version() {
    case "$1" in
    "1.21.1") echo "0.16.5" ;;
    "1.21.3") echo "0.16.10" ;;
    "1.21.4") echo "0.16.10" ;;
    "1.21.5") echo "0.16.12" ;;
    "1.21.6") echo "0.16.13" ;;
    "1.21.7") echo "0.16.13" ;;
    "1.21.8") echo "0.16.14" ;;
    "1.21.9") echo "0.17.3" ;;
    "1.21.10") echo "0.17.3" ;;
    "1.21.11") echo "0.18.4" ;;
    "26.1") echo "0.19.2" ;;
    *) return 1 ;;
    esac
}

resolve_fabric_version() {
    case "$1" in
    "1.21.1") echo "0.102.0+1.21.1" ;;
    "1.21.3") echo "0.106.1+1.21.3" ;;
    "1.21.4") echo "0.119.4+1.21.4" ;;
    "1.21.5") echo "0.119.5+1.21.5" ;;
    "1.21.6") echo "0.128.2+1.21.6" ;;
    "1.21.7") echo "0.129.0+1.21.7" ;;
    "1.21.8") echo "0.131.0+1.21.8" ;;
    "1.21.9") echo "0.134.1+1.21.9" ;;
    "1.21.10") echo "0.138.4+1.21.10" ;;
    "1.21.11") echo "0.141.3+1.21.11" ;;
    "26.1") echo "0.145.1+26.1" ;;
    *) return 1 ;;
    esac
}

resolve_fabric_kotlin_version() {
    case "$1" in
    "1.21.1") echo "1.12.3+kotlin.2.0.21" ;;
    "1.21.3") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.4") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.5") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.6") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.7") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.8") echo "1.13.0+kotlin.2.1.0" ;;
    "1.21.9") echo "1.13.9+kotlin.2.3.10" ;;
    "1.21.10") echo "1.13.9+kotlin.2.3.10" ;;
    "1.21.11") echo "1.13.9+kotlin.2.3.10" ;;
    "26.1") echo "1.13.11+kotlin.2.3.21" ;;
    *) return 1 ;;
    esac
}

resolve_modmenu_version() {
    case "$1" in
    "1.21.1") echo "11.0.3" ;;
    "1.21.3") echo "12.0.1" ;;
    "1.21.4") echo "13.0.4" ;;
    "1.21.5") echo "14.0.0" ;;
    "1.21.6") echo "15.0.2" ;;
    "1.21.7") echo "15.0.2" ;;
    "1.21.8") echo "15.0.2" ;;
    "1.21.9") echo "16.0.0" ;;
    "1.21.10") echo "17.0.0-alpha.1" ;;
    "1.21.11") echo "17.0.0-beta.2" ;;
    "26.1") echo "18.0.0-beta.1" ;;
    *) return 1 ;;
    esac
}

run_gradle_with_retry() {
    local attempt=1
    local max_attempts=3
    local status

    while true; do
        if ./gradlew "$@"; then
            return 0
        else
            status=$?
        fi

        if ((attempt >= max_attempts)); then
            echo "Gradle failed after ${max_attempts} attempts." >&2
            return "$status"
        fi

        echo "Gradle attempt ${attempt} failed; retrying this range in $((attempt * 5)) seconds..." >&2
        sleep $((attempt * 5))
        ((attempt += 1))
    done
}

resolve_paper_version() {
    case "$1" in
    "1.21.1") echo "1.21.1-R0.1-SNAPSHOT" ;;
    "1.21.3") echo "1.21.3-R0.1-SNAPSHOT" ;;
    "1.21.4") echo "1.21.4-R0.1-SNAPSHOT" ;;
    "1.21.5") echo "1.21.5-R0.1-SNAPSHOT" ;;
    "1.21.6") echo "1.21.6-R0.1-SNAPSHOT" ;;
    "1.21.7") echo "1.21.7-R0.1-SNAPSHOT" ;;
    "1.21.8") echo "1.21.8-R0.1-SNAPSHOT" ;;
    "1.21.9") echo "1.21.9-R0.1-SNAPSHOT" ;;
    "1.21.10") echo "1.21.10-R0.1-SNAPSHOT" ;;
    "1.21.11") echo "1.21.11-R0.1-SNAPSHOT" ;;
    "26.1") echo "26.1.2.build.63-stable" ;;
    *) return 1 ;;
    esac
}

resolve_loom_version() {
    case "$1" in
    "26.1") echo "1.16-SNAPSHOT" ;;
    *) echo "1.15.4" ;;
    esac
}

resolve_paperweight_version() {
    case "$1" in
    "26.1") echo "2.0.0-SNAPSHOT" ;;
    *) echo "2.0.0-beta.19" ;;
    esac
}

resolve_range_tag() {
    case "$1" in
    "mc1_21_0_1") echo "mc1.21-1.21.1" ;;
    "mc1_21_2_3") echo "mc1.21.2-1.21.3" ;;
    "mc1_21_4") echo "mc1.21.4" ;;
    "mc1_21_5") echo "mc1.21.5" ;;
    "mc1_21_6") echo "mc1.21.6" ;;
    "legacy") echo "mc1.21.6-1.21.8" ;;
    "mc1_21_8") echo "mc1.21.8" ;;
    "mc1_21_9") echo "mc1.21.9" ;;
    "mc1_21_10") echo "mc1.21.10" ;;
    "modern") echo "mc1.21.9-1.21.11" ;;
    "mc26_1_x") echo "mc26.1.x" ;;
    *) return 1 ;;
    esac
}

resolve_metadata_version_range() {
    case "$1" in
    "mc1_21_0_1") echo ">=1.21 <=1.21.1" ;;
    "mc1_21_2_3") echo ">=1.21.2 <=1.21.3" ;;
    "mc1_21_4") echo "1.21.4" ;;
    "mc1_21_5") echo "1.21.5" ;;
    "mc1_21_6") echo "1.21.6" ;;
    "legacy") echo ">=1.21.6 <=1.21.8" ;;
    "mc1_21_8") echo "1.21.8" ;;
    "mc1_21_9") echo "1.21.9" ;;
    "mc1_21_10") echo "1.21.10" ;;
    "modern") echo ">=1.21.9 <=1.21.11" ;;
    "mc26_1_x") echo ">=26.1 <=26.1.2" ;;
    *) return 1 ;;
    esac
}

cleanup_range_jars() {
    local range_tag="$1"
    local mod_output_dir="build/libs/${range_tag}"
    local paper_output_dir="paper-plugin/build/libs/${range_tag}"

    mkdir -p "${mod_output_dir}" "${paper_output_dir}"

    # Remove stale release jars for this target range before building. Gradle's
    # own caches, dev jars, dependencies, and jars for other ranges are left
    # untouched.
    rm -f \
        "build/libs/Axion-v"*"-${range_tag}.jar" \
        "${mod_output_dir}/Axion-v"*"-${range_tag}.jar" \
        "${paper_output_dir}/AxionPaper-v"*"-${range_tag}.jar"

    # The Paper subproject emits a compile-version jar first, then this script
    # renames it into the range directory. Remove only AxionPaper outputs from
    # the staging directory so an older version cannot be picked up by ls/head.
    rm -f "paper-plugin/build/libs/AxionPaper-v"*.jar
}

build_range() {
    local range="$1"
    local compile_version
    local yarn_mappings
    local loader_version
    local fabric_version
    local fabric_kotlin_version
    local modmenu_version
    local paper_version
    local loom_version
    local paperweight_version
    local range_tag
    local metadata_version_range
    local mod_jar
    local paper_jar
    local mod_output_dir
    local paper_output_dir

    compile_version="$(resolve_compile_version "$range")"
    yarn_mappings="$(resolve_yarn_mappings "$compile_version")"
    loader_version="$(resolve_loader_version "$compile_version")"
    fabric_version="$(resolve_fabric_version "$compile_version")"
    fabric_kotlin_version="$(resolve_fabric_kotlin_version "$compile_version")"
    modmenu_version="$(resolve_modmenu_version "$compile_version")"
    paper_version="$(resolve_paper_version "$compile_version")"
    loom_version="$(resolve_loom_version "$compile_version")"
    paperweight_version="$(resolve_paperweight_version "$compile_version")"
    range_tag="$(resolve_range_tag "$range")"
    metadata_version_range="$(resolve_metadata_version_range "$range")"
    mod_jar="Axion-v${MOD_VERSION}-${range_tag}.jar"
    paper_jar="AxionPaper-v${MOD_VERSION}-${range_tag}.jar"
    local output_dir_tag="${range_tag}"
    mod_output_dir="build/libs/${output_dir_tag}"
    paper_output_dir="paper-plugin/build/libs/${output_dir_tag}"

    echo
    echo "==> Building Axion v${MOD_VERSION} for range ${range_tag} (compiled against MC ${compile_version})"
    cleanup_range_jars "${range_tag}"
    wipe_kotlin_caches

    local gradle_tasks=(remapJar :paper-plugin:jar verifyGpuPreviewCoverage)
    if [[ "$range" == "mc26_1_x" ]]; then
        echo "    Fabric client/mod 26.1.x builds in the official namespace; using jar instead of remapJar."
        gradle_tasks=(jar :paper-plugin:jar verifyGpuPreviewCoverage)
    fi

    run_gradle_with_retry "${gradle_tasks[@]}" \
        -Pmod_version="${MOD_VERSION}" \
        -Pminecraft_version="${compile_version}" \
        -Pyarn_mappings="${yarn_mappings}" \
        -Ploader_version="${loader_version}" \
        -Pfabric_version="${fabric_version}" \
        -Pfabric_kotlin_version="${fabric_kotlin_version}" \
        -Pmodmenu_version="${modmenu_version}" \
        -Ppaper_version="${paper_version}" \
        -Ploom_version="${loom_version}" \
        -Ppaperweight_version="${paperweight_version}" \
        -Paxion_artifact_tag="${range_tag}" \
        -Paxion_minecraft_version_range="${metadata_version_range}"

    if [[ -f "build/libs/${mod_jar}" ]]; then
        mv -f "build/libs/${mod_jar}" "${mod_output_dir}/${mod_jar}"
    fi
    # Paper plugin emits a single-version filename; rename to range-style for output
    local actual_paper_jar
    actual_paper_jar="$(find paper-plugin/build/libs -maxdepth 1 -type f -name 'AxionPaper-*.jar' -print -quit 2>/dev/null)"
    if [[ -n "${actual_paper_jar}" ]]; then
        mv -f "${actual_paper_jar}" "${paper_output_dir}/${paper_jar}"
    fi

    echo "Built:"
    if [[ -f "${mod_output_dir}/${mod_jar}" ]]; then
        echo "  ${mod_output_dir}/${mod_jar}"
    fi
    if [[ -n "${actual_paper_jar}" ]]; then
        echo "  ${paper_output_dir}/${paper_jar}"
    fi
}

# Wipe Kotlin incremental compilation caches so new source files are
# detected.  Avoids full `clean` which would delete output directories
# needed for jar gathering in the release workflow.
wipe_kotlin_caches() {
    rm -rf \
        build/tmp/compileClientKotlin \
        build/tmp/compileKotlin \
        build/tmp/kotlin-classes \
        build/tmp/kotlinClientClasses \
        build/classes/kotlin \
        build/classes/kotlinClient
}

print_menu() {
    echo "Select a build target:"
    echo "  1) Minecraft 1.21 - 1.21.1"
    echo "  2) Minecraft 1.21.2 - 1.21.3"
    echo "  3) Minecraft 1.21.4"
    echo "  4) Minecraft 1.21.5"
    echo "  5) Legacy range (Minecraft 1.21.6 - 1.21.8)"
    echo "  6) Modern range (Minecraft 1.21.9 - 1.21.11)"
    echo "  7) Minecraft 26.1.x"
    echo "  8) Exact Minecraft 1.21.6"
    echo "  9) Exact Minecraft 1.21.8"
    echo "  10) Exact Minecraft 1.21.9"
    echo "  11) Exact Minecraft 1.21.10"
    echo "  12) All ranges"
    echo "  q) Cancel"
}

wipe_kotlin_caches

if [[ $# -gt 0 ]]; then
    choice="$1"
else
    print_menu
    read -r -p "> " choice
fi

case "$choice" in
 1 | 1.21-1.21.1 | 1.21.1-1.21.1 | 1.21.1 | mc1.21-1.21.1 | mc1_21_0_1 | MC1_21_0_1)
    build_range "mc1_21_0_1"
    ;;
 2 | 1.21.2-1.21.3 | 1.21.2-3 | 1.21.3 | mc1.21.2-1.21.3 | mc1_21_2_3 | MC1_21_2_3)
    build_range "mc1_21_2_3"
    ;;
 3 | 1.21.4 | mc1.21.4 | mc1_21_4 | MC1_21_4)
    build_range "mc1_21_4"
    ;;
 4 | 1.21.5 | mc1.21.5 | mc1_21_5 | MC1_21_5)
    build_range "mc1_21_5"
    ;;
 5 | legacy | LEGACY)
    build_range "legacy"
    ;;
 6 | modern | MODERN)
    build_range "modern"
    ;;
 7 | 26.1 | 26.1.x | mc26.1.x | mc26_1_x | MC26_1_X)
    build_range "mc26_1_x"
    ;;
 8 | 1.21.6 | mc1.21.6 | mc1_21_6 | MC1_21_6)
    build_range "mc1_21_6"
    ;;
 9 | 1.21.8 | mc1.21.8 | mc1_21_8 | MC1_21_8)
    build_range "mc1_21_8"
    ;;
 10 | 1.21.9 | mc1.21.9 | mc1_21_9 | MC1_21_9)
    build_range "mc1_21_9"
    ;;
 11 | 1.21.10 | mc1.21.10 | mc1_21_10 | MC1_21_10)
    build_range "mc1_21_10"
    ;;
 12 | all | ALL | both | BOTH)
    for range in "${SUPPORTED_RANGES[@]}"; do
        build_range "$range"
    done
    ;;
 q | Q | quit | QUIT)
    echo "Cancelled."
    exit 0
    ;;
 *)
    echo "Unknown selection: $choice" >&2
    exit 1
    ;;
esac
