#!/bin/bash
set -euo pipefail

# --- 1. Read current versions from the project ---
echo "Reading current versions from gradle/libs.versions.toml..."

TOML="gradle/libs.versions.toml"
if [ ! -f "$TOML" ]; then
    echo "✖ $TOML not found — run from project root"
    exit 1
fi

CURRENT_AGP=$(grep '^agp' "$TOML" | head -1 | sed 's/.*"\(.*\)".*/\1/')
CURRENT_KOTLIN=$(grep '^kotlin = ' "$TOML" | head -1 | sed 's/.*"\(.*\)".*/\1/')
CURRENT_COMPOSE_BOM=$(grep '^compose-bom' "$TOML" | head -1 | sed 's/.*"\(.*\)".*/\1/')
CURRENT_SDK=$(grep 'compileSdk' app/build.gradle.kts | head -1 | sed 's/.*= \([0-9]*\).*/\1/')

echo "Current versions:"
echo "  AGP:        $CURRENT_AGP"
echo "  Kotlin:     $CURRENT_KOTLIN"
echo "  Compose BOM: $CURRENT_COMPOSE_BOM"
echo "  compileSdk:  $CURRENT_SDK"

# --- 2. Fetch latest stable versions via API ---
echo ""
echo "Fetching latest stable versions..."

# Gradle (GitHub API)
LATEST_GRADLE=$(curl -s https://api.github.com/repos/gradle/gradle/releases/latest | grep -Po '"tag_name": "v\K[0-9.]*' | head -1) || true

# Kotlin (GitHub API)
LATEST_KOTLIN=$(curl -s https://api.github.com/repos/JetBrains/kotlin/releases/latest | grep -Po '"tag_name": "v\K[0-9.]*' | head -1) || true

echo "Latest available:"
echo "  Gradle: ${LATEST_GRADLE:-"(fetch failed)"}"
echo "  Kotlin: ${LATEST_KOTLIN:-"(fetch failed)"}"
echo "  AGP / Compose BOM / SDK: must be set manually below"
echo ""

# --- 3. Define target versions (edit these when upgrading) ---
TARGET_AGP="$CURRENT_AGP"            # e.g. "9.2.1"
TARGET_KOTLIN="${LATEST_KOTLIN:-$CURRENT_KOTLIN}"
TARGET_COMPOSE_BOM="$CURRENT_COMPOSE_BOM"  # e.g. "2026.06.01"
TARGET_SDK="$CURRENT_SDK"            # e.g. "37"
TARGET_GRADLE="${LATEST_GRADLE:-}"    # leave empty to skip wrapper upgrade

echo "Target versions (edit script to change):"
echo "  AGP:        $TARGET_AGP"
echo "  Kotlin:     $TARGET_KOTLIN"
echo "  Compose BOM: $TARGET_COMPOSE_BOM"
echo "  compileSdk:  $TARGET_SDK"
echo "  Gradle:      ${TARGET_GRADLE:-"(skip)"}"
echo ""

read -r -p "Proceed with upgrade? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Cancelled."
    exit 0
fi

# --- 4. Backup current files ---
BACKUP_DIR="./gradle_backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"
cp gradle/libs.versions.toml app/build.gradle.kts gradle/wrapper/gradle-wrapper.properties "$BACKUP_DIR/" 2>/dev/null || true
echo "Backed up to $BACKUP_DIR/"

# --- 5. Apply upgrades ---

# Update Gradle Wrapper
if [ -n "$TARGET_GRADLE" ]; then
    echo "Updating Gradle to $TARGET_GRADLE..."
    ./gradlew wrapper --gradle-version "$TARGET_GRADLE"
else
    echo "Skipping Gradle wrapper upgrade."
fi

# Update AGP
if [ "$TARGET_AGP" != "$CURRENT_AGP" ]; then
    sed -i "s/agp = \".*\"/agp = \"$TARGET_AGP\"/" "$TOML"
    echo "AGP: $CURRENT_AGP → $TARGET_AGP"
fi

# Update Kotlin
if [ "$TARGET_KOTLIN" != "$CURRENT_KOTLIN" ]; then
    sed -i "s/^kotlin = \".*\"/kotlin = \"$TARGET_KOTLIN\"/" "$TOML"
    echo "Kotlin: $CURRENT_KOTLIN → $TARGET_KOTLIN"
fi

# Update Compose BOM
if [ "$TARGET_COMPOSE_BOM" != "$CURRENT_COMPOSE_BOM" ]; then
    sed -i "s/compose-bom = \".*\"/compose-bom = \"$TARGET_COMPOSE_BOM\"/" "$TOML"
    echo "Compose BOM: $CURRENT_COMPOSE_BOM → $TARGET_COMPOSE_BOM"
fi

# Update compileSdk in app and geoman modules
if [ "$TARGET_SDK" != "$CURRENT_SDK" ]; then
    sed -i "s/compileSdk = [0-9]*/compileSdk = $TARGET_SDK/" app/build.gradle.kts
    sed -i "s/compileSdk = [0-9]*/compileSdk = $TARGET_SDK/" ../maplibre-geoman-android/app/build.gradle.kts
    echo "compileSdk: $CURRENT_SDK → $TARGET_SDK"
fi

# --- 6. Verify ---
echo ""
echo "Building project..."
./gradlew --stop 2>/dev/null || true

# Run a real build and the unit tests so a broken upgrade fails loudly.
if ! ./gradlew :app:assembleDebug :app:testDebugUnitTest; then
    echo ""
    echo "✖ Build or tests failed after upgrade. Restore with:"
    echo "    rm -rf app/build ../maplibre-geoman-android/app/build"
    echo "    cp -r $BACKUP_DIR/* ."
    exit 1
fi

echo ""
echo "Done! Build and unit tests passed after upgrade."
