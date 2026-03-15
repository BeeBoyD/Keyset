#!/usr/bin/env bash

set -euo pipefail

loader="${1:-}"
if [[ -z "$loader" ]]; then
  printf 'Missing loader argument.\n' >&2
  exit 1
fi
shift

case "$loader" in
  fabric)
    loader_name="Fabric"
    support_line="1.16.5, 1.17.1, 1.18.2, 1.19.2-1.19.4, 1.20.1-1.20.6, 1.21-1.21.11, latest"
    notes=(
      "- This resolves a requested Minecraft version to the nearest supported Fabric leaf module."
      "- Dev launch still runs the module's configured representative Minecraft target."
    )
    examples=(
      "./run-fabric.sh 1.21.3"
      "./run-fabric.sh 1.20.6 --stacktrace"
      "./run-fabric.sh 1.21.3 --print-command"
    )
    ;;
  forge)
    loader_name="Forge"
    support_line="1.16.5, 1.17.1, 1.18.2, 1.19.2-1.19.4, 1.20.1-1.20.6, 1.21-1.21.1, latest"
    notes=(
      "- This resolves a requested Minecraft version to the nearest supported Forge leaf module."
      "- Forge support in the active graph is capped at 1.21.1."
    )
    examples=(
      "./run-forge.sh 1.20.6"
      "./run-forge.sh 1.21.1 --stacktrace"
      "./run-forge.sh latest --print-command"
    )
    ;;
  neoforge)
    loader_name="NeoForge"
    support_line="1.20.1-1.20.6, 1.21-1.21.11, latest"
    notes=(
      "- This resolves a requested Minecraft version to the nearest supported NeoForge leaf module."
      "- NeoForge starts at 1.20.1 in this repo."
    )
    examples=(
      "./run-neoforge.sh 1.20.6"
      "./run-neoforge.sh 1.21.11 --stacktrace"
      "./run-neoforge.sh latest --print-command"
    )
    ;;
  quilt)
    loader_name="Quilt"
    support_line="1.16.5, 1.17.1, 1.18.2, 1.19.2-1.19.4, 1.20.1-1.20.6, 1.21-1.21.11, latest"
    notes=(
      "- Quilt uses the Fabric-compatible targets in this repo."
      "- This resolves a requested Quilt version to the nearest supported Fabric leaf module."
    )
    examples=(
      "./run-quilt.sh 1.21.3"
      "./run-quilt.sh 1.20.6 --stacktrace"
      "./run-quilt.sh latest --print-command"
    )
    ;;
  *)
    printf 'Unsupported loader: %s\n' "$loader" >&2
    exit 1
    ;;
esac

usage() {
  local script_name="./run-${loader}.sh"
  cat <<EOF
Usage:
  ${script_name} <mc-version> [gradle args...]
  ${script_name} <mc-version> --print-module
  ${script_name} <mc-version> --print-command

Examples:
  ${examples[0]}
  ${examples[1]}
  ${examples[2]}

Notes:
  ${notes[0]}
  ${notes[1]}
EOF
}

resolve_module() {
  case "${loader}:$1" in
    fabric:1.16.5 | quilt:1.16.5)
      printf '%s\n' 'platform-fabric-1_16_5'
      ;;
    fabric:1.17.1 | quilt:1.17.1)
      printf '%s\n' 'platform-fabric-1_17_1'
      ;;
    fabric:1.18.2 | quilt:1.18.2)
      printf '%s\n' 'platform-fabric-1_18_2'
      ;;
    fabric:1.19.2 | quilt:1.19.2)
      printf '%s\n' 'platform-fabric-1_19_2'
      ;;
    fabric:1.19.3 | fabric:1.19.4 | quilt:1.19.3 | quilt:1.19.4)
      printf '%s\n' 'platform-fabric-1_19_4'
      ;;
    fabric:1.20.1 | fabric:1.20.2 | quilt:1.20.1 | quilt:1.20.2)
      printf '%s\n' 'platform-fabric-1_20_1'
      ;;
    fabric:1.20.3 | fabric:1.20.4 | quilt:1.20.3 | quilt:1.20.4)
      printf '%s\n' 'platform-fabric-1_20_4'
      ;;
    fabric:1.20.5 | fabric:1.20.6 | quilt:1.20.5 | quilt:1.20.6)
      printf '%s\n' 'platform-fabric-1_20_6'
      ;;
    fabric:1.21 | fabric:1.21.0 | fabric:1.21.1 | quilt:1.21 | quilt:1.21.0 | quilt:1.21.1)
      printf '%s\n' 'platform-fabric-1_21_1'
      ;;
    fabric:1.21.2 | fabric:1.21.3 | fabric:1.21.4 | quilt:1.21.2 | quilt:1.21.3 | quilt:1.21.4)
      printf '%s\n' 'platform-fabric-1_21_4'
      ;;
    fabric:1.21.5 | fabric:1.21.6 | fabric:1.21.7 | fabric:1.21.8 | fabric:1.21.9 | quilt:1.21.5 | quilt:1.21.6 | quilt:1.21.7 | quilt:1.21.8 | quilt:1.21.9)
      printf '%s\n' 'platform-fabric-1_21_9'
      ;;
    fabric:1.21.10 | fabric:1.21.11 | fabric:latest | quilt:1.21.10 | quilt:1.21.11 | quilt:latest)
      printf '%s\n' 'platform-fabric-1_21_11'
      ;;
    forge:1.16.5)
      printf '%s\n' 'platform-forge-1_16_5'
      ;;
    forge:1.17.1)
      printf '%s\n' 'platform-forge-1_17_1'
      ;;
    forge:1.18.2)
      printf '%s\n' 'platform-forge-1_18_2'
      ;;
    forge:1.19.2)
      printf '%s\n' 'platform-forge-1_19_2'
      ;;
    forge:1.19.3 | forge:1.19.4)
      printf '%s\n' 'platform-forge-1_19_4'
      ;;
    forge:1.20.1 | forge:1.20.2)
      printf '%s\n' 'platform-forge-1_20_1'
      ;;
    forge:1.20.3 | forge:1.20.4)
      printf '%s\n' 'platform-forge-1_20_4'
      ;;
    forge:1.20.5 | forge:1.20.6)
      printf '%s\n' 'platform-forge-1_20_6'
      ;;
    forge:1.21 | forge:1.21.0 | forge:1.21.1 | forge:latest)
      printf '%s\n' 'platform-forge-1_21_1'
      ;;
    neoforge:1.20.1 | neoforge:1.20.2)
      printf '%s\n' 'platform-neoforge-1_20_1'
      ;;
    neoforge:1.20.3 | neoforge:1.20.4)
      printf '%s\n' 'platform-neoforge-1_20_4'
      ;;
    neoforge:1.20.5 | neoforge:1.20.6)
      printf '%s\n' 'platform-neoforge-1_20_6'
      ;;
    neoforge:1.21 | neoforge:1.21.0 | neoforge:1.21.1)
      printf '%s\n' 'platform-neoforge-1_21_1'
      ;;
    neoforge:1.21.2 | neoforge:1.21.3 | neoforge:1.21.4)
      printf '%s\n' 'platform-neoforge-1_21_4'
      ;;
    neoforge:1.21.5 | neoforge:1.21.6 | neoforge:1.21.7 | neoforge:1.21.8 | neoforge:1.21.9 | neoforge:1.21.10 | neoforge:1.21.11 | neoforge:latest)
      printf '%s\n' 'platform-neoforge-1_21_11'
      ;;
    *)
      return 1
      ;;
  esac
}

prepare_java_home() {
  local default_java_home="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
  local script_java_home="${KEYSET_JAVA_HOME:-${JAVA_HOME:-$default_java_home}}"
  if [[ -d "$script_java_home" ]]; then
    export JAVA_HOME="$script_java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
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
  printf 'Unsupported %s version: %s\n' "$loader_name" "$requested_version" >&2
  printf 'Supported requests: %s\n' "$support_line" >&2
  exit 1
fi

if [[ $# -gt 0 && "$1" == "--print-module" ]]; then
  printf '%s\n' "$module"
  exit 0
fi

gradle_task=":${module}:runClient"
if [[ $# -gt 0 && "$1" == "--print-command" ]]; then
  printf './gradlew %s\n' "$gradle_task"
  exit 0
fi

prepare_java_home

printf 'Requested %s %s -> using %s\n' "$loader_name" "$requested_version" "$module"
exec ./gradlew "$gradle_task" "$@"
