#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Prefer the Temurin JDK installed in ~/workspace/tools if java is not on PATH
if ! command -v javac >/dev/null 2>&1; then
  for d in "$HOME"/workspace/tools/jdk-*/; do
    [ -x "${d}bin/javac" ] && export PATH="${d}bin:$PATH" && break
  done
fi
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out tank.TankGame
