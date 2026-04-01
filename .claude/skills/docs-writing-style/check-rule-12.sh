#!/bin/bash
# Check Rule 12: No bare subheaders
# Rule 12: Never place a `###` or `####` subheader immediately after a `##` header
# with nothing in between. Always write at least one sentence of explanation
# before the first subheader.

if [ $# -eq 0 ]; then
  echo "Usage: $0 <markdown-file>"
  echo ""
  echo "Checks for Rule 12 violations: bare subheaders (### or ####) directly after"
  echo "headers (## or ###) with no prose in between."
  exit 1
fi

FILE="$1"

if [ ! -f "$FILE" ]; then
  echo "Error: File not found: $FILE"
  exit 1
fi

VIOLATIONS=0
IN_CODE_BLOCK=0
PREV_HEADER_LEVEL=0
PREV_HEADER_LINE_NUM=0
LINE_NUM=0

while IFS= read -r line; do
  ((LINE_NUM++))

  # Track code blocks (skip checking inside them)
  if [[ "$line" =~ ^[[:space:]]*\`\`\` ]]; then
    IN_CODE_BLOCK=$((1 - IN_CODE_BLOCK))
    continue
  fi

  # Skip content inside code blocks
  if [ $IN_CODE_BLOCK -eq 1 ]; then
    continue
  fi

  # Detect headers
  if [[ "$line" =~ ^(#+)[[:space:]] ]]; then
    HEADER_MARKER="${BASH_REMATCH[1]}"
    CURRENT_HEADER_LEVEL=${#HEADER_MARKER}

    # Check if previous line was a header (no prose between headers)
    if [ $PREV_HEADER_LEVEL -gt 0 ]; then
      # Check for bare subheader: ### or #### directly after ## or ###
      if ([ $PREV_HEADER_LEVEL -eq 2 ] && [ $CURRENT_HEADER_LEVEL -eq 3 ]) || \
         ([ $PREV_HEADER_LEVEL -eq 2 ] && [ $CURRENT_HEADER_LEVEL -eq 4 ]) || \
         ([ $PREV_HEADER_LEVEL -eq 3 ] && [ $CURRENT_HEADER_LEVEL -eq 4 ]); then
        # Violation found
        PREV_HASHES=$(printf '#%.0s' $(seq 1 $PREV_HEADER_LEVEL))
        CURR_HASHES=$(printf '#%.0s' $(seq 1 $CURRENT_HEADER_LEVEL))
        echo "Line $PREV_HEADER_LINE_NUM: Header ($PREV_HASHES)"
        echo "Line $LINE_NUM: Bare subheader ($CURR_HASHES) immediately follows with no prose"
        echo "Violation: Add at least one sentence of explanation between them"
        echo "---"
        ((VIOLATIONS++))
      fi
    fi

    PREV_HEADER_LEVEL=$CURRENT_HEADER_LEVEL
    PREV_HEADER_LINE_NUM=$LINE_NUM
  elif [[ "$line" =~ ^[[:space:]]*$ ]]; then
    # Empty line: don't reset header tracking (headers can be separated by blank lines)
    :
  elif [ $PREV_HEADER_LEVEL -gt 0 ]; then
    # Non-header, non-empty line found: reset header tracking (prose was found)
    PREV_HEADER_LEVEL=0
  fi

done < "$FILE"

if [ $VIOLATIONS -eq 0 ]; then
  echo "✓ Rule 12: No violations found (no bare subheaders)"
  exit 0
else
  echo ""
  echo "✗ Rule 12: Found $VIOLATIONS violation(s)"
  exit 1
fi
