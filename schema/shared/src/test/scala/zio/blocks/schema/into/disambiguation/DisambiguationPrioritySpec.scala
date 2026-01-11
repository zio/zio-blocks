package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for field disambiguation priority in Into derivation. */
object DisambiguationPrioritySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("DisambiguationPrioritySpec")(
    test("exact name+type match") {
      case class Source(name: String, count: Int)
      case class Target(name: String, count: Int)

      val result = Into.derived[Source, Target].into(Source("test", 42))
      assert(result)(isRight(equalTo(Target("test", 42))))
    },
    test("name match with coercion") {
      case class Source(value: Int, flag: Boolean)
      case class Target(value: Long, flag: Boolean)

      val result = Into.derived[Source, Target].into(Source(42, true))
      assert(result)(isRight(equalTo(Target(42L, true))))
    },
    test("unique type match when names differ") {
      case class Source(firstName: String, age: Int, active: Boolean)
      case class Target(fullName: String, years: Int, enabled: Boolean)

      val result = Into.derived[Source, Target].into(Source("Alice", 30, true))
      assert(result)(isRight(equalTo(Target("Alice", 30, true))))
    }
  )
}
