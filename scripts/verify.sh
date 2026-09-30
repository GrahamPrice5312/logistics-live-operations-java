#!/bin/sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/logistics-live-operations-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print | xargs javac --release 17 -d "$BUILD_DIR"
java -ea -cp "$BUILD_DIR" example.logistics.ShipmentDecisionTest
