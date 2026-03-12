# Scope Module — Code Style Rules

These rules apply to all code, tests, examples, and documentation in the `scope` module.

## Rule 1: Don't explicitly use an implicit

If a parameter is `implicit`/`given`/`using`, use the top-level helper instead of calling through the named parameter. If the name isn't needed, drop it.

```scala
// ❌ Bad: declaring given but calling through it explicitly
class Pool(config: Config)(using fin: Finalizer):
  fin.defer(pool.shutdown())

// ✅ Good: use top-level defer, drop the unused name
class Pool(config: Config)(using Finalizer):
  defer(pool.shutdown())
```

## Rule 2: Don't qualify after importing

If you write `import scope.*`, use the imported members unqualified. If you're going to qualify everything, don't import.

```scala
// ❌ Bad: importing then ignoring the import
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Database))
  (scope $ db)(_.query("x"))   // why qualify scope?
}

// ✅ Good: use the import
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Database))
  $(db)(_.query("x"))
}
```

This applies to all scope operations: `$`, `allocate`, `lower`, `open`, `defer`, `leak`, `scoped`.

## Rule 3: Use infix `$`, not dot-call

The `$` operator is declared `infix` and reads as "scope applies function to scoped value". Use infix syntax. But if `import scope.*` is active, prefer unqualified `$(...)`.

```scala
// ❌ Bad: dot-call
scope.$(db)(_.query("x"))

// ✅ OK: infix (when no import is active)
(scope $ db)(_.query("x"))

// ✅ Best: unqualified (when import scope.* is active)
$(db)(_.query("x"))
```

For N-ary `$` (N≥2), `infix` is not available (it requires exactly one operand).
Use unqualified syntax after `import scope.*`:

```scala
// ✅ N=2
$(db, cache)((d, c) => d.query(c.key()))

// ✅ N=3
$(db, cache, log)((d, c, l) => { l.info("start"); d.query(c.key()) })
```

For N>5, compose nested single-param calls:

```scala
$(sa1)(v1 => $(sa2)(v2 => $(sa3)(v3 => v1.method(v2.result(), v3.tag()))))
```
