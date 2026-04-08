#!/bin/bash
# Method coverage checker for ZIO Blocks reference documentation
# Usage: check-method-coverage.sh <TypeName> <doc-file.md>
# Exit codes: 0 = all methods covered, 1 = missing methods, 2 = error
#
# Checks that all public methods of a data type are documented in the reference page.

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <TypeName> <doc-file.md>" >&2
  echo "Example: $0 Chunk docs/reference/chunk.md" >&2
  exit 1
fi

TYPE_NAME="$1"
DOC_FILE="$2"

if [[ ! -f "$DOC_FILE" ]]; then
  echo "Error: Documentation file not found: $DOC_FILE" >&2
  exit 2
fi

# Find the source file for the type
# Search for files ending with TypeName.scala under src/main/scala/zio/blocks/
SOURCE_FILE=""
possible_paths=(
  "$(find . -path "*/src/main/scala/zio/blocks/*/${TYPE_NAME}.scala" 2>/dev/null | head -1)"
  "$(find . -path "*/src/main/scala/zio/blocks/${TYPE_NAME}.scala" 2>/dev/null | head -1)"
  "$(find . -name "${TYPE_NAME}.scala" -path "*/src/main/scala/zio/blocks/*" 2>/dev/null | head -1)"
)

for path in "${possible_paths[@]}"; do
  if [[ -n "$path" && -f "$path" ]]; then
    SOURCE_FILE="$path"
    break
  fi
done

if [[ -z "$SOURCE_FILE" ]]; then
  echo "Warning: Could not find source file for type '$TYPE_NAME'" >&2
  echo "Will attempt to extract methods from documentation only." >&2
  echo "" >&2
  # Still run the doc extraction to show what's documented
else
  echo "Found source: $SOURCE_FILE" >&2
fi

# Extract all public methods from the source
# Extract public, top-level method names from the primary type body.
# This is a best-effort parser that:
# - ignores private/protected defs
# - only considers defs at brace depth 1 (inside the outer class/object body)
# - skips nested/local defs inside other blocks
extract_methods_from_source() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    return
  fi

  awk '
    BEGIN { brace_depth = 0 }
    {
      line = $0
      current_depth = brace_depth

      if (
        current_depth == 1 &&
        line !~ /(^|[^[:alnum:]_])(private|protected)([^[:alnum:]_]|$)/ &&
        line ~ /^[[:space:]]*(override[[:space:]]+|final[[:space:]]+|inline[[:space:]]+)*def[[:space:]]+[A-Za-z_][A-Za-z0-9_]*/
      ) {
        if (match(line, /def[[:space:]]+([A-Za-z_][A-Za-z0-9_]*)/)) {
          name = substr(line, RSTART, RLENGTH)
          sub(/^def[[:space:]]+/, "", name)
          print name
        }
      }

      opens = gsub(/\{/, "{", line)
      closes = gsub(/\}/, "}", line)
      brace_depth += opens - closes
      if (brace_depth < 0) {
        brace_depth = 0
      }
    }
  ' "$file" | sort -u
}

# Extract companion object methods
extract_object_methods_from_source() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    return
  fi

  # Look for object <TypeName> or object following the class
  awk '
    /^\s*object\s+'"$TYPE_NAME"'\s*[#{]/ { in_object=1; next }
    /^\s*object\s+\w+/ { in_object=0 }
    in_object && /^\s*def\s+/ {
      line=$0
      sub(/^\s*def\s+/, "", line)
      sub(/\(.*/, "", line)
      print line
    }
  ' "$file" | grep -E '^[a-zA-Z][a-zA-Z0-9_]*$' | sort -u
}

# Extract documented methods from markdown
# Looks for backtick-enclosed method references like `methodName`, `++`, or TypeName#methodName
# Supports both identifier-based names (a-zA-Z0-9_) and symbolic names (++, &, |, ^, etc.)
extract_methods_from_doc() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    return
  fi

  grep -oE '`([A-Za-z0-9_#]|(\+\+|:?\+|:\-|:\*|:[/\\|&^%]|[&|^%]|\+:))+`' "$file" | \
    sed -E 's/`//g' | \
    # Extract just the method name if it's Type#method format
    sed -E 's/^[^#]+#//' | \
    sort -u
}

echo "=== Method Coverage Check for '$TYPE_NAME' ==="
echo ""

# Collect source methods - use current directory for temp files (writable)
SOURCE_METHODS="source_methods_$$.txt"
DOC_METHODS="doc_methods_$$.txt"

extract_methods_from_source "$SOURCE_FILE" > "$SOURCE_METHODS" 2>/dev/null || true
extract_object_methods_from_source "$SOURCE_FILE" >> "$SOURCE_METHODS" 2>/dev/null || true

# Collect documented methods
extract_methods_from_doc "$DOC_FILE" > "$DOC_METHODS"

echo "Public methods found in source:"
if [[ -s "$SOURCE_METHODS" ]]; then
  cat "$SOURCE_METHODS" | sed 's/^/  - /'
  SOURCE_COUNT=$(wc -l < "$SOURCE_METHODS" | tr -d ' ')
else
  echo "  (none found - check source file format)"
fi

echo ""
echo "Methods documented in '$DOC_FILE':"
if [[ -s "$DOC_METHODS" ]]; then
  cat "$DOC_METHODS" | sed 's/^/  - /'
  DOC_COUNT=$(wc -l < "$DOC_METHODS" | tr -d ' ')
else
  echo "  (none found - check documentation format)"
fi

echo ""
echo "=== Coverage Analysis ==="

if [[ ! -s "$SOURCE_METHODS" ]]; then
  echo "⚠ Could not determine source methods. Manual review required." >&2
  rm -f "$SOURCE_METHODS" "$DOC_METHODS"
  exit 2
fi

# Find missing methods (in source but not in doc)
MISSING="missing_$$.txt"
comm -23 "$SOURCE_METHODS" "$DOC_METHODS" > "$MISSING" 2>/dev/null || true

# Find extra methods (in doc but not in source - possibly inherited or from other types)
EXTRA="extra_$$.txt"
comm -13 "$SOURCE_METHODS" "$DOC_METHODS" > "$EXTRA" 2>/dev/null || true

MISSING_COUNT=$(wc -l < "$MISSING" | tr -d ' ')
EXTRA_COUNT=$(wc -l < "$EXTRA" | tr -d ' ')

if [[ $MISSING_COUNT -gt 0 ]]; then
  echo "❌ Missing methods ($MISSING_COUNT):"
  cat "$MISSING" | sed 's/^/   /'
else
  echo "✓ All source methods are documented"
fi

if [[ $EXTRA_COUNT -gt 0 ]]; then
  echo ""
  echo "⚠ Documented methods NOT in source (possibly inherited/overloaded) ($EXTRA_COUNT):"
  cat "$EXTRA" | sed 's/^/   /'
fi

echo ""

# Cleanup
rm -f "$SOURCE_METHODS" "$DOC_METHODS" "$MISSING" "$EXTRA"

if [[ $MISSING_COUNT -gt 0 ]]; then
  exit 1
else
  exit 0
fi