#!/bin/bash
# Method coverage checker for ZIO Blocks reference documentation
# Uses Scala-aware parsing to extract public methods.
#
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

# Get directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Extract all public methods from the source using Scala-aware parser
# The Scala parser properly handles:
# - Method signatures with generic type parameters
# - Access modifiers (private, protected)
# - Complex nested structures
# - Both identifier and symbolic method names
extract_methods_from_source() {
  local file="$1"
  local type_name="${2:-}"

  if [[ ! -f "$file" ]]; then
    return
  fi

  # Try to run the Scala extractor
  if [[ -f "$SCRIPT_DIR/extract-methods.scala" ]]; then
    # Run with scala if available, otherwise fall back to bash extraction
    if command -v scala >/dev/null 2>&1; then
      if [[ -n "$type_name" ]]; then
        scala "$SCRIPT_DIR/extract-methods.scala" "$file" "$type_name" 2>/dev/null || true
      else
        scala "$SCRIPT_DIR/extract-methods.scala" "$file" 2>/dev/null || true
      fi
    else
      # Fallback to improved bash-based extraction if scala is not available
      extract_methods_bash_fallback "$file" "$type_name"
    fi
  else
    # Fallback if Scala script not found
    extract_methods_bash_fallback "$file" "$type_name"
  fi
}

# Bash fallback for method extraction when Scala is not available
extract_methods_bash_fallback() {
  local file="$1"
  local type_name="$2"

  awk -v type_name="$type_name" '
    BEGIN {
      brace_depth = 0
      in_target_type = (type_name == "")
      target_type_depth = -1
    }

    # Check if entering target type
    !in_target_type && $0 ~ "(abstract +)?(class|trait|object) +" type_name " " {
      in_target_type = 1
      target_type_depth = brace_depth
    }

    # Check if leaving target type
    in_target_type && type_name != "" && brace_depth <= target_type_depth && /}/ {
      in_target_type = 0
    }

    # Extract methods when in target scope
    in_target_type && brace_depth > 0 {
      if ($0 !~ /(private|protected)/ && $0 ~ /(def|given) +/) {
        if (match($0, /(def|given) +([a-zA-Z_][a-zA-Z0-9_]*)/)) {
          name = substr($0, RSTART + 4, RLENGTH - 4)
          gsub(/^[[:space:]]+/, "", name)
          gsub(/[[:space:]]+.*/, "", name)
          if (name != "") print name
        }
      }
    }

    {
      opens = gsub(/{/, "{")
      closes = gsub(/}/, "}")
      brace_depth += opens - closes
      if (brace_depth < 0) brace_depth = 0
    }
  ' "$file" | sort -u
}

# Extract documented methods from markdown
# Looks for backtick-enclosed method references like:
# - `methodName` (bare method name)
# - `TypeName#methodName` (instance method)
# - `TypeName.methodName` (companion/static method)
#
# Filters to only method-like patterns:
# - Must start with lowercase (camelCase methods) or be symbolic operators
# - Allow alphanumeric, underscores
# - Excludes type names, constants, keywords, variables, and code tokens
extract_methods_from_doc() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    return
  fi

  # Extract all backtick-quoted content
  grep -oE '`[^`]+`' "$file" | \
    sed -E 's/`//g' | \
    # Strip parameter lists and type parameters: remove everything from '(' or '[' onwards
    sed -E 's/[\(\[].*//' | \
    # Extract just the method name if it's Type#method or Type.method format
    sed -E 's/^[^#.]*[#.]//' | \
    # Filter to only method-like identifiers:
    # - Start with lowercase letter (methods are camelCase)
    # - Allow alphanumeric, underscores, symbolic operators
    # - Must be at least 2 characters (exclude single-letter variables like f, n, z, x, y)
    grep -E '^[a-z][a-zA-Z0-9_]{1,}$|^[+:*/%&|^!<>@\\-]+$' | \
    # Exclude common keywords and non-method tokens
    grep -vE '^(true|false|null|this|super|self|finally|inline|bufSize|pred|f|n|z|via|nio)$' | \
    sort -u
}

echo "=== Method Coverage Check for '$TYPE_NAME' ==="
echo ""

# Collect source methods - use current directory for temp files (writable)
SOURCE_METHODS="source_methods_$$.txt"
DOC_METHODS="doc_methods_$$.txt"

extract_methods_from_source "$SOURCE_FILE" "$TYPE_NAME" > "$SOURCE_METHODS" 2>/dev/null || true

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