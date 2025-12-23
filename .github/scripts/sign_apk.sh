#!/bin/bash
set -euo pipefail # Strict mode

# ### Variables ###
PROJECT_NAME="AndroidAutoGLM"
VERSION="${VERSION_NAME:?VERSION_NAME must be set}" # Exit if not set
SDK_ROOT="${SDK_ROOT:?SDK_ROOT must be set}"
BUILD_TOOL_VERSION="${BUILD_TOOL_VERSION:?BUILD_TOOL_VERSION must be set}"

BUILD_TOOLS_PATH="$SDK_ROOT/build-tools/$BUILD_TOOL_VERSION"
UNSIGNED_FILE="app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_FILE="app/build/outputs/apk/release/app-release-aligned.apk"
FINAL_ARTIFACT_NAME="${PROJECT_NAME}-${VERSION}-signed.apk"

# Helper function to write to GITHUB_ENV or stdout
export_env() {
    local key=$1
    local val=$2
    echo "$key=$val"
    if [ -n "$GITHUB_ENV" ]; then
        echo "$key=$val" >> "$GITHUB_ENV"
    fi
}

# ### Pre-checks ###
if [ ! -f "$UNSIGNED_FILE" ]; then
    echo "Error: Unsigned APK not found at $UNSIGNED_FILE" >&2
    exit 1
fi

# ### Zipalign (common step) ###
echo ">>> Zipaligning APK..."
"$BUILD_TOOLS_PATH/zipalign" -v -f -p 4 "$UNSIGNED_FILE" "$ALIGNED_FILE"

# ### Signing Logic ###
SIGNED_SUCCESSFULLY=false

# 1. Attempt Release Signing
if [ -n "${SIGNING_KEY:-}" ] && [ -n "${KEY_ALIAS:-}" ] && [ -n "${KEY_STORE_PASSWORD:-}" ] && [ -n "${KEY_PASSWORD:-}" ]; then
    echo ">>> Attempting to sign with Release Key..."
    RELEASE_KEYSTORE="release.keystore"
    SIGNED_FILE="app/build/outputs/apk/release/app-release-signed.apk"

    # Decode keystore from secret
    echo "$SIGNING_KEY" | base64 -d > "$RELEASE_KEYSTORE"

    # Sign with apksigner
    if "$BUILD_TOOLS_PATH/apksigner" sign \
      --ks "$RELEASE_KEYSTORE" \
      --ks-key-alias "$KEY_ALIAS" \
      --ks-pass "pass:$KEY_STORE_PASSWORD" \
      --key-pass "pass:$KEY_PASSWORD" \
      --out "$SIGNED_FILE" \
      "$ALIGNED_FILE"; then
        
        mv "$SIGNED_FILE" "$FINAL_ARTIFACT_NAME"
        SIGNED_SUCCESSFULLY=true
        echo "Successfully signed with Release Key."
    else
        echo "Warning: Failed to sign with Release Key, falling back to debug..." >&2
    fi

    # Cleanup
    rm -f "$RELEASE_KEYSTORE"
else
    echo ">>> Release secrets not found, skipping release signing."
fi

# 2. Fallback to Debug Signing
if ! $SIGNED_SUCCESSFULLY; then
    echo ">>> Signing with Debug Key..."
    DEBUG_KEYSTORE="debug.keystore"
    DEBUG_SIGNED_APK="app/build/outputs/apk/release/app-release-signed-debug.apk"
    
    # Generate Debug Keystore if not exists
    if [ ! -f "$DEBUG_KEYSTORE" ]; then
        keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -storepass android -alias androiddebugkey \
                -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
                -dname "CN=Android Debug,O=Android,C=US"
    fi

    # Sign with debug key
    if "$BUILD_TOOLS_PATH/apksigner" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$DEBUG_SIGNED_APK" "$ALIGNED_FILE"; then
        mv "$DEBUG_SIGNED_APK" "$FINAL_ARTIFACT_NAME"
        SIGNED_SUCCESSFULLY=true
        echo "Successfully signed with Debug Key."
    else
        echo "Error: Failed to sign with debug key." >&2
        exit 1 # Fatal error if even debug signing fails
    fi
fi

# ### Final Steps ###
if $SIGNED_SUCCESSFULLY; then
    echo ">>> Finalizing artifact..."
    export_env "ARTIFACT_PATH" "$FINAL_ARTIFACT_NAME"
    echo "Artifact ready at: $FINAL_ARTIFACT_NAME"
    # Cleanup intermediate files
    rm -f "$ALIGNED_FILE"
else
    echo "Error: No APK was signed successfully." >&2
    exit 1
fi
