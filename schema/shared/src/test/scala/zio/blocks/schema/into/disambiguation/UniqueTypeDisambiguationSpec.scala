package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for unique type disambiguation in Into derivation. */
object UniqueTypeDisambiguationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("UniqueTypeDisambiguationSpec")(
    test("maps fields by unique type when names differ") {
      case class Source(name: String, age: Int, active: Boolean)
      case class Target(fullName: String, years: Int, enabled: Boolean)

      val result = Into.derived[Source, Target].into(Source("Alice", 30, true))
      assert(result)(isRight(equalTo(Target("Alice", 30, true))))
    },
    test("unique types with coercion") {
      case class Source(id: Int, score: Float)
      case class Target(identifier: Long, rating: Double)

      val result = Into.derived[Source, Target].into(Source(42, 3.14f))
      assert(result)(isRight(equalTo(Target(42L, 3.14f.toDouble))))
    },
    test("unique types allow field reordering") {
      case class Source(a: String, b: Int)
      case class Target(x: Int, y: String)

      val result = Into.derived[Source, Target].into(Source("hello", 42))
      assert(result)(isRight(equalTo(Target(42, "hello"))))
    }
  )
}
