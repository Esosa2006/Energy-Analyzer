#!/usr/bin/env bash
# ============================================================
# energy-analyze.sh
#
# Wrapper script that simulates "mvn energy-analyze" behavior.
# Run this from the root of any Java/Maven project.
#
# Usage:
#   ./energy-analyze.sh
#   ./energy-analyze.sh --threshold=70
#   ./energy-analyze.sh --warn-only
#   ./energy-analyze.sh --source=src/main/java --name="My Project"
#
# Can be integrated into Maven lifecycle via:
#   pom.xml → exec-maven-plugin → executions → energy-analyze phase
# ============================================================

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
ANALYZER_JAR="${ENERGY_ANALYZER_JAR:-$(dirname "$0")/target/java-energy-analyzer-1.0.0.jar}"
SOURCE_DIR="${SOURCE_DIR:-src/main/java}"
PROJECT_NAME="${PROJECT_NAME:-$(basename "$(pwd)")}"
EEI_THRESHOLD="${EEI_THRESHOLD:-60}"
WARN_ONLY=false

# ── Parse Arguments ───────────────────────────────────────────────────────────
for arg in "$@"; do
    case $arg in
        --threshold=*)
            EEI_THRESHOLD="${arg#*=}"
            ;;
        --source=*)
            SOURCE_DIR="${arg#*=}"
            ;;
        --name=*)
            PROJECT_NAME="${arg#*=}"
            ;;
        --warn-only)
            WARN_ONLY=true
            ;;
        --help|-h)
            echo "Usage: $0 [--threshold=N] [--source=path] [--name=name] [--warn-only]"
            exit 0
            ;;
    esac
done

# ── Validate ──────────────────────────────────────────────────────────────────
if [ ! -f "$ANALYZER_JAR" ]; then
    echo "ERROR: Energy analyzer JAR not found at: $ANALYZER_JAR"
    echo "       Run 'mvn package' first, or set ENERGY_ANALYZER_JAR env variable."
    exit 2
fi

if [ ! -d "$SOURCE_DIR" ]; then
    echo "ERROR: Source directory not found: $SOURCE_DIR"
    exit 2
fi

# ── Write temp config ─────────────────────────────────────────────────────────
TEMP_CONFIG=$(mktemp /tmp/energy-analyzer-XXXXXX.yml)
cat > "$TEMP_CONFIG" << EOF
energy-analyzer:
  thresholds:
    minimum-eei-threshold: ${EEI_THRESHOLD}
    critical-threshold: 30
    max-allowed-hotspots: 5
    fail-build-on-violation: $( [ "$WARN_ONLY" = true ] && echo "false" || echo "true" )
EOF

# ── Run Analysis ──────────────────────────────────────────────────────────────
echo "Running energy analysis on: $SOURCE_DIR"
echo "Threshold: EEI >= $EEI_THRESHOLD | Warn-only: $WARN_ONLY"
echo ""

java -jar "$ANALYZER_JAR" \
    "$SOURCE_DIR" \
    --name="$PROJECT_NAME" \
    --spring.config.additional-location="file:${TEMP_CONFIG}"

EXIT_CODE=$?

# ── Cleanup ───────────────────────────────────────────────────────────────────
rm -f "$TEMP_CONFIG"

# ── Handle Result ─────────────────────────────────────────────────────────────
if [ $EXIT_CODE -eq 1 ] && [ "$WARN_ONLY" = true ]; then
    echo ""
    echo "⚠  Warn-only mode: threshold violations detected but build continues."
    exit 0
fi

exit $EXIT_CODE
