#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./run-neoforge.sh <mc-version> [gradle args...]
  ./run-neoforge.sh <mc-version> --print-module
  ./run-neoforge.sh <mc-version> --print-command

Examples:
  ./run-neoforge.sh 1.20.6
  ./run-neoforge.sh 1.21.11 --stacktrace
  ./run-neoforge.sh latest --print-command

Notes:
  - This resolves a requested Minecraft version to the nearest supported NeoForge leaf module.
  - NeoForge starts at 1.20.1 in this repo.
EOF
}

resolve_module() {
  case "$1" in
    1.20.1 | 1.20.2)
      printf '%s\n' 'platform-neoforge-1_20_1'
      ;;
    1.20.3 | 1.20.4)
      printf '%s\n' 'platform-neoforge-1_20_4'
      ;;
    1.20.5 | 1.20.6)
      printf '%s\n' 'platform-neoforge-1_20_6'
      ;;
    1.21 | 1.21.0 | 1.21.1)
      printf '%s\n' 'platform-neoforge-1_21_1'
      ;;
    1.21.2 | 1.21.3 | 1.21.4)
      printf '%s\n' 'platform-neoforge-1_21_4'
      ;;
    1.21.5 | 1.21.6 | 1.21.7 | 1.21.8 | 1.21.9 | 1.21.10 | 1.21.11 | latest)
      printf '%s\n' 'platform-neoforge-1_21_11'
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
  printf 'Unsupported NeoForge version: %s\n' "$requested_version" >&2
  printf 'Supported requests: 1.20.1-1.20.6, 1.21-1.21.11, latest\n' >&2
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
