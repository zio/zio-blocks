#!/bin/bash
# Documentation style checker — mechanical rules for ZIO Blocks docs
# Usage: check-docs-style.sh <file.md>
# Exit codes: 0 = no violations, 1 = violations found
#
# Rules checked:
#   Rule 1:  "zio-blocks" in prose (not in URLs or sbt artifact strings)
#   Rule 5:  No filler phrases
#   Rule 6:  No emoji in prose
#   Rule 11: No bare subheaders (### or #### immediately after ## or ###)
#   Rule 12: Provide narrative introduction before subheaders
#   Rule 13: Code block preceded by prose sentence ending with ":"
#   Rule 15: No "var" in Scala code blocks
#   Rule 16: No hardcoded result comments in Scala blocks

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

# Helper to track violations across subshell boundaries
count_violations() {
  local output="$1"
  if [[ -n "$output" ]]; then
    echo "$output"
    VIOLATIONS=$((VIOLATIONS + $(printf '%s\n' "$output" | wc -l | tr -d ' ')))
  fi
}

# Rule 1: "zio-blocks" in prose (not in URLs or sbt artifact strings or inline code)
count_violations "$(awk '
  /^```/ { in_code = !in_code; next }
  in_code { next }
  /^[[:space:]]*-[[:space:]]|^[[:space:]]*\*[[:space:]]|^0-9]+\.[[:space:]]/ { in_list = 1 }
  in_list && /^[[:space:]]*$/ { in_list = 0 }
  /zio-blocks/ && !/^http/ && !/^.*:.*=.*zio-blocks/ {
    # Remove inline code (backtick-quoted sections) to check if zio-blocks is still there
    temp = $0
    gsub(/`[^`]*`/, "", temp)
    if (temp ~ /zio-blocks/) {
      print FILENAME ":" NR ": [Rule 1] \"zio-blocks\" in prose (not in code/URL)"
    }
  }
' "$FILE")"

# Rule 5: Filler phrases
count_violations "$(awk '
  /^```/ { in_code = !in_code; next }
  in_code { next }
  {
    fillers = "as we can see|it'\''s worth noting that|it should be noted that|importantly|needless to say|it is important to note that|by the way|obviously|clearly|basically|essentially"
    if ($0 ~ "(" fillers ")") {
      print FILENAME ":" NR ": [Rule 5] filler phrase detected"
    }
  }
' "$FILE")"

# Rule 6: Emoji characters (using Python for robust Unicode detection)
if command -v python3 >/dev/null 2>&1; then
  count_violations "$(python3 - "$FILE" << 'PYTHON_EOF'
import sys, re
EMOJI_RE = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F900-\U0001F9FF\uFE00-\uFE0F]"
)
in_code = False
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            s = line.rstrip()
            if s.lstrip().startswith("```"):
                in_code = not in_code
                continue
            if in_code:
                continue
            m = EMOJI_RE.search(s)
            if m:
                print(f"{sys.argv[1]}:{lineno}: [Rule 6] emoji in prose: {repr(m.group())}")
except Exception as e:
    print(f"Error in Rule 6: {e}", file=sys.stderr)
    sys.exit(1)
PYTHON_EOF
)"
fi

# Rule 11 & 12: No bare subheaders (### or #### immediately after ## or ###)
# This comprehensive check replaces the old Rule 11 & 12 check
count_violations "$(awk '
  /^```/ { in_code_block = !in_code_block; next }
  in_code_block { next }
  /^(#+)[[:space:]]/ {
    match($0, /^(#+)/)
    header_marker = substr($0, 1, RLENGTH)
    current_level = length(header_marker)

    # Check if previous line was a header (no prose between headers)
    if (prev_header_level > 0) {
      # Violation: subheader directly after header
      if ((prev_header_level == 2 && current_level == 3) || \
          (prev_header_level == 2 && current_level == 4) || \
          (prev_header_level == 3 && current_level == 4)) {
        print FILENAME ":" NR ": [Rule 11] bare subheader immediately after header (no prose)"
      }
    }

    prev_header_level = current_level
    prev_header_line = NR
    next
  }
  /^[[:space:]]*$/ {
    # Empty line: dont reset header tracking
    next
  }
  prev_header_level > 0 {
    # Non-header, non-empty line: reset tracking (prose was found)
    prev_header_level = 0
  }
' "$FILE")"

# Rule 13: Code block not preceded by prose sentence ending with ":"
count_violations "$(awk '
  /^```/ {
    if (in_code) {
      in_code = 0
    } else {
      if (NR > 1 && prev_line !~ /:$/ && prev_line !~ /^[#]/ && prev_line !~ /^$/) {
        print FILENAME ":" NR ": [Rule 13] code block not preceded by sentence ending with \":\""
      } else if (NR == 1) {
        print FILENAME ":" NR ": [Rule 13] code block at start of file (no preceding prose)"
      }
      in_code = 1
    }
    next
  }
  { prev_line = $0 }
' "$FILE")"

# Rule 15: "var" in Scala code blocks
count_violations "$(awk '
  /^```scala/ {
    in_scala = 1
    next
  }
  /^```/ {
    in_scala = 0
    next
  }
  in_scala && /var[^a-zA-Z0-9_]|^var[[:space:]]|[[:space:]]var[[:space:]]/ {
    print FILENAME ":" NR ": [Rule 15] \"var\" in Scala code block"
  }
' "$FILE")"

# Rule 16: Hardcoded result comments in Scala blocks
count_violations "$(awk '
  /^```scala/ {
    in_scala = 1
    next
  }
  /^```/ {
    in_scala = 0
    next
  }
  in_scala && /\/\/\s*(None|Some\(|Right\(|Left\(|Success\(|Failure\(|".*")/ {
    print FILENAME ":" NR ": [Rule 16] hardcoded result comment"
  }
' "$FILE")"

# Report summary
echo ""
if [[ $VIOLATIONS -gt 0 ]]; then
  echo "✗ Found $VIOLATIONS violation(s)"
  exit 1
else
  echo "✓ No violations found"
  exit 0
fi
