# Unsound `Unscoped[Resource[A]]`

## The Bug

`Unscoped[Resource[A]]` was added in commit `4d218204` on branch `leak-stomp` with
the rationale that `Resource[A]` is "a lazy description, not a live resource." This
is unsound.

## Exploit

A user can construct a `Resource` whose internal thunk captures scoped references,
then extract it from the scope:

```scala
val leaked: Resource[() => Int] = Scope.global.scoped { scope =>
  import scope._
  val r: $[FileInputStream] = allocate(new FileInputStream("file.json"))

  // Resource.apply takes => A (by-name), so the thunk is NOT evaluated here.
  // The closure () => ... captures scope and r.
  Resource[() => Int](() => (scope $ r)(_.read()).get)
}
// scope is now closed, FileInputStream is closed

Scope.global.scoped { scope2 =>
  import scope2._
  // allocate evaluates the by-name thunk, producing the closure
  val fn: $[() => Int] = allocate(leaked)
  // calling the closure accesses the closed scope's resource
  (scope2 $ fn)(_.apply()).get // accesses closed FileInputStream!
}
```

The closed-scope defense returns null/0 instead of crashing, but this is still a
leak — scoped references escape the scope boundary. `Resource[A]` is internally
`Scope => A` — essentially a function. We don't (and shouldn't) mark function types
as `Unscoped`, and the same reasoning applies to `Resource`.

## Fix

### 1. Remove `Unscoped[Resource[A]]`

Delete from both files:
- `scope/shared/src/main/scala-3/zio/blocks/scope/Unscoped.scala`
- `scope/shared/src/main/scala-2/zio/blocks/scope/Unscoped.scala`

### 2. Add `ScopedResourceOps` to `Scope.scala`

Add an implicit class alongside `ScopedOps` in `Scope.scala`:

```scala
implicit class ScopedResourceOps[A](private val sr: $[Resource[A]]) {
  def allocate: $[A] = self.allocate($unwrap(sr))
}
```

This lets users go from `$[Resource[A]]` → `$[A]` without ever extracting the
`Resource` from `$`. The `Resource` stays scoped; only its *result* becomes scoped.

### 3. Update call sites

The pattern `allocate(scope.$(x)(_.resourceMethod()).get)` becomes:

```scala
// Before (unsound — extracted Resource via .get):
val tx: $[DbTransaction] = allocate((txScope $ c)(_.beginTransaction("tx-001")).get)

// After (sound — Resource stays inside $):
val tx: $[DbTransaction] = (txScope $ c)(_.beginTransaction("tx-001")).allocate
```

Files to update:
- `scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala`
- `scope-examples/src/main/scala/scope/examples/ScopedForComprehensionExample.scala`
- `scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala`
- `scope/shared/src/test/scala/zio/blocks/scope/ScopeSpec.scala` (2 tests that use `Unscoped[Resource[A]]`)
- `docs/scope.md` (chaining resource acquisition section, built-in Unscoped list, etc.)
- `scope/scope.md` (if it still exists — it was deleted by the `use`→`$` rename commit)

### 4. Add a negative compile-time test

Verify that `Resource[A]` can NOT be `.get`-ed:

```scala
test("Resource[A] cannot be .get-ed (not Unscoped)") {
  assertZIO(typeCheck("""
    import zio.blocks.scope._
    Scope.global.scoped { scope =>
      import scope._
      val r: $[Resource[Int]] = $(Resource(42))
      r.get
      ()
    }
  """))(isLeft)
}
```

### 5. Add a positive test for `.allocate` on `$[Resource[A]]`

```scala
test("ScopedResourceOps.allocate works for $[Resource[A]]") {
  val result: String = Scope.global.scoped { scope =>
    import scope._
    val outer: $[Outer] = allocate(Resource.fromAutoCloseable(new Outer))
    val inner: $[Inner] = (scope $ outer)(_.makeInner).allocate
    (scope $ inner)(_.value).get
  }
  assertTrue(result == "inner")
}
```

## Process

1. Make all edits (remove Unscoped[Resource[A]], add ScopedResourceOps, update call sites)
2. Fast loop: `sbt --client "++3.7.4; scopeJVM/test"` then `"++2.13.16; scopeJVM/test"`
3. Compile examples: `sbt --client "++3.7.4; scope-examples/compile"`
4. Format: `sbt --client "++3.7.4; scopeJVM/scalafmt; scopeJVM/Test/scalafmt; scope-examples/scalafmt"`
5. Commit, push, monitor CI
