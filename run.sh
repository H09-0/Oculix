#!/usr/bin/env bash
set -e

# ==============================================================================
# OculiX Runner Script
# Usage:
#   ./run.sh            Run the packaged standalone OculiX IDE
#   ./run.sh -h         Show OculiX CLI help
#   ./run.sh --build    Rebuild the standalone JAR and run
#   ./run.sh --dev      Run directly from classes without rebuilding the full JAR
#   ./run.sh [args...]  Pass arguments directly to OculiX
# ==============================================================================

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/IDE/target/oculixide-4.0.0-complete-lux.jar"

# Check if build is requested or jar missing
if [[ "$1" == "--build" || "$1" == "-b" ]] || [[ ! -f "$JAR" ]]; then
  echo "==> Building OculiX standalone Linux package..."
  (cd "$DIR" && mvn -pl IDE -Pcomplete-lux-jar package -DskipTests)
  if [[ "$1" == "--build" || "$1" == "-b" ]]; then
    shift
  fi
fi

# Dev mode execution
if [[ "$1" == "--dev" || "$1" == "dev" ]]; then
  shift
  echo "==> Running OculiX IDE in dev mode..."
  exec mvn -f "$DIR/pom.xml" -pl IDE compile exec:java -Dexec.mainClass="org.sikuli.ide.Sikulix" -Dexec.args="$*"
fi

# Normal JAR execution
if [[ -f "$JAR" ]]; then
  exec java -jar "$JAR" "$@"
else
  echo "Error: Jar file not found at $JAR" >&2
  exit 1
fi
