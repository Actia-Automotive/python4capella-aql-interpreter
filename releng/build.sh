#!/usr/bin/env bash
# Copyright (c) 2026 ACTIA Automotive SA.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
# Builds the com.actia.capella.core.interpreter plugin jar against a local
# Capella / Capella Studio installation, without Maven.
#
# Requirements: bash (Git Bash on Windows), Java 17+ on PATH, and a Capella
# installation providing the Eclipse compiler (org.eclipse.jdt.core.compiler.batch).
#
# Usage:
#   releng/build.sh [CAPELLA_HOME]
#   CAPELLA_HOME=/path/to/capella releng/build.sh
#
# Output: build/com.actia.capella.core.interpreter_<version>.jar

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_ID="com.actia.capella.core.interpreter"
PLUGIN_DIR="$REPO_ROOT/plugins/$PLUGIN_ID"
BUILD_DIR="$REPO_ROOT/build"

# Bundles needed on the compile classpath in addition to Require-Bundle
# (types referenced by the required bundles' public API).
EXTRA_BUNDLES=(
    org.eclipse.osgi
    org.eclipse.equinox.common
    org.eclipse.emf.common
    org.eclipse.sirius.ecore.extender
    org.eclipse.sirius.ext.base
    org.eclipse.sirius.model
)

die() {
    echo "ERROR: $*" >&2
    exit 1
}

# Converts a path for the Java executable (Windows paths under Git Bash).
native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        echo "$1"
    fi
}

if command -v cygpath >/dev/null 2>&1; then
    CP_SEP=";"
else
    CP_SEP=":"
fi

# Returns the highest version of a bundle (jar or directory) in the Capella plugins folder.
find_bundle() {
    local name="$1"
    local match
    match="$(ls -d "$CAPELLA_PLUGINS/${name}"_[0-9]* 2>/dev/null | grep -v '\.source_' | sort -V | tail -1 || true)"
    [ -n "$match" ] || die "bundle '$name' not found in $CAPELLA_PLUGINS"
    echo "$match"
}

# Prints the value of a (possibly multi-line) MANIFEST.MF header.
manifest_header() {
    local header="$1" file="$2"
    tr -d '\r' < "$file" | awk -v h="$header:" '
        index($0, h) == 1 { value = substr($0, length(h) + 1); reading = 1; next }
        reading && /^ / { value = value substr($0, 2); next }
        reading { exit }
        END { print value }'
}

# Prints the entries of a (possibly multi-line) build.properties key, one per line.
build_property() {
    local key="$1" file="$2"
    tr -d '\r' < "$file" | awk -v k="$key" '
        { line = $0 }
        !reading && line ~ "^" k "[ \t]*=" { sub(/^[^=]*=/, "", line); reading = 1 }
        reading {
            cont = (line ~ /\\[ \t]*$/)
            sub(/\\[ \t]*$/, "", line)
            value = value line
            if (!cont) { reading = 0 }
        }
        END { print value }' | tr ',' '\n' | sed 's/^[ \t]*//; s/[ \t]*$//' | grep -v '^$'
}

# ---------------------------------------------------------------------------
# Locate Capella and the Eclipse compiler
# ---------------------------------------------------------------------------

CAPELLA_HOME="${1:-${CAPELLA_HOME:-}}"
[ -n "$CAPELLA_HOME" ] || die "Capella installation not specified. Usage: releng/build.sh <CAPELLA_HOME>"
CAPELLA_PLUGINS="$CAPELLA_HOME/plugins"
[ -d "$CAPELLA_PLUGINS" ] || die "no 'plugins' folder in $CAPELLA_HOME"
command -v java >/dev/null 2>&1 || die "java not found on PATH (Java 17+ required)"

ECJ="$(find_bundle org.eclipse.jdt.core.compiler.batch)"

# ---------------------------------------------------------------------------
# Resolve the compile classpath from MANIFEST.MF
# ---------------------------------------------------------------------------

MANIFEST="$PLUGIN_DIR/META-INF/MANIFEST.MF"
REQUIRED_BUNDLES="$(manifest_header Require-Bundle "$MANIFEST" | sed 's/"[^"]*"//g' | tr ',' '\n' | sed 's/;.*//; s/[ \t]//g' | grep -v '^$')"

CLASSPATH=""
for bundle in $REQUIRED_BUNDLES "${EXTRA_BUNDLES[@]}"; do
    CLASSPATH="$CLASSPATH${CLASSPATH:+$CP_SEP}$(native_path "$(find_bundle "$bundle")")"
done

# ---------------------------------------------------------------------------
# Compute the version (qualifier replaced by a UTC timestamp)
# ---------------------------------------------------------------------------

QUALIFIER="v$(date -u +%Y%m%d-%H%M)"
VERSION="$(manifest_header Bundle-Version "$MANIFEST" | tr -d ' ')"
VERSION="${VERSION/qualifier/$QUALIFIER}"
OUTPUT_JAR="$BUILD_DIR/${PLUGIN_ID}_${VERSION}.jar"

echo "Capella   : $CAPELLA_HOME"
echo "Compiler  : $(basename "$ECJ")"
echo "Version   : $VERSION"

rm -rf "$BUILD_DIR"
STAGING="$BUILD_DIR/staging"
TOOLS="$BUILD_DIR/tools"
mkdir -p "$STAGING/META-INF" "$TOOLS"

# ---------------------------------------------------------------------------
# Compile sources (arguments passed through a file to avoid command line limits)
# ---------------------------------------------------------------------------

ARGS_FILE="$BUILD_DIR/ecj.args"
{
    echo "-17"
    echo "-encoding"
    echo "UTF-8"
    echo "-proc:none"
    echo "-nowarn"
    echo "-d"
    echo "\"$(native_path "$STAGING")\""
    echo "-cp"
    echo "\"$CLASSPATH\""
    echo "\"$(native_path "$PLUGIN_DIR/src")\""
} > "$ARGS_FILE"

echo "Compiling..."
java -jar "$(native_path "$ECJ")" "@$(native_path "$ARGS_FILE")"

# ---------------------------------------------------------------------------
# Stage bin.includes (the "." entry is the compiled output)
# ---------------------------------------------------------------------------

while IFS= read -r entry; do
    entry="${entry%/}"
    case "$entry" in
        .) ;;
        META-INF) ;;
        *)
            [ -e "$PLUGIN_DIR/$entry" ] || die "bin.includes entry '$entry' not found"
            cp -r "$PLUGIN_DIR/$entry" "$STAGING/$(dirname "$entry")/"
            ;;
    esac
done < <(build_property bin.includes "$PLUGIN_DIR/build.properties")

tr -d '\r' < "$MANIFEST" | sed "s/^Bundle-Version: .*/Bundle-Version: $VERSION/" > "$STAGING/META-INF/MANIFEST.MF"

# ---------------------------------------------------------------------------
# Package
# ---------------------------------------------------------------------------

echo "Packaging..."
java -jar "$(native_path "$ECJ")" -17 -nowarn -proc:none -d "$(native_path "$TOOLS")" "$(native_path "$REPO_ROOT/releng/JarPacker.java")"
java -cp "$(native_path "$TOOLS")" JarPacker "$(native_path "$STAGING")" "$(native_path "$OUTPUT_JAR")"

echo
echo "Built: $OUTPUT_JAR"
echo "Install: copy it into <CAPELLA_HOME>/dropins/ and restart Capella."
