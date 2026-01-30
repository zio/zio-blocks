package zio.blocks.schema.into.validation

import zio.blocks.schema.Into
import zio.test.*

object OpaqueTypeDefs {
  opaque type SimpleAge = Int
  object SimpleAge {
    def apply(value: Int): Either[String, SimpleAge] =
      if (value >= 0 && value <= 150) Right(value)
      else Left(s"Invalid age: $value")

    def unsafe(value: Int): SimpleAge = value

    extension (age: SimpleAge) def toInt: Int = age
  }

  opaque type Email = String
  object Email {
    def apply(value: String): Either[String, Email] =
      if (value.contains("@")) Right(value)
      else Left(s"Invalid email: $value")

    def unsafe(value: String): Email = value

    extension (email: Email) def value: String = email
  }

  opaque type PositiveInt = Int
  object PositiveInt {
    def apply(value: Int): Either[String, PositiveInt] =
      if (value > 0) Right(value)
      else Left(s"Value must be positive: $value")

    def unsafe(value: Int): PositiveInt = value

    extension (pi: PositiveInt) def toInt: Int = pi
  }
}

object IntoOpaqueTypeSpec extends ZIOSpecDefault {
  import OpaqueTypeDefs.*

  case class PersonV1(name: String, age: Int, email: String)
  case class PersonV2(name: String, age: SimpleAge, email: Email)

  def spec: Spec[TestEnvironment, Any] = suite("IntoOpaqueTypeSpec")(
    suite("Direct opaque type derivation")(
      test("Into.derived[Int, SimpleAge] works for valid value") {
        val into   = Into.derived[Int, SimpleAge]
        val result = into.into(30)

        assertTrue(
          result.isRight,
          result.map(_.toInt).getOrElse(0) == 30
        )
      },
      test("Into.derived[Int, SimpleAge] fails for invalid value") {
        val into   = Into.derived[Int, SimpleAge]
        val result = into.into(-5)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      },
    ),
    suite("Product type with Opaque Type field")(
      test("converts to opaque types with successful validation") {
        val person = PersonV1("Alice", 30, "a@b.com")
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isRight,
          result.map(_.name).getOrElse("") == "Alice",
          result.map(_.age.toInt).getOrElse(0) == 30
        )
      },
      test("fails when age validation fails - negative value") {
        val person = PersonV1("Bob", -5, "a@b.com")
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      }
    ),
    test("errors are accumulated when multiple fields are invalid") {
      val person = PersonV1("Invalid", -5, "bad-email")
      val result = Into.derived[PersonV1, PersonV2].into(person)

      assertTrue(
        result.isLeft,
        result.swap.exists { err =>
          err.getMessage ==
            """converting field PersonWithEmailV1.email to PersonWithEmailV2.email failed
              |  Caused by: Validation failed for field 'email': Invalid email: bad-email
              |converting field PersonWithEmailV1.age to PersonWithEmailV2.age failed
              |  Caused by: Validation failed for field 'age': Invalid age: -5""".stripMargin
        }
      )
    },
    test("opaque type in collection - error propagates from collection element") {
      case class AgeList(ages: List[Int])
      case class ValidatedAgeList(ages: List[SimpleAge])

      val ageList = AgeList(List(30, 200, 40))
      val result  = Into.derived[AgeList, ValidatedAgeList].into(ageList)

      assertTrue(
        result.isLeft,
        result.swap.exists(err => err.toString.contains("Invalid age"))
      )
    },
    test("opaque type validation in coproduct case") {
      sealed trait RequestV1
      case class CreateV1(name: String, age: Int) extends RequestV1

      sealed trait RequestV2
      case class CreateV2(name: String, age: SimpleAge) extends RequestV2

      val request: RequestV1 = CreateV1("Alice", -5)
      val result             = Into.derived[RequestV1, RequestV2].into(request)

      assertTrue(
        result.isLeft,
        result.swap.exists(err => err.toString.contains("Invalid age"))
      )
    }
  )
}
