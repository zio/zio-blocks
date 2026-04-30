# Evaluation 3: Debugging Broken mdoc

## Prompt
"I have an mdoc block that's failing to compile. The error says 'value Product is not defined'. Here's my documentation:

```scala mdoc:compile-only
case class Product(name: String, price: Double)
```

Later:

```scala mdoc:compile-only
val p = Product(\"Widget\", 99)
```

Why doesn't it work and how do I fix it?"

## Skill Response Summary
The skill correctly identified the root cause and provided the fix:
1. Explained that `compile-only` blocks are isolated (no shared scope)
2. Recommended switching to `mdoc:silent` + `mdoc` pattern
3. Provided working example showing both blocks

## Evaluation Results

### Criteria Assessment
- ✅ **Explains isolation** — Correctly identified that `compile-only` blocks are isolated
- ✅ **Root cause identified** — `Product` not in scope across blocks
- ✅ **Recommends silent+mdoc pattern** — Correct fix strategy aligned with ZIO Blocks conventions
- ✅ **Provides fixed code** — Working example with both blocks shown

**Result: PASS (4/4 criteria met)**

## Key Strengths
- Directly quotes the skill's isolation rule: "definitions do NOT carry over"
- Explains the exact modifier to use: `mdoc:silent` for setup
- Confirms this is the standard pattern in ZIO Blocks documentation
- No wasted words — diagnostic + fix in one clear explanation

## Technical Accuracy
The skill correctly explains:
- Each `compile-only` block is compiled in isolation
- Definitions don't persist across blocks
- The recommended fix (silent + mdoc) is the standard ZIO Blocks pattern

## Overall Assessment
**Perfect diagnostic.** This is exactly how a documentation writer would debug this error, and the fix is correct and immediately applicable.
