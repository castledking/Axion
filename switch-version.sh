#!/bin/bash
# Version switcher script for Axion multi-version support

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Usage: ./switch-version.sh <version>"
    echo "Supported versions: 1.20.6, 1.21.0, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.11"
    exit 1
fi

if [ ! -f "versions/$VERSION/gradle.properties" ]; then
    echo "Error: Version $VERSION not found in versions/ directory"
    exit 1
fi

echo "Switching to Minecraft $VERSION..."

# Copy version-specific properties
cp versions/$VERSION/gradle.properties gradle.properties.version

# Preserve mod_version and maven_group from existing gradle.properties
grep -E "^(mod_version|maven_group|archives_base_name|loom_version|kotlin_version|org.gradle)" gradle.properties >> gradle.properties.version

# Replace gradle.properties
mv gradle.properties.version gradle.properties

echo "Switched to $VERSION"
echo "Run './gradlew build' to build for this version"
