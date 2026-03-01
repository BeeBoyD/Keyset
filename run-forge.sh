#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./run-forge.sh <mc-version> [gradle args...]
  ./run-forge.sh <mc-version> --print-module
  ./run-forge.sh <mc-version> --print-command

Examples:
  ./run-forge.sh 1.20.6
  ./run-forge.sh 1.21.1 --stacktrace
  ./run-forge.sh latest --print-command

Notes:
  - This resolves a requested Minecraft version to the nearest supported Forge leaf module.
  - Forge support in the active graph is capped at 1.21.1.
EOF
}

resolve_module() {
  case "$1" in
    1.16.5)
      printf '%s\n' 'platform-forge-1_16_5'
      ;;
    1.17.1)
      printf '%s\n' 'platform-forge-1_17_1'
      ;;
    1.18.2)
      printf '%s\n' 'platform-forge-1_18_2'
      ;;
    1.19.2)
      printf '%s\n' 'platform-forge-1_19_2'
      ;;
    1.19.3 | 1.19.4)
      printf '%s\n' 'platform-forge-1_19_4'
      ;;
    1.20.1 | 1.20.2)
      printf '%s\n' 'platform-forge-1_20_1'
      ;;
    1.20.3 | 1.20.4)
      printf '%s\n' 'platform-forge-1_20_4'
      ;;
    1.20.5 | 1.20.6)
      printf '%s\n' 'platform-forge-1_20_6'
      ;;
    1.21 | 1.21.0 | 1.21.1 | latest)
      printf '%s\n' 'platform-forge-1_21_1'
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
  printf 'Unsupported Forge version: %s\n' "$requested_version" >&2
  printf 'Supported requests: 1.16.5, 1.17.1, 1.18.2, 1.19.2-1.19.4, 1.20.1-1.20.6, 1.21-1.21.1, latest\n' >&2
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

printf 'Requested Minecraft %s -> using %s\n' "$requested_version" "$module"
exec ./gradlew "$gradle_task" "$@"
