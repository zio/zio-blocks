# Integrated Documentation Style Checker

## Overview

`check-docs-style.sh` is a comprehensive mechanical style checker for ZIO Blocks documentation. It validates multiple rules from the `docs-writing-style` skill in a single pass.

## Usage

```bash
bash check-docs-style.sh <file.md>
```

### Examples

```bash
# Check a single file
bash check-docs-style.sh docs/reference/chunk.md

# Check all reference docs
for file in docs/reference/*.md; do
  bash check-docs-style.sh "$file"
done
```

## Exit Codes

- **Exit 0** — No violations found
- **Exit 1** — One or more violations found

## Rules Checked

| Rule | Description |
|------|-------------|
| **Rule 1** | "zio-blocks" must use backticks in prose (not bare text) |
| **Rule 5** | No filler phrases (as we can see, it's worth noting, etc.) |
| **Rule 6** | No emoji characters in prose |
| **Rule 11** | No bare subheaders (`###` or `####` immediately after `##` or `###`) |
| **Rule 13** | Code blocks must be preceded by prose sentence ending with `:` |
| **Rule 15** | No `var` declarations in Scala code blocks |
| **Rule 16** | No hardcoded result comments in Scala blocks |

## Output Format

Violations are reported with file name, line number, rule ID, and description:

```
docs/reference/chunk.md:84: [Rule 1] "zio-blocks" in prose (not in code/URL)
docs/reference/chunk.md:100: [Rule 5] filler phrase detected

✗ Found 2 violation(s)
```

Passing files show:

```
✓ No violations found
```

## Integration

This tool can be integrated into:

- **CI/CD pipelines** — Fail builds if documentation violates style rules
- **Pre-commit hooks** — Validate documentation before commits
- **Documentation linting** — Part of a larger docs quality suite

### Example: Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

for file in $(git diff --cached --name-only | grep '\.md$'); do
  if ! bash .claude/skills/docs-writing-style/check-docs-style.sh "$file"; then
    echo "Fix documentation style violations in $file before committing"
    exit 1
  fi
done
```

## Testing

A test file `test-docs-style.md` is included to demonstrate rule violations:

```bash
bash check-docs-style.sh test-docs-style.md
```

This test file intentionally contains violations to validate that the checker works correctly.

## Limitations

- Only checks mechanical/syntactic rules, not semantic or narrative quality
- Some rules may have false positives (e.g., Rule 1 may trigger on backtick-wrapped "zio-blocks")
- Python 3 required for Rule 6 (emoji detection)

## Related Documentation

- `SKILL.md` — Full prose style rules (Rule 1–23)
- `CHECK-RULE-12-README.md` — Dedicated Rule 12 checker documentation
- `test-docs-style.md` — Test file with intentional violations
