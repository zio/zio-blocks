# Development Instructions

## SBT Command Guidelines

When running sbt commands in the terminal:

1. **Never use `timeout`** - SBT compilation can take time, let it complete
2. **Never use `head`, `tail`, or `grep` to truncate output** - Always show the full output
3. **Always show complete build logs** - Errors and warnings at the end are important

### Correct Usage

```bash
# Correct - full output
cd /path/to/project && sbt "++ 3.7.4; schemaJVM/compile" 2>&1

# Correct - run tests with full output
cd /path/to/project && sbt "++ 3.7.4; schemaJVM/testOnly *SomeSpec*" 2>&1
```

### Incorrect Usage

```bash
# WRONG - truncates output
sbt compile 2>&1 | head -100
sbt compile 2>&1 | tail -80
sbt compile 2>&1 | grep error
timeout 60 sbt compile
```

## Test Guidelines

- Run tests with full output to see all passing/failing tests
- Use `testOnly` with specific test class patterns for focused testing
- Check both compilation warnings and test results

