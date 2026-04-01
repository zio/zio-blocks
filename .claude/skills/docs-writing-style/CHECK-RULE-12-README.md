# Check Rule 12: No Bare Subheaders

## Overview

This tool checks for violations of **Rule 12** from the ZIO Blocks Documentation Writing Style guide:

> **Rule 12: No bare subheaders** — Never place a `###` or `####` subheader immediately after a `##` header with nothing in between. Always write at least one sentence of explanation before the first subheader.

## Usage

```bash
bash check-rule-12.sh <markdown-file>
```

### Examples

```bash
# Check a single file
bash check-rule-12.sh docs/reference/chunk.md

# Check multiple files
for file in docs/reference/*.md; do
  bash check-rule-12.sh "$file"
done
```

## What It Detects

The script detects these violations:

1. **`###` directly after `##`** — A subsection header immediately follows a main section header with no prose in between
2. **`####` directly after `##`** — A subsubsection header immediately follows a main section header with no prose in between
3. **`####` directly after `###`** — A subsubsection header immediately follows a subsection header with no prose in between

### Example Violations

```markdown
## Main Section
### Subsection  ❌ Violation: no prose between them

---

## Main Section
This is prose that explains the section.

### Subsection  ✓ Correct: prose separates them
```

## Output Format

When violations are found, the tool reports:

```
Line 367: Header (###)
Line 369: Bare subheader (####) immediately follows with no prose
Violation: Add at least one sentence of explanation between them
---
Line 447: Header (###)
Line 449: Bare subheader (####) immediately follows with no prose
Violation: Add at least one sentence of explanation between them
---

✗ Rule 12: Found 2 violation(s)
```

When no violations are found:

```
✓ Rule 12: No violations found (no bare subheaders)
```

## Exit Codes

- **Exit 0** — No violations found
- **Exit 1** — One or more violations found

## How It Works

1. **Tracks header hierarchy** — Monitors `##`, `###`, and `####` headers
2. **Skips code blocks** — Ignores markdown fenced code blocks (```...```) to avoid false positives
3. **Allows blank lines** — Headers can be separated by blank lines; only prose resets the violation check
4. **Minimal output** — Reports line numbers and clear violation descriptions

## Integration

This tool can be integrated into:

- **Pre-commit hooks** — Validate documentation before commits
- **CI/CD pipelines** — Fail builds if documentation violates Rule 12
- **Documentation linters** — Part of a larger docs-writing-style validation suite

### Example: Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

for file in $(git diff --cached --name-only | grep '\.md$'); do
  if ! bash .claude/skills/docs-writing-style/check-rule-12.sh "$file"; then
    echo "Fix Rule 12 violations in $file before committing"
    exit 1
  fi
done
```

## Limitations

- Only detects bare subheaders, not other Rule 12-adjacent issues
- Does not fix violations automatically (manual intervention required)
- Empty lines between headers are allowed (they don't count as prose)

## Related Rules

- **Rule 12** — No bare subheaders (this tool)
- **Rule 13** — No lone subheaders (single child in a section)
- **Rule 15** — Every code block preceded by prose sentence

See `SKILL.md` for the complete style guide.
