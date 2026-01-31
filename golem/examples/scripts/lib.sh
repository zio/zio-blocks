#!/usr/bin/env bash

examples_require_cli() {
  local name="$1"
  if ! command -v golem-cli >/dev/null 2>&1; then
    echo "[$name] error: golem-cli not found on PATH" >&2
    exit 1
  fi
}

examples_parse_flags() {
  GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
  EXAMPLE_FLAGS=()
  read -r -a EXAMPLE_FLAGS <<<"$GOLEM_CLI_FLAGS" || true

  EXAMPLE_IS_CLOUD=0
  for f in "${EXAMPLE_FLAGS[@]}"; do
    if [[ "$f" == "--cloud" ]]; then
      EXAMPLE_IS_CLOUD=1
    fi
  done
}

examples_check_router() {
  local name="$1"
  if [[ "${EXAMPLE_IS_CLOUD:-0}" -eq 0 ]]; then
    local host="${GOLEM_ROUTER_HOST:-127.0.0.1}"
    local port="${GOLEM_ROUTER_PORT:-9881}"
    if ! timeout 1 bash -lc "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
      echo "[$name] Local router not reachable at $host:$port." >&2
      echo "[$name] Start it in another terminal, then rerun:" >&2
      echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
      exit 1
    fi
  fi
}

examples_build_js() {
  local name="$1"
  local build_log
  build_log="$(mktemp)"
  if ! ( cd "$PWD" && sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" ) >"$build_log" 2>&1; then
    cat "$build_log" >&2
    rm -f "$build_log"
    echo "[$name] sbt failed; see output above." >&2
    exit 1
  fi
  rm -f "$build_log"
}

examples_run_repl() {
  local app_dir="$1"
  local script_file="$2"
  cd "$app_dir"
  env -u ARGV0 golem-cli "${EXAMPLE_FLAGS[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  local timeout_secs="${REPL_TIMEOUT_SECS:-60}"
  timeout "$timeout_secs" env -u ARGV0 golem-cli "${EXAMPLE_FLAGS[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
    repl scala:examples --script-file "$script_file" --disable-stream < /dev/null
}

examples_check_repl_errors() {
  local name="$1"
  local output="$2"
  local hint="${3:-}"
  if echo "$output" | grep -F -q 'CustomError(' || \
     echo "$output" | grep -F -q 'JavaScript error:' || \
     echo "$output" | grep -F -q 'Exception during call' || \
     echo "$output" | grep -F -q '[runtime error]' || \
     echo "$output" | grep -F -q '[ERROR'; then
    echo "[$name] ERROR: repl output contains an error:" >&2
    if [[ -n "$hint" ]]; then
      echo "[$name] $hint" >&2
    fi
    echo "$output" >&2
    exit 1
  fi
}
