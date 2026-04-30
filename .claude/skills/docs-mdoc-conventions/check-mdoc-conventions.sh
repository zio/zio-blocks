#!/bin/bash
# mdoc conventions checker — mechanical rules for ZIO Blocks docs
# Usage: check-mdoc-conventions.sh <file.md>
# Exit codes: 0 = no violations, 1 = violations found
#
# Rules checked:
#   Missing mdoc modifiers on Scala code blocks (except data type definitions)

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <file.md>" >&2
  exit 1
fi

FILE="$1"
if [[ ! -f "$FILE" ]]; then
  echo "Error: File not found: $FILE" >&2
  exit 1
fi

VIOLATIONS=0

# Check for Scala code blocks missing mdoc modifiers
# Excludes plain ```scala when used for data type definitions (structural illustrations)
count_violations() {
  local output="$1"
  if [[ -n "$output" ]]; then
    echo "$output"
    VIOLATIONS=$((VIOLATIONS + $(printf '%s\n' "$output" | wc -l | tr -d ' ')))
  fi
}

count_violations "$(awk '
  /^```scala$/ {
    print FILENAME ":" NR ": Scala code block missing mdoc modifier (use ```scala mdoc:compile-only or appropriate modifier)"
  }
' "$FILE")"

if [[ $VIOLATIONS -gt 0 ]]; then
  echo ""
  echo "✗ Found $VIOLATIONS violation(s)"
  exit 1
else
  echo "✓ All mdoc conventions passed"
  exit 0
fi
