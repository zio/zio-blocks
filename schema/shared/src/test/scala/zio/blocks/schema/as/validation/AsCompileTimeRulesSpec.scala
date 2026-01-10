package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for As compile-time validation rules. */
object AsCompileTimeRulesSpec extends ZIOSpecDefault {

  case class SimpleA(name: String, age: Int)
  case class SimpleB(name: String, age: Int)

  case class WithOptA(name: String, opt: Option[Int])
  case class WithOptB(name: String, opt: Option[Int])

  def spec: Spec[TestEnvironment, Any] = suite("AsCompileTimeRulesSpec")(
    test("derives As for identical case classes") {
      val as     = As.derived[SimpleA, SimpleB]
      val result = as.into(SimpleA("test", 42))
      assert(result)(isRight(equalTo(SimpleB("test", 42))))
    },
    test("both directions work for valid derivation") {
      val as = As.derived[SimpleA, SimpleB]
      assertTrue(
        as.into(SimpleA("alice", 30)).isRight,
        as.from(SimpleB("bob", 25)).isRight
      )
    },
    test("Option None preserved in round-trip") {
      val as        = As.derived[WithOptA, WithOptB]
      val original  = WithOptA("test", None)
      val roundTrip = as.into(original).flatMap(as.from)
      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("Option Some preserved in round-trip") {
      val as        = As.derived[WithOptA, WithOptB]
      val original  = WithOptA("test", Some(42))
      val roundTrip = as.into(original).flatMap(as.from)
      assert(roundTrip)(isRight(equalTo(original)))
    }
  )
}
