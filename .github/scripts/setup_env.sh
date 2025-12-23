#!/bin/bash
set -e

# Helper function to write to GITHUB_ENV or stdout
export_env() {
    local key=$1
    local val=$2
    echo "$key=$val"
    if [ -n "$GITHUB_ENV" ]; then
        echo "$key=$val" >> "$GITHUB_ENV"
    fi
}

echo ">>> Extracting Version Name..."
if [ -f "app/build.gradle.kts" ]; then
    # More robust regex: handles variable spacing around '='
    VERSION_NAME=$(grep 'versionName' app/build.gradle.kts | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/')
else
    echo "Error: app/build.gradle.kts not found!"
    exit 1
fi

if [ -z "$VERSION_NAME" ]; then
    echo "Error: Could not extract versionName from build.gradle.kts"
    exit 1
fi

echo "Detected Version: $VERSION_NAME"
export_env "VERSION_NAME" "$VERSION_NAME"

echo ">>> Finding Build Tools..."
# Try to detect ANDROID_SDK_ROOT or ANDROID_HOME
if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    SDK_ROOT="$ANDROID_SDK_ROOT"
elif [ -n "${ANDROID_HOME:-}" ]; then
    SDK_ROOT="$ANDROID_HOME"
else
    SDK_ROOT="/usr/local/lib/android/sdk"
fi

export_env "SDK_ROOT" "$SDK_ROOT"

if [ -d "$SDK_ROOT/build-tools" ]; then
    # Get the latest version
    BUILD_TOOL_VERSION=$(ls "$SDK_ROOT/build-tools/" | sort -V | tail -n 1)
else
    echo "Error: build-tools not found in $SDK_ROOT"
    exit 1
fi

if [ -z "$BUILD_TOOL_VERSION" ]; then
    echo "Error: Could not determine latest build-tools version"
    exit 1
fi

export_env "BUILD_TOOL_VERSION" "$BUILD_TOOL_VERSION"
echo "Latest build tool version found: $BUILD_TOOL_VERSION"
