package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Option type coercion in Into conversions.
 *
 * Covers:
 *   - Option[A] → Option[B] with element coercion
 *   - Some/None handling
 *   - Option in products
 */
object OptionCoercionSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("OptionCoercionSpec")(
    suite("Basic Option Coercion")(
      test("coerces Some[Int] to Some[Long]") {
        val source: Option[Int] = Some(42)
        val result              = Into.derived[Option[Int], Option[Long]].into(source)

        assert(result)(isRight(equalTo(Some(42L))))
      },
      test("coerces None[Int] to None[Long]") {
        val source: Option[Int] = None
        val result              = Into.derived[Option[Int], Option[Long]].into(source)

        assert(result)(isRight(equalTo(None)))
      },
      test("coerces Some[Byte] to Some[Int]") {
        val source: Option[Byte] = Some(100.toByte)
        val result               = Into.derived[Option[Byte], Option[Int]].into(source)

        assert(result)(isRight(equalTo(Some(100))))
      },
      test("coerces Some[Float] to Some[Double]") {
        val source: Option[Float] = Some(3.14f)
        val result                = Into.derived[Option[Float], Option[Double]].into(source)

        assert(result)(isRight(equalTo(Some(3.14f.toDouble))))
      }
    ),
    suite("Option with Case Classes")(
      test("coerces Some[CaseClass] with element conversion") {
        val source: Option[PersonV1] = Some(PersonV1("Alice", 30))
        val result                   = Into.derived[Option[PersonV1], Option[PersonV2]].into(source)

        assert(result)(isRight(equalTo(Some(PersonV2("Alice", 30L)))))
      },
      test("coerces None[CaseClass]") {
        val source: Option[PersonV1] = None
        val result                   = Into.derived[Option[PersonV1], Option[PersonV2]].into(source)

        assert(result)(isRight(equalTo(None)))
      }
    ),
    suite("Option Narrowing")(
      test("narrows Some[Long] to Some[Int] when value fits") {
        val source: Option[Long] = Some(42L)
        val result               = Into.derived[Option[Long], Option[Int]].into(source)

        assert(result)(isRight(equalTo(Some(42))))
      },
      test("fails when narrowing Some[Long] overflows Int") {
        val source: Option[Long] = Some(Long.MaxValue)
        val result               = Into.derived[Option[Long], Option[Int]].into(source)

        assert(result)(isLeft)
      },
      test("None passes even with narrowing conversion") {
        val source: Option[Long] = None
        val result               = Into.derived[Option[Long], Option[Int]].into(source)

        assert(result)(isRight(equalTo(None)))
      }
    ),
    suite("Option in Products")(
      test("coerces case class with optional field") {
        case class Source(name: String, value: Option[Int])
        case class Target(name: String, value: Option[Long])

        val source = Source("test", Some(42))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("test", Some(42L)))))
      },
      test("coerces case class with None optional field") {
        case class Source(name: String, value: Option[Int])
        case class Target(name: String, value: Option[Long])

        val source = Source("test", None)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("test", None))))
      },
      test("coerces case class with multiple optional fields") {
        case class Source(a: Option[Int], b: Option[Short], c: Option[Float])
        case class Target(a: Option[Long], b: Option[Int], c: Option[Double])

        val source = Source(Some(1), Some(2.toShort), Some(3.0f))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Some(1L), Some(2), Some(3.0f.toDouble)))))
      }
    ),
    suite("Nested Options")(
      test("coerces Option[Option[Int]] to Option[Option[Long]]") {
        val source: Option[Option[Int]] = Some(Some(42))
        val result                      = Into.derived[Option[Option[Int]], Option[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(Some(42L)))))
      },
      test("coerces Some(None) correctly") {
        val source: Option[Option[Int]] = Some(None)
        val result                      = Into.derived[Option[Option[Int]], Option[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(None))))
      },
      test("coerces None correctly for nested Option") {
        val source: Option[Option[Int]] = None
        val result                      = Into.derived[Option[Option[Int]], Option[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(None)))
      }
    ),
    suite("Option with Collections")(
      test("coerces Option[List[Int]] to Option[List[Long]]") {
        val source: Option[List[Int]] = Some(List(1, 2, 3))
        val result                    = Into.derived[Option[List[Int]], Option[List[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(List(1L, 2L, 3L)))))
      },
      test("coerces None[List[Int]] to None[List[Long]]") {
        val source: Option[List[Int]] = None
        val result                    = Into.derived[Option[List[Int]], Option[List[Long]]].into(source)

        assert(result)(isRight(equalTo(None)))
      },
      test("coerces Option[Vector[Byte]] to Option[Vector[Int]]") {
        val source: Option[Vector[Byte]] = Some(Vector(1.toByte, 2.toByte))
        val result                       = Into.derived[Option[Vector[Byte]], Option[Vector[Int]]].into(source)

        assert(result)(isRight(equalTo(Some(Vector(1, 2)))))
      }
    ),
    suite("Identity Option Conversions")(
      test("Option[Int] to Option[Int]") {
        val source: Option[Int] = Some(42)
        val result              = Into.derived[Option[Int], Option[Int]].into(source)

        assert(result)(isRight(equalTo(Some(42))))
      },
      test("None[Int] to Option[Int]") {
        val source: Option[Int] = None
        val result              = Into.derived[Option[Int], Option[Int]].into(source)

        assert(result)(isRight(equalTo(None)))
      }
    )
  )
}
