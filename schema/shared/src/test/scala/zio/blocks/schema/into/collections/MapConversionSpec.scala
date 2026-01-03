package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for Map type conversions. */
object MapConversionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("MapConversionSpec")(
    test("Map[String, Int] to Map[String, Long] - value coercion") {
      val result = Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2))
      assert(result)(isRight(equalTo(Map("a" -> 1L, "b" -> 2L))))
    },
    test("Map[Int, String] to Map[Long, String] - key coercion") {
      val result = Into[Map[Int, String], Map[Long, String]].into(Map(1 -> "a", 2 -> "b"))
      assert(result)(isRight(equalTo(Map(1L -> "a", 2L -> "b"))))
    },
    test("Map[Int, Int] to Map[Long, Long] - both key and value coercion") {
      val result = Into[Map[Int, Int], Map[Long, Long]].into(Map(1 -> 10, 2 -> 20))
      assert(result)(isRight(equalTo(Map(1L -> 10L, 2L -> 20L))))
    },
    test("case class with Map field") {
      case class Source(data: Map[Int, Int])
      case class Target(data: Map[Long, Long])

      val result = Into.derived[Source, Target].into(Source(Map(1 -> 10)))
      assert(result)(isRight(equalTo(Target(Map(1L -> 10L)))))
    }
  )
}
