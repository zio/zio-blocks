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
#   Rule 15: Consecutive code blocks must have bridging prose; no "var" in Scala blocks
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
  /^[[:space:]]*-[[:space:]]|^[[:space:]]*\*[[:space:]]|^[0-9]+\.[[:space:]]/ { in_list = 1 }
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
      if (NR == 1) {
        print FILENAME ":" NR ": [Rule 13] code block at start of file (no preceding prose)"
      } else if (!have_prose || last_prose_line !~ /:$/) {
        print FILENAME ":" NR ": [Rule 13] code block not preceded by sentence ending with \":\""
      }
      in_code = 1
    }
    next
  }
  in_code { next }
  /^[[:space:]]*$/ { next }
  /^(#+)[[:space:]]/ { next }
  {
    last_prose_line = $0
    have_prose = 1
  }
' "$FILE")"

# Rule 15 (Part 1): Consecutive code blocks without bridging prose
count_violations "$(awk '
  /^```/ {
    if (in_code) {
      in_code = 0
      last_code_end = NR
    } else {
      # Entering a code block
      if (last_code_end > 0 && NR - last_code_end <= 2) {
        # Check if there is prose (non-empty, non-header) between blocks
        # If last_code_end was within 2 lines, only blank lines or headers between blocks
        if (!had_prose_since_last_code) {
          print FILENAME ":" NR ": [Rule 15] consecutive code blocks without bridging prose (add sentence ending with \":\" between blocks)"
        }
      }
      in_code = 1
      had_prose_since_last_code = 0
    }
    next
  }
  in_code { next }
  /^[[:space:]]*$/ { next }
  /^(#+)[[:space:]]/ { next }
  {
    had_prose_since_last_code = 1
  }
' "$FILE")"

# Rule 15 (Part 2): "var" in Scala code blocks
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

# Rule 8: Unqualified method/constructor names (heuristic-based algorithm)
# This mechanical check requires feedback loop to refine the SAFE_NAMES set
# and confidence heuristics, but it can catch common cases of unqualified
# method references in prose. After running this check, review the output
# and add any false positives to the SAFE_NAMES set
if command -v python3 >/dev/null 2>&1; then
  count_violations "$(python3 - "$FILE" << 'PYTHON_EOF'
import sys, re

# Known safe names (variables, parameters, constants, type names)
SAFE_NAMES = {
    # Variables and parameters
    "f", "z", "g", "x", "y", "n", "i", "j", "k", "v", "a", "b", "c",
    "buf", "buffer", "reader", "sink", "stream", "result", "value",
    "e", "err", "error", "ex", "exception", "cause",
    # Type names (capitalized or special)
    "Sink", "Stream", "Reader", "Pipeline", "Chunk", "Any", "Unit",
    "List", "Option", "Either", "Right", "Left", "Some", "None",
    "Boolean", "Int", "Long", "Double", "Float", "String", "Byte",
    "EndOfStream", "Throwable", "Exception", "IOException", "Error",
    # Constants
    "MaxValue", "MinValue", "MaxValu e", "Infinity",
    # Common values
    "null", "true", "false", "this", "super", "self",
    # Common Scala/functional terms
    "pred", "predicate", "init", "default", "Interpreted",
}

in_code = False
qualified_methods = set()

# First pass: collect all qualified method names to build confidence
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        content = f.read()
        # Find all Qualified methods: Type#method or Type.method
        for m in re.finditer(r'[A-Z][a-zA-Z0-9_]*[#.]([a-z][a-zA-Z0-9_]*)', content):
            qualified_methods.add(m.group(1))
except:
    pass

# Second pass: detect unqualified methods
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            s = line.rstrip()
            if s.lstrip().startswith("```"):
                in_code = not in_code
                continue
            if in_code:
                continue

            # Find all backtick-quoted identifiers
            for m in re.finditer(r'`([a-z][a-zA-Z0-9_]*)`', s):
                name = m.group(1)
                start = m.start()

                # Skip if name is in safe list
                if name in SAFE_NAMES:
                    continue

                # Skip if preceded by # or . (already qualified)
                if start > 0 and s[start-1] in '#.':
                    continue

                # Skip very short names (likely variables)
                if len(name) <= 2:
                    continue

                # Skip if in a markdown link or reference
                if '[' in s[:start] and ']' in s[start:]:
                    continue

                # **Confidence check**: if this identifier appears qualified elsewhere
                # in the document, it's a real method and should be reported
                if name in qualified_methods:
                    print(f"{sys.argv[1]}:{lineno}: [Rule 8] unqualified method `{name}` (use Type#{name} or Type.{name})")
except Exception as e:
    print(f"Error in Rule 8: {e}", file=sys.stderr)
    sys.exit(1)
PYTHON_EOF
)"
fi

# Report summary
echo ""
if [[ $VIOLATIONS -gt 0 ]]; then
  echo "✗ Found $VIOLATIONS violation(s)"
  exit 1
else
  echo "✓ No violations found"
  exit 0
fi
