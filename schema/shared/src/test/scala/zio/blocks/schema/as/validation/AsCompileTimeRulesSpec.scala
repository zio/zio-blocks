package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for As compile-time validation rules.
 */
object AsCompileTimeRulesSpec extends ZIOSpecDefault {

  // === Types for Testing ===

  final case class SimpleA(name: String, age: Int)
  final case class SimpleB(name: String, age: Int)

  final case class WithOptA(name: String, opt: Option[Int])
  final case class WithOptB(name: String, opt: Option[Int])

  def spec: Spec[TestEnvironment, Any] = suite("AsCompileTimeRulesSpec")(
    suite("Valid As Derivations")(
      test("derives As for identical case classes") {
        val as = As.derived[SimpleA, SimpleB]

        val a      = SimpleA("test", 42)
        val result = as.into(a)

        assert(result)(isRight(equalTo(SimpleB("test", 42))))
      },
      test("derives As for case classes with matching Option fields") {
        val as = As.derived[WithOptA, WithOptB]

        val a      = WithOptA("test", Some(42))
        val result = as.into(a)

        assert(result)(isRight(equalTo(WithOptB("test", Some(42)))))
      },
      test("both directions work for valid derivation") {
        val as = As.derived[SimpleA, SimpleB]

        val a = SimpleA("alice", 30)
        val b = SimpleB("bob", 25)

        assertTrue(
          as.into(a).isRight,
          as.from(b).isRight
        )
      }
    ),
    suite("Field Mapping Consistency")(
      test("field order preserved in round-trip") {
        val as = As.derived[SimpleA, SimpleB]

        val original  = SimpleA("test", 100)
        val roundTrip = as.into(original).flatMap(as.from)

        roundTrip match {
          case Right(result) =>
            assertTrue(
              result.name == original.name,
              result.age == original.age
            )
          case Left(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("Option Field Behavior")(
      test("None is preserved in round-trip") {
        val as = As.derived[WithOptA, WithOptB]

        val original  = WithOptA("test", None)
        val roundTrip = as.into(original).flatMap(as.from)

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Some is preserved in round-trip") {
        val as = As.derived[WithOptA, WithOptB]

        val original  = WithOptA("test", Some(42))
        val roundTrip = as.into(original).flatMap(as.from)

        assert(roundTrip)(isRight(equalTo(original)))
      }
    )
  )
}
