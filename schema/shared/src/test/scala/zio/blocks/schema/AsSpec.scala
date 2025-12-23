package zio.blocks.schema

import zio.test._

object AsSpec extends ZIOSpecDefault {

  // === Basic Case Classes (Same Structure, Different Names) ===
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, yearsOld: Int)

  // === Case Class ↔ Tuple ===
  case class Point(x: Double, y: Double)

  // === Numeric Coercion (Widening/Narrowing) ===
  case class ConfigV1(timeout: Int, retries: Int)
  case class ConfigV2(timeout: Long, retries: Long)

  // === Collection Type Conversions ===
  case class DataList(items: List[Int])
  case class DataVector(items: Vector[Int])

  // === Optional Field Handling ===
  case class UserV1(id: String, name: String)
  case class UserV2(id: String, name: String, email: Option[String])

  // === Nested Products ===
  case class AddressV1(street: String, zip: Int)
  case class CompanyV1(name: String, address: AddressV1)

  case class AddressV2(street: String, zip: Long)
  case class CompanyV2(name: String, address: AddressV2)

  // We need Into instances for nested types
  implicit val addressV1ToV2: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]
  implicit val addressV2ToV1: Into[AddressV2, AddressV1] = Into.derived[AddressV2, AddressV1]

  // === Coproduct (Sealed Trait) ===
  sealed trait ColorV1
  object ColorV1 {
    case object Red   extends ColorV1
    case object Blue  extends ColorV1
    case object Green extends ColorV1
  }

  sealed trait ColorV2
  object ColorV2 {
    case object Red   extends ColorV2
    case object Blue  extends ColorV2
    case object Green extends ColorV2
  }

  // === Coproduct with Payloads ===
  sealed trait ResultV1
  object ResultV1 {
    case class Success(value: Int)  extends ResultV1
    case class Failure(msg: String) extends ResultV1
  }

  sealed trait ResultV2
  object ResultV2 {
    case class Success(value: Long) extends ResultV2
    case class Failure(msg: String) extends ResultV2
  }

  // === Set ↔ List (Lossy but Valid) ===
  case class DataSet(values: Set[Int])
  case class DataListInt(values: List[Int])

  // Helper to verify round-trip property
  def roundTripTest[A, B](as: As[A, B], original: A): TestResult = {
    val converted    = as.into(original)
    val roundTripped = converted.flatMap(as.from)
    assertTrue(roundTripped.isRight, roundTripped == Right(original))
  }

  def roundTripTestReverse[A, B](as: As[A, B], original: B): TestResult = {
    val converted    = as.from(original)
    val roundTripped = converted.flatMap(as.into)
    assertTrue(roundTripped.isRight, roundTripped == Right(original))
  }

  def spec: Spec[TestEnvironment, Any] = suite("AsSpec")(
    suite("Basic Case Class Conversion")(
      test("derives As[PersonV1, PersonV2] and converts correctly") {
        val as     = As.derived[PersonV1, PersonV2]
        val person = PersonV1("Alice", 30)

        val converted = as.into(person)
        assertTrue(converted == Right(PersonV2("Alice", 30)))
      },
      test("round-trip PersonV1 → PersonV2 → PersonV1") {
        val as       = As.derived[PersonV1, PersonV2]
        val original = PersonV1("Bob", 25)

        roundTripTest(as, original)
      },
      test("round-trip PersonV2 → PersonV1 → PersonV2") {
        val as       = As.derived[PersonV1, PersonV2]
        val original = PersonV2("Charlie", 35)

        roundTripTestReverse(as, original)
      }
    ),
    suite("Case Class ↔ Tuple")(
      test("derives As[Point, (Double, Double)] and converts correctly") {
        val as    = As.derived[Point, (Double, Double)]
        val point = Point(1.0, 2.0)

        val converted = as.into(point)
        assertTrue(converted == Right((1.0, 2.0)))
      },
      test("round-trip Point → (Double, Double) → Point") {
        val as       = As.derived[Point, (Double, Double)]
        val original = Point(3.5, 4.5)

        roundTripTest(as, original)
      },
      test("round-trip (Double, Double) → Point → (Double, Double)") {
        val as       = As.derived[Point, (Double, Double)]
        val original = (5.0, 6.0)

        roundTripTestReverse(as, original)
      }
    ),
    suite("Numeric Coercion (Int ↔ Long)")(
      test("derives As[ConfigV1, ConfigV2] and widens correctly") {
        val as     = As.derived[ConfigV1, ConfigV2]
        val config = ConfigV1(30, 3)

        val converted = as.into(config)
        assertTrue(converted == Right(ConfigV2(30L, 3L)))
      },
      test("narrows Long to Int when value fits") {
        val as     = As.derived[ConfigV1, ConfigV2]
        val config = ConfigV2(30L, 3L)

        val converted = as.from(config)
        assertTrue(converted == Right(ConfigV1(30, 3)))
      },
      test("fails when Long value exceeds Int.MaxValue") {
        val as     = As.derived[ConfigV1, ConfigV2]
        val config = ConfigV2(3000000000L, 3L) // exceeds Int.MaxValue

        val converted = as.from(config)
        assertTrue(converted.isLeft)
      },
      test("round-trip ConfigV1 → ConfigV2 → ConfigV1 (within Int range)") {
        val as       = As.derived[ConfigV1, ConfigV2]
        val original = ConfigV1(100, 5)

        roundTripTest(as, original)
      }
    ),
    suite("Collection Type Conversions (List ↔ Vector)")(
      test("derives As[DataList, DataVector] and converts correctly") {
        val as   = As.derived[DataList, DataVector]
        val data = DataList(List(1, 2, 3))

        val converted = as.into(data)
        assertTrue(converted == Right(DataVector(Vector(1, 2, 3))))
      },
      test("round-trip DataList → DataVector → DataList") {
        val as       = As.derived[DataList, DataVector]
        val original = DataList(List(4, 5, 6))

        roundTripTest(as, original)
      },
      test("round-trip DataVector → DataList → DataVector") {
        val as       = As.derived[DataList, DataVector]
        val original = DataVector(Vector(7, 8, 9))

        roundTripTestReverse(as, original)
      }
    ),
    suite("Optional Field Handling")(
      test("derives As[UserV1, UserV2] - adds None for optional field") {
        val as   = As.derived[UserV1, UserV2]
        val user = UserV1("123", "Alice")

        val converted = as.into(user)
        assertTrue(converted == Right(UserV2("123", "Alice", None)))
      },
      test("drops optional field when going from UserV2 to UserV1") {
        val as   = As.derived[UserV1, UserV2]
        val user = UserV2("456", "Bob", Some("bob@example.com"))

        val converted = as.from(user)
        assertTrue(converted == Right(UserV1("456", "Bob")))
      },
      test("round-trip UserV1 → UserV2 → UserV1") {
        val as       = As.derived[UserV1, UserV2]
        val original = UserV1("789", "Charlie")

        roundTripTest(as, original)
      }
      // Note: round-trip UserV2 → UserV1 → UserV2 is NOT guaranteed to preserve email
    ),
    suite("Nested Products")(
      test("derives As[CompanyV1, CompanyV2] with nested conversion") {
        val as      = As.derived[CompanyV1, CompanyV2]
        val company = CompanyV1("Acme", AddressV1("123 Main St", 12345))

        val converted = as.into(company)
        assertTrue(converted == Right(CompanyV2("Acme", AddressV2("123 Main St", 12345L))))
      },
      test("round-trip CompanyV1 → CompanyV2 → CompanyV1") {
        val as       = As.derived[CompanyV1, CompanyV2]
        val original = CompanyV1("TechCorp", AddressV1("456 Oak Ave", 67890))

        roundTripTest(as, original)
      }
    ),
    suite("Coproduct (Sealed Trait) Conversion")(
      test("derives As[ColorV1, ColorV2] for case objects") {
        val as = As.derived[ColorV1, ColorV2]

        assertTrue(
          as.into(ColorV1.Red) == Right(ColorV2.Red),
          as.into(ColorV1.Blue) == Right(ColorV2.Blue),
          as.into(ColorV1.Green) == Right(ColorV2.Green)
        )
      },
      test("round-trip ColorV1 → ColorV2 → ColorV1") {
        val as = As.derived[ColorV1, ColorV2]

        assertTrue(
          as.into(ColorV1.Red).flatMap(as.from) == Right(ColorV1.Red),
          as.into(ColorV1.Blue).flatMap(as.from) == Right(ColorV1.Blue),
          as.into(ColorV1.Green).flatMap(as.from) == Right(ColorV1.Green)
        )
      }
    ),
    suite("Coproduct with Payloads")(
      test("derives As[ResultV1, ResultV2] with payload conversion") {
        val as = As.derived[ResultV1, ResultV2]

        assertTrue(
          as.into(ResultV1.Success(42)) == Right(ResultV2.Success(42L)),
          as.into(ResultV1.Failure("error")) == Right(ResultV2.Failure("error"))
        )
      },
      test("round-trip ResultV1.Success → ResultV2.Success → ResultV1.Success") {
        val as                 = As.derived[ResultV1, ResultV2]
        val original: ResultV1 = ResultV1.Success(100)

        val converted    = as.into(original)
        val roundTripped = converted.flatMap(as.from)
        assertTrue(roundTripped == Right(original))
      },
      test("round-trip ResultV1.Failure → ResultV2.Failure → ResultV1.Failure") {
        val as                 = As.derived[ResultV1, ResultV2]
        val original: ResultV1 = ResultV1.Failure("oops")

        val converted    = as.into(original)
        val roundTripped = converted.flatMap(as.from)
        assertTrue(roundTripped == Right(original))
      }
    ),
    suite("Lossy Conversions (Set ↔ List)")(
      test("converts Set to List") {
        val as   = As.derived[DataSet, DataListInt]
        val data = DataSet(Set(1, 2, 3))

        val converted = as.into(data)
        assertTrue(converted.isRight, converted.map(_.values.toSet) == Right(Set(1, 2, 3)))
      },
      test("converts List to Set (loses duplicates)") {
        val as   = As.derived[DataSet, DataListInt]
        val data = DataListInt(List(1, 2, 2, 3, 3, 3))

        val converted = as.from(data)
        assertTrue(converted == Right(DataSet(Set(1, 2, 3))))
      },
      test("round-trip Set → List → Set preserves elements") {
        val as       = As.derived[DataSet, DataListInt]
        val original = DataSet(Set(1, 2, 3))

        val converted    = as.into(original)
        val roundTripped = converted.flatMap(as.from)
        assertTrue(roundTripped == Right(original))
      }
      // Note: List → Set → List is NOT guaranteed to preserve duplicates or order
    ),
    suite("Tuple to Tuple")(
      test("derives As[(Int, String), (Long, String)]") {
        val as       = As.derived[(Int, String), (Long, String)]
        val original = (42, "hello")

        val converted = as.into(original)
        assertTrue(converted == Right((42L, "hello")))
      },
      test("round-trip (Int, String) → (Long, String) → (Int, String)") {
        val as       = As.derived[(Int, String), (Long, String)]
        val original = (100, "world")

        val converted    = as.into(original)
        val roundTripped = converted.flatMap(as.from)
        assertTrue(roundTripped == Right(original))
      }
    ),
    suite("Arity Mismatch - Should handle gracefully")(
      test("case class with 3 fields to tuple with 3 elements") {
        case class Triple(a: Int, b: String, c: Boolean)

        val as       = As.derived[Triple, (Int, String, Boolean)]
        val original = Triple(1, "two", true)

        val converted = as.into(original)
        assertTrue(converted == Right((1, "two", true)))
      },
      test("round-trip Triple → (Int, String, Boolean) → Triple") {
        case class Triple(a: Int, b: String, c: Boolean)

        val as       = As.derived[Triple, (Int, String, Boolean)]
        val original = Triple(42, "answer", false)

        roundTripTest(as, original)
      }
    ),
    suite("Compilation Failure Tests - Default Values")(
      test("should not compile As[A, B] when B has default values and A is missing fields") {
        // Default values break round-trip guarantee because we can't distinguish
        // between explicitly set default value and missing field
        typeCheck {
          """
          case class ProductV1(name: String, price: Double)
          case class ProductV2(name: String, price: Double, taxable: Boolean = true)

          As.derived[ProductV1, ProductV2]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("target field 'taxable'") ||
              result.swap.getOrElse("").contains("missing field") ||
              result.swap.getOrElse("").contains("could not find implicit")
          )
        )
      },
      test("should not compile As[A, B] when A has default values and B is missing fields") {
        // Same issue in reverse direction
        typeCheck {
          """
          case class ConfigA(host: String, port: Int, debug: Boolean = false)
          case class ConfigB(host: String, port: Int)

          As.derived[ConfigA, ConfigB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("source field 'debug'") ||
              result.swap.getOrElse("").contains("missing field") ||
              result.swap.getOrElse("").contains("could not find implicit")
          )
        )
      },
      test("should not compile As[A, B] when types have fields with default values") {
        // Default values break round-trip guarantee because we can't distinguish
        // between explicitly set default value and the actual default
        typeCheck {
          """
          case class SettingsV1(name: String, count: Int = 0)
          case class SettingsV2(name: String, count: Long = 0L)

          As.derived[SettingsV1, SettingsV2]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("default values") ||
              result.swap.getOrElse("").contains("Cannot derive")
          )
        )
      }
    )
  )
}
