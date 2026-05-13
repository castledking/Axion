#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# Parse command line arguments
VERSIONS="${1:-26.1}"
BUILD_FIRST="${BUILD_FIRST:-false}"
WITH_PAPER="${WITH_PAPER:-false}"
WITH_FABRIC="${WITH_FABRIC:-false}"
STARTED_SERVER_PIDS=()

# Display usage
if [[ "$VERSIONS" == "-h" || "$VERSIONS" == "--help" ]]; then
    echo "Usage: ./run-axion.sh [VERSIONS] [OPTIONS]"
    echo ""
    echo "VERSIONS: Minecraft version to run (1.21.5, 1.21.7, 1.21.11, or 26.1)"
    echo "  Default: 26.1"
    echo ""
    echo "OPTIONS:"
    echo "  BUILD_FIRST=true    Build jars before running"
    echo "  WITH_PAPER=true     Also start Paper server"
    echo "  WITH_FABRIC=true    Start Fabric server for 1.21.11 (same port as Paper)"
    echo ""
    echo "Examples:"
    echo "  ./run-axion.sh 26.1"
    echo "  BUILD_FIRST=true ./run-axion.sh 1.21.11"
    echo "  WITH_PAPER=true ./run-axion.sh 26.1"
    echo "  WITH_FABRIC=true ./run-axion.sh 1.21.11"
    exit 0
fi

# Build jars first if requested
if [[ "$BUILD_FIRST" == "true" ]]; then
    echo "Building jars first..."
    case "$VERSIONS" in
        1.21.5)
            ./build-axion.sh mc1_21_5
            ;;
        1.21.7|legacy)
            ./build-axion.sh legacy
            ;;
        1.21.11|modern)
            ./build-axion.sh modern
            ;;
        26.1|26.1.x)
            ./build-axion.sh mc26_1_x
            ;;
        *)
            echo "Unknown version for build: $VERSIONS" >&2
            ;;
    esac
fi

# Function to resolve Minecraft version for gradle
resolve_mc_version() {
    case "$1" in
        1.21.5) echo "1.21.5" ;;
        1.21.7) echo "1.21.7" ;;
        1.21.11) echo "1.21.11" ;;
        26.1|26.1.x) echo "26.1.2" ;;
        *) echo "" ;;
    esac
}

resolve_yarn_mappings() {
    case "$1" in
        "1.21.5") echo "1.21.5+build.1" ;;
        "1.21.7") echo "1.21.7+build.1" ;;
        "1.21.11") echo "1.21.11+build.4" ;;
        "26.1"|"26.1.2") echo "" ;;
        *) return 1 ;;
    esac
}

resolve_loader_version() {
    case "$1" in
        "1.21.5") echo "0.16.12" ;;
        "1.21.7") echo "0.16.13" ;;
        "1.21.11") echo "0.18.4" ;;
        "26.1"|"26.1.2") echo "0.19.2" ;;
        *) return 1 ;;
    esac
}

resolve_fabric_version() {
    case "$1" in
        "1.21.5") echo "0.119.5+1.21.5" ;;
        "1.21.7") echo "0.129.0+1.21.7" ;;
        "1.21.11") echo "0.141.3+1.21.11" ;;
        "26.1"|"26.1.2") echo "0.145.1+26.1" ;;
        *) return 1 ;;
    esac
}

resolve_fabric_kotlin_version() {
    case "$1" in
        "1.21.5") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.7") echo "1.13.0+kotlin.2.1.0" ;;
        "1.21.11") echo "1.13.9+kotlin.2.3.10" ;;
        "26.1"|"26.1.2") echo "1.13.11+kotlin.2.3.21" ;;
        *) return 1 ;;
    esac
}

resolve_modmenu_version() {
    case "$1" in
        "1.21.5") echo "14.0.0" ;;
        "1.21.7") echo "15.0.2" ;;
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
        1.21.5) echo "1.21.5-R0.1-SNAPSHOT" ;;
        1.21.7) echo "1.21.7-R0.1-SNAPSHOT" ;;
        1.21.11) echo "1.21.11-R0.1-SNAPSHOT" ;;
        26.1|26.1.x) echo "26.1.2.build.63-stable" ;;
        *) echo "" ;;
    esac
}

# Function to resolve range tag
resolve_range_tag() {
    case "$1" in
        1.21.5) echo "mc1.21.5" ;;
        1.21.7) echo "mc1.21.6-1.21.8" ;;
        1.21.11) echo "mc1.21.9-1.21.11" ;;
        26.1|26.1.x) echo "mc26.1.x" ;;
        *) echo "" ;;
    esac
}

# Function to start Paper server
start_paper_server() {
    local version="$1"
    local paper_version
    local range_tag
    local mod_version
    local run_dir
    local paper_jar
    local plugins_dir
    local plugin_jar

    paper_version="$(resolve_paper_version "$version")"
    range_tag="$(resolve_range_tag "$version")"
    mod_version="$(awk -F= '/^mod_version=/{print $2}' gradle.properties)"
    run_dir="run/paper/$version"
    paper_jar="$run_dir/paper-server.jar"
    plugins_dir="$run_dir/plugins"
    plugin_jar="paper-plugin/build/libs/${range_tag}/AxionPaper-v${mod_version}-${range_tag}.jar"

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
            1.21.5) paper_url="https://fill-data.papermc.io/v1/objects/2ae6ae22adf417699746e0f89fc2ef6cb6ee050a5f6608cee58f0535d60b509e/paper-1.21.5-114.jar" ;;
            1.21.7) paper_url="https://fill-data.papermc.io/v1/objects/83838188699cb2837e55b890fb1a1d39ad0710285ed633fbf9fc14e9f47ce078/paper-1.21.7-32.jar" ;;
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
    if [[ -f "$plugin_jar" ]]; then
        echo "    Copying AxionPaper plugin..."
        cp "$plugin_jar" "$plugins_dir/"
    else
        echo "    WARNING: AxionPaper jar not found at $plugin_jar" >&2
        echo "    Run BUILD_FIRST=true ./run-axion.sh to build the plugin first."
    fi

    # Ensure eula.txt has eula=true
    if [[ ! -f "$run_dir/eula.txt" ]] || ! grep -qx "eula=true" "$run_dir/eula.txt" 2>/dev/null; then
        echo "    Setting eula=true in eula.txt..."
        echo "eula=true" > "$run_dir/eula.txt"
    fi

    # Create server.properties if not present
    if [[ ! -f "$run_dir/server.properties" ]]; then
        local port
        case "$version" in
            1.21.5) port=25567 ;;
            1.21.7) port=25568 ;;
            1.21.11) port=25569 ;;
            26.1|26.1.x) port=25570 ;;
            *) port=25565 ;;
        esac
        echo "    Creating server.properties (port $port)..."
        cat > "$run_dir/server.properties" << EOF
server-ip=127.0.0.1
server-port=$port
enable-rcon=false
enable-command-block=true
spawn-protection=0
gamemode=creative
difficulty=peaceful
level-seed=axiontest
EOF
    fi

    # Create ops.json with pre-authorized users
    if [[ ! -f "$run_dir/ops.json" ]] || ! grep -q "ggpots" "$run_dir/ops.json" 2>/dev/null; then
        echo "    Adding ggpots as operator..."
        cat > "$run_dir/ops.json" << EOF
[
  {
    "uuid": "710f96df-8b04-4c91-8828-b2b5afc45cd3",
    "name": "ggpots",
    "level": 4,
    "bypassesPlayerLimit": false
  }
]
EOF
    fi

    # Ensure server-ip is empty (JDK 26+ defaults to 127.0.0.1)
    if [[ -f "$run_dir/server.properties" ]]; then
        sed -i 's/^server-ip=.*/server-ip=/' "$run_dir/server.properties"
    fi

    # Disable spark profiler (crashes on JDK 26+)
    local paper_global="$run_dir/config/paper-global.yml"
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
    java -Djava.net.preferIPv6Addresses=false -jar "paper-server.jar" nogui &
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
    local port=25569
    local mod_version
    local run_dir
    local mods_dir
    local installer_jar

    mod_version="$(awk -F= '/^mod_version=/{print $2}' gradle.properties)"
    run_dir="run/fabric/$version"
    mods_dir="$run_dir/mods"
    installer_jar="$ROOT_DIR/.cache/fabric-installer.jar"

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
enable-rcon=false
enable-command-block=true
spawn-protection=0
gamemode=creative
difficulty=peaceful
level-seed=axiontest
EOF
    fi

    # Ensure server-ip is empty
    sed -i 's/^server-ip=.*/server-ip=/' "$run_dir/server.properties"

    # Create ops.json with pre-authorized users
    if [[ ! -f "$run_dir/ops.json" ]] || ! grep -q "ggpots" "$run_dir/ops.json" 2>/dev/null; then
        echo "    Adding ggpots as operator..."
        cat > "$run_dir/ops.json" << EOF
[
  {
    "uuid": "710f96df-8b04-4c91-8828-b2b5afc45cd3",
    "name": "ggpots",
    "level": 4,
    "bypassesPlayerLimit": false
  }
]
EOF
    fi

    # Start Fabric server in background
    echo "    Starting Fabric server in background..."
    cd "$run_dir"
    java -Djava.net.preferIPv6Addresses=false -jar fabric-server-launch.jar nogui &
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

# Trap to stop background servers on exit
trap stop_servers EXIT

# IAS version IDs per Minecraft version (Modrinth)
declare -A IAS_VERSION
IAS_VERSION["1.21.5"]="Rqmwlwr6"
IAS_VERSION["1.21.7"]="Fs2YTzMh"
IAS_VERSION["1.21.11"]="YUbSyjUy"
IAS_VERSION["26.1.2"]="2ea1OpDg"

# Download Ingame Account Switcher mod for a specific MC version if not present
ensure_ias() {
    local mc_version="$1"
    local version_id="${IAS_VERSION[$mc_version]:-}"
    [[ -z "$version_id" ]] && return 0

    local mods_dir="run/mods"
    local target_file="$mods_dir/IAS-${mc_version}.jar"
    mkdir -p "$mods_dir"

    [[ -f "$target_file" ]] && return 0

    echo "  Downloading Ingame Account Switcher for $mc_version..."
    local meta
    meta="$(curl -sf "https://api.modrinth.com/v2/version/${version_id}")" || {
        echo "  WARNING: Failed to fetch IAS version info" >&2
        return 0
    }

    local url
    url="$(echo "$meta" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['files'][0]['url'])" 2>/dev/null)" || return 0

    if command -v wget &>/dev/null; then
        wget -q -O "$target_file" "$url"
    elif command -v curl &>/dev/null; then
        curl -sL -o "$target_file" "$url"
    else
        echo "  WARNING: Neither wget nor curl found, cannot download IAS" >&2
        return 0
    fi
    echo "  Downloaded IAS-${mc_version}.jar"
}

# Run
echo
echo "==> Running Minecraft version: $VERSIONS"

# Start Paper or Fabric server if requested
if [[ "$WITH_FABRIC" == "true" ]]; then
    start_fabric_server "$VERSIONS"
elif [[ "$WITH_PAPER" == "true" ]]; then
    start_paper_server "$VERSIONS"
fi

# Run client
mc_version="$(resolve_mc_version "$VERSIONS")"

if [[ -n "$mc_version" ]]; then
    echo "  Running Minecraft $mc_version client..."
    ensure_ias "$mc_version"
    
    # Resolve all properties for this version
    yarn_mappings="$(resolve_yarn_mappings "$mc_version")"
    loader_version="$(resolve_loader_version "$mc_version")"
    fabric_version="$(resolve_fabric_version "$mc_version")"
    fabric_kotlin_version="$(resolve_fabric_kotlin_version "$mc_version")"
    modmenu_version="$(resolve_modmenu_version "$mc_version")"
    loom_version="$(resolve_loom_version "$mc_version")"
    
    # Run client with version properties passed directly as -P flags.
    ./gradlew :runClient \
        -Pminecraft_version="$mc_version" \
        -Pyarn_mappings="$yarn_mappings" \
        -Ploader_version="$loader_version" \
        -Pfabric_version="$fabric_version" \
        -Pfabric_kotlin_version="$fabric_kotlin_version" \
        -Pmodmenu_version="$modmenu_version" \
        -Ploom_version="$loom_version"
else
    echo "  ERROR: Unknown version: $VERSIONS" >&2
    echo "  Supported versions: 1.21.5, 1.21.7, 1.21.11, 26.1" >&2
    exit 1
fi

echo
echo "==> All done"
