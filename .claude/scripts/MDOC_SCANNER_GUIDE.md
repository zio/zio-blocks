# mdoc Code Block Scanner & Fixer Guide

## Overview

The `find-unmodified-mdoc-blocks.sh` script identifies Scala code blocks in documentation that lack mdoc modifiers. This is useful for the **docs-mdoc-conventions** skill to ensure consistent code block handling.

## Why This Matters

Code blocks without mdoc modifiers are treated as **runnable code by default** during mdoc compilation. This means:

- ✅ **They will be compiled** against the actual project code
- ✅ **They will be executed** and output will be captured
- ❌ **If they fail to compile**, the entire docs build fails
- ❌ **If they have side effects**, those will execute during doc generation

This is powerful for code examples that should actually run, but problematic for:
- Illustrative code that can't compile standalone
- Code that would have unwanted side effects
- Architecture/pattern examples that are conceptual

## Quick Start

```bash
# Find all unmodified blocks in docs/reference
./.claude/scripts/find-unmodified-mdoc-blocks.sh docs/reference

# Show context for each block (what comes before/after)
./.claude/scripts/find-unmodified-mdoc-blocks.sh docs/reference --verbose

# Show code context (surrounding code in the file)
./.claude/scripts/find-unmodified-mdoc-blocks.sh docs/reference --show-context
```

## Decision Tree: Which Modifier to Use

### Question 1: Should this code execute?

**YES (Code should actually run)**
- Use **no modifier** or **`:silent`**
- `:silent` hides the output (good if output is verbose or not useful)
- No modifier shows output (good for demonstrating behavior)

**NO (Code is illustrative, can't/shouldn't compile)**
- Go to Question 2

### Question 2: Why can't it compile?

**Imports/types undefined** (references external API, complex setup)
- Use **`:compile-only`**
- Indicates "this is pseudocode showing the pattern, not real code"

**Needs setup code first** (needs variables from previous examples)
- Previous example: **`:silent`** (hidden setup)
- Main example: **no modifier** (show the actual code you care about)
- Between them: add **`:reset`** to clear scope if next section is unrelated

**Should fail to compile** (anti-pattern, what NOT to do)
- Use **`:fail`**
- Compiler error becomes part of the documentation

**Complex initialization** (multiple lines of setup before actual example)
- Setup block: **`:silent:nest:1`** (silent + nested in function)
- Main block: **`:reset`** then show the real code

## Examples by Module

### http-model-schema.md (Current State)

Current: 27 unmodified blocks in file
Status: All intentionally `plain ```scala` (illustrative, not compilable)
Reason: Code shows API usage without being self-contained

Decision: These should be **`:compile-only`** because:
- They show realistic API usage (good!)
- They reference external types/imports
- They're meant to illustrate, not execute

### What to Look For

```scala
// ❌ This needs :compile-only
import zio.http.{Request, URL}
import zio.http.schema._

val request = Request.get(URL.parse("/api/users?page=2").toOption.get)
// Missing context: params, schema, etc.
```

```scala
// ✅ This is okay without modifier (self-contained, compiles)
val x = 42
val y = x * 2
println(y)  // Output: 84
```

```scala
// ⚠️ Needs :silent:nest:1 (setup code)
val db = DatabaseConnection("localhost")  // Side effect!
db.insert("user", Map("id" -> 1))         // Actual operation
```

## Fixing Strategies

### Strategy 1: Add :compile-only (Safest)

When in doubt, use `:compile-only`. It tells mdoc:
- "Try to compile this"
- "Don't execute it"
- "It might reference undefined things, that's okay"

```markdown
Before:
\`\`\`scala
import zio.http.schema._
params.query[Int]("page")
\`\`\`

After:
\`\`\`scala mdoc:compile-only
import zio.http.schema._
params.query[Int]("page")
\`\`\`
```

### Strategy 2: Make It Self-Contained (Better)

Rewrite the example to be standalone (no external dependencies):

```markdown
Before:
\`\`\`scala
val userId = params.query[UUID]("userId")
\`\`\`

After (compile-only, shows API usage):
\`\`\`scala mdoc:compile-only
import zio.http.QueryParams
import zio.http.schema._
import zio.blocks.schema.Schema

val params = QueryParams("userId" -> "550e8400-e29b-41d4-a716-446655440000")
val userId = params.query[java.util.UUID]("userId")
// Right(550e8400-e29b-41d4-a716-446655440000)
\`\`\`
```

### Strategy 3: Use :silent for Hidden Setup

When you need to set up state that's not interesting to show:

```markdown
\`\`\`scala mdoc:silent
val data = loadSomeData()  // Boring setup, hide it
\`\`\`

Then show the interesting part:
\`\`\`scala
val result = data.map(transform)
println(result)  // Shows output
\`\`\`
```

## Scanning Results Interpretation

### Files with Many Unmodified Blocks

Example: `http-model-schema.md` with 27 blocks

Check:
1. Are these blocks meant to illustrate patterns?
2. Do they reference external types?
3. Would adding all necessary imports make them unwieldy?

**If YES to any**: Mark as **`:compile-only`**
**If NO to all**: They should execute → leave as-is

## Integration with docs-mdoc-conventions Skill

The `docs-mdoc-conventions` skill should:

1. **Scan** using this script to find unmodified blocks
2. **Classify** each block:
   - Illustrative (pseudocode) → `:compile-only`
   - Executable (real code) → no modifier
   - Setup only → `:silent`
   - Failing example → `:fail`
3. **Suggest** fixes in documentation with clear explanations
4. **Provide context** showing surrounding code

## Common Pitfalls

### Pitfall 1: Unqualified References

```scala
// ❌ This will fail - 'params' is undefined
val page = params.query[Int]("page")

// ✅ Fix with :compile-only
\`\`\`scala mdoc:compile-only
val page = params.query[Int]("page")  // Assumes 'params' from context
\`\`\`
```

### Pitfall 2: Side Effects

```scala
// ❌ This will execute during doc generation!
\`\`\`scala
val file = new File("/tmp/delete-me.txt")
file.createNewFile()  // SIDE EFFECT!
\`\`\`

// ✅ Hide with :silent:nest:1
\`\`\`scala mdoc:silent:nest:1
val file = new File("/tmp/safe.txt")
file.createNewFile()
\`\`\`
```

### Pitfall 3: Multi-block Examples

```scala
// Block 1: Setup
\`\`\`scala mdoc:silent
val data = List(1, 2, 3)
\`\`\`

// Block 2: Use data (references 'data' from Block 1)
\`\`\`scala
val sum = data.sum
println(sum)  // Output: 6
\`\`\`

// Block 3: Unrelated, needs fresh scope
\`\`\`scala mdoc:reset
val x = 42  // Doesn't see 'data' anymore
println(x)
\`\`\`
```

## For the docs-mdoc-conventions Skill

When you implement the skill to use this script:

1. **Extraction Phase**: Run scanner, collect all unmodified blocks with file/line
2. **Analysis Phase**: Group by category (illustrative, executable, setup)
3. **Suggestion Phase**: For each block, recommend a modifier + reasoning
4. **Validation Phase**: Show what the block looks like with modifier added
5. **Action Phase**: Apply changes or ask user for confirmation

Example output:

```
📄 docs/reference/http-model-schema.md:29
   Code: Query parameter extraction example
   Status: Illustrative (references external QueryParams, Schema)
   Suggested Modifier: :compile-only
   Reasoning: Shows API usage pattern; can't compile without external context
   
   Before:
   \`\`\`scala
   val userId = params.query[java.util.UUID]("userId")
   \`\`\`
   
   After:
   \`\`\`scala mdoc:compile-only
   val userId = params.query[java.util.UUID]("userId")
   \`\`\`
```

## Testing Your Fixes

After adding modifiers, verify docs compile:

```bash
sbt "docs/mdoc --in docs/reference/http-model-schema.md"
```

Should show: `Compiled in X.XXs (0 errors)`

If there are still errors, the modifier didn't fix the issue — likely needs more setup or `:fail`.
