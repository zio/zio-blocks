package zio.blocks.typeid

import scala.compiletime.testing.typeChecks
import zio.test._

/**
 * Negative compile-time tests for [[IsNominalType]] using Scala 3's
 * [[scala.compiletime.testing.typeChecks]], which evaluates whether a code
 * snippet type-checks at compile time (not runtime).
 *
 * This proves the core guarantee from Github issue #1172: `IsNominalType[A]`
 * cannot be satisfied when `A` is an unresolved type parameter, so a DSL method
 * like `def contains[A: IsNominalType](a: A)` cannot be called from an
 * unconstrained generic context.
 */
object IsNominalTypeNegativeSpec extends ZIOSpecDefault {

  def spec = suite("IsNominalType — Scala 3 negative compile-time proofs")(
    test("unconstrained generic caller cannot forward abstract B to IsNominalType-constrained method") {
      // typeChecks runs at compile time and returns false if the snippet
      // would fail to compile. This is the definitive proof that IsNominalType
      // blocks abstract type parameters at compile time.
      val compiles = typeChecks(
        """
        import zio.blocks.typeid.IsNominalType
        def constrained[A: IsNominalType](a: A): String =
          IsNominalType[A].typeId.fullName
        def unconstrained[B](b: B): String = constrained(b)
        """
      )
      assertTrue(!compiles)
    },
    test("IsNominalType cannot be summoned for a bare type parameter") {
      val compiles = typeChecks(
        """
        import zio.blocks.typeid.IsNominalType
        def foo[A]: IsNominalType[A] = summon[IsNominalType[A]]
        """
      )
      assertTrue(!compiles)
    },
    test("IsNominalType CAN be summoned when the caller also requires it") {
      val compiles = typeChecks(
        """
        import zio.blocks.typeid.IsNominalType
        def constrained[A: IsNominalType](a: A): String =
          IsNominalType[A].typeId.fullName
        def alsoConstrained[A: IsNominalType](a: A): String =
          constrained(a)
        """
      )
      assertTrue(compiles)
    }
  )
}
