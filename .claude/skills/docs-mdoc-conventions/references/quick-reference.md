# Quick Reference: mdoc Modifiers


## Scope Persistence

Once a `mdoc:silent` block runs, **all names remain in scope for subsequent blocks** until reset:

```
Block A: mdoc:silent
  ├─ case class Person(...)    ← in scope
  └─ val alice = ...           ← in scope

Block B: mdoc
  ├─ Can reference alice       ✓
  └─ Can reference Person      ✓

Block C: mdoc:silent:reset
  ├─ All prior scope cleared   ✗
  └─ Must redefine Person      

Block D: mdoc
  ├─ Can reference new Person  ✓
  └─ Cannot reference old Person ✗
```

## Common Patterns

### Pattern: Setup + Show Output
```scala mdoc:silent
def add(a: Int, b: Int): Int = a + b
```

Now call it and show the result:
```scala mdoc
add(2, 3)
```

### Pattern: Self-Contained Example
```scala mdoc:compile-only
case class User(name: String, age: Int)
val user = User("Alice", 30)
```

### Pattern: Multi-Step Guide
1. **Setup block** → `mdoc:silent` (case classes, imports)
2. **Example 1** → `mdoc` (show output)
3. **Building on Example 1** → `mdoc` (reuse prior definitions)
4. **New Topic** → `mdoc:silent:reset` + `mdoc:silent` (fresh context)
5. **Final Copy-Paste** → `mdoc:compile-only` (standalone)

## Decision Tree (Short Version)

```
Executable Scala?
├─ NO → ```scala (plain)
└─ YES
   ├─ Later blocks need this? NO → mdoc:compile-only
   └─ Later blocks need this? YES
      ├─ This block shows result? YES → mdoc
      └─ Redefining name? YES → mdoc:silent:nest
      └─ NO → mdoc:silent
```

## When to Use `:reset`

- Switching to a **completely different domain** (Product → JSON → User)
- When `:nest` would be too complex (many redefinitions)
- Starting a **new tutorial section** with independent examples
- Avoid if: just defining a new helper function (doesn't need reset)

## Scope Gotchas

**❌ Wrong:**
```scala mdoc:silent
case class V1(x: Int)
val v = V1(1)
```

```scala mdoc:silent
case class V1(x: String)  // ERROR: V1 already defined
```

**✓ Correct:**
```scala mdoc:silent
case class V1(x: Int)
val v = V1(1)
```

```scala mdoc:silent:nest
case class V1(x: String)  // OK: wrapped in object
```

**✓ Also Correct:**
```scala mdoc:silent
case class V1(x: Int)
```

```scala mdoc:silent:reset
case class V1(x: String)  // OK: scope cleared
```

## Tips

- **Never manually write `// result` comments** — use `mdoc` to show real output
- **Test locally with `sbt docs`** before committing mdoc blocks
- **Group related setup blocks** — define all prerequisites in one `silent` block if possible
- **Use `:reset` sparingly** — prefer `:nest` for minor redefinitions
- **Imports are usually `silent`** — so they stay in scope but don't clutter the output
