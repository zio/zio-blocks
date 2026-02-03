package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for name-based field disambiguation in Into derivation. */
object NameDisambiguationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NameDisambiguationSpec")(
    test("maps fields by name when types are not unique") {
      case class Source(firstName: String, lastName: String)
      case class Target(firstName: String, lastName: String)

      val result = Into.derived[Source, Target].into(Source("John", "Doe"))
      assert(result)(isRight(equalTo(Target("John", "Doe"))))
    },
    test("name match with type coercion") {
      case class Source(count: Int, total: Int)
      case class Target(count: Long, total: Long)

      val result = Into.derived[Source, Target].into(Source(100, 500))
      assert(result)(isRight(equalTo(Target(100L, 500L))))
    },
    test("collection fields with same element type match by name") {
      case class Source(items: List[String], tags: List[String])
      case class Target(items: List[String], tags: List[String])

      val result = Into.derived[Source, Target].into(Source(List("a"), List("x", "y")))
      assert(result)(isRight(equalTo(Target(List("a"), List("x", "y")))))
    }
  )
}
