# Troubleshooting: Common mdoc Mistakes

## "My code won't compile in mdoc"

**Symptom:** mdoc reports a compilation error when running locally.

**Common causes:**

### Missing Import
```scala mdoc:silent
// ❌ Missing: where is Product?
val p = Product("Widget", 99)
```

**Fix:** Add the import:
```scala mdoc:silent
case class Product(name: String, price: Double)
val p = Product("Widget", 99)
```

### Type Mismatch
```scala mdoc:silent
case class User(name: String, age: Int)
```

```scala mdoc:compile-only
// ❌ age should be Int, not String
val user = User("Alice", "thirty")
```

**Fix:** Match the types:
```scala mdoc:compile-only
val user = User("Alice", 30)
```

### Undefined Method
```scala mdoc:silent
case class Point(x: Int, y: Int)
```

```scala mdoc:compile-only
// ❌ Point has no moveTo method
val moved = Point(1, 2).moveTo(3, 4)
```

**Fix:** Define the method or use available operations:
```scala mdoc:compile-only
val p = Point(1, 2)
val moved = Point(3, 4)  // Create new point
```

### Implicit Not Found
```scala mdoc:compile-only
// ❌ Schema not derived
case class Product(name: String, price: Double)
val schema = Schema[Product]
```

**Fix:** Add the implicit derivation:
```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Product(name: String, price: Double)
implicit val schema: Schema[Product] = Schema.derived
```

---

## "I can't redefine a name — it says the type already exists"

**Symptom:** Compilation error like "type Person is defined twice" when redefining a name in a later block.

### Reason
Once a `mdoc:silent` block runs, the name stays in scope for all subsequent blocks. You can't redefine it in another `silent` block:

```scala mdoc:silent
case class Person(name: String, age: Int)
```

```scala mdoc:silent
// ❌ ERROR: Person already defined!
case class Person(name: String, email: String)
```

### Fix 1: Use `mdoc:silent:nest`
```scala mdoc:silent
case class Person(name: String, age: Int)
val alice = Person("Alice", 30)
```

```scala mdoc:silent:nest
// ✓ OK: wrapped in object, can shadow Person
case class Person(name: String, email: String)
val bob = Person("Bob", "bob@example.com")
```

The `:nest` modifier wraps code in an anonymous object, creating a new namespace.

### Fix 2: Use `mdoc:silent:reset`
Use this when you want a **completely fresh scope** (often better for new sections of a guide):

```scala mdoc:silent
case class Person(name: String, age: Int)
```

```scala mdoc:silent:reset
// ✓ OK: reset clears all prior scope
case class Person(name: String, email: String)
```

### Fix 3: Use different names
If you just need a variation, name them differently:

```scala mdoc:silent
case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, email: String)
```

---

## "The output doesn't show — the result is hidden"

**Symptom:** You use `mdoc` but nothing appears in the rendered output.

### Reason 1: You used `mdoc:compile-only` instead of `mdoc`
```scala mdoc:silent
def greet(name: String) = s"Hello, $name"
```

```scala mdoc:compile-only
// ❌ This shows source but NOT the output
greet("World")
```

**Fix:** Use `mdoc` to render output:
```scala mdoc
greet("World")
```

### Reason 2: The variable isn't in scope
```scala mdoc:compile-only
val x = 42
```

```scala mdoc
// ❌ ERROR: x is not in scope (compile-only isolated)
x + 1
```

**Fix:** Use `mdoc:silent` for setup, then `mdoc` for output:
```scala mdoc:silent
val x = 42
```

```scala mdoc
x + 1
```

### Reason 3: You're in an `mdoc:silent` block
```scala mdoc:silent
// ❌ This is hidden — silenced output
val result = 2 + 2
result
```

**Fix:** Put the expression in a separate `mdoc` block:
```scala mdoc:silent
val result = 2 + 2
```

```scala mdoc
result
```

---

## "My `mdoc:silent:reset` didn't clear the prior scope"

**Symptom:** A name you thought was cleared is still accessible after reset.

### Reason
Scope is per-document-section. If the blocks are in **different Markdown sections** separated by headers or prose, they may not share scope. Reset applies within the current context.

**Example (wrong assumption):**
```scala mdoc:silent
val x = 1
```

Lots of prose here...

```scala mdoc:silent:reset
val y = x + 1  // ❌ x might not be in scope if reset "cleared" it
```

**Fix:** Test locally with `sbt docs` to verify scope behavior. If you want to be explicit, redefine everything you need:

```scala mdoc:silent:reset
val x = 1
val y = x + 1
```

---

## "Why is the order of my mdoc blocks weird?"

**Symptom:** Your blocks compile individually but fail when run together.

### Reason
Scope is **cumulative and sequential**. If Block B depends on something defined in Block A, Block A **must come first**:

```scala mdoc:silent
val name = "Alice"
```

```scala mdoc:silent
val greeting = s"Hello, $name"  // ✓ OK: name defined earlier
```

But reversing them fails:

```scala mdoc:silent
// ❌ ERROR: name not yet defined
val greeting = s"Hello, $name"
```

```scala mdoc:silent
val name = "Alice"
```

**Fix:** Arrange blocks in dependency order:
1. Imports
2. Type definitions (case classes, traits)
3. Helper functions
4. Examples using the above

---

## "I used imports in multiple blocks — will they conflict?"

**Symptom:** You have `import zio.blocks.schema._` in multiple blocks and wonder if there's duplication.

### Answer
**No conflict.** Imports are idempotent. Importing the same module multiple times is safe:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._
```

```scala mdoc:silent
import zio.blocks.schema._  // ✓ OK: re-importing is safe
val x = Schema[Int]
```

**Style tip:** Put all imports in the first `mdoc:silent` block to keep them visible. If a later section needs different imports, use `mdoc:silent:reset` first.

---

## "I want to show imports but not actually use them in later blocks"

**Symptom:** You want readers to see the import statement, but you don't want it in scope.

### Solution: Use `mdoc:compile-only`
```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json._
```

This shows the imports without adding them to the shared scope.

If you **do** want them in scope, use `mdoc:silent`:

```scala mdoc:silent
import zio.blocks.schema._
```

---

## "The real output differs from what I wrote in comments"

**Symptom:** You manually wrote `// Right(42)` but mdoc shows `// val res0: Either[...] = Right(42)`.

### Why This Happens
You used `mdoc:compile-only` (which doesn't evaluate) but manually added a result comment. mdoc doesn't evaluate `compile-only` blocks, so the comment is just text.

### Fix
Use `mdoc` and let mdoc render the actual output:

```scala mdoc:silent
def safeDivide(a: Int, b: Int): Either[String, Int] =
  if (b == 0) Left("division by zero") else Right(a / b)
```

```scala mdoc
safeDivide(10, 2)
safeDivide(10, 0)
```

mdoc will show the real evaluated results, which are always more accurate than hand-written comments.

---

## "I have a large example — should it be one block or multiple?"

**Symptom:** Your example is long and you're not sure how to structure it.

### Guideline
Split into **multiple blocks** if:
- You want to explain something **between the setup and the usage**
- Different **sections** of the example should be independently visible
- The output of one part is **educational by itself**

Keep as **one block** if:
- It's a **self-contained, standalone example** (use `compile-only`)
- All parts are **equally important** and best understood together

### Example: Multi-block (Progressive Narrative)
```scala mdoc:silent
case class Request(id: String, status: String)
val req = Request("123", "pending")
```

Here's the initial request. Now let's update it:

```scala mdoc:silent:nest
val updated = req.copy(status = "completed")
```

```scala mdoc
updated
```

### Example: Single-block (Self-Contained)
```scala mdoc:compile-only
case class Request(id: String, status: String)
val req = Request("123", "pending")
val updated = req.copy(status = "completed")
println(updated)
```
