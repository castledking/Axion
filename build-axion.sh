#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MOD_VERSION="${MOD_VERSION:-$(awk -F= '/^mod_version=/{print $2}' gradle.properties)}"

SUPPORTED_VERSIONS=(
  "1.21.8"
  "1.21.9"
  "1.21.10"
  "1.21.11"
)

resolve_yarn_mappings() {
  case "$1" in
    "1.21.8") echo "1.21.8+build.1" ;;
    "1.21.9") echo "1.21.9+build.1" ;;
    "1.21.10") echo "1.21.10+build.3" ;;
    "1.21.11") echo "1.21.11+build.4" ;;
    *) return 1 ;;
  esac
}

resolve_fabric_version() {
  case "$1" in
    "1.21.8") echo "0.131.0+1.21.8" ;;
    "1.21.9") echo "0.134.1+1.21.9" ;;
    "1.21.10") echo "0.138.4+1.21.10" ;;
    "1.21.11") echo "0.141.3+1.21.11" ;;
    *) return 1 ;;
  esac
}

resolve_paper_version() {
  case "$1" in
    "1.21.8") echo "1.21.8-R0.1-SNAPSHOT" ;;
    "1.21.9") echo "1.21.9-R0.1-SNAPSHOT" ;;
    "1.21.10") echo "1.21.10-R0.1-SNAPSHOT" ;;
    "1.21.11") echo "1.21.11-R0.1-SNAPSHOT" ;;
    *) return 1 ;;
  esac
}

build_version() {
  local version="$1"
  local yarn_mappings
  local fabric_version
  local paper_version
  local mod_jar
  local paper_jar
  local mod_output_dir
  local paper_output_dir

  yarn_mappings="$(resolve_yarn_mappings "$version")"
  fabric_version="$(resolve_fabric_version "$version")"
  paper_version="$(resolve_paper_version "$version")"
  mod_jar="Axion-v${MOD_VERSION}-mc${version}.jar"
  paper_jar="AxionPaper-v${MOD_VERSION}-mc${version}.jar"
  mod_output_dir="build/libs/${version}"
  paper_output_dir="paper-plugin/build/libs/${version}"

  echo
  echo "==> Building Axion v${MOD_VERSION} for Minecraft ${version}"
  local gradle_tasks=(remapJar :paper-plugin:jar)

  ./gradlew "${gradle_tasks[@]}" \
    -Pmod_version="${MOD_VERSION}" \
    -Pminecraft_version="${version}" \
    -Pyarn_mappings="${yarn_mappings}" \
    -Pfabric_version="${fabric_version}" \
    -Ppaper_version="${paper_version}"

  mkdir -p "${mod_output_dir}" "${paper_output_dir}"
  mv -f "build/libs/${mod_jar}" "${mod_output_dir}/${mod_jar}"
  mv -f "paper-plugin/build/libs/${paper_jar}" "${paper_output_dir}/${paper_jar}"

  echo "Built:"
  echo "  ${mod_output_dir}/${mod_jar}"
  echo "  ${paper_output_dir}/${paper_jar}"
}

print_menu() {
  echo "Select a build target:"
  echo "  1) Minecraft 1.21.8"
  echo "  2) Minecraft 1.21.9"
  echo "  3) Minecraft 1.21.10"
  echo "  4) Minecraft 1.21.11"
  echo "  5) All supported versions"
  echo "  q) Cancel"
}

if [[ $# -gt 0 ]]; then
  choice="$1"
else
  print_menu
  read -r -p "> " choice
fi

case "$choice" in
  1|"1.21.8")
    build_version "1.21.8"
    ;;
  2|"1.21.9")
    build_version "1.21.9"
    ;;
  3|"1.21.10")
    build_version "1.21.10"
    ;;
  4|"1.21.11")
    build_version "1.21.11"
    ;;
  5|all|ALL)
    for version in "${SUPPORTED_VERSIONS[@]}"; do
      build_version "$version"
    done
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
