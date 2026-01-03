package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for position-based disambiguation in Into derivation. */
object PositionDisambiguationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("PositionDisambiguationSpec")(
    test("tuple to case class by position") {
      case class Target(name: String, age: Int, active: Boolean)
      val result = Into.derived[(String, Int, Boolean), Target].into(("Alice", 30, true))
      assert(result)(isRight(equalTo(Target("Alice", 30, true))))
    },
    test("case class to tuple by position") {
      case class Source(name: String, age: Int)
      val result = Into.derived[Source, (String, Int)].into(Source("Bob", 25))
      assert(result)(isRight(equalTo(("Bob", 25))))
    },
    test("tuple to tuple with coercion") {
      val result = Into.derived[(Int, Int, Int), (Long, Long, Long)].into((1, 2, 3))
      assert(result)(isRight(equalTo((1L, 2L, 3L))))
    }
  )
}
