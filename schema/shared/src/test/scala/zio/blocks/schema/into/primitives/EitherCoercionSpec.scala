package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Either type coercion in Into conversions.
 *
 * Covers:
 *   - Either[A, B] → Either[C, D] with element coercion
 *   - Left/Right handling
 *   - Either in products
 */
object EitherCoercionSpec extends ZIOSpecDefault {

  // Test data types
  case class ErrorV1(code: Int, msg: String)
  case class ErrorV2(code: Long, msg: String)

  case class ValueV1(data: Int)
  case class ValueV2(data: Long)

  implicit val errorV1ToV2: Into[ErrorV1, ErrorV2] = Into.derived[ErrorV1, ErrorV2]
  implicit val valueV1ToV2: Into[ValueV1, ValueV2] = Into.derived[ValueV1, ValueV2]

  def spec: Spec[TestEnvironment, Any] = suite("EitherCoercionSpec")(
    suite("Basic Either Coercion")(
      test("coerces Right[Int] to Right[Long]") {
        val source: Either[String, Int] = Right(42)
        val result                      = Into.derived[Either[String, Int], Either[String, Long]].into(source)

        assert(result)(isRight(equalTo(Right(42L))))
      },
      test("coerces Left unchanged when right type changes") {
        val source: Either[String, Int] = Left("error")
        val result                      = Into.derived[Either[String, Int], Either[String, Long]].into(source)

        assert(result)(isRight(equalTo(Left("error"))))
      },
      test("coerces both Left and Right types") {
        val source: Either[Int, Short] = Right(100.toShort)
        val result                     = Into.derived[Either[Int, Short], Either[Long, Int]].into(source)

        assert(result)(isRight(equalTo(Right(100))))
      },
      test("coerces Left when both types change") {
        val source: Either[Int, Short] = Left(42)
        val result                     = Into.derived[Either[Int, Short], Either[Long, Int]].into(source)

        assert(result)(isRight(equalTo(Left(42L))))
      }
    ),
    suite("Either with Case Classes")(
      test("coerces Right[CaseClass]") {
        val source: Either[ErrorV1, ValueV1] = Right(ValueV1(100))
        val result                           = Into.derived[Either[ErrorV1, ValueV1], Either[ErrorV2, ValueV2]].into(source)

        assert(result)(isRight(equalTo(Right(ValueV2(100L)))))
      },
      test("coerces Left[CaseClass]") {
        val source: Either[ErrorV1, ValueV1] = Left(ErrorV1(404, "Not found"))
        val result                           = Into.derived[Either[ErrorV1, ValueV1], Either[ErrorV2, ValueV2]].into(source)

        assert(result)(isRight(equalTo(Left(ErrorV2(404L, "Not found")))))
      }
    ),
    suite("Either Narrowing")(
      test("narrows Right[Long] to Right[Int] when value fits") {
        val source: Either[String, Long] = Right(42L)
        val result                       = Into.derived[Either[String, Long], Either[String, Int]].into(source)

        assert(result)(isRight(equalTo(Right(42))))
      },
      test("fails when narrowing Right overflows") {
        val source: Either[String, Long] = Right(Long.MaxValue)
        val result                       = Into.derived[Either[String, Long], Either[String, Int]].into(source)

        assert(result)(isLeft)
      },
      test("narrows Left type when value fits") {
        val source: Either[Long, String] = Left(100L)
        val result                       = Into.derived[Either[Long, String], Either[Int, String]].into(source)

        assert(result)(isRight(equalTo(Left(100))))
      },
      test("fails when narrowing Left overflows") {
        val source: Either[Long, String] = Left(Long.MaxValue)
        val result                       = Into.derived[Either[Long, String], Either[Int, String]].into(source)

        assert(result)(isLeft)
      },
      test("Right passes even when Left would overflow") {
        val source: Either[Long, String] = Right("success")
        val result                       = Into.derived[Either[Long, String], Either[Int, String]].into(source)

        assert(result)(isRight(equalTo(Right("success"))))
      }
    ),
    suite("Either in Products")(
      test("coerces case class with Either field - Right case") {
        case class Source(name: String, result: Either[String, Int])
        case class Target(name: String, result: Either[String, Long])

        val source = Source("test", Right(42))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("test", Right(42L)))))
      },
      test("coerces case class with Either field - Left case") {
        case class Source(name: String, result: Either[String, Int])
        case class Target(name: String, result: Either[String, Long])

        val source = Source("test", Left("error"))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("test", Left("error")))))
      },
      test("coerces case class with multiple Either fields") {
        case class Source(a: Either[Int, String], b: Either[String, Int])
        case class Target(a: Either[Long, String], b: Either[String, Long])

        val source = Source(Left(1), Right(2))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Left(1L), Right(2L)))))
      }
    ),
    suite("Nested Either")(
      test("coerces Either[Either[A, B], C] to Either[Either[A', B'], C']") {
        val source: Either[Either[Int, String], Short] = Left(Right("inner"))
        val result                                     = Into.derived[Either[Either[Int, String], Short], Either[Either[Long, String], Int]].into(source)

        assert(result)(isRight(equalTo(Left(Right("inner")))))
      },
      test("coerces nested Left") {
        val source: Either[Either[Int, String], Short] = Left(Left(42))
        val result                                     = Into.derived[Either[Either[Int, String], Short], Either[Either[Long, String], Int]].into(source)

        assert(result)(isRight(equalTo(Left(Left(42L)))))
      },
      test("coerces outer Right") {
        val source: Either[Either[Int, String], Short] = Right(100.toShort)
        val result                                     = Into.derived[Either[Either[Int, String], Short], Either[Either[Long, String], Int]].into(source)

        assert(result)(isRight(equalTo(Right(100))))
      }
    ),
    suite("Either with Collections")(
      test("coerces Either[String, List[Int]] to Either[String, List[Long]]") {
        val source: Either[String, List[Int]] = Right(List(1, 2, 3))
        val result                            = Into.derived[Either[String, List[Int]], Either[String, List[Long]]].into(source)

        assert(result)(isRight(equalTo(Right(List(1L, 2L, 3L)))))
      },
      test("coerces Either with empty List") {
        val source: Either[String, List[Int]] = Right(List.empty)
        val result                            = Into.derived[Either[String, List[Int]], Either[String, List[Long]]].into(source)

        assert(result)(isRight(equalTo(Right(List.empty[Long]))))
      },
      test("coerces Either[List[Int], String] - Left case") {
        val source: Either[List[Int], String] = Left(List(1, 2))
        val result                            = Into.derived[Either[List[Int], String], Either[List[Long], String]].into(source)

        assert(result)(isRight(equalTo(Left(List(1L, 2L)))))
      }
    ),
    suite("Identity Either Conversions")(
      test("Either[String, Int] to Either[String, Int] - Right") {
        val source: Either[String, Int] = Right(42)
        val result                      = Into.derived[Either[String, Int], Either[String, Int]].into(source)

        assert(result)(isRight(equalTo(Right(42))))
      },
      test("Either[String, Int] to Either[String, Int] - Left") {
        val source: Either[String, Int] = Left("error")
        val result                      = Into.derived[Either[String, Int], Either[String, Int]].into(source)

        assert(result)(isRight(equalTo(Left("error"))))
      }
    )
  )
}
