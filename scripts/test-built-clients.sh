#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GRADLEW="${REPO_ROOT}/gradlew"
GRADLE_PROPERTIES="${REPO_ROOT}/gradle.properties"
RESULTS_DIR="${REPO_ROOT}/testResults"
HOST_OS="$(uname -s)"
HOST_ARCH="$(uname -m)"

read_gradle_property() {
  local key="$1"
  local value
  value="$(awk -F= -v target="$key" '$1 == target { print substr($0, index($0, "=") + 1); exit }' "${GRADLE_PROPERTIES}")"
  if [[ -z "${value}" ]]; then
    printf 'Missing %s in %s\n' "${key}" "${GRADLE_PROPERTIES}" >&2
    exit 1
  fi
  printf '%s\n' "${value}"
}

MOD_ID="$(read_gradle_property "modId")"
PROJECT_VERSION="$(read_gradle_property "projectVersion")"

ALL_LABELS=(
  "fabric-1.20.1-1.20.2"
  "fabric-1.20.4"
  "fabric-1.20.3-1.20.6"
  "fabric-1.21.1"
  "fabric-1.21.4"
  "fabric-1.21.9"
  "fabric-1.21-1.21.11"
  "fabric-26.1"
  "forge-1.20.1-1.20.2"
  "forge-1.20.4"
  "forge-1.20.3-1.20.6"
  "forge-1.21.1"
  "neoforge-1.20.1-1.20.2"
  "neoforge-1.20.4"
  "neoforge-1.20.6"
  "neoforge-1.21.1"
  "neoforge-1.21.4"
  "neoforge-1.21-1.21.11"
  "neoforge-26.1"
)

usage() {
  cat <<EOF
Usage:
  ./scripts/test-built-clients.sh [--skip <label[,label...]|loader>] <label[,label...]|all|loader>
  ./scripts/test-built-clients.sh [--skip <label[,label...]|loader>] <label> <label> ...

Examples:
  ./scripts/test-built-clients.sh fabric-1.21-1.21.11
  ./scripts/test-built-clients.sh fabric-1.21-1.21.11,neoforge-1.21-1.21.11
  ./scripts/test-built-clients.sh forge-1.20.4 neoforge-1.20.6
  ./scripts/test-built-clients.sh forge
  ./scripts/test-built-clients.sh all:fabric
  ./scripts/test-built-clients.sh all --skip forge
  ./scripts/test-built-clients.sh forge --skip forge-1.20.4,forge-1.21.1
  ./scripts/test-built-clients.sh all

Notes:
  - Exact CI labels work, and exact version aliases are also accepted.
  - Example: neoforge-1.21.11 maps to neoforge-1.21-1.21.11.
  - Loader selectors are accepted: fabric, forge, neoforge, all:fabric, all:forge, all:neoforge.
  - --skip accepts labels, aliases, or loader selectors and is applied after expansion.
  - The script builds release jars into builtJars/<version>/ before launching.
  - Clients are launched from each target's run/ directory with a built jar copied into run/mods/.
  - Runtime jar selection prefers build/devlibs/*-dev.jar, then build/libs/*.jar, then builtJars/ release jars.
  - The script tries to generate missing launch metadata first. If that still fails, it records a preflight failure for that target.
EOF
}

require_command() {
  local name="$1"
  if ! command -v "${name}" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "${name}" >&2
    exit 1
  fi
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

strip_trailing_label_punctuation() {
  local value="$1"
  while true; do
    case "${value}" in
      *.) value="${value%?}" ;;
      *,) value="${value%?}" ;;
      *\;) value="${value%?}" ;;
      *:) value="${value%?}" ;;
      *) break ;;
    esac
  done
  printf '%s\n' "${value}"
}

normalize_requested_label() {
  local label="$1"
  label="$(strip_trailing_label_punctuation "$(trim "${label}")")"

  case "${label}" in
    fabric-1.20.1|fabric-1.20.2)
      printf '%s\n' "fabric-1.20.1-1.20.2"
      ;;
    fabric-1.20.3|fabric-1.20.4)
      printf '%s\n' "fabric-1.20.4"
      ;;
    fabric-1.20.5|fabric-1.20.6)
      printf '%s\n' "fabric-1.20.3-1.20.6"
      ;;
    fabric-1.21|fabric-1.21.1)
      printf '%s\n' "fabric-1.21.1"
      ;;
    fabric-1.21.2|fabric-1.21.3|fabric-1.21.4)
      printf '%s\n' "fabric-1.21.4"
      ;;
    fabric-1.21.5|fabric-1.21.6|fabric-1.21.7|fabric-1.21.8|fabric-1.21.9)
      printf '%s\n' "fabric-1.21.9"
      ;;
    fabric-1.21.10|fabric-1.21.11)
      printf '%s\n' "fabric-1.21-1.21.11"
      ;;
    forge-1.20.1|forge-1.20.2)
      printf '%s\n' "forge-1.20.1-1.20.2"
      ;;
    forge-1.20.3|forge-1.20.4)
      printf '%s\n' "forge-1.20.4"
      ;;
    forge-1.20.5|forge-1.20.6)
      printf '%s\n' "forge-1.20.3-1.20.6"
      ;;
    forge-1.21|forge-1.21.1)
      printf '%s\n' "forge-1.21.1"
      ;;
    neoforge-1.20.1|neoforge-1.20.2)
      printf '%s\n' "neoforge-1.20.1-1.20.2"
      ;;
    neoforge-1.20.3|neoforge-1.20.4)
      printf '%s\n' "neoforge-1.20.4"
      ;;
    neoforge-1.20.5|neoforge-1.20.6)
      printf '%s\n' "neoforge-1.20.6"
      ;;
    neoforge-1.21|neoforge-1.21.1)
      printf '%s\n' "neoforge-1.21.1"
      ;;
    neoforge-1.21.2|neoforge-1.21.3|neoforge-1.21.4)
      printf '%s\n' "neoforge-1.21.4"
      ;;
    neoforge-1.21.5|neoforge-1.21.6|neoforge-1.21.7|neoforge-1.21.8|neoforge-1.21.9|neoforge-1.21.10|neoforge-1.21.11)
      printf '%s\n' "neoforge-1.21-1.21.11"
      ;;
    *)
      printf '%s\n' "${label}"
      ;;
  esac
}

is_wayland_session() {
  [[ "${XDG_SESSION_TYPE:-}" == "wayland" || -n "${WAYLAND_DISPLAY:-}" ]]
}

compute_sha1() {
  local file_path="$1"
  if command -v sha1sum >/dev/null 2>&1; then
    sha1sum "${file_path}" | awk '{ print $1 }'
    return 0
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 1 "${file_path}" | awk '{ print $1 }'
    return 0
  fi
  return 1
}

download_gradle_cache_artifact_if_possible() {
  local path="$1"
  local cache_root="${HOME}/.gradle/caches/modules-2/files-2.1/"
  local relative_path group module version checksum filename group_path url tmp_file actual_sha1

  [[ -e "${path}" ]] && return 0
  [[ "${path}" == "${cache_root}"* ]] || return 1

  relative_path="${path#${cache_root}}"
  IFS='/' read -r group module version checksum filename <<<"${relative_path}"
  [[ -n "${group}" && -n "${module}" && -n "${version}" && -n "${checksum}" && -n "${filename}" ]] || return 1
  [[ -z "${filename#*.jar}" || "${filename}" == *.pom || "${filename}" == *.module ]] || return 1
  # Only hydrate the known stale dependency path that appears in copied NeoForge argfiles.
  [[ "${group}" == "io.github.juuxel" && "${module}" == "unprotect" ]] || return 1

  group_path="${group//./\/}"
  url="https://repo1.maven.org/maven2/${group_path}/${module}/${version}/${filename}"
  tmp_file="$(mktemp "${TMPDIR:-/tmp}/keyset-artifact.XXXXXX")"
  if ! curl -fsSL --retry 2 --connect-timeout 10 --max-time 60 -o "${tmp_file}" "${url}" 2>/dev/null; then
    rm -f "${tmp_file}"
    return 1
  fi

  if actual_sha1="$(compute_sha1 "${tmp_file}")"; then
    if [[ "${actual_sha1}" != "${checksum}" ]]; then
      rm -f "${tmp_file}"
      return 1
    fi
  fi

  mkdir -p "$(dirname "${path}")"
  mv "${tmp_file}" "${path}"
  return 0
}

java_major_version() {
  local java_bin="$1"
  local version_line raw major
  version_line="$("${java_bin}" -version 2>&1 | head -n 1)"
  raw="$(printf '%s' "${version_line}" | sed -E 's/.*version "([^"]+)".*/\1/')"
  major="${raw%%.*}"
  if [[ "${major}" == "1" ]]; then
    major="$(printf '%s' "${raw}" | awk -F. '{ print $2 }')"
  fi
  printf '%s\n' "${major}"
}

java_bin_from_home() {
  local home="$1"
  if [[ -n "${home}" && -x "${home}/bin/java" ]]; then
    printf '%s\n' "${home}/bin/java"
  fi
}

find_java_bin() {
  local min_version="$1"
  local env_name="KEYSET_JAVA_${min_version}_HOME"
  local env_home="${!env_name:-}"
  local -a candidate_bins=()
  local java_bin major

  if [[ -n "${env_home}" ]]; then
    candidate_bins+=("$(java_bin_from_home "${env_home}")")
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidate_bins+=("$(java_bin_from_home "${JAVA_HOME}")")
  fi
  candidate_bins+=(
    "$(java_bin_from_home "/usr/lib/jvm/java-${min_version}-temurin")"
    "$(java_bin_from_home "/usr/lib/jvm/java-${min_version}-openjdk")"
  )
  if [[ "${min_version}" -eq 25 ]]; then
    candidate_bins+=("$(java_bin_from_home "/usr/lib/jvm/java-26-temurin")")
  fi
  candidate_bins+=(
    "$(command -v java 2>/dev/null || true)"
    "$(java_bin_from_home "/usr/lib/jvm/default")"
    "$(java_bin_from_home "/usr/lib/jvm/default-runtime")"
  )

  for java_bin in "${candidate_bins[@]}"; do
    if [[ -z "${java_bin}" || ! -x "${java_bin}" ]]; then
      continue
    fi
    major="$(java_major_version "${java_bin}")"
    if [[ -n "${major}" && "${major}" -ge "${min_version}" ]]; then
      printf '%s\n' "${java_bin}"
      return 0
    fi
  done

  return 1
}

require_java_bin() {
  local min_version="$1"
  local java_bin
  if ! java_bin="$(find_java_bin "${min_version}")"; then
    printf 'Unable to find a Java runtime with version %s or newer.\n' "${min_version}" >&2
    printf 'Set KEYSET_JAVA_%s_HOME to an appropriate JDK home.\n' "${min_version}" >&2
    exit 1
  fi
  printf '%s\n' "${java_bin}"
}

require_gradle_java_bin() {
  local gradle_java_bin
  if [[ -n "${KEYSET_GRADLE_JAVA_HOME:-}" ]]; then
    gradle_java_bin="$(java_bin_from_home "${KEYSET_GRADLE_JAVA_HOME}")"
    if [[ -z "${gradle_java_bin}" ]]; then
      printf 'KEYSET_GRADLE_JAVA_HOME does not point to a valid JDK: %s\n' "${KEYSET_GRADLE_JAVA_HOME}" >&2
      exit 1
    fi
    if [[ "$(java_major_version "${gradle_java_bin}")" -lt 25 ]]; then
      printf 'KEYSET_GRADLE_JAVA_HOME must point to Java 25 or newer: %s\n' "${KEYSET_GRADLE_JAVA_HOME}" >&2
      exit 1
    fi
    printf '%s\n' "${gradle_java_bin}"
    return 0
  fi

  require_java_bin 25
}

csv_escape() {
  local value="${1//$'\r'/ }"
  value="${value//$'\n'/ }"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
}

append_csv_row() {
  local csv_path="$1"
  shift
  local first=1
  local field
  for field in "$@"; do
    if [[ "${first}" -eq 0 ]]; then
      printf ',' >>"${csv_path}"
    fi
    csv_escape "${field}" >>"${csv_path}"
    first=0
  done
  printf '\n' >>"${csv_path}"
}

prompt_from_tty() {
  local prompt="$1"
  local answer
  printf '%s' "${prompt}" > /dev/tty
  IFS= read -r answer < /dev/tty
  printf '%s\n' "${answer}"
}

prompt_yes_no() {
  local prompt="$1"
  local answer
  while true; do
    answer="$(prompt_from_tty "${prompt}")"
    answer="$(trim "${answer}")"
    case "${answer,,}" in
      y|yes)
        printf 'yes\n'
        return 0
        ;;
      n|no)
        printf 'no\n'
        return 0
        ;;
      *)
        printf 'Please answer yes or no.\n' > /dev/tty
        ;;
    esac
  done
}

resolve_target_metadata() {
  local label="$1"
  case "${label}" in
    fabric-1.20.1-1.20.2)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_20_1" "17" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.20.4)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_20_4" "17" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.20.3-1.20.6)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_20_6" "21" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.21.1)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_21_1" "21" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.21.4)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_21_4" "21" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.21.9)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_21_9" "21" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-1.21-1.21.11)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/1_21_11" "21" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    fabric-26.1)
      printf '%s|%s|%s|%s\n' "fabric" "platforms/fabric/26_1" "25" "net.fabricmc.loader.impl.launch.knot.KnotClient"
      ;;
    forge-1.20.1-1.20.2)
      printf '%s|%s|%s|%s\n' "forge" "platforms/forge/1_20_1" "17" "cpw.mods.bootstraplauncher.BootstrapLauncher"
      ;;
    forge-1.20.4)
      printf '%s|%s|%s|%s\n' "forge" "platforms/forge/1_20_4" "17" "net.minecraftforge.bootstrap.ForgeBootstrap"
      ;;
    forge-1.20.3-1.20.6)
      printf '%s|%s|%s|%s\n' "forge" "platforms/forge/1_20_6" "21" "net.minecraftforge.bootstrap.ForgeBootstrap"
      ;;
    forge-1.21.1)
      printf '%s|%s|%s|%s\n' "forge" "platforms/forge/1_21_1" "21" "net.minecraftforge.bootstrap.ForgeBootstrap"
      ;;
    neoforge-1.20.1-1.20.2)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_20_1" "17" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-1.20.4)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_20_4" "17" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-1.20.6)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_20_6" "21" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-1.21.1)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_21_1" "21" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-1.21.4)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_21_4" "21" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-1.21-1.21.11)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/1_21_11" "21" "net.neoforged.fml.startup.Client"
      ;;
    neoforge-26.1)
      printf '%s|%s|%s|%s\n' "neoforge" "platforms/neoforge/26_1" "25" "net.neoforged.fml.startup.Client"
      ;;
    *)
      return 1
      ;;
  esac
}

resolve_runtime_jar_path() {
  local label="$1"
  local loader="$2"
  local platform_dir="$3"
  local dev_jar
  local libs_jar
  local release_jar

  dev_jar="${REPO_ROOT}/${platform_dir}/build/devlibs/${MOD_ID}-${label}-${PROJECT_VERSION}-dev.jar"
  libs_jar="${REPO_ROOT}/${platform_dir}/build/libs/${MOD_ID}-${label}-${PROJECT_VERSION}.jar"
  release_jar="${REPO_ROOT}/builtJars/${PROJECT_VERSION}/${loader}/${MOD_ID}-${label}-${PROJECT_VERSION}.jar"

  if [[ -f "${dev_jar}" ]]; then
    printf '%s\n' "${dev_jar}"
    return 0
  fi
  if [[ -f "${libs_jar}" ]]; then
    printf '%s\n' "${libs_jar}"
    return 0
  fi

  # Fallback for targets that only produce release jars in this workspace.
  printf '%s\n' "${release_jar}"
}

split_labels() {
  local raw="$1"
  local label normalized
  raw="$(trim "${raw}")"
  raw="$(strip_trailing_label_punctuation "${raw}")"
  if [[ -z "${raw}" ]]; then
    return 0
  fi

  case "${raw}" in
    all)
      printf '%s\n' "${ALL_LABELS[@]}"
      return 0
      ;;
    fabric|all:fabric|fabric:all)
      printf '%s\n' "${ALL_LABELS[@]}" | awk '/^fabric-/'
      return 0
      ;;
    forge|all:forge|forge:all)
      printf '%s\n' "${ALL_LABELS[@]}" | awk '/^forge-/'
      return 0
      ;;
    neoforge|all:neoforge|neoforge:all)
      printf '%s\n' "${ALL_LABELS[@]}" | awk '/^neoforge-/'
      return 0
      ;;
  esac

  IFS=',' read -r -a parsed <<<"${raw}"
  for label in "${parsed[@]}"; do
    normalized="$(normalize_requested_label "${label}")"
    case "${normalized}" in
      fabric|all:fabric|fabric:all)
        printf '%s\n' "${ALL_LABELS[@]}" | awk '/^fabric-/'
        ;;
      forge|all:forge|forge:all)
        printf '%s\n' "${ALL_LABELS[@]}" | awk '/^forge-/'
        ;;
      neoforge|all:neoforge|neoforge:all)
        printf '%s\n' "${ALL_LABELS[@]}" | awk '/^neoforge-/'
        ;;
      *)
        if [[ -n "${normalized}" ]]; then
          printf '%s\n' "${normalized}"
        fi
        ;;
    esac
  done
}

label_is_known() {
  local needle="$1"
  local label
  for label in "${ALL_LABELS[@]}"; do
    if [[ "${label}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

locate_cache_jar() {
  local group="$1"
  local module="$2"
  local jar_name_prefix="$3"
  local jar_path
  jar_path="$(find "${HOME}/.gradle/caches/modules-2/files-2.1/${group}/${module}" -name "${jar_name_prefix}*.jar" 2>/dev/null | sort | tail -n 1)"
  if [[ -z "${jar_path}" ]]; then
    return 1
  fi
  printf '%s\n' "${jar_path}"
}

locate_asm_runtime_jars() {
  local joined=""
  local jar=""
  local module=""
  local modules=("asm" "asm-tree" "asm-commons" "asm-analysis" "asm-util")

  for module in "${modules[@]}"; do
    jar="$(locate_cache_jar "org.ow2.asm" "${module}" "${module}-" || true)"
    if [[ -z "${jar}" ]]; then
      continue
    fi
    if [[ -n "${joined}" ]]; then
      joined="${joined}:"
    fi
    joined="${joined}${jar}"
  done

  printf '%s\n' "${joined}"
}

normalize_path_prefix() {
  local path="$1"
  path="${path//\/Users\/beeboyd\/Developer\/MCMods\/Keyset/${REPO_ROOT}}"
  path="${path//\/Users\/beeboyd\/.gradle/${HOME}/.gradle}"
  path="${path//\/Users\/beeboyd/${HOME}}"
  printf '%s\n' "${path}"
}

normalize_file_to_temp() {
  local source_file="$1"
  local suffix="$2"
  local temp_file
  temp_file="$(mktemp "${TMPDIR:-/tmp}/keyset-${suffix}.XXXXXX")"
  sed \
    -e "s|/Users/beeboyd/Developer/MCMods/Keyset|${REPO_ROOT}|g" \
    -e "s|/Users/beeboyd/.gradle|${HOME}/.gradle|g" \
    -e "s|/Users/beeboyd|${HOME}|g" \
    "${source_file}" >"${temp_file}"
  printf '%s\n' "${temp_file}"
}

host_native_suffixes() {
  case "${HOST_OS}:${HOST_ARCH}" in
    Linux:x86_64)
      printf '%s\n' "natives-linux-x86_64.jar" "natives-linux.jar"
      ;;
    Linux:aarch64 | Linux:arm64)
      printf '%s\n' "natives-linux-arm64.jar" "natives-linux-aarch_64.jar" "natives-linux.jar"
      ;;
    Darwin:arm64)
      printf '%s\n' "natives-macos-arm64.jar" "natives-macos.jar" "natives-osx-arm64.jar" "natives-osx.jar"
      ;;
    Darwin:x86_64)
      printf '%s\n' "natives-macos.jar" "natives-osx.jar"
      ;;
    *)
      printf '%s\n' ""
      ;;
  esac
}

resolve_existing_classpath_entry() {
  local path="$1"
  local normalized base artifact_prefix version_dir suffix candidate
  normalized="$(normalize_path_prefix "${path}")"

  if [[ -e "${normalized}" ]]; then
    printf '%s\n' "${normalized}"
    return 0
  fi

  base="$(basename "${normalized}")"
  if [[ "${base}" == *-natives-* ]]; then
    artifact_prefix="${base%%-natives-*}"
    version_dir="$(dirname "$(dirname "${normalized}")")"
    if [[ -d "${version_dir}" ]]; then
      while IFS= read -r suffix; do
        [[ -z "${suffix}" ]] && continue
        candidate="$(find "${version_dir}" -maxdepth 2 -type f -name "${artifact_prefix}-${suffix}" | head -n 1)"
        if [[ -n "${candidate}" ]]; then
          printf '%s\n' "${candidate}"
          return 0
        fi
      done < <(host_native_suffixes)
    fi
  fi

  if download_gradle_cache_artifact_if_possible "${normalized}"; then
    printf '%s\n' "${normalized}"
    return 0
  fi

  printf '%s\n' "${normalized}"
}

build_classpath_from_argfile() {
  local argfile="$1"
  local filtered_parts=()
  local raw_classpath part
  raw_classpath="$(sed -n '2p' "${argfile}")"
  IFS=':' read -r -a parts <<<"${raw_classpath}"
  for part in "${parts[@]}"; do
    part="$(resolve_existing_classpath_entry "${part}")"
    if [[ -z "${part}" ]]; then
      continue
    fi
    case "${part}" in
      */build/classes/java/main|\
      */build/resources/main|\
      */build/libs/${MOD_ID}-*.jar|\
      */build/devlibs/${MOD_ID}-*.jar)
        continue
        ;;
    esac
    filtered_parts+=("${part}")
  done

  if [[ "${#filtered_parts[@]}" -eq 0 ]]; then
    return 1
  fi

  local joined=""
  for part in "${filtered_parts[@]}"; do
    if [[ -n "${joined}" ]]; then
      joined="${joined}:"
    fi
    joined="${joined}${part}"
  done
  printf '%s\n' "${joined}"
}

build_classpath_from_remap() {
  local remap_classpath_file="$1"
  local runtime_classpath normalized_parts=() part
  runtime_classpath="$(<"${remap_classpath_file}")"
  IFS=':' read -r -a parts <<<"${runtime_classpath}"
  for part in "${parts[@]}"; do
    normalized_parts+=("$(resolve_existing_classpath_entry "${part}")")
  done
  local joined=""
  for part in "${normalized_parts[@]}"; do
    if [[ -n "${joined}" ]]; then
      joined="${joined}:"
    fi
    joined="${joined}${part}"
  done
  printf '%s\n' "${joined}"
}

ensure_runclient_classpath_init_script() {
  if [[ -n "${RUNCLIENT_CLASSPATH_INIT_SCRIPT:-}" && -f "${RUNCLIENT_CLASSPATH_INIT_SCRIPT}" ]]; then
    return 0
  fi

  RUNCLIENT_CLASSPATH_INIT_SCRIPT="$(mktemp "${TMPDIR:-/tmp}/keyset-runclient-cp-init.XXXXXX.gradle")"
  cat >"${RUNCLIENT_CLASSPATH_INIT_SCRIPT}" <<'GRADLE'
gradle.projectsEvaluated {
  def projectPath = gradle.startParameter.projectProperties.get('keysetProjectPath')
  if (projectPath == null || projectPath.isBlank()) {
    throw new GradleException('Missing -PkeysetProjectPath for printRunClientClasspathFromInit')
  }

  def p = gradle.rootProject.findProject(projectPath)
  if (p == null) {
    throw new GradleException("Project not found: ${projectPath}")
  }

  p.tasks.register('printRunClientClasspathFromInit') {
    doLast {
      def runClientTask = p.tasks.findByName('runClient')
      if (runClientTask == null || !runClientTask.hasProperty('classpath')) {
        throw new GradleException("runClient classpath is unavailable for ${projectPath}")
      }
      println("RUNCLIENT_CP=${runClientTask.classpath.asPath}")
      (runClientTask.jvmArgs ?: []).each { arg ->
        println("RUNCLIENT_JVMARG=${arg}")
      }
    }
  }
}
GRADLE
}

build_classpath_from_gradle_run_client() {
  local gradle_java_home="$1"
  local platform_dir="$2"
  local project_path output cp_line part
  local normalized_parts=()
  local joined=""

  ensure_runclient_classpath_init_script
  project_path="$(platform_dir_to_project_path "${platform_dir}")"

  if ! output="$(
    (
      cd "${REPO_ROOT}"
      export JAVA_HOME="${gradle_java_home}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      "${GRADLEW}" \
        -q \
        --console=plain \
        -I "${RUNCLIENT_CLASSPATH_INIT_SCRIPT}" \
        "-PkeysetProjectPath=${project_path}" \
        "${project_path}:printRunClientClasspathFromInit"
    ) 2>&1
  )"; then
    return 1
  fi

  cp_line="$(printf '%s\n' "${output}" | awk -F'RUNCLIENT_CP=' '/RUNCLIENT_CP=/{print $2}' | tail -n 1)"
  if [[ -z "${cp_line}" ]]; then
    return 1
  fi

  IFS=':' read -r -a parts <<<"${cp_line}"
  for part in "${parts[@]}"; do
    part="$(resolve_existing_classpath_entry "${part}")"
    if [[ -z "${part}" ]]; then
      continue
    fi
    case "${part}" in
      */build/classes/java/main|\
      */build/resources/main|\
      */build/libs/${MOD_ID}-*.jar|\
      */build/devlibs/${MOD_ID}-*.jar)
        continue
        ;;
    esac
    normalized_parts+=("${part}")
  done

  if [[ "${#normalized_parts[@]}" -eq 0 ]]; then
    return 1
  fi

  for part in "${normalized_parts[@]}"; do
    if [[ -n "${joined}" ]]; then
      joined="${joined}:"
    fi
    joined="${joined}${part}"
  done
  printf '%s\n' "${joined}"
}

build_jvmargs_from_gradle_run_client() {
  local gradle_java_home="$1"
  local platform_dir="$2"
  local project_path output arg_line arg_value

  ensure_runclient_classpath_init_script
  project_path="$(platform_dir_to_project_path "${platform_dir}")"

  if ! output="$(
    (
      cd "${REPO_ROOT}"
      export JAVA_HOME="${gradle_java_home}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      "${GRADLEW}" \
        -q \
        --console=plain \
        -I "${RUNCLIENT_CLASSPATH_INIT_SCRIPT}" \
        "-PkeysetProjectPath=${project_path}" \
        "${project_path}:printRunClientClasspathFromInit"
    ) 2>&1
  )"; then
    return 1
  fi

  while IFS= read -r arg_line; do
    arg_value="${arg_line#RUNCLIENT_JVMARG=}"
    if [[ -z "${arg_value}" ]]; then
      continue
    fi

    case "${arg_value}" in
      @*)
        # Keep launch independent from Gradle-generated classpath argfiles so we
        # can control classpath injection and always load the built jar from mods/.
        continue
        ;;
      -cp|-classpath)
        # runClient sometimes emits these in pairs with an explicit classpath value.
        # We already provide the final classpath explicitly in this script.
        continue
        ;;
      -Dfabric.dli.config=*|-Dfabric.dli.env=*|-Dfabric.dli.main=*)
        continue
        ;;
    esac

    printf '%s\n' "${arg_value}"
  done < <(printf '%s\n' "${output}" | awk '/^RUNCLIENT_JVMARG=/{print}')
}

resolve_dli_main_from_gradle_run_client() {
  local gradle_java_home="$1"
  local platform_dir="$2"
  local fallback_main="$3"
  local project_path output detected_main

  ensure_runclient_classpath_init_script
  project_path="$(platform_dir_to_project_path "${platform_dir}")"

  if ! output="$(
    (
      cd "${REPO_ROOT}"
      export JAVA_HOME="${gradle_java_home}"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      "${GRADLEW}" \
        -q \
        --console=plain \
        -I "${RUNCLIENT_CLASSPATH_INIT_SCRIPT}" \
        "-PkeysetProjectPath=${project_path}" \
        "${project_path}:printRunClientClasspathFromInit"
    ) 2>&1
  )"; then
    printf '%s\n' "${fallback_main}"
    return 0
  fi

  detected_main="$(printf '%s\n' "${output}" | awk -F'RUNCLIENT_JVMARG=-Dfabric.dli.main=' '/RUNCLIENT_JVMARG=-Dfabric.dli.main=/{print $2; exit}')"
  if [[ -n "${detected_main}" ]]; then
    printf '%s\n' "${detected_main}"
  else
    printf '%s\n' "${fallback_main}"
  fi
}

prepare_forge_1211_runtime() {
  local gradle_java_home="$1"
  (
    cd "${REPO_ROOT}"
    export JAVA_HOME="${gradle_java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    "${GRADLEW}" \
      ":platform-forge-1_21_1:prepareForgeDevFmlConfig" \
      ":platform-forge-1_21_1:compileLauncherPatchJava" \
      ":platform-forge-1_21_1:compileEventbusPatchJava" \
      ":platform-forge-1_21_1:bootstrapPatchJar"
  ) >/dev/null
}

platform_dir_to_project_path() {
  local platform_dir="$1"
  local project_suffix
  project_suffix="${platform_dir#platforms/}"
  project_suffix="${project_suffix//\//-}"
  printf ':%s\n' "platform-${project_suffix}"
}

prepare_launch_metadata() {
  local gradle_java_home="$1"
  local loader="$2"
  local platform_dir="$3"
  local project_path
  local -a prep_tasks=()

  project_path="$(platform_dir_to_project_path "${platform_dir}")"

  case "${loader}" in
    fabric)
      prep_tasks=("${project_path}:configureClientLaunch" "${project_path}:generateDLIConfig" "${project_path}:generateRemapClasspath")
      ;;
    forge|neoforge)
      prep_tasks=("${project_path}:configureClientLaunch" "${project_path}:generateDLIConfig")
      ;;
    *)
      return 0
      ;;
  esac

  (
    cd "${REPO_ROOT}"
    export JAVA_HOME="${gradle_java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    "${GRADLEW}" "${prep_tasks[@]}"
  )
}

runtime_extra_classpath() {
  local label="$1"
  case "${label}" in
    forge-1.21.1)
      printf '%s\n' "${REPO_ROOT}/platforms/forge/1_21_1/build/libs/keyset-forge-1.21.1-${PROJECT_VERSION}-bootstrap-patch.jar"
      ;;
    *)
      printf '%s\n' ""
      ;;
  esac
}

runtime_extra_java_args() {
  local label="$1"
  case "${label}" in
    forge-1.21.1)
      printf '%s\n' "-Dkeyset.fmlloaderPatchPath=${REPO_ROOT}/platforms/forge/1_21_1/build/classes/java/launcherPatch|-Dkeyset.eventbusPatchPath=${REPO_ROOT}/platforms/forge/1_21_1/build/classes/java/eventbusPatch"
      ;;
    *)
      printf '%s\n' ""
      ;;
  esac
}

ensure_file_exists() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    printf 'Missing required file: %s\n' "${path}" >&2
    return 1
  fi
}

resolve_launch_inputs_problem() {
  local jar_path="$1"
  local launch_cfg="$2"
  local run_dir="$3"
  local argfile="$4"
  local remap_classpath_file="$5"
  local missing=()

  [[ ! -f "${jar_path}" ]] && missing+=("jar")
  [[ ! -f "${launch_cfg}" ]] && missing+=("launch.cfg")
  [[ ! -d "${run_dir}" ]] && missing+=("run directory")
  if [[ ! -f "${argfile}" && ! -f "${remap_classpath_file}" ]]; then
    missing+=("argfile/remapClasspath")
  fi

  if [[ "${#missing[@]}" -eq 0 ]]; then
    return 1
  fi

  local joined=""
  local item
  for item in "${missing[@]}"; do
    if [[ -n "${joined}" ]]; then
      joined+=", "
    fi
    joined+="${item}"
  done
  printf 'Missing required launch inputs: %s\n' "${joined}"
}

prepare_runtime_config() {
  local loader="$1"
  local run_dir="$2"
  local -n compatibility_note_ref="$3"
  local -n compatibility_backup_ref="$4"

  compatibility_note_ref=""
  compatibility_backup_ref=""

  if [[ "${loader}" != "neoforge" ]] || ! is_wayland_session; then
    return 0
  fi

  local fml_config="${run_dir}/config/fml.toml"
  if [[ ! -f "${fml_config}" ]]; then
    return 0
  fi

  local backup
  backup="$(mktemp "${TMPDIR:-/tmp}/keyset-fml-config.XXXXXX")"
  cp -f "${fml_config}" "${backup}"

  if grep -q '^earlyWindowControl *= *' "${fml_config}"; then
    sed -i 's/^earlyWindowControl *= *.*/earlyWindowControl = false/' "${fml_config}"
  else
    printf '\nearlyWindowControl = false\n' >>"${fml_config}"
  fi

  compatibility_note_ref="NeoForge early loading screen disabled for Wayland compatibility."
  compatibility_backup_ref="${backup}"
}

restore_runtime_config() {
  local backup_path="$1"
  local run_dir="$2"

  if [[ -z "${backup_path}" || ! -f "${backup_path}" ]]; then
    return 0
  fi

  cp -f "${backup_path}" "${run_dir}/config/fml.toml"
  rm -f "${backup_path}"
}

launch_target() {
  local label="$1"
  local loader="$2"
  local platform_dir="$3"
  local dli_main="$4"
  local jar_path="$5"
  local launch_cfg="$6"
  local run_dir="$7"
  local argfile="$8"
  local remap_classpath_file="$9"
  local java_bin="${10}"
  local dli_jar="${11}"
  local log4j_util_jar="${12}"
  local gradle_java_home="${13}"

  local runtime_classpath=""
  local extra_cp=""
  local extra_java_args_line=""
  local -a extra_java_args=()
  local runclient_jvm_arg=""
  local normalized_launch_cfg normalized_remap_classpath=""
  local compatibility_note=""
  local compatibility_backup=""
  local java_major exit_code

  if [[ -f "${argfile}" ]]; then
    runtime_classpath="$(build_classpath_from_argfile "${argfile}")"
  elif runtime_classpath="$(build_classpath_from_gradle_run_client "${gradle_java_home}" "${platform_dir}")"; then
    :
  else
    runtime_classpath="$(build_classpath_from_remap "${remap_classpath_file}")"
    runtime_classpath="${dli_jar}:${log4j_util_jar}:${runtime_classpath}"
  fi

  extra_cp="$(runtime_extra_classpath "${label}")"
  if [[ -n "${extra_cp}" ]]; then
    runtime_classpath="${runtime_classpath}:${extra_cp}"
  fi
  extra_java_args_line="$(runtime_extra_java_args "${label}")"
  if [[ -n "${extra_java_args_line}" ]]; then
    IFS='|' read -r -a extra_java_args <<<"${extra_java_args_line}"
  fi
  while IFS= read -r runclient_jvm_arg; do
    [[ -n "${runclient_jvm_arg}" ]] || continue
    extra_java_args+=("${runclient_jvm_arg}")
  done < <(build_jvmargs_from_gradle_run_client "${gradle_java_home}" "${platform_dir}" || true)
  java_major="$(java_major_version "${java_bin}")"
  if [[ "${loader}" == "forge" || "${loader}" == "neoforge" ]]; then
    extra_java_args+=(
      "--add-opens"
      "java.base/java.lang.invoke=ALL-UNNAMED"
    )
  fi

  if [[ "${loader}" == "neoforge" ]]; then
    if [[ "${java_major}" -ge 24 ]]; then
      extra_java_args+=("--sun-misc-unsafe-memory-access=allow")
    fi
    extra_java_args+=(
      "--enable-native-access=ALL-UNNAMED"
      "--add-exports"
      "jdk.naming.dns/com.sun.jndi.dns=java.naming"
    )
  fi

  normalized_launch_cfg="$(normalize_file_to_temp "${launch_cfg}" "launch-cfg")"
  if [[ -f "${remap_classpath_file}" ]]; then
    normalized_remap_classpath="$(normalize_file_to_temp "${remap_classpath_file}" "remap-classpath")"
    python - "${normalized_launch_cfg}" "${normalized_remap_classpath}" <<'PY'
from pathlib import Path
import sys
launch_cfg = Path(sys.argv[1])
remap = sys.argv[2]
text = launch_cfg.read_text()
for line in text.splitlines():
    if line.startswith("\tfabric.remapClasspathFile="):
        text = text.replace(line, "\tfabric.remapClasspathFile=" + remap)
launch_cfg.write_text(text)
PY
  fi

  mkdir -p "${run_dir}/mods"
  find "${run_dir}/mods" -maxdepth 1 -type f -name "${MOD_ID}-*.jar" -delete
  cp -f "${jar_path}" "${run_dir}/mods/"
  prepare_runtime_config "${loader}" "${run_dir}" compatibility_note compatibility_backup

  printf '\n=== Launching %s ===\n' "${label}"
  printf 'Loader: %s\n' "${loader}"
  printf 'Runtime Java: %s (major %s)\n' "${java_bin}" "${java_major}"
  printf 'Run dir: %s\n' "${run_dir}"
  printf 'Mods jar: %s\n\n' "${run_dir}/mods/$(basename "${jar_path}")"
  if [[ -n "${compatibility_note}" ]]; then
    printf '%s\n\n' "${compatibility_note}"
  fi

  set +e
  (
    cd "${run_dir}"
    "${java_bin}" \
      "-cp" "${runtime_classpath}" \
      "-Dfabric.dli.config=${normalized_launch_cfg}" \
      "-Dfabric.dli.env=client" \
      "-Dfabric.dli.main=${dli_main}" \
      "-Dfile.encoding=UTF-8" \
      "-Duser.language=en" \
      "${extra_java_args[@]}" \
      net.fabricmc.devlaunchinjector.Main
  )
  exit_code="$?"
  set -e
  restore_runtime_config "${compatibility_backup}" "${run_dir}"
  return "${exit_code}"
}

main() {
  require_command awk
  require_command date
  require_command find
  require_command sed

  if [[ $# -lt 1 ]]; then
    usage
    exit 1
  fi

  local -a requested_inputs=()
  local -a skip_inputs=()
  local raw_arg label
  while [[ $# -gt 0 ]]; do
    case "${1}" in
      --help|-h)
        usage
        exit 0
        ;;
      --skip|-s)
        if [[ $# -lt 2 ]]; then
          printf 'Missing value for %s\n' "${1}" >&2
          exit 1
        fi
        skip_inputs+=("${2}")
        shift 2
        ;;
      *)
        requested_inputs+=("${1}")
        shift
        ;;
    esac
  done

  if [[ "${#requested_inputs[@]}" -eq 0 ]]; then
    printf 'No test labels were provided.\n' >&2
    exit 1
  fi

  local -a requested_labels=()
  for raw_arg in "${requested_inputs[@]}"; do
    while IFS= read -r label; do
      [[ -n "${label}" ]] && requested_labels+=("${label}")
    done < <(split_labels "${raw_arg}")
  done

  if [[ "${#requested_labels[@]}" -eq 0 ]]; then
    printf 'No test labels were resolved from input.\n' >&2
    exit 1
  fi

  local -a skip_labels=()
  for raw_arg in "${skip_inputs[@]}"; do
    while IFS= read -r label; do
      [[ -n "${label}" ]] && skip_labels+=("${label}")
    done < <(split_labels "${raw_arg}")
  done

  local -A skip_lookup=()
  local -A seen_labels=()
  local -a filtered_labels=()
  for label in "${skip_labels[@]}"; do
    skip_lookup["${label}"]=1
  done
  for label in "${requested_labels[@]}"; do
    if [[ -n "${skip_lookup["${label}"]+x}" ]]; then
      continue
    fi
    if [[ -z "${seen_labels["${label}"]+x}" ]]; then
      filtered_labels+=("${label}")
      seen_labels["${label}"]=1
    fi
  done
  requested_labels=("${filtered_labels[@]}")

  if [[ "${#requested_labels[@]}" -eq 0 ]]; then
    printf 'All requested targets were excluded by --skip.\n' >&2
    exit 1
  fi

  local -a unknown_labels=()
  for label in "${requested_labels[@]}"; do
    if ! label_is_known "${label}"; then
      unknown_labels+=("${label}")
    fi
  done
  if [[ "${#unknown_labels[@]}" -gt 0 ]]; then
    printf 'Unknown build target label(s): %s\n' "$(printf '%s, ' "${unknown_labels[@]}" | sed 's/, $//')" >&2
    printf 'Valid labels: %s\n' "$(printf '%s, ' "${ALL_LABELS[@]}" | sed 's/, $//')" >&2
    exit 1
  fi

  local build_targets_csv
  build_targets_csv="$(printf '%s,' "${requested_labels[@]}")"
  build_targets_csv="${build_targets_csv%,}"

  local gradle_java_bin gradle_java_home
  gradle_java_bin="$(require_gradle_java_bin)"
  gradle_java_home="$(cd "$(dirname "${gradle_java_bin}")/.." && pwd)"

  printf 'Using Gradle Java: %s (major %s)\n' "${gradle_java_bin}" "$(java_major_version "${gradle_java_bin}")"
  printf 'Building release jars for: %s\n\n' "${build_targets_csv}"

  (
    cd "${REPO_ROOT}"
    export JAVA_HOME="${gradle_java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    "${GRADLEW}" \
      "validateBuildTargets" \
      "buildSelectedTargets" \
      "-PbuildTargets=${build_targets_csv}"
  )

  mkdir -p "${RESULTS_DIR}"
  local csv_path="${RESULTS_DIR}/${PROJECT_VERSION}-$(date +%Y%m%d-%H%M%S).csv"
  append_csv_row "${csv_path}" "timestamp" "label" "loader" "platform_dir" "jar_path" "launch_exit_code" "result" "problems"

  local dli_jar log4j_util_jar
  dli_jar="$(locate_cache_jar "net.fabricmc" "dev-launch-injector" "dev-launch-injector-")"
  log4j_util_jar="$(locate_cache_jar "net.fabricmc" "fabric-log4j-util" "fabric-log4j-util-")"

  local metadata loader platform_dir java_version dli_main detected_dli_main jar_path launch_cfg run_dir argfile remap_classpath_file java_bin launch_exit_code result problems response timestamp

  for label in "${requested_labels[@]}"; do
    timestamp="$(date '+%Y-%m-%dT%H:%M:%S%z')"
    problems=""
    launch_exit_code=""
    result=""

    if ! metadata="$(resolve_target_metadata "${label}")"; then
      printf '\nSkipping unknown label: %s\n' "${label}" >&2
      append_csv_row "${csv_path}" "${timestamp}" "${label}" "" "" "" "" "unknown_label" "Label is not mapped in scripts/test-built-clients.sh"
      continue
    fi

    IFS='|' read -r loader platform_dir java_version dli_main <<<"${metadata}"
    detected_dli_main="$(resolve_dli_main_from_gradle_run_client "${gradle_java_home}" "${platform_dir}" "${dli_main}")"
    jar_path="$(resolve_runtime_jar_path "${label}" "${loader}" "${platform_dir}")"
    launch_cfg="${REPO_ROOT}/${platform_dir}/.gradle/loom-cache/launch.cfg"
    run_dir="${REPO_ROOT}/${platform_dir}/run"
    argfile="${REPO_ROOT}/${platform_dir}/build/loom-cache/argFiles/runClient"
    remap_classpath_file="${REPO_ROOT}/${platform_dir}/.gradle/loom-cache/remapClasspath.txt"

    set +e
    prepare_launch_metadata "${gradle_java_home}" "${loader}" "${platform_dir}" >/dev/null 2>&1
    set -e

    if [[ "${label}" == "forge-1.21.1" ]]; then
      prepare_forge_1211_runtime "${gradle_java_home}"
    fi

    if ! java_bin="$(find_java_bin "${java_version}")"; then
      printf '\nSkipping %s: no Java %s+ runtime available.\n' "${label}" "${java_version}" >&2
      append_csv_row "${csv_path}" "${timestamp}" "${label}" "${loader}" "${platform_dir}" "${jar_path}" "" "missing_java" "No Java ${java_version}+ runtime available"
      continue
    fi

    problems=""
    if ! ensure_file_exists "${jar_path}" || \
       ! ensure_file_exists "${launch_cfg}" || \
       [[ ! -d "${run_dir}" ]]; then
      problems="$(resolve_launch_inputs_problem "${jar_path}" "${launch_cfg}" "${run_dir}" "${argfile}" "${remap_classpath_file}")"
    elif [[ ! -f "${argfile}" && ! -f "${remap_classpath_file}" ]]; then
      problems="$(resolve_launch_inputs_problem "${jar_path}" "${launch_cfg}" "${run_dir}" "${argfile}" "${remap_classpath_file}")"
    fi

    if [[ -n "${problems}" ]]; then
      printf '\nSkipping %s: required launch inputs are missing.\n' "${label}" >&2
      append_csv_row "${csv_path}" "${timestamp}" "${label}" "${loader}" "${platform_dir}" "${jar_path}" "" "preflight_failed" "${problems}"
      continue
    fi

    set +e
    launch_target \
      "${label}" \
      "${loader}" \
      "${platform_dir}" \
      "${detected_dli_main}" \
      "${jar_path}" \
      "${launch_cfg}" \
      "${run_dir}" \
      "${argfile}" \
      "${remap_classpath_file}" \
      "${java_bin}" \
      "${dli_jar}" \
      "${log4j_util_jar}" \
      "${gradle_java_home}"
    launch_exit_code="$?"
    set -e

    if [[ "${launch_exit_code}" -ne 0 ]]; then
      printf '\n%s exited with code %s.\n' "${label}" "${launch_exit_code}" > /dev/tty
    fi

    response="$(prompt_yes_no "Is everything working for ${label}? [yes/no] ")"
    if [[ "${response}" == "yes" ]]; then
      result="passed"
    else
      result="failed"
      problems="$(prompt_from_tty "Describe the problems for ${label}: ")"
    fi

    append_csv_row "${csv_path}" "${timestamp}" "${label}" "${loader}" "${platform_dir}" "${jar_path}" "${launch_exit_code}" "${result}" "${problems}"
  done

  printf '\nResults written to %s\n' "${csv_path}"
}

main "$@"
