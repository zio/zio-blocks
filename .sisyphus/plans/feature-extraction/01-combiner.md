# PR 1: Combiner Module

## Summary

Extract `Combiner`, `Alternator`, and `Zippable` typeclasses from zio-http/zio-core into zio-blocks.

## What These Are

### Combiner
A typeclass for combining invariant type parameters using tuples, with intelligent flattening.

```scala
sealed trait Combiner[L, R] {
  type Out
  def combine(l: L, r: R): Out
  def separate(out: Out): (L, R)
}
```

Key features:
- Unit identity: `Unit ++ A = A`, `A ++ Unit = A`
- Tuple flattening up to 22 elements
- Bidirectional: combine AND separate

### Alternator
A typeclass for combining types using Either, with smart identity handling.

```scala
sealed trait Alternator[L, R] {
  type Out
  def left(l: L): Out
  def right(r: R): Out
  def unleft(out: Out): Option[L]
  def unright(out: Out): Option[R]
}
```

Key features:
- Same-type collapsing: `A | A = A`
- Nothing identity: `Nothing | A = A`
- Bidirectional: wrap AND unwrap

### Zippable
A typeclass for zipping values into tuples with identity handling.

```scala
trait Zippable[-A, -B] {
  type Out
  def zip(left: A, right: B): Out
  def discardsLeft: Boolean
  def discardsRight: Boolean
}
```

Key features:
- Unit identity handling
- Progressive tuple building up to 22 elements
- Discard tracking for optimization

## Source Files

| File | Source Location |
|------|-----------------|
| Combiner.scala | `zio-http/zio-http/shared/src/main/scala/zio/http/codec/Combiner.scala` |
| Alternator.scala | `zio-http/zio-http/shared/src/main/scala/zio/http/codec/Alternator.scala` |
| Zippable.scala | `zio/core/shared/src/main/scala/zio/Zippable.scala` |

## Target Location

```
zio-blocks/
├── combiner/
│   └── shared/
│       └── src/
│           └── main/
│               └── scala/
│                   └── zio/
│                       └── blocks/
│                           └── combiner/
│                               ├── Combiner.scala
│                               ├── Alternator.scala
│                               └── Zippable.scala
```

## Required Changes

### Combiner.scala
1. Change package: `zio.http.codec` → `zio.blocks.combiner`
2. Remove import: `zio.stacktracer.TracingImplicits.disableAutoTrace`
3. No other changes needed

### Alternator.scala
1. Change package: `zio.http.codec` → `zio.blocks.combiner`
2. Remove import: `zio.stacktracer.TracingImplicits.disableAutoTrace`
3. Replace `zio.ZNothing` with `scala.Nothing`
4. Update implicit definitions accordingly

### Zippable.scala
1. Change package: `zio` → `zio.blocks.combiner`
2. Remove import: `zio.stacktracer.TracingImplicits.disableAutoTrace`
3. No other changes needed

## Build Configuration

Add to `build.sbt`:

```scala
lazy val combiner = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("combiner"))
  .settings(stdSettings("zio-blocks-combiner"))
  .settings(
    libraryDependencies ++= Seq(
      // test dependencies only
    )
  )
```

## Testing

- Copy relevant tests from zio-http for Combiner/Alternator
- Copy relevant tests from zio-core for Zippable
- Ensure cross-platform tests pass (JVM/JS/Native)

## Estimated Effort

**Trivial** - Nearly copy-paste with minimal modifications.

~2-4 hours including tests and build setup.

## Acceptance Criteria

- [ ] All three typeclasses compile in zio-blocks
- [ ] No ZIO dependencies in the module
- [ ] Cross-platform build works (JVM/JS/Native)
- [ ] Tests pass
- [ ] Scaladoc preserved
