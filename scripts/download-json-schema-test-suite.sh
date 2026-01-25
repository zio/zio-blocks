#!/usr/bin/env bash
# Downloads the official JSON Schema Test Suite for draft2020-12
# Run this script before running JsonSchemaOfficialTestSuiteSpec tests
# Usage: ./scripts/download-json-schema-test-suite.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TARGET_DIR="$PROJECT_ROOT/schema/jvm/src/test/resources/json-schema-test-suite"
DRAFT_DIR="$TARGET_DIR/tests/draft2020-12"

BASE_URL="https://raw.githubusercontent.com/json-schema-org/JSON-Schema-Test-Suite/main/tests/draft2020-12"

# Core test files to download
FILES=(
  "type.json"
  "properties.json"
  "additionalProperties.json"
  "items.json"
  "prefixItems.json"
  "allOf.json"
  "anyOf.json"
  "oneOf.json"
  "not.json"
  "if-then-else.json"
  "const.json"
  "enum.json"
  "minimum.json"
  "maximum.json"
  "exclusiveMinimum.json"
  "exclusiveMaximum.json"
  "minLength.json"
  "maxLength.json"
  "pattern.json"
  "minItems.json"
  "maxItems.json"
  "uniqueItems.json"
  "minProperties.json"
  "maxProperties.json"
  "required.json"
  "boolean_schema.json"
  "contains.json"
  "multipleOf.json"
  "propertyNames.json"
  "dependentRequired.json"
  "dependentSchemas.json"
  "unevaluatedItems.json"
  "unevaluatedProperties.json"
)

# Create target directory
mkdir -p "$DRAFT_DIR"

echo "Downloading JSON Schema Test Suite (draft2020-12)..."
echo "Target: $DRAFT_DIR"
echo ""

DOWNLOADED=0
FAILED=0

for file in "${FILES[@]}"; do
  if curl -sfL "$BASE_URL/$file" -o "$DRAFT_DIR/$file"; then
    echo "✓ $file"
    ((DOWNLOADED++))
  else
    echo "✗ $file (failed)"
    ((FAILED++))
  fi
done

echo ""
echo "Downloaded: $DOWNLOADED files"
if [ $FAILED -gt 0 ]; then
  echo "Failed: $FAILED files"
  exit 1
fi
echo "Done!"
