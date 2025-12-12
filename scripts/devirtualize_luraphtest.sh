#!/bin/bash
set -e

# Directories
PROJECT_ROOT=$(pwd)
LIB_DIR="$PROJECT_ROOT/lib"
SRC_DIR="$PROJECT_ROOT/src"
BIN_DIR="$PROJECT_ROOT/bin"
OUTPUT_DIR="$PROJECT_ROOT/outputs"
UNLUAC_JAR="$LIB_DIR/unluac.jar"

# Input/Output
INPUT_FILE="$PROJECT_ROOT/luraphtest.lua"
OUTPUT_LUAC="$OUTPUT_DIR/luraphtest.luac"
OUTPUT_LUA="$OUTPUT_DIR/luraphtest.lua"

# Create directories
mkdir -p "$BIN_DIR"
mkdir -p "$OUTPUT_DIR"

echo "Compiling Java sources..."
javac -d "$BIN_DIR" -cp "$LIB_DIR/*" $(find "$SRC_DIR" -name "*.java")

echo "Running Luraph Devirtualizer..."
# Remove output file if exists because Main checks for existence and fails
if [ -f "$OUTPUT_LUAC" ]; then
    rm "$OUTPUT_LUAC"
fi

# Generate source dump for debugging
java -cp "$LIB_DIR/*:$BIN_DIR" Main -i "$INPUT_FILE" -p > "$OUTPUT_DIR/source_dump.lua"

java -cp "$LIB_DIR/*:$BIN_DIR" Main -i "$INPUT_FILE" -o "$OUTPUT_LUAC"

if [ -f "$UNLUAC_JAR" ]; then
    echo "Decompiling with unluac..."
    java -jar "$UNLUAC_JAR" "$OUTPUT_LUAC" > "$OUTPUT_LUA"
    echo "Decompiled Lua saved to $OUTPUT_LUA"
    
    # Sanity check: check file size or simple grep
    if [ -s "$OUTPUT_LUA" ]; then
        echo "Sanity Check: Decompiled file is not empty."
    else
        echo "Sanity Check Failed: Decompiled file is empty."
    fi
else
    echo "Warning: unluac.jar not found in $LIB_DIR. Skipping decompilation."
    echo "Please download unluac.jar to $LIB_DIR to enable decompilation."
fi

echo "Done."
