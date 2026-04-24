#!/bin/bash

# Script: Find Scala code blocks in documentation without mdoc modifiers
# Purpose: Identify code examples that need mdoc directives for docs-mdoc-conventions skill
# Usage: ./.claude/scripts/find-unmodified-mdoc-blocks.sh [--dir <path>] [--verbose] [--show-context]

set -e

DOCS_PATH="${1:-.}"
VERBOSE="${2:-}"
SHOW_CONTEXT="${3:-}"

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔍 Scanning for Scala code blocks without mdoc modifiers...${NC}"
echo ""

TOTAL_FILES=0
TOTAL_BLOCKS=0
DECLARED_UNMODIFIED=0

# Find all markdown files
find "$DOCS_PATH" -name "*.md" -type f | sort | while read mdfile; do
    # Find unmodified ```scala blocks (without mdoc modifiers)
    # Regex: exactly ```scala at end of line (no space, no modifiers)
    matches=$(grep -n "^\`\`\`scala$" "$mdfile" 2>/dev/null || true)

    if [ -n "$matches" ]; then
        ((TOTAL_FILES++))
        echo -e "${RED}📄 $mdfile${NC}"

        while IFS=: read -r linenum _; do
            ((TOTAL_BLOCKS++))
            echo -e "  ${YELLOW}Line $linenum:${NC} \`\`\`scala (NO MODIFIER)"

            if [ -n "$SHOW_CONTEXT" ]; then
                # Show 2 lines before and 8 lines after for context
                echo "  ${BLUE}Context:${NC}"
                sed -n "$((linenum-2)),$((linenum+8))p" "$mdfile" | \
                  sed -n "1p; 2p; 3,$((8-linenum+linenum))p" | \
                  sed 's/^/    /'
                echo ""
            fi
        done <<< "$matches"
        echo ""
    fi
done

echo -e "${GREEN}Summary:${NC}"
echo "--------"
echo "Total unmodified blocks: $(find "$DOCS_PATH" -name "*.md" -type f -exec grep -l "^\`\`\`scala$" {} \; 2>/dev/null | wc -l) files"

echo ""
echo -e "${BLUE}Common mdoc modifiers:${NC}"
echo "  ${GREEN}:compile-only${NC}      - Compile but don't execute (for examples that can't run)"
echo "  ${GREEN}:silent${NC}            - Compile and run, but hide output"
echo "  ${GREEN}:reset${NC}             - Clear previous scope between unrelated examples"
echo "  ${GREEN}:fail${NC}              - Code block should fail to compile (anti-patterns)"
echo "  ${GREEN}:nest:n${NC}            - Nest code in a function (for setup code)"
echo ""
echo -e "${BLUE}Usage examples:${NC}"
echo "  \`\`\`scala mdoc:compile-only"
echo "  \`\`\`scala mdoc:silent"
echo "  \`\`\`scala mdoc:compile-only:reset"
echo ""
echo -e "${BLUE}Decision Guide:${NC}"
echo "  1. Code that's meant to execute in docs        → ${GREEN}no modifier${NC} or ${GREEN}:silent${NC}"
echo "  2. Code that illustrates but can't compile     → ${GREEN}:compile-only${NC}"
echo "  3. Setup code before main examples             → ${GREEN}:silent:nest:1${NC}"
echo "  4. Code that should intentionally fail         → ${GREEN}:fail${NC}"
echo "  5. Multiple independent examples               → Use ${GREEN}:reset${NC} between them"
