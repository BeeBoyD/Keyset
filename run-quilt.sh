#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./run-quilt.sh <mc-version> [gradle args...]
  ./run-quilt.sh <mc-version> --print-module
  ./run-quilt.sh <mc-version> --print-command

Examples:
  ./run-quilt.sh 1.21.3
  ./run-quilt.sh 1.20.6 --stacktrace
  ./run-quilt.sh latest --print-command

Notes:
  - Quilt uses the Fabric-compatible targets in this repo.
  - This resolves a requested Quilt version to the nearest supported Fabric leaf module.
EOF
}

resolve_module() {
  case "$1" in
    1.16.5)
      printf '%s\n' 'platform-fabric-1_16_5'
      ;;
    1.17.1)
      printf '%s\n' 'platform-fabric-1_17_1'
      ;;
    1.18.2)
      printf '%s\n' 'platform-fabric-1_18_2'
      ;;
    1.19.2)
      printf '%s\n' 'platform-fabric-1_19_2'
      ;;
    1.19.3 | 1.19.4)
      printf '%s\n' 'platform-fabric-1_19_4'
      ;;
    1.20.1 | 1.20.2)
      printf '%s\n' 'platform-fabric-1_20_1'
      ;;
    1.20.3 | 1.20.4)
      printf '%s\n' 'platform-fabric-1_20_4'
      ;;
    1.20.5 | 1.20.6)
      printf '%s\n' 'platform-fabric-1_20_6'
      ;;
    1.21 | 1.21.0 | 1.21.1)
      printf '%s\n' 'platform-fabric-1_21_1'
      ;;
    1.21.2 | 1.21.3 | 1.21.4)
      printf '%s\n' 'platform-fabric-1_21_4'
      ;;
    1.21.5 | 1.21.6 | 1.21.7 | 1.21.8 | 1.21.9)
      printf '%s\n' 'platform-fabric-1_21_9'
      ;;
    1.21.10 | 1.21.11 | latest)
      printf '%s\n' 'platform-fabric-1_21_11'
      ;;
    *)
      return 1
      ;;
  esac
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

if [[ "$1" == "--help" || "$1" == "-h" ]]; then
  usage
  exit 0
fi

requested_version="$1"
shift

if ! module="$(resolve_module "$requested_version")"; then
  printf 'Unsupported Quilt version: %s\n' "$requested_version" >&2
  printf 'Supported requests: 1.16.5, 1.17.1, 1.18.2, 1.19.2-1.19.4, 1.20.1-1.20.6, 1.21-1.21.11, latest\n' >&2
  exit 1
fi

gradle_task=":${module}:runClient"

if [[ $# -gt 0 && "$1" == "--print-module" ]]; then
  printf '%s\n' "$module"
  exit 0
fi

if [[ $# -gt 0 && "$1" == "--print-command" ]]; then
  printf './gradlew %s\n' "$gradle_task"
  exit 0
fi

default_java_home="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
script_java_home="${KEYSET_JAVA_HOME:-$default_java_home}"
if [[ -d "$script_java_home" ]]; then
  export JAVA_HOME="$script_java_home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

printf 'Requested Minecraft %s -> using %s (Quilt via Fabric-compatible target)\n' "$requested_version" "$module"
exec ./gradlew "$gradle_task" "$@"
