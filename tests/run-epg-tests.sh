#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac -encoding UTF-8 -d "$classes" app/src/main/java/com/example/tvsites/EpgGuide.java app/src/main/java/com/example/tvsites/EpgLoader.java tests/EpgGuideTest.java tests/EpgLoaderTest.java
java -cp "$classes" com.example.tvsites.EpgGuideTest "$@"

java -cp "$classes" com.example.tvsites.EpgLoaderTest
