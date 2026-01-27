# GitHub Issue Draft: Combiner Module

## Title

Add Combiner, Alternator, and Zippable typeclasses

## Labels

- `enhancement`
- `good first issue`
- `new module`

## Body

### Summary

Extract the `Combiner`, `Alternator`, and `Zippable` typeclasses from zio-http and zio-core into zio-blocks. These are effect-agnostic composition utilities useful for building DSLs, codecs, and other combinatorial APIs.

### Motivation

These typeclasses are currently embedded in zio-http (`Combiner`, `Alternator`) and zio-core (`Zippable`), requiring users to depend on those larger libraries even when they only need the composition primitives.

Moving them to zio-blocks:
- Makes them available without heavy dependencies
- Provides foundational building blocks for schema derivation and codec composition
- Enables reuse across the ZIO ecosystem

### What to Extract

#### Combiner
Combines types using tuples with intelligent flattening:
```scala
trait Combiner[L, R] {
  type Out
  def combine(l: L, r: R): Out
  def separate(out: Out): (L, R)
}
// Unit ++ A = A, (A, B) ++ C = (A, B, C), etc.
```

#### Alternator  
Combines types using Either with identity handling:
```scala
trait Alternator[L, R] {
  type Out
  def left(l: L): Out
  def right(r: R): Out
  def unleft(out: Out): Option[L]
  def unright(out: Out): Option[R]
}
// A | A = A, Nothing | A = A, etc.
```

#### Zippable
Zips values into tuples:
```scala
trait Zippable[-A, -B] {
  type Out
  def zip(left: A, right: B): Out
}
```

### Source Files

- `Combiner.scala`: https://github.com/zio/zio-http/blob/main/zio-http/shared/src/main/scala/zio/http/codec/Combiner.scala
- `Alternator.scala`: https://github.com/zio/zio-http/blob/main/zio-http/shared/src/main/scala/zio/http/codec/Alternator.scala
- `Zippable.scala`: https://github.com/zio/zio/blob/main/core/shared/src/main/scala/zio/Zippable.scala

### Tasks

- [ ] Create `combiner` module with cross-platform setup (JVM/JS/Native)
- [ ] Extract `Combiner.scala` with package change
- [ ] Extract `Alternator.scala`, replace `ZNothing` with `Nothing`
- [ ] Extract `Zippable.scala` with package change
- [ ] Add tests
- [ ] Update build.sbt

### Acceptance Criteria

- All three typeclasses compile and work in zio-blocks
- No ZIO dependencies
- Cross-platform support (JVM/JS/Native)
- Tests pass
