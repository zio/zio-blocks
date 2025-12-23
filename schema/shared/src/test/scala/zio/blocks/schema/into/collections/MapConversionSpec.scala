package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Map type conversions with key and value type coercion.
 *
 * Covers:
 *   - Map[K1, V1] → Map[K2, V2] with key coercion
 *   - Map[K1, V1] → Map[K2, V2] with value coercion
 *   - Map[K1, V1] → Map[K2, V2] with both key and value coercion
 *   - Nested map conversions
 */
object MapConversionSpec extends ZIOSpecDefault {

  // Test data types
  case class KeyV1(id: Int)
  case class KeyV2(id: Long)
  case class ValueV1(data: Int)
  case class ValueV2(data: Long)

  implicit val keyV1ToV2: Into[KeyV1, KeyV2]       = Into.derived[KeyV1, KeyV2]
  implicit val valueV1ToV2: Into[ValueV1, ValueV2] = Into.derived[ValueV1, ValueV2]

  def spec: Spec[TestEnvironment, Any] = suite("MapConversionSpec")(
    suite("Map - Same Types")(
      test("converts Map[String, Int] to Map[String, Int]") {
        val source = Map("a" -> 1, "b" -> 2)
        val result = Into[Map[String, Int], Map[String, Int]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> 1, "b" -> 2))))
      },
      test("converts empty Map") {
        val source = Map.empty[String, Int]
        val result = Into[Map[String, Int], Map[String, Int]].into(source)

        assert(result)(isRight(equalTo(Map.empty[String, Int])))
      }
    ),
    suite("Map - Value Coercion")(
      test("converts Map[String, Int] to Map[String, Long]") {
        val source = Map("a" -> 1, "b" -> 2)
        val result = Into[Map[String, Int], Map[String, Long]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> 1L, "b" -> 2L))))
      },
      test("converts Map[String, Byte] to Map[String, Int]") {
        val source = Map("x" -> 1.toByte, "y" -> 2.toByte)
        val result = Into[Map[String, Byte], Map[String, Int]].into(source)

        assert(result)(isRight(equalTo(Map("x" -> 1, "y" -> 2))))
      },
      test("converts Map with CaseClass values") {
        val source = Map("alice" -> ValueV1(100))
        val result = Into[Map[String, ValueV1], Map[String, ValueV2]].into(source)

        assert(result)(isRight(equalTo(Map("alice" -> ValueV2(100L)))))
      }
    ),
    suite("Map - Key Coercion")(
      test("converts Map[Int, String] to Map[Long, String]") {
        val source = Map(1 -> "a", 2 -> "b")
        val result = Into[Map[Int, String], Map[Long, String]].into(source)

        assert(result)(isRight(equalTo(Map(1L -> "a", 2L -> "b"))))
      },
      test("converts Map[Byte, String] to Map[Int, String]") {
        val source = Map(1.toByte -> "x", 2.toByte -> "y")
        val result = Into[Map[Byte, String], Map[Int, String]].into(source)

        assert(result)(isRight(equalTo(Map(1 -> "x", 2 -> "y"))))
      },
      test("converts Map with CaseClass keys") {
        val source = Map(KeyV1(1) -> "a", KeyV1(2) -> "b")
        val result = Into[Map[KeyV1, String], Map[KeyV2, String]].into(source)

        assert(result)(isRight(equalTo(Map(KeyV2(1L) -> "a", KeyV2(2L) -> "b"))))
      }
    ),
    suite("Map - Both Key and Value Coercion")(
      test("converts Map[Int, Int] to Map[Long, Long]") {
        val source = Map(1 -> 10, 2 -> 20)
        val result = Into[Map[Int, Int], Map[Long, Long]].into(source)

        assert(result)(isRight(equalTo(Map(1L -> 10L, 2L -> 20L))))
      },
      test("converts Map[Byte, Short] to Map[Int, Long]") {
        val source = Map(1.toByte -> 100.toShort, 2.toByte -> 200.toShort)
        val result = Into[Map[Byte, Short], Map[Int, Long]].into(source)

        assert(result)(isRight(equalTo(Map(1 -> 100L, 2 -> 200L))))
      },
      test("converts Map with CaseClass keys and values") {
        val source = Map(KeyV1(1) -> ValueV1(10))
        val result = Into[Map[KeyV1, ValueV1], Map[KeyV2, ValueV2]].into(source)

        assert(result)(isRight(equalTo(Map(KeyV2(1L) -> ValueV2(10L)))))
      }
    ),
    suite("Map - Narrowing")(
      test("narrows Map[String, Long] to Map[String, Int] when all values fit") {
        val source = Map("a" -> 1L, "b" -> 2L)
        val result = Into[Map[String, Long], Map[String, Int]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> 1, "b" -> 2))))
      },
      test("fails when value narrowing overflows") {
        val source = Map("a" -> 1L, "b" -> Long.MaxValue)
        val result = Into[Map[String, Long], Map[String, Int]].into(source)

        assert(result)(isLeft)
      },
      test("narrows Map[Long, String] to Map[Int, String] when all keys fit") {
        val source = Map(1L -> "a", 2L -> "b")
        val result = Into[Map[Long, String], Map[Int, String]].into(source)

        assert(result)(isRight(equalTo(Map(1 -> "a", 2 -> "b"))))
      },
      test("fails when key narrowing overflows") {
        val source = Map(Long.MaxValue -> "a")
        val result = Into[Map[Long, String], Map[Int, String]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Map with Collection Values")(
      test("converts Map[String, List[Int]] to Map[String, List[Long]]") {
        val source = Map("nums" -> List(1, 2, 3))
        val result = Into[Map[String, List[Int]], Map[String, List[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("nums" -> List(1L, 2L, 3L)))))
      },
      test("converts Map[String, Vector[Int]] to Map[String, Vector[Long]]") {
        val source = Map("data" -> Vector(10, 20))
        val result = Into[Map[String, Vector[Int]], Map[String, Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("data" -> Vector(10L, 20L)))))
      },
      test("converts Map[String, Option[Int]] to Map[String, Option[Long]]") {
        val source = Map("a" -> Some(1), "b" -> None)
        val result = Into[Map[String, Option[Int]], Map[String, Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> Some(1L), "b" -> None))))
      }
    ),
    suite("Nested Maps")(
      test("converts Map[String, Map[String, Int]] to Map[String, Map[String, Long]]") {
        val source = Map("outer" -> Map("inner" -> 42))
        val result = Into[Map[String, Map[String, Int]], Map[String, Map[String, Long]]].into(source)

        assert(result)(isRight(equalTo(Map("outer" -> Map("inner" -> 42L)))))
      },
      test("converts nested Map with key coercion") {
        val source = Map("outer" -> Map(1 -> "a", 2 -> "b"))
        val result = Into[Map[String, Map[Int, String]], Map[String, Map[Long, String]]].into(source)

        assert(result)(isRight(equalTo(Map("outer" -> Map(1L -> "a", 2L -> "b")))))
      }
    ),
    suite("In Products")(
      test("converts case class with Map field - value coercion") {
        case class Source(data: Map[String, Int])
        case class Target(data: Map[String, Long])

        val source = Source(Map("a" -> 1, "b" -> 2))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Map("a" -> 1L, "b" -> 2L)))))
      },
      test("converts case class with Map field - key coercion") {
        case class Source(data: Map[Int, String])
        case class Target(data: Map[Long, String])

        val source = Source(Map(1 -> "x", 2 -> "y"))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Map(1L -> "x", 2L -> "y")))))
      },
      test("converts case class with Map field - both key and value coercion") {
        case class Source(data: Map[Int, Int])
        case class Target(data: Map[Long, Long])

        val source = Source(Map(1 -> 10, 2 -> 20))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Map(1L -> 10L, 2L -> 20L)))))
      }
    ),
    suite("Large Maps")(
      test("converts large Map with coercion") {
        val source = (1 to 1000).map(i => s"key$i" -> i).toMap
        val result = Into[Map[String, Int], Map[String, Long]].into(source)

        assert(result.map(_.size))(isRight(equalTo(1000))) &&
        assert(result.map(_.get("key1")))(isRight(equalTo(Some(1L)))) &&
        assert(result.map(_.get("key1000")))(isRight(equalTo(Some(1000L))))
      }
    )
  )
}
