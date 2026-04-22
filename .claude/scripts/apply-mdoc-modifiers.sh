#!/bin/bash

# Script: Apply mdoc modifiers to unmodified Scala code blocks
# Purpose: Batch-fix Scala code blocks in documentation files
# Usage: ./.claude/scripts/apply-mdoc-modifiers.sh <file> <modifier> [--dry-run]
#
# Examples:
#   # Add :compile-only to all unmodified blocks in a file
#   ./apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only
#
#   # Dry run (show what would change without making changes)
#   ./apply-mdoc-modifiers.sh docs/reference/http-model-schema.md compile-only --dry-run
#
#   # Add :silent to specific line range
#   ./apply-mdoc-modifiers.sh docs/reference/http-model.md silent --start-line 100 --end-line 200

set -e

FILE="$1"
MODIFIER="$2"
DRY_RUN="${3:-}"
START_LINE="${4:-}"
END_LINE="${5:-}"

if [ -z "$FILE" ] || [ -z "$MODIFIER" ]; then
    echo "Usage: $(basename "$0") <file> <modifier> [--dry-run] [--start-line N] [--end-line N]"
    echo ""
    echo "Modifiers: compile-only, silent, reset, fail, silent:nest:1"
    echo ""
    echo "Examples:"
    echo "  $(basename "$0") docs/reference/http-model-schema.md compile-only"
    echo "  $(basename "$0") docs/reference/http-model-schema.md compile-only --dry-run"
    echo "  $(basename "$0") docs/reference/http-model.md silent --start-line 100 --end-line 200"
    exit 1
fi

if [ ! -f "$FILE" ]; then
    echo "❌ File not found: $FILE"
    exit 1
fi

# Create temporary file
TEMP_FILE=$(mktemp)
trap "rm -f $TEMP_FILE" EXIT

# Count matches
MATCH_COUNT=$(grep -c "^\`\`\`scala$" "$FILE" || true)

if [ "$MATCH_COUNT" -eq 0 ]; then
    echo "✓ No unmodified \`\`\`scala blocks found in $FILE"
    exit 0
fi

echo "📝 Processing: $FILE"
echo "   Modifier: mdoc:$MODIFIER"
echo "   Blocks found: $MATCH_COUNT"
echo ""

if [ -n "$DRY_RUN" ] || [ "$DRY_RUN" = "--dry-run" ]; then
    echo "🔍 DRY RUN MODE - No changes will be made"
    echo ""
    echo "Changes that would be applied:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    grep -n "^\`\`\`scala$" "$FILE" | while IFS=: read -r linenum _; do
        echo "  Line $linenum: \`\`\`scala  →  \`\`\`scala mdoc:$MODIFIER"
    done
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "To apply these changes, run without --dry-run:"
    echo "  ./.claude/scripts/apply-mdoc-modifiers.sh \"$FILE\" \"$MODIFIER\""
else
    # Apply the changes
    sed -i "s/^\`\`\`scala$/\`\`\`scala mdoc:$MODIFIER/g" "$FILE"

    echo "✅ Applied mdoc:$MODIFIER to $MATCH_COUNT code block(s)"
    echo "   File: $FILE"
    echo ""
    echo "🔍 Verify the changes:"
    echo "   git diff $FILE"
    echo ""
    echo "🧪 Test the docs build:"
    echo "   sbt \"docs/mdoc --in $FILE\""
fi
