#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MOD_VERSION="${MOD_VERSION:-$(awk -F= '/^mod_version=/{print $2}' gradle.properties)}"

# Two-range release strategy (mirroring Axiom's multi-version-jar approach):
#   range "mc1_21_5" → covers 1.21.5, compiled against 1.21.5
#   range "legacy"   → covers 1.21.6 .. 1.21.8, compiled against 1.21.7
#   range "modern"  → covers 1.21.9 .. 1.21.11, compiled against 1.21.11
# Within each range, cross-version mixin compatibility is handled by `require = 0`
# dual-signature injections.
SUPPORTED_RANGES=(
  "mc1_21_5"
  "legacy"
  "modern"
)

resolve_compile_version() {
  case "$1" in
    "mc1_21_5") echo "1.21.5" ;;
    "legacy") echo "1.21.7" ;;
    "modern") echo "1.21.11" ;;
    *) return 1 ;;
  esac
}

resolve_yarn_mappings() {
  case "$1" in
    "1.21.5") echo "1.21.5+build.1" ;;
    "1.21.7") echo "1.21.7+build.1" ;;
    "1.21.11") echo "1.21.11+build.4" ;;
    *) return 1 ;;
  esac
}

resolve_fabric_version() {
  case "$1" in
    "1.21.5") echo "0.119.5+1.21.5" ;;
    "1.21.7") echo "0.129.0+1.21.7" ;;
    "1.21.11") echo "0.141.3+1.21.11" ;;
    *) return 1 ;;
  esac
}

resolve_paper_version() {
  case "$1" in
    "1.21.5") echo "1.21.5-R0.1-SNAPSHOT" ;;
    "1.21.7") echo "1.21.7-R0.1-SNAPSHOT" ;;
    "1.21.11") echo "1.21.11-R0.1-SNAPSHOT" ;;
    *) return 1 ;;
  esac
}

resolve_range_tag() {
  case "$1" in
    "mc1_21_5") echo "mc1.21.5" ;;
    "legacy") echo "mc1.21.6-1.21.8" ;;
    "modern") echo "mc1.21.9-1.21.11" ;;
    *) return 1 ;;
  esac
}

build_range() {
  local range="$1"
  local compile_version
  local yarn_mappings
  local fabric_version
  local paper_version
  local range_tag
  local mod_jar
  local paper_jar
  local mod_output_dir
  local paper_output_dir

  compile_version="$(resolve_compile_version "$range")"
  yarn_mappings="$(resolve_yarn_mappings "$compile_version")"
  fabric_version="$(resolve_fabric_version "$compile_version")"
  paper_version="$(resolve_paper_version "$compile_version")"
  range_tag="$(resolve_range_tag "$range")"
  mod_jar="Axion-v${MOD_VERSION}-${range_tag}.jar"
  paper_jar="AxionPaper-v${MOD_VERSION}-${range_tag}.jar"
  local output_dir_tag="${range_tag}"
  mod_output_dir="build/libs/${output_dir_tag}"
  paper_output_dir="paper-plugin/build/libs/${output_dir_tag}"

  echo
  echo "==> Building Axion v${MOD_VERSION} for range ${range_tag} (compiled against MC ${compile_version})"
  local gradle_tasks=(remapJar :paper-plugin:jar)

  ./gradlew "${gradle_tasks[@]}" \
    -Pmod_version="${MOD_VERSION}" \
    -Pminecraft_version="${compile_version}" \
    -Pyarn_mappings="${yarn_mappings}" \
    -Pfabric_version="${fabric_version}" \
    -Ppaper_version="${paper_version}"

  mkdir -p "${mod_output_dir}" "${paper_output_dir}"
  mv -f "build/libs/${mod_jar}" "${mod_output_dir}/${mod_jar}"
  # Paper plugin emits a single-version filename; rename to range-style for output
  local actual_paper_jar
  actual_paper_jar="$(ls -1 paper-plugin/build/libs/AxionPaper-*.jar 2>/dev/null | head -1)"
  if [[ -n "${actual_paper_jar}" ]]; then
    mv -f "${actual_paper_jar}" "${paper_output_dir}/${paper_jar}"
  fi

  echo "Built:"
  echo "  ${mod_output_dir}/${mod_jar}"
  if [[ -n "${actual_paper_jar}" ]]; then
    echo "  ${paper_output_dir}/${paper_jar}"
  fi
}

print_menu() {
  echo "Select a build target:"
  echo "  1) Legacy range (Minecraft 1.21.6 - 1.21.8)"
  echo "  2) Modern range (Minecraft 1.21.9 - 1.21.11)"
  echo "  3) Both ranges"
  echo "  4) Minecraft 1.21.5"
  echo "  q) Cancel"
}

if [[ $# -gt 0 ]]; then
  choice="$1"
else
  print_menu
  read -r -p "> " choice
fi

case "$choice" in
  1|legacy|LEGACY)
    build_range "legacy"
    ;;
  2|modern|MODERN)
    build_range "modern"
    ;;
  3|all|ALL|both|BOTH)
    for range in "${SUPPORTED_RANGES[@]}"; do
      build_range "$range"
    done
    ;;
  4|1.21.5|mc1.21.5|mc1_21_5|MC1_21_5)
    build_range "mc1_21_5"
    ;;
  q|Q|quit|QUIT)
    echo "Cancelled."
    exit 0
    ;;
  *)
    echo "Unknown selection: $choice" >&2
    exit 1
    ;;
esac
