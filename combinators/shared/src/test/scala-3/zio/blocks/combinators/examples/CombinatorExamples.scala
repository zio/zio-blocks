package zio.blocks.combinators.examples

import zio.blocks.combinators.*

/**
 * Usage examples for the combinators module.
 *
 * These examples serve as both documentation and compile-time verification of
 * the combinators API. They demonstrate common patterns and edge cases.
 */
object CombinatorExamples {

  // ============================================================================
  // Combiner Examples
  // ============================================================================

  /**
   * Example 1: Basic tuple combination
   *
   * Demonstrates combining two values into a tuple.
   */
  def basicCombine(): Unit = {
    val a        = 1
    val b        = "hello"
    val combiner = implicitly[Combiner[Int, String]]
    val combined = combiner.combine(a, b)
    val (x, y)   = combiner.separate(combined)
    assert(x == a && y == b)
  }

  /**
   * Example 2: Unit identity (left)
   *
   * Demonstrates that combining Unit on the left returns the right value.
   */
  def unitIdentityLeft(): Unit = {
    val a        = 42
    val combiner = implicitly[Combiner[Unit, Int]]
    val combined = combiner.combine((), a)
    assert(combined.asInstanceOf[Any] == a)

    val (unit, value) = combiner.separate(combined)
    assert(unit == () && value == a)
  }

  /**
   * Example 3: Unit identity (right)
   *
   * Demonstrates that combining Unit on the right returns the left value.
   */
  def unitIdentityRight(): Unit = {
    val a        = "test"
    val combiner = implicitly[Combiner[String, Unit]]
    val combined = combiner.combine(a, ())
    assert(combined.asInstanceOf[Any] == a)

    val (value, unit) = combiner.separate(combined)
    assert(value == a && unit == ())
  }

  /**
   * Example 4: Tuple flattening
   *
   * Demonstrates that combining tuples flattens them into a single tuple.
   */
  def tupleFlattening(): Unit = {
    val ab       = (1, "a")
    val c        = true
    val combiner = implicitly[Combiner[(Int, String), Boolean]]
    val flat     = combiner.combine(ab, c)
    assert(flat == (1, "a", true))

    val (tuple, bool) = combiner.separate(flat)
    assert(tuple == ab && bool == c)
  }

  /**
   * Example 5: Multiple tuple flattening
   *
   * Demonstrates flattening nested tuple combinations.
   */
  def multipleTupleFlattening(): Unit = {
    val a        = (1, "hello")
    val b        = (true, 3.14)
    val combiner = implicitly[Combiner[(Int, String), (Boolean, Double)]]
    val combined = combiner.combine(a, b)
    assert(combined == (1, "hello", true, 3.14))

    val (left, right) = combiner.separate(combined)
    assert(left == a && right == b)
  }

  /**
   * Example 6: EmptyTuple identity
   *
   * Demonstrates that EmptyTuple behaves like Unit in combinations.
   */
  def emptyTupleIdentity(): Unit = {
    val a        = 99
    val combiner = implicitly[Combiner[EmptyTuple, Int]]
    val combined = combiner.combine(EmptyTuple, a)
    assert(combined.asInstanceOf[Any] == a)

    val (empty, value) = combiner.separate(combined)
    assert(empty == EmptyTuple && value == a)
  }

  // ============================================================================
  // Zippable Examples
  // ============================================================================

  /**
   * Example 7: Basic zipping
   *
   * Demonstrates basic zipping of two values.
   */
  def basicZip(): Unit = {
    val a        = 10
    val b        = "world"
    val zippable = implicitly[Zippable[Int, String]]
    val result   = zippable.zip(a, b)
    assert(result == (10, "world"))
  }

  /**
   * Example 8: Zipping with Unit (discards)
   *
   * Demonstrates that zipping with Unit discards the Unit value.
   */
  def zipWithUnit(): Unit = {
    val a         = "test"
    val zippable1 = implicitly[Zippable[Unit, String]]
    val zipped    = zippable1.zip((), a)
    assert(zipped.asInstanceOf[Any] == a)
    assert(zippable1.discardsLeft)

    val b         = 42
    val zippable2 = implicitly[Zippable[Int, Unit]]
    val zipped2   = zippable2.zip(b, ())
    assert(zipped2.asInstanceOf[Any] == b)
    assert(zippable2.discardsRight)
  }

  /**
   * Example 9: Tuple zipping with flattening
   *
   * Demonstrates that zipping tuples flattens them.
   */
  def zipTuples(): Unit = {
    val a        = (1, 2)
    val b        = ("x", false)
    val zippable = implicitly[Zippable[(Int, Int), (String, Boolean)]]
    val result   = zippable.zip(a, b)
    assert(result == (1, 2, "x", false))
  }

  // ============================================================================
  // UnionAlternator Examples
  // ============================================================================

  /**
   * Example 10: Creating a union type
   *
   * Demonstrates creating union types from left and right values.
   */
  def unionCreation(): Unit = {
    val alternator   = implicitly[UnionAlternator[Int, String]]
    val intOrString1 = alternator.left(42)
    val intOrString2 = alternator.right("hello")

    assert(alternator.unleft(intOrString1).contains(42))
    assert(alternator.unright(intOrString1).isEmpty)

    assert(alternator.unleft(intOrString2).isEmpty)
    assert(alternator.unright(intOrString2).contains("hello"))
  }

  /**
   * Example 11: Union type pattern matching
   *
   * Demonstrates extracting values from union types.
   */
  def unionPatternMatching(): Unit = {
    val alternator = implicitly[UnionAlternator[Int, String]]
    val union      = alternator.left(100)

    val result = alternator.unleft(union) match {
      case Some(i) => s"Int: $i"
      case None    =>
        alternator.unright(union) match {
          case Some(s) => s"String: $s"
          case None    => "Neither"
        }
    }

    assert(result == "Int: 100")
  }

  // ============================================================================
  // EitherAlternator Examples
  // ============================================================================

  /**
   * Example 13: Creating Either values
   *
   * Demonstrates creating Either from left and right values.
   */
  def eitherCreation(): Unit = {
    val alternator = implicitly[EitherAlternator[Int, String]]
    val either1    = alternator.left(42)
    val either2    = alternator.right("hello")

    assert(either1 == Left(42))
    assert(either2 == Right("hello"))

    assert(alternator.unleft(either1).contains(42))
    assert(alternator.unright(either2).contains("hello"))
  }

  /**
   * Example 14: Either extraction
   *
   * Demonstrates extracting values from Either.
   */
  def eitherExtraction(): Unit = {
    val alternator = implicitly[EitherAlternator[Boolean, Double]]
    val either     = alternator.right(3.14)

    val maybeLeft  = alternator.unleft(either)
    val maybeRight = alternator.unright(either)

    assert(maybeLeft.isEmpty)
    assert(maybeRight.contains(3.14))
  }

  /**
   * Example 15: Either bidirectional conversion
   *
   * Demonstrates roundtrip conversion with Either.
   */
  def eitherRoundtrip(): Unit = {
    val alternator = implicitly[EitherAlternator[Int, String]]
    val original   = "test"
    val either     = alternator.right(original)
    val extracted  = alternator.unright(either)

    assert(extracted.contains(original))
  }

  // ============================================================================
  // Limitations Documentation
  // ============================================================================

  /**
   * Scala 2 Arity Limitation:
   *
   * In Scala 2.13, tuples are limited to maximum arity of 22. Attempting to
   * combine tuples that would exceed this limit will result in a compile-time
   * error.
   *
   * Scala 3 has no such limitation and supports arbitrary tuple sizes.
   *
   * Example (would fail in Scala 2.13):
   * {{{
   * // This would work in Scala 3 but fail in Scala 2.13:
   * // val big1: (1, 2, 3, ..., 22) = ...
   * // val big2: (23, 24) = ...
   * // val combined = Combiner.combine(big1, big2) // Error in Scala 2.13!
   * }}}
   */
  def scala2ArityLimitation(): Unit = ()

  /**
   * StructuralCombiner JVM-Only Restriction:
   *
   * StructuralCombiner uses Scala 3's Selectable trait with Java reflection,
   * which is only available on the JVM platform.
   *
   * It will NOT compile on:
   *   - Scala.js
   *   - Scala Native
   *
   * This is a fundamental limitation of the reflection-based approach.
   */
  def structuralCombinerJvmOnly(): Unit = ()

  /**
   * Same-Type Alternator Restriction:
   *
   * Both UnionAlternator and EitherAlternator reject same-type combinations at
   * compile time to maintain type safety.
   *
   * Examples that would fail:
   * {{{
   * // UnionAlternator.alternator[Int, Int] // Compile error!
   * // EitherAlternator.alternator[String, String] // Compile error!
   * }}}
   *
   * The error messages suggest using Either[A, A] directly or wrapping in
   * distinct types.
   */
  def sameTypeAlternatorRestriction(): Unit = ()
}
