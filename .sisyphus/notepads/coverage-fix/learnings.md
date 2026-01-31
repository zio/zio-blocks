# Learnings from Coverage Fix

## 2026-01-31 Session: ses_3f2913899ffe8bWDrRLCLbxu2z

### Coverage Gap Analysis

When analyzing scoverage reports, look for `invocation-count="0"` in the XML:
```bash
grep 'invocation-count="0"' .../scoverage.xml | head -30
```

### Key Coverage Gaps Found

1. **Type aliases vs explicit types**: `Text` and `Inline.Text` are the same type but scoverage tracks them separately in pattern matches. Need to test both variants.

2. **Standalone vs nested blocks**: `ListItem` inside `BulletList` uses `normalizeListItem`, but standalone `ListItem` passed to `normalizeBlock` is a different code path.

3. **Instance methods vs static calls**: Tests calling `HtmlRenderer.render(doc)` don't cover `doc.toHtml` - need explicit tests for instance methods.

### Test Patterns That Work

```scala
// Testing type alias variants
test("normalizeInlines merges adjacent Inline.Text variants") {
  val inlines = Chunk[Inline](Inline.Text("a"), Inline.Text("b"))
  val normalized = Doc.normalizeInlines(inlines)
  assertTrue(normalized.size == 1, normalized.head == Text("ab"))
}

// Testing standalone block normalization
test("normalizeBlock handles standalone ListItem") {
  val item = ListItem(Chunk(Paragraph(Chunk(Text("a"), Text("b")))), Some(true))
  val normalized = Doc.normalizeBlock(item)
  // ...
}
```

### Coverage Thresholds

- Module requires 95% statement coverage
- Branch coverage threshold is 90%
- Native tests take 30+ minutes on CI

### Subagent Behavior

- Subagents may refuse multi-task requests - provide ONE atomic task per delegation
- Always verify subagent claims with your own tool calls
- Use `session_id` to continue work in same session (preserves context)
