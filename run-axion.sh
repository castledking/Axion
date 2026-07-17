#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

SUPPORTED_VERSION_LIST=("1.21" "1.21.1" "1.21.2" "1.21.3" "1.21.5" "1.21.6" "1.21.7" "1.21.8" "1.21.9" "1.21.10" "1.21.11" "26.1")

# Parse command line arguments
VERSION_ARG=""
ARGS_PROVIDED=false
if [[ $# -gt 0 ]]; then
    ARGS_PROVIDED=true
fi
if [[ $# -gt 0 && "$1" != -* && "$1" != "paper" && "$1" != "fabric" && "$1" != "with" ]]; then
    VERSION_ARG="$1"
    shift
fi

WITH_PAPER="${WITH_PAPER:-false}"
WITH_FABRIC="${WITH_FABRIC:-false}"
QUICKPLAY="${QUICKPLAY:-false}"
BUILD_FIRST="${BUILD_FIRST:-false}"
BUILD_ONLY="${BUILD_ONLY:-false}"
STARTED_SERVER_PIDS=()
STARTED_CLIENT_PIDS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        paper|with-paper|with_paper|--paper)
            WITH_PAPER=true
            ;;
        fabric|with-fabric|with_fabric|--fabric)
            WITH_FABRIC=true
            ;;
        quickplay|--quickplay)
            QUICKPLAY=true
            ;;
        build|--build)
            # Just build, don't launch
            BUILD_ONLY=true
            ;;
        with)
            ;;
        -h|--help)
            VERSION_ARG="--help"
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
    shift
done

# Interactive prompt when no arguments provided
if [[ "$ARGS_PROVIDED" == "false" && $# -eq 0 ]]; then
    echo "Axion Development Launcher"
    echo "=========================="
    echo ""
    echo "Select Minecraft version (comma-separated for multiple, e.g., '1,2,3,4'):"
    echo "  1) 1.21"
    echo "  2) 1.21.1"
    echo "  3) 1.21.2"
    echo "  4) 1.21.3"
    echo "  5) 1.21.5"
    echo "  6) 1.21.6"
    echo "  7) 1.21.7"
    echo "  8) 1.21.8"
    echo "  9) 1.21.9"
    echo " 10) 1.21.10"
    echo " 11) 1.21.11"
    echo " 12) 26.1 (default)"
    echo " 13) All versions"
    read -p "Enter choice [1-13 or comma-separated]: " version_choice

    # Convert comma-separated choices to version list
    if [[ "$version_choice" == *,* ]]; then
        IFS=',' read -r -a choices <<< "$version_choice"
        selected_versions=()
        for choice in "${choices[@]}"; do
            case "$choice" in
                1) selected_versions+=("1.21") ;;
                2) selected_versions+=("1.21.1") ;;
                3) selected_versions+=("1.21.2") ;;
                4) selected_versions+=("1.21.3") ;;
                5) selected_versions+=("1.21.5") ;;
                6) selected_versions+=("1.21.6") ;;
                7) selected_versions+=("1.21.7") ;;
                8) selected_versions+=("1.21.8") ;;
                9) selected_versions+=("1.21.9") ;;
                10) selected_versions+=("1.21.10") ;;
                11) selected_versions+=("1.21.11") ;;
                12) selected_versions+=("26.1") ;;
                13) selected_versions+=("all") ;;
                *) ;;
            esac
        done
        VERSION_ARG="${selected_versions[*]}"
        VERSION_ARG="${VERSION_ARG// /,}"
    else
        case "$version_choice" in
            1) VERSION_ARG="1.21" ;;
            2) VERSION_ARG="1.21.1" ;;
            3) VERSION_ARG="1.21.2" ;;
            4) VERSION_ARG="1.21.3" ;;
            5) VERSION_ARG="1.21.5" ;;
            6) VERSION_ARG="1.21.6" ;;
            7) VERSION_ARG="1.21.7" ;;
            8) VERSION_ARG="1.21.8" ;;
            9) VERSION_ARG="1.21.9" ;;
            10) VERSION_ARG="1.21.10" ;;
            11) VERSION_ARG="1.21.11" ;;
            12) VERSION_ARG="26.1" ;;
            13) VERSION_ARG="all" ;;
            *) VERSION_ARG="26.1" ;;
        esac
    fi

    echo ""
    read -p "Start Paper server? [y/N]: " paper_choice
    if [[ "$paper_choice" =~ ^[Yy]$ ]]; then
        WITH_PAPER=true
    fi

    read -p "Start Fabric server? [y/N]: " fabric_choice
    if [[ "$fabric_choice" =~ ^[Yy]$ ]]; then
        WITH_FABRIC=true
    fi

    read -p "Enable quickplay? [y/N]: " quickplay_choice
    if [[ "$quickplay_choice" =~ ^[Yy]$ ]]; then
        QUICKPLAY=true
    fi

    read -p "Build before launching? [y/N]: " build_choice
    if [[ "$build_choice" =~ ^[Yy]$ ]]; then
        BUILD_FIRST=true
    fi

    read -p "Build only (don't launch)? [y/N]: " build_only_choice
    if [[ "$build_only_choice" =~ ^[Yy]$ ]]; then
        BUILD_ONLY=true
        BUILD_FIRST=true
    fi

    echo ""
    echo "Configuration:"
    echo "  Version: $VERSION_ARG"
    echo "  Paper: $WITH_PAPER"
    echo "  Fabric: $WITH_FABRIC"
    echo "  Quickplay: $QUICKPLAY"
    echo "  Build: $BUILD_FIRST"
    echo "  Build-only: $BUILD_ONLY"
    echo ""
fi

# Default to 26.1 if no version specified
if [[ -z "$VERSION_ARG" ]]; then
    VERSION_ARG="26.1"
fi

# Display usage
if [[ "$VERSION_ARG" == "-h" || "$VERSION_ARG" == "--help" ]]; then
    echo "Usage: ./run-axion.sh [VERSIONS] [OPTIONS]"
    echo ""
    echo "VERSIONS: Minecraft version, comma-separated versions, or all"
    echo "  Supported: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1"
    echo "  Default: 26.1"
    echo ""
    echo "OPTIONS:"
    echo "  paper, --paper      Also start Paper server(s)"
    echo "  fabric, --fabric    Start Fabric server for 1.21.11"
    echo "  quickplay, --quickplay Auto-join server or latest world"
    echo "  build, --build      Build only, don't launch clients"
    echo ""
    echo "ENVIRONMENT VARIABLES:"
    echo "  WITH_PAPER=true     Also start Paper server(s)"
    echo "  WITH_FABRIC=true    Start Fabric server for 1.21.11"
    echo "  QUICKPLAY=true      Auto-join server or latest world"
    echo "  BUILD_FIRST=true    Build before launching (default: false)"
    echo "  BUILD_ONLY=true     Build only, don't launch clients"
    echo ""
    echo "INTERACTIVE MODE:"
    echo "  Running ./run-axion.sh without arguments shows an interactive prompt"
    echo ""
    echo "Examples:"
    echo "  ./run-axion.sh 26.1"
    echo "  ./run-axion.sh 1.21,1.21.1"
    echo "  ./run-axion.sh 1.21.6,1.21.7 paper"
    echo "  ./run-axion.sh all paper fabric quickplay"
    echo "  ./run-axion.sh all build"
    echo "  WITH_PAPER=true QUICKPLAY=true ./run-axion.sh 26.1"
    echo "  BUILD_FIRST=true ./run-axion.sh 26.1"
    exit 0
fi

if [[ "$VERSION_ARG" == "all" ]]; then
    VERSIONS=("${SUPPORTED_VERSION_LIST[@]}")
else
    IFS=',' read -r -a VERSIONS <<< "$VERSION_ARG"
fi

for i in "${!VERSIONS[@]}"; do
    VERSIONS[$i]="$(printf '%s' "${VERSIONS[$i]}" | xargs)"
done

if [[ ${#VERSIONS[@]} -gt 1 && "$(uname -s)" != "Linux" ]]; then
    echo "Multiple clients are currently only supported on Linux." >&2
    exit 1
fi

RUN_AXION_LOG_DIR="${RUN_AXION_LOG_DIR:-.logs/run-axion}"
RUN_AXION_LOG_FILE="${RUN_AXION_LOG_FILE:-$RUN_AXION_LOG_DIR/run-$(date +%Y%m%d-%H%M%S).log}"
mkdir -p "$(dirname "$RUN_AXION_LOG_FILE")"
ln -sfn "$(basename "$RUN_AXION_LOG_FILE")" "$(dirname "$RUN_AXION_LOG_FILE")/latest.log"
exec > >(tee -a "$RUN_AXION_LOG_FILE") 2>&1

echo "Run log: $ROOT_DIR/$RUN_AXION_LOG_FILE"
echo "Versions: ${VERSIONS[*]}"
echo "Options: WITH_PAPER=$WITH_PAPER WITH_FABRIC=$WITH_FABRIC"
echo "Started: $(date -Is)"
echo

build_version() {
    case "$1" in
        1.21|1.21.1)
            ./build-axion.sh mc1_21_0_1
            ;;
        1.21.2|1.21.3)
            ./build-axion.sh mc1_21_2_3
            ;;
        1.21.5)
            ./build-axion.sh mc1_21_5
            ;;
        1.21.6)
            ./build-axion.sh mc1_21_6
            ;;
        1.21.7|legacy)
            ./build-axion.sh legacy
            ;;
        1.21.8)
            ./build-axion.sh mc1_21_8
            ;;
        1.21.9)
            ./build-axion.sh mc1_21_9
            ;;
        1.21.10)
            ./build-axion.sh mc1_21_10
            ;;
        1.21.11|modern)
            ./build-axion.sh modern
            ;;
        26.1|26.1.x)
            ./build-axion.sh mc26_1_x
            ;;
        *)
            echo "Unknown version for build: $1" >&2
            echo "Supported versions: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1" >&2
            exit 1
            ;;
    esac
}

# Function to resolve Minecraft version for gradle
resolve_mc_version() {
    case "$1" in
        1.21) echo "1.21" ;;
        1.21.1) echo "1.21.1" ;;
        1.21.2) echo "1.21.2" ;;
        1.21.3) echo "1.21.3" ;;
        1.21.5) echo "1.21.5" ;;
        1.21.6) echo "1.21.6" ;;
        1.21.7) echo "1.21.7" ;;
        1.21.8) echo "1.21.8" ;;
        1.21.9) echo "1.21.9" ;;
        1.21.10) echo "1.21.10" ;;
        1.21.11) echo "1.21.11" ;;
        26.1|26.1.x) echo "26.1.2" ;;
        *) echo "" ;;
    esac
}

resolve_yarn_mappings() {
    case "$1" in
        "1.21") echo "1.21+build.9" ;;
        "1.21.1") echo "1.21.1+build.3" ;;
        "1.21.2") echo "1.21.2+build.1" ;;
        "1.21.3") echo "1.21.3+build.2" ;;
        "1.21.5") echo "1.21.5+build.1" ;;
        "1.21.6") echo "1.21.6+build.1" ;;
        "1.21.7") echo "1.21.7+build.1" ;;
        "1.21.8") echo "1.21.8+build.1" ;;
        "1.21.9") echo "1.21.9+build.1" ;;
        "1.21.10") echo "1.21.10+build.3" ;;
        "1.21.11") echo "1.21.11+build.4" ;;
        "26.1"|"26.1.2") echo "" ;;
        *) return 1 ;;
    esac
}

resolve_loader_version() {
    case "$1" in
        "1.21") echo "0.16.5" ;;
        "1.21.1") echo "0.16.5" ;;
        "1.21.2") echo "0.16.10" ;;
        "1.21.3") echo "0.16.10" ;;
        "1.21.5") echo "0.16.12" ;;
        "1.21.6") echo "0.16.13" ;;
        "1.21.7") echo "0.16.13" ;;
        "1.21.8") echo "0.16.14" ;;
        "1.21.9") echo "0.17.3" ;;
        "1.21.10") echo "0.17.3" ;;
        "1.21.11") echo "0.18.4" ;;
        "26.1"|"26.1.2") echo "0.19.2" ;;
        *) return 1 ;;
    esac
}

resolve_fabric_version() {
    case "$1" in
        "1.21") echo "0.100.8+1.21" ;;
        "1.21.1") echo "0.102.0+1.21.1" ;;
        "1.21.2") echo "0.106.1+1.21.2" ;;
        "1.21.3") echo "0.106.1+1.21.3" ;;
        "1.21.5") echo "0.119.5+1.21.5" ;;
        "1.21.6") echo "0.128.2+1.21.6" ;;
        "1.21.7") echo "0.129.0+1.21.7" ;;
        "1.21.8") echo "0.131.0+1.21.8" ;;
        "1.21.9") echo "0.134.1+1.21.9" ;;
        "1.21.10") echo "0.138.4+1.21.10" ;;
        "1.21.11") echo "0.141.3+1.21.11" ;;
        "26.1"|"26.1.2") echo "0.145.1+26.1" ;;
        *) return 1 ;;
    esac
}

resolve_fabric_kotlin_version() {
    case "$1" in
        "1.21") echo "1.12.3+kotlin.2.0.21" ;;
        "1.21.1") echo "1.12.3+kotlin.2.0.21" ;;
        "1.21.2") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.3") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.5") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.6") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.7") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.8") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.9") echo "1.13.9+kotlin.2.3.10" ;;
        "1.21.10") echo "1.13.9+kotlin.2.3.10" ;;
        "1.21.11") echo "1.13.9+kotlin.2.3.10" ;;
        "26.1"|"26.1.2") echo "1.13.11+kotlin.2.3.21" ;;
        *) return 1 ;;
    esac
}

resolve_modmenu_version() {
    case "$1" in
        "1.21") echo "11.0.3" ;;
        "1.21.1") echo "11.0.3" ;;
        "1.21.2") echo "12.0.1" ;;
        "1.21.3") echo "12.0.1" ;;
        "1.21.5") echo "14.0.0" ;;
        "1.21.6") echo "15.0.2" ;;
        "1.21.7") echo "15.0.2" ;;
        "1.21.8") echo "15.0.2" ;;
        "1.21.9") echo "16.0.1" ;;
        "1.21.10") echo "16.0.1" ;;
        "1.21.11") echo "17.0.0-beta.2" ;;
        "26.1"|"26.1.2") echo "18.0.0-beta.1" ;;
        *) return 1 ;;
    esac
}

resolve_loom_version() {
    case "$1" in
        "26.1"|"26.1.2") echo "1.16-SNAPSHOT" ;;
        *) echo "1.15.4" ;;
    esac
}

# Function to resolve compile version for Paper
resolve_paper_version() {
    case "$1" in
        1.21) echo "1.21-R0.1-SNAPSHOT" ;;
        1.21.1) echo "1.21.1-R0.1-SNAPSHOT" ;;
        1.21.3) echo "1.21.3-R0.1-SNAPSHOT" ;;
        1.21.5) echo "1.21.5-R0.1-SNAPSHOT" ;;
        1.21.6) echo "1.21.6-R0.1-SNAPSHOT" ;;
        1.21.7) echo "1.21.7-R0.1-SNAPSHOT" ;;
        1.21.8) echo "1.21.8-R0.1-SNAPSHOT" ;;
        1.21.9) echo "1.21.9-R0.1-SNAPSHOT" ;;
        1.21.10) echo "1.21.10-R0.1-SNAPSHOT" ;;
        1.21.11) echo "1.21.11-R0.1-SNAPSHOT" ;;
        26.1|26.1.x) echo "26.1.2.build.63-stable" ;;
        *) echo "" ;;
    esac
}

# Function to resolve range tag
resolve_range_tag() {
    case "$1" in
        1.21|1.21.1) echo "mc1.21-1.21.1" ;;
        1.21.2|1.21.3) echo "mc1.21.2-1.21.3" ;;
        1.21.5) echo "mc1.21.5" ;;
        1.21.6) echo "mc1.21.6" ;;
        1.21.7) echo "mc1.21.6-1.21.8" ;;
        1.21.8) echo "mc1.21.8" ;;
        1.21.9) echo "mc1.21.9" ;;
        1.21.10) echo "mc1.21.10" ;;
        1.21.11) echo "mc1.21.9-1.21.11" ;;
        26.1|26.1.x) echo "mc26.1.x" ;;
        *) echo "" ;;
    esac
}

set_server_property() {
    local file="$1"
    local key="$2"
    local value="$3"

    if grep -q "^${key}=" "$file" 2>/dev/null; then
        sed -i "s/^${key}=.*/${key}=${value}/" "$file"
    else
        printf '%s=%s\n' "$key" "$value" >> "$file"
    fi
}

configure_server_properties() {
    local file="$1"
    local port="$2"

    set_server_property "$file" "server-ip" ""
    set_server_property "$file" "server-port" "$port"
    set_server_property "$file" "online-mode" "false"
    set_server_property "$file" "enforce-secure-profile" "false"
    set_server_property "$file" "view-distance" "2"
    set_server_property "$file" "simulation-distance" "2"
    set_server_property "$file" "spawn-protection" "0"
    set_server_property "$file" "max-players" "2"
    set_server_property "$file" "sync-chunk-writes" "false"
}

write_axion_dev_marker() {
    local marker="$1"
    mkdir -p "$(dirname "$marker")"
    cat > "$marker" << 'EOF'
# Created by ./run-axion.sh for local Axion offline-mode testing.
# Axion server integrations only honor this marker when online-mode=false and server is on localhost.
EOF
}

# Function to start Paper server
start_paper_server() {
    local version="$1"

    # Paper skipped 1.21.2 and did not publish a server build for it.
    if [[ "$version" == "1.21.2" ]]; then
        echo "  WARNING: Paper did not publish a 1.21.2 server build; skipping Paper for $version" >&2
        return 0
    fi

    local paper_version
    local range_tag
    local mod_version
    local run_dir
    local paper_jar
    local plugins_dir
    local plugin_jar
    local port
    local paper_global

    paper_version="$(resolve_paper_version "$version")"
    range_tag="$(resolve_range_tag "$version")"
    mod_version="$(awk -F= '/^mod_version=/{print $2}' gradle.properties)"
    run_dir="run/paper/$version"
    paper_jar="$run_dir/paper-server.jar"
    plugins_dir="$run_dir/plugins"
    plugin_jar="paper-plugin/build/libs/${range_tag}/AxionPaper-v${mod_version}-${range_tag}.jar"
    paper_global="$run_dir/config/paper-global.yml"
    case "$version" in
        1.21) port=25563 ;;
        1.21.1) port=25564 ;;
        1.21.3) port=25566 ;;
        1.21.5) port=25567 ;;
        1.21.6) port=25568 ;;
        1.21.7) port=25569 ;;
        1.21.8) port=25570 ;;
        1.21.9) port=25571 ;;
        1.21.10) port=25572 ;;
        1.21.11) port=25573 ;;
        26.1|26.1.x) port=25574 ;;
        *) port=25565 ;;
    esac

    echo "  Setting up Paper server for $version..."

    # Create directories
    mkdir -p "$run_dir"
    mkdir -p "$plugins_dir"

    # Download Paper server if not present
    if [[ ! -f "$paper_jar" ]]; then
        echo "    Downloading Paper server $paper_version..."
        mkdir -p "$run_dir"

        local paper_url
        case "$version" in
            1.21) paper_url="https://fill-data.papermc.io/v1/objects/ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5/paper-1.21-130.jar" ;;
            1.21.1) paper_url="https://fill-data.papermc.io/v1/objects/39bd8c00b9e18de91dcabd3cc3dcfa5328685a53b7187a2f63280c22e2d287b9/paper-1.21.1-133.jar" ;;
            1.21.3) paper_url="https://fill-data.papermc.io/v1/objects/87e973e1d338e869e7fdbc4b8fadc1579d7bb0246a0e0cf6e5700ace6c8bc17e/paper-1.21.3-83.jar" ;;
            1.21.5) paper_url="https://fill-data.papermc.io/v1/objects/2ae6ae22adf417699746e0f89fc2ef6cb6ee050a5f6608cee58f0535d60b509e/paper-1.21.5-114.jar" ;;
            1.21.6) paper_url="https://fill-data.papermc.io/v1/objects/35e2dfa66b3491b9d2f0bb033679fa5aca1e1fdf097e7a06a80ce8afeda5c214/paper-1.21.6-48.jar" ;;
            1.21.7) paper_url="https://fill-data.papermc.io/v1/objects/83838188699cb2837e55b890fb1a1d39ad0710285ed633fbf9fc14e9f47ce078/paper-1.21.7-32.jar" ;;
            1.21.8) paper_url="https://fill-data.papermc.io/v1/objects/8de7c52c3b02403503d16fac58003f1efef7dd7a0256786843927fa92ee57f1e/paper-1.21.8-60.jar" ;;
            1.21.9) paper_url="https://fill-data.papermc.io/v1/objects/aec002e77c7566e49494fdf05430b96078ffd1d7430e652d4f338fef951e7a10/paper-1.21.9-59.jar" ;;
            1.21.10) paper_url="https://fill-data.papermc.io/v1/objects/158703f75a26f842ea656b3dc6d75bf3d1ec176b97a2c36384d0b80b3871af53/paper-1.21.10-130.jar" ;;
            1.21.11) paper_url="https://fill-data.papermc.io/v1/objects/e708e8c132dc143ffd73528cccb9532e2eb17628b1a0eee74469bf466c7003f8/paper-1.21.11-116.jar" ;;
            26.1|26.1.x) paper_url="https://fill-data.papermc.io/v1/objects/b51d49a5f62446b7cfc01e6c29e48e0ce6abd35a783134aace1047b839b178ef/paper-26.1.2-63.jar" ;;
            *) echo "    ERROR: Unknown version $version for Paper download" >&2; return 1 ;;
        esac

        if command -v wget &> /dev/null; then
            wget -O "$paper_jar" "$paper_url"
        elif command -v curl &> /dev/null; then
            curl -sL -o "$paper_jar" "$paper_url"
        else
            echo "    ERROR: Neither wget nor curl found. Please install one of them." >&2
            return 1
        fi
    fi

    # Copy AxionPaper plugin
    find "$plugins_dir" -maxdepth 1 -type f -name 'AxionPaper-*.jar' -delete
    if [[ -f "$plugin_jar" ]]; then
        echo "    Copying AxionPaper plugin..."
        cp "$plugin_jar" "$plugins_dir/"
    else
        echo "    WARNING: AxionPaper jar not found at $plugin_jar" >&2
        echo "    The automatic build completed, so check the build output above for errors."
    fi

    # Ensure eula.txt has eula=true
    if [[ ! -f "$run_dir/eula.txt" ]] || ! grep -qx "eula=true" "$run_dir/eula.txt" 2>/dev/null; then
        echo "    Setting eula=true in eula.txt..."
        echo "eula=true" > "$run_dir/eula.txt"
    fi

    # Create server.properties if not present
    if [[ ! -f "$run_dir/server.properties" ]]; then
        echo "    Creating server.properties (port $port)..."
        cat > "$run_dir/server.properties" << EOF
server-ip=127.0.0.1
server-port=$port
online-mode=false
enforce-secure-profile=false
enable-rcon=false
enable-command-block=true
spawn-protection=0
gamemode=creative
difficulty=peaceful
level-seed=axiontest
EOF
    fi

    configure_server_properties "$run_dir/server.properties" "$port"
    write_axion_dev_marker "$run_dir/.axiondev"
    write_axion_dev_marker "$plugins_dir/Axion/.axiondev"
    stop_server_in_run_dir "$run_dir"

    # Disable spark profiler (crashes on JDK 26+)
    if [[ ! -f "$paper_global" ]]; then
        mkdir -p "$run_dir/config"
        cat > "$paper_global" << 'EOF'
spark:
  enabled: false
EOF
    elif grep -q "^spark:" "$paper_global"; then
        sed -i '/^spark:/,/^[a-z]/s/enabled: true/enabled: false/' "$paper_global"
    fi

    # Start Paper server in background
    echo "    Starting Paper server in background..."
    cd "$run_dir"
    java -Xms128M -Xmx512M -Djava.net.preferIPv6Addresses=false -jar "paper-server.jar" nogui &
    local server_pid=$!
    cd "$ROOT_DIR"
    
    echo "    Paper server started with PID: $server_pid"
    echo "    Server directory: $run_dir"
    echo "    Server logs: $run_dir/logs/latest.log"
    
    # Save PID for cleanup
    echo "$server_pid" > "$run_dir/server.pid"
    STARTED_SERVER_PIDS+=("${server_pid}:${run_dir}/server.pid")
}

# Function to start Fabric server (1.21.11 only)
start_fabric_server() {
    local version="$1"

    # Only 1.21.11 is supported by the AxionFabricServer module
    if [[ "$version" != "1.21.11" ]]; then
        echo "  WARNING: Fabric server only supported for 1.21.11, skipping $version" >&2
        return 0
    fi

    local mc_version="1.21.11"
    local port=25573
    local mod_version
    local run_dir
    local mods_dir
    local installer_jar

    mod_version="$(awk -F= '/^mod_version=/{print $2}' gradle.properties)"
    run_dir="run/fabric/$version"
    mods_dir="$run_dir/mods"
    installer_jar="$ROOT_DIR/.cache/fabric-installer.jar"
    if [[ "$WITH_PAPER" == "true" ]]; then
        port=25575
    fi

    echo "  Setting up Fabric server for $version..."

    mkdir -p "$run_dir" "$mods_dir" "$(dirname "$installer_jar")"

    # Download Fabric installer if not cached
    if [[ ! -f "$installer_jar" ]]; then
        echo "    Downloading Fabric installer..."
        local installer_url="https://maven.fabricmc.net/net/fabricmc/fabric-installer/fabric-installer-1.0.1/fabric-installer-1.0.1.jar"
        if command -v wget &> /dev/null; then
            wget -q -O "$installer_jar" "$installer_url"
        elif command -v curl &> /dev/null; then
            curl -sL -o "$installer_jar" "$installer_url"
        else
            echo "    ERROR: Neither wget nor curl found" >&2
            return 1
        fi
    fi

    # Install Fabric server if not already installed
    if [[ ! -f "$run_dir/fabric-server-launch.jar" ]]; then
        echo "    Installing Fabric server for $version..."
        java -jar "$installer_jar" server -mcversion "$version" -dir "$run_dir" -downloadMinecraft || {
            echo "    ERROR: Fabric server installation failed" >&2
            return 1
        }
    fi

    # Download Fabric API if not present
    local fabric_api_jar="$mods_dir/fabric-api.jar"
    if [[ ! -f "$fabric_api_jar" ]]; then
        local fabric_version
        fabric_version="$(resolve_fabric_version "$mc_version")"
        local fabric_api_url="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${fabric_version}/fabric-api-${fabric_version}.jar"
        echo "    Downloading Fabric API..."
        if command -v wget &> /dev/null; then
            wget -q -O "$fabric_api_jar" "$fabric_api_url"
        elif command -v curl &> /dev/null; then
            curl -sL -o "$fabric_api_jar" "$fabric_api_url"
        fi
    fi

    # Download Fabric Language Kotlin if not present
    local flk_jar="$mods_dir/fabric-language-kotlin.jar"
    if [[ ! -f "$flk_jar" ]]; then
        local flk_version
        flk_version="$(resolve_fabric_kotlin_version "$mc_version")"
        local flk_url="https://maven.fabricmc.net/net/fabricmc/fabric-language-kotlin/fabric-language-kotlin/${flk_version}/fabric-language-kotlin-${flk_version}.jar"
        echo "    Downloading Fabric Language Kotlin..."
        if command -v wget &> /dev/null; then
            wget -q -O "$flk_jar" "$flk_url"
        elif command -v curl &> /dev/null; then
            curl -sL -o "$flk_jar" "$flk_url"
        fi
    fi

    # Build and copy AxionFabricServer mod
    local fabric_jar="fabric-server/build/libs/AxionFabricServer-v${mod_version}-mc${version}.jar"
    if [[ ! -f "$fabric_jar" ]]; then
        echo "    Building AxionFabricServer..."
        ./gradlew :fabric-server:remapJar \
            -Pminecraft_version="$mc_version" \
            -Pyarn_mappings="$(resolve_yarn_mappings "$mc_version")" \
            -Ploader_version="$(resolve_loader_version "$mc_version")" \
            -Pfabric_version="$(resolve_fabric_version "$mc_version")" \
            -Pfabric_kotlin_version="$(resolve_fabric_kotlin_version "$mc_version")" \
            -Ploom_version="$(resolve_loom_version "$mc_version")" \
            2>/dev/null
    fi
    if [[ -f "$fabric_jar" ]]; then
        echo "    Copying AxionFabricServer mod..."
        cp "$fabric_jar" "$mods_dir/"
    else
        echo "    WARNING: AxionFabricServer jar not found at $fabric_jar" >&2
    fi

    # Ensure eula.txt has eula=true
    if [[ ! -f "$run_dir/eula.txt" ]] || ! grep -qx "eula=true" "$run_dir/eula.txt" 2>/dev/null; then
        echo "    Setting eula=true in eula.txt..."
        echo "eula=true" > "$run_dir/eula.txt"
    fi

    # Create server.properties if not present
    if [[ ! -f "$run_dir/server.properties" ]]; then
        echo "    Creating server.properties (port $port)..."
        cat > "$run_dir/server.properties" << EOF
server-ip=
server-port=$port
online-mode=false
enforce-secure-profile=false
enable-rcon=false
enable-command-block=true
spawn-protection=0
gamemode=creative
difficulty=peaceful
level-seed=axiontest
EOF
    fi

    configure_server_properties "$run_dir/server.properties" "$port"
    write_axion_dev_marker "$run_dir/.axiondev"
    write_axion_dev_marker "$run_dir/config/axion/.axiondev"
    stop_server_in_run_dir "$run_dir"

    # Start Fabric server in background
    echo "    Starting Fabric server in background..."
    cd "$run_dir"
    java -Xms128M -Xmx512M -Djava.net.preferIPv6Addresses=false -jar fabric-server-launch.jar nogui &
    local server_pid=$!
    cd "$ROOT_DIR"

    echo "    Fabric server started with PID: $server_pid"
    echo "    Server directory: $run_dir"
    echo "    Server logs: $run_dir/logs/latest.log"

    echo "$server_pid" > "$run_dir/server.pid"
    STARTED_SERVER_PIDS+=("${server_pid}:${run_dir}/server.pid")
}

# Function to stop background servers (Paper + Fabric)
stop_servers() {
    if [[ ${#STARTED_SERVER_PIDS[@]} -eq 0 ]]; then
        return
    fi

    echo "Stopping servers..."
    for entry in "${STARTED_SERVER_PIDS[@]}"; do
        local pid="${entry%%:*}"
        local pid_file="${entry#*:}"
        if [[ -f "$pid_file" ]]; then
            if kill -0 "$pid" 2>/dev/null; then
                echo "  Stopping server with PID: $pid"
                kill "$pid"
            fi
            rm -f "$pid_file"
        fi
    done
}

stop_server_in_run_dir() {
    local run_dir="$1"
    local pid_file="$run_dir/server.pid"
    local lock_file="$run_dir/world/session.lock"
    local pid

    if [[ -f "$pid_file" ]]; then
        pid="$(cat "$pid_file" 2>/dev/null || true)"
        if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
            echo "    Stopping existing server for $run_dir (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            wait_for_pid_exit "$pid"
        fi
        rm -f "$pid_file"
    fi

    if [[ -e "$lock_file" ]] && command -v lsof >/dev/null 2>&1; then
        while IFS= read -r pid; do
            if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
                echo "    Stopping server holding $lock_file (PID: $pid)..."
                kill "$pid" 2>/dev/null || true
                wait_for_pid_exit "$pid"
            fi
        done < <(lsof -t "$lock_file" 2>/dev/null || true)
    fi
}

wait_for_pid_exit() {
    local pid="$1"
    local i

    for i in {1..50}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
        sleep 0.1
    done

    kill -9 "$pid" 2>/dev/null || true
}

stop_clients() {
    if [[ ${#STARTED_CLIENT_PIDS[@]} -eq 0 ]]; then
        return
    fi

    for pid in "${STARTED_CLIENT_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
        fi
    done
}

cleanup() {
    local status=$?
    trap - EXIT
    stop_clients
    stop_servers
    echo
    echo "Finished: $(date -Is)"
    echo "Exit status: $status"
    echo "Run log saved to: $ROOT_DIR/$RUN_AXION_LOG_FILE"
    exit "$status"
}

# Trap to stop background clients and servers on exit
trap cleanup EXIT

# IAS version IDs per Minecraft version (Modrinth)
declare -A IAS_VERSION
IAS_VERSION["1.21"]="RQG6VufY"
IAS_VERSION["1.21.1"]="UvDJxEv7"
IAS_VERSION["1.21.3"]="SBaEsGhZ"
IAS_VERSION["1.21.5"]="Rqmwlwr6"
IAS_VERSION["1.21.7"]="Fs2YTzMh"
IAS_VERSION["1.21.8"]="OqjOUdNs"
IAS_VERSION["1.21.10"]="fgPKk2RF"
IAS_VERSION["1.21.11"]="YUbSyjUy"
IAS_VERSION["26.1.2"]="2ea1OpDg"

declare -A AUTHME_VERSION
AUTHME_VERSION["1.21.6"]="CvPwgFbQ"
AUTHME_VERSION["1.21.9"]="VDR6iBtH"

declare -A SODIUM_VERSION
# Sodium 0.6.x expects 1.21.1's newer Fabric Renderer API; exact 1.21 needs 0.5.11.
SODIUM_VERSION["1.21"]="RncWhTxD"
SODIUM_VERSION["1.21.1"]="u1OEbNKx"
SODIUM_VERSION["1.21.2"]="rLBgU2jc"
SODIUM_VERSION["1.21.3"]="rLBgU2jc"
SODIUM_VERSION["1.21.5"]="DA250htH"
SODIUM_VERSION["1.21.6"]="7pwil2dy"
SODIUM_VERSION["1.21.7"]="7pwil2dy"
SODIUM_VERSION["1.21.8"]="7pwil2dy"
SODIUM_VERSION["1.21.9"]="sFfidWgd"
SODIUM_VERSION["1.21.10"]="sFfidWgd"
SODIUM_VERSION["1.21.11"]="x0XUezGL"
SODIUM_VERSION["26.1.2"]="8l4Yx5Q1"

declare -A FERRITECORE_VERSION
FERRITECORE_VERSION["1.21"]="wmIZ4wP4"
FERRITECORE_VERSION["1.21.1"]="wmIZ4wP4"
FERRITECORE_VERSION["1.21.3"]="a3QXXGz2"
FERRITECORE_VERSION["1.21.5"]="LdlksamY"
FERRITECORE_VERSION["1.21.6"]="LdlksamY"
FERRITECORE_VERSION["1.21.7"]="LdlksamY"
FERRITECORE_VERSION["1.21.8"]="LdlksamY"
FERRITECORE_VERSION["1.21.9"]="bPLllEgi"
FERRITECORE_VERSION["1.21.10"]="bPLllEgi"
FERRITECORE_VERSION["1.21.11"]="Ii0gP3D8"
FERRITECORE_VERSION["26.1.2"]="d5ddUdiB"

declare -A LITHIUM_VERSION
LITHIUM_VERSION["1.21"]="Yu6L8EnD"
LITHIUM_VERSION["1.21.1"]="Yu6L8EnD"
LITHIUM_VERSION["1.21.2"]="W0Cc7ZVd"
LITHIUM_VERSION["1.21.3"]="W0Cc7ZVd"
# No compatible Lithium release for 1.21.5-1.21.7
LITHIUM_VERSION["1.21.8"]="qxIL7Kb8"
LITHIUM_VERSION["1.21.9"]="L1sSIxFm"
LITHIUM_VERSION["1.21.10"]="NsswKiwi"
LITHIUM_VERSION["1.21.11"]="Ow7wA0kG"
LITHIUM_VERSION["26.1.2"]="R7MxYvuW"

declare -A IMMEDIATELYFAST_VERSION
# ImmediatelyFast 1.6.11 embeds Java 24 Reflect classes, which Fabric Loader
# 0.16.5's development remapper cannot analyze on Minecraft 1.21-1.21.1.
# Keep the optional optimization out of these development launch profiles.
IMMEDIATELYFAST_VERSION["1.21.2"]="2zcdbf00"
IMMEDIATELYFAST_VERSION["1.21.3"]="2zcdbf00"
# No compatible ImmediatelyFast release for 1.21.5-1.21.7
IMMEDIATELYFAST_VERSION["1.21.8"]="iNldtLH8"
IMMEDIATELYFAST_VERSION["1.21.9"]="ntac1Na0"
IMMEDIATELYFAST_VERSION["1.21.10"]="ntac1Na0"
IMMEDIATELYFAST_VERSION["1.21.11"]="QwkfUKSj"
IMMEDIATELYFAST_VERSION["26.1.2"]="lRuSLf0Y"

download_modrinth_version() {
    local version_id="$1"
    local target_file="$2"
    local label="$3"

    echo "  Downloading $label..."
    local meta
    meta="$(curl -sf "https://api.modrinth.com/v2/version/${version_id}")" || {
        echo "  WARNING: Failed to fetch $label version info" >&2
        return 1
    }

    local url
    url="$(echo "$meta" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['files'][0]['url'])" 2>/dev/null)" || return 1

    if command -v wget &>/dev/null; then
        wget -q -O "$target_file" "$url"
    elif command -v curl &>/dev/null; then
        curl -sL -o "$target_file" "$url"
    else
        echo "  WARNING: Neither wget nor curl found, cannot download $label" >&2
        return 1
    fi
}

install_cached_client_mod() {
    local mc_version="$1"
    local mod_name="$2"
    local version_id="$3"
    local mods_dir="$4"
    local cache_dir=".cache/client-mods"
    local cached_file="$cache_dir/${mod_name}-${mc_version}.jar"
    local target_file="$mods_dir/${mod_name}-${mc_version}.jar"

    mkdir -p "$cache_dir"
    local should_download=false
    if [[ ! -f "$cached_file" ]]; then
        should_download=true
    else
        # Re-download if cached file is older than 7 days
        local file_age
        file_age=$(find "$cached_file" -mtime +7 -print 2>/dev/null)
        if [[ -n "$file_age" ]]; then
            should_download=true
            echo "  Cached $mod_name is older than 7 days, updating..."
        fi
    fi

    if [[ "$should_download" == true ]]; then
        download_modrinth_version "$version_id" "$cached_file" "${mod_name} for Minecraft ${mc_version}" || return 0
    fi

    cp "$cached_file" "$target_file"
}

# Install a compatible client auth helper for the selected MC version, if one exists.
ensure_client_auth_mod() {
    local mc_version="$1"
    local client_run_dir="${2:-run}"
    local mods_dir="$client_run_dir/mods"
    mkdir -p "$mods_dir"

    # Each client run directory gets exactly one compatible auth helper.
    # Stale auth jars for other MC versions make Fabric reject the launch.
    find "$mods_dir" -maxdepth 1 -type f \
        \( -iname '*ias*.jar' -o -iname '*account*switcher*.jar' -o -iname '*authme*.jar' -o -iname '*auth-me*.jar' \) \
        -delete

    local ias_version_id="${IAS_VERSION[$mc_version]:-}"
    if [[ -n "$ias_version_id" ]]; then
        install_cached_client_mod "$mc_version" "IAS" "$ias_version_id" "$mods_dir"
        return 0
    fi

    local authme_version_id="${AUTHME_VERSION[$mc_version]:-}"
    if [[ -n "$authme_version_id" ]]; then
        install_cached_client_mod "$mc_version" "AuthMe" "$authme_version_id" "$mods_dir"
        return 0
    fi

    echo "  No compatible IAS/Auth Me release for Minecraft $mc_version; launching without an auth helper."
}

ensure_sodium_mod() {
    local mc_version="$1"
    local client_run_dir="${2:-run}"
    local mods_dir="$client_run_dir/mods"
    mkdir -p "$mods_dir"

    # Remove old Sodium jars for this MC version
    find "$mods_dir" -maxdepth 1 -type f -iname '*sodium*.jar' -delete

    local sodium_version_id="${SODIUM_VERSION[$mc_version]:-}"
    if [[ -n "$sodium_version_id" ]]; then
        install_cached_client_mod "$mc_version" "Sodium" "$sodium_version_id" "$mods_dir"
        echo "  Installed Sodium for Minecraft $mc_version"
    else
        echo "  No compatible Sodium release for Minecraft $mc_version"
    fi
}

ensure_ferritecore_mod() {
    local mc_version="$1"
    local client_run_dir="${2:-run}"
    local mods_dir="$client_run_dir/mods"
    mkdir -p "$mods_dir"

    # Remove old FerriteCore jars for this MC version
    find "$mods_dir" -maxdepth 1 -type f -iname '*ferritecore*.jar' -delete

    local ferritecore_version_id="${FERRITECORE_VERSION[$mc_version]:-}"
    if [[ -n "$ferritecore_version_id" ]]; then
        install_cached_client_mod "$mc_version" "FerriteCore" "$ferritecore_version_id" "$mods_dir"
        echo "  Installed FerriteCore for Minecraft $mc_version"
    else
        echo "  No compatible FerriteCore release for Minecraft $mc_version"
    fi
}

ensure_lithium_mod() {
    local mc_version="$1"
    local client_run_dir="${2:-run}"
    local mods_dir="$client_run_dir/mods"
    mkdir -p "$mods_dir"

    # Remove old Lithium jars for this MC version
    find "$mods_dir" -maxdepth 1 -type f -iname '*lithium*.jar' -delete

    local lithium_version_id="${LITHIUM_VERSION[$mc_version]:-}"
    if [[ -n "$lithium_version_id" ]]; then
        install_cached_client_mod "$mc_version" "Lithium" "$lithium_version_id" "$mods_dir"
        echo "  Installed Lithium for Minecraft $mc_version"
    else
        echo "  No compatible Lithium release for Minecraft $mc_version"
    fi
}

ensure_immediatelyfast_mod() {
    local mc_version="$1"
    local client_run_dir="${2:-run}"
    local mods_dir="$client_run_dir/mods"
    mkdir -p "$mods_dir"

    # Remove old ImmediatelyFast jars for this MC version
    find "$mods_dir" -maxdepth 1 -type f -iname '*immediatelyfast*.jar' -delete

    local immediatelyfast_version_id="${IMMEDIATELYFAST_VERSION[$mc_version]:-}"
    if [[ -n "$immediatelyfast_version_id" ]]; then
        install_cached_client_mod "$mc_version" "ImmediatelyFast" "$immediatelyfast_version_id" "$mods_dir"
        echo "  Installed ImmediatelyFast for Minecraft $mc_version"
    else
        echo "  No compatible ImmediatelyFast release for Minecraft $mc_version"
    fi
}

client_run_dir_for() {
    local version="$1"
    if [[ ${#VERSIONS[@]} -gt 1 ]]; then
        echo "$ROOT_DIR/run/clients/${version}"
    else
        echo "$ROOT_DIR/run"
    fi
}

prepare_client_run_dir() {
    local client_run_dir="$1"

    mkdir -p "$client_run_dir"
    mkdir -p "$ROOT_DIR/run"

    install_servers_dat "$client_run_dir"
    configure_client_options "$client_run_dir/options.txt"
}

configure_client_options() {
    local options_file="$1"
    mkdir -p "$(dirname "$options_file")"

    python3 - "$options_file" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
desired = {
    "fullscreen": "false",
    "narrator": "0",
    "onboardAccessibility": "false",
    "tutorialStep": "none",
}

lines = []
seen = set()
if path.exists():
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        key = raw.split(":", 1)[0] if ":" in raw else raw
        if key in desired:
            lines.append(f"{key}:{desired[key]}")
            seen.add(key)
        else:
            lines.append(raw)

for key, value in desired.items():
    if key not in seen:
        lines.append(f"{key}:{value}")

path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

install_servers_dat() {
    local client_run_dir="$1"
    local server_list_source="$ROOT_DIR/run/servers.dat"

    generate_servers_dat "$server_list_source"

    rm -f "$client_run_dir/servers.dat" "$client_run_dir/servers.dat_old"
    if [[ -f "$server_list_source" ]]; then
        cp "$server_list_source" "$client_run_dir/servers.dat"
        cp "$server_list_source" "$client_run_dir/servers.dat_old"
    else
        echo "Warning: servers.dat not generated, skipping server list copy"
    fi
}

generate_servers_dat() {
    local target="$1"
    local fabric_port=""

    if [[ "$WITH_FABRIC" == "true" ]]; then
        if [[ "$WITH_PAPER" == "true" ]]; then
            fabric_port="25575"
        else
            fabric_port="25573"
        fi
    fi

    mkdir -p "$(dirname "$target")"
    python3 - "$target" "$fabric_port" <<'PY'
import struct
import sys

target = sys.argv[1]
fabric_port = sys.argv[2]

servers = [
    ("1.21", "127.0.0.1:25563"),
    ("1.21.1", "127.0.0.1:25564"),
    ("1.21.3", "127.0.0.1:25566"),
    ("1.21.5", "127.0.0.1:25567"),
    ("1.21.6", "127.0.0.1:25568"),
    ("1.21.7", "127.0.0.1:25569"),
    ("1.21.8", "127.0.0.1:25570"),
    ("1.21.9", "127.0.0.1:25571"),
    ("1.21.10", "127.0.0.1:25572"),
    ("1.21.11 Paper", "127.0.0.1:25573"),
    ("26.1.x", "127.0.0.1:25574"),
]
if fabric_port:
    servers.append(("1.21.11 Fabric", f"127.0.0.1:{fabric_port}"))

def utf(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data

out = bytearray()
out += b"\x0a\x00\x00"
out += b"\x09" + utf("servers")
out += b"\x0a" + struct.pack(">i", len(servers))

for name, address in servers:
    out += b"\x01" + utf("hidden") + b"\x00"
    out += b"\x08" + utf("ip") + utf(address)
    out += b"\x08" + utf("name") + utf(name)
    out += b"\x00"

out += b"\x00"

with open(target, "wb") as f:
    f.write(out)
PY
}

prepare_client_workspace() {
    local version="$1"
    local workspace="$ROOT_DIR/.run-workspaces/$version"

    if [[ ${#VERSIONS[@]} -eq 1 ]]; then
        echo "$ROOT_DIR"
        return 0
    fi

    echo "  Preparing isolated Gradle workspace for $version..." >&2
    mkdir -p "$workspace"
    rsync -a --delete --delete-excluded \
        --exclude='.agents/' \
        --exclude='.codex/' \
        --exclude='.factory/' \
        --exclude='.git/' \
        --exclude='.github/' \
        --exclude='.gradle/' \
        --exclude='.idea/' \
        --exclude='.kotlin/' \
        --exclude='build/' \
        --exclude='bin/' \
        --exclude='docs/' \
        --exclude='wiki/' \
        --exclude='.modrinth/' \
        --exclude='.vscode/' \
        --exclude='.logs/' \
        --exclude='.run-workspaces/' \
        --exclude='run/' \
        --exclude='.cache/' \
        --exclude='paper-plugin/build/' \
        --exclude='fabric-server/build/' \
        --exclude='paper-plugin/bin/' \
        --exclude='fabric-server/bin/' \
        "$ROOT_DIR/" "$workspace/" >&2
    echo "$workspace"
}

relative_path() {
    local from_dir="$1"
    local to_path="$2"

    python3 - "$from_dir" "$to_path" <<'PY'
import os
import sys

print(os.path.relpath(os.path.abspath(sys.argv[2]), os.path.abspath(sys.argv[1])))
PY
}

# Function to get server port for a version
get_port() {
    local version="$1"
    case "$version" in
        1.21) echo "25563" ;;
        1.21.1) echo "25564" ;;
        1.21.2) echo "25565" ;;
        1.21.3) echo "25566" ;;
        1.21.5) echo "25567" ;;
        1.21.6) echo "25568" ;;
        1.21.7) echo "25569" ;;
        1.21.8) echo "25570" ;;
        1.21.9) echo "25571" ;;
        1.21.10) echo "25572" ;;
        1.21.11) echo "25573" ;;
        26.1|26.1.x) echo "25574" ;;
        *) echo "25565" ;;
    esac
}

# Function to find or create a world for quickplay
get_or_create_world() {
    local version="$1"
    local client_run_dir
    local saves_dir
    local latest_world
    local new_world_name

    client_run_dir="$(client_run_dir_for "$version")"
    saves_dir="$client_run_dir/saves"

    # Check if saves directory exists and has worlds
    if [[ -d "$saves_dir" ]]; then
        # Find the most recently modified world
        latest_world=$(find "$saves_dir" -maxdepth 1 -type d -name "New World*" -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
        
        if [[ -n "$latest_world" && -d "$latest_world" ]]; then
            local world_name
            world_name=$(basename "$latest_world")
            echo "$world_name"
            return
        fi
    fi

    # Create a new world name with timestamp
    new_world_name="New World $(date +%Y%m%d-%H%M%S)"
    echo "$new_world_name"
}

start_client() {
    local version="$1"
    local mc_version
    local client_run_dir
    local gradle_dir
    local gradle_run_dir
    local yarn_mappings
    local loader_version
    local fabric_version
    local fabric_kotlin_version
    local modmenu_version
    local loom_version
    local quickplay_args=()

    mc_version="$(resolve_mc_version "$version")"
    if [[ -z "$mc_version" ]]; then
        echo "  ERROR: Unknown version: $version" >&2
        echo "  Supported versions: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1" >&2
        exit 1
    fi

    client_run_dir="$(client_run_dir_for "$version")"
    prepare_client_run_dir "$client_run_dir"
    gradle_dir="$(prepare_client_workspace "$version")"
    gradle_run_dir="$(relative_path "$gradle_dir" "$client_run_dir")"

    echo "  Launching Minecraft $mc_version client (run dir: $client_run_dir, workspace: $gradle_dir, gradle runDir: $gradle_run_dir)..."
    ensure_client_auth_mod "$mc_version" "$client_run_dir"
    ensure_sodium_mod "$mc_version" "$client_run_dir"
    ensure_ferritecore_mod "$mc_version" "$client_run_dir"
    ensure_lithium_mod "$mc_version" "$client_run_dir"
    ensure_immediatelyfast_mod "$mc_version" "$client_run_dir"

    yarn_mappings="$(resolve_yarn_mappings "$mc_version")"
    loader_version="$(resolve_loader_version "$mc_version")"
    fabric_version="$(resolve_fabric_version "$mc_version")"
    fabric_kotlin_version="$(resolve_fabric_kotlin_version "$mc_version")"
    modmenu_version="$(resolve_modmenu_version "$mc_version")"
    loom_version="$(resolve_loom_version "$mc_version")"

    (
        cd "$gradle_dir"
        ./gradlew --no-daemon :runClient \
            -Pminecraft_version="$mc_version" \
            -Pyarn_mappings="$yarn_mappings" \
            -Ploader_version="$loader_version" \
            -Pfabric_version="$fabric_version" \
            -Pfabric_kotlin_version="$fabric_kotlin_version" \
            -Pmodmenu_version="$modmenu_version" \
            -Ploom_version="$loom_version" \
            -Paxion_run_dir="$gradle_run_dir" \
            "${quickplay_args[@]}"
    ) &

    local client_pid=$!
    STARTED_CLIENT_PIDS+=("$client_pid")
    echo "  Client for $mc_version started with PID: $client_pid"
}

# Run
echo
echo "==> Running Minecraft versions: ${VERSIONS[*]}"

if [[ "$BUILD_ONLY" == "true" ]]; then
    echo "==> Build-only mode: building jars without launching"
fi

if [[ "$BUILD_FIRST" == "true" || "$BUILD_ONLY" == "true" ]]; then
    echo "Building jars..."
    for version in "${VERSIONS[@]}"; do
        build_version "$version"
    done
else
    echo "Skipping build (BUILD_FIRST=false)"
fi

# Exit early if build-only mode
if [[ "$BUILD_ONLY" == "true" ]]; then
    echo "==> Build complete"
    exit 0
fi

# Start Paper or Fabric server if requested
if [[ "$WITH_PAPER" == "true" ]]; then
    for version in "${VERSIONS[@]}"; do
        start_paper_server "$version"
    done
fi

if [[ "$WITH_FABRIC" == "true" ]]; then
    for version in "${VERSIONS[@]}"; do
        start_fabric_server "$version"
    done
fi

for version in "${VERSIONS[@]}"; do
    start_client "$version"
    if [[ ${#VERSIONS[@]} -gt 1 ]]; then
        sleep 3
    fi
done

client_status=0
for pid in "${STARTED_CLIENT_PIDS[@]}"; do
    if ! wait "$pid"; then
        client_status=1
    fi
done

echo
echo "==> All done"
exit "$client_status"
