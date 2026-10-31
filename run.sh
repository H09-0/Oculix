#!/usr/bin/env bash
# OculiX IDE launcher
JAR="IDE/target/oculixide-4.0.0-complete-lux.jar"
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$DIR/$JAR"

if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: JAR not found at $JAR_PATH"
  echo "Build it first: mvn -pl IDE -Pcomplete-lux-jar package -DskipTests"
  exit 1
fi

echo "==> Launching OculiX IDE from $JAR_PATH..."
exec java -jar "$JAR_PATH" "$@"
