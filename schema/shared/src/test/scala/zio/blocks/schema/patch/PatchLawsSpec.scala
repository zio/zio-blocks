package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.DynamicValueGen._
import zio.test._

object PatchLawsSpec extends SchemaBaseSpec {

  // Simple case classes for testing
  case class Person(name: String, age: Int)
  implicit val personSchema: Schema[Person] = Schema.derived

  case class Address(street: String, city: String, zipCode: Int)
  implicit val addressSchema: Schema[Address] = Schema.derived

  case class Company(name: String, employees: Vector[Person])
  implicit val companySchema: Schema[Company] = Schema.derived

  sealed trait Status
  case class Active(since: String)    extends Status
  case class Inactive(reason: String) extends Status
  implicit val activeSchema: Schema[Active]     = Schema.derived
  implicit val inactiveSchema: Schema[Inactive] = Schema.derived
  implicit val statusSchema: Schema[Status]     = Schema.derived

  // Generators for test data
  val genPerson: Gen[Any, Person] = for {
    name <- Gen.alphaNumericStringBounded(3, 15)
    age  <- Gen.int(0, 120)
  } yield Person(name, age)

  val genAddress: Gen[Any, Address] = for {
    street  <- Gen.alphaNumericStringBounded(5, 20)
    city    <- Gen.alphaNumericStringBounded(3, 15)
    zipCode <- Gen.int(10000, 99999)
  } yield Address(street, city, zipCode)

  val genCompany: Gen[Any, Company] = for {
    name      <- Gen.alphaNumericStringBounded(3, 15)
    employees <- Gen.listOfBounded(0, 3)(genPerson).map(_.toVector)
  } yield Company(name, employees)

  val genStatus: Gen[Any, Status] = Gen.oneOf(
    Gen.alphaNumericStringBounded(5, 15).map(Active(_)),
    Gen.alphaNumericStringBounded(5, 15).map(Inactive(_))
  )

  def spec: Spec[TestEnvironment, Any] = suite("PatchLawsSpec")(
    suite("Monoid Laws for Patch[A]")(
      test("left identity: Patch.empty ++ p == p (Int)") {
        check(Gen.int, Gen.int) { (old, new_) =>
          val schema = Schema[Int]
          val patch  = schema.diff(old, new_)
          val empty  = Patch.empty[Int]

          val composed = empty ++ patch
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("right identity: p ++ Patch.empty == p (Int)") {
        check(Gen.int, Gen.int) { (old, new_) =>
          val schema = Schema[Int]
          val patch  = schema.diff(old, new_)
          val empty  = Patch.empty[Int]

          val composed = patch ++ empty
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3) (Int)") {
        check(Gen.int, Gen.int, Gen.int, Gen.int) { (v0, v1, v2, v3) =>
          val schema = Schema[Int]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)
          val p3     = schema.diff(v2, v3)

          val left  = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)

          val result1 = left(v0, PatchMode.Strict)
          val result2 = right(v0, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("left identity: Patch.empty ++ p == p (Person)") {
        check(genPerson, genPerson) { (old, new_) =>
          val schema = Schema[Person]
          val patch  = schema.diff(old, new_)
          val empty  = Patch.empty[Person]

          val composed = empty ++ patch
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("right identity: p ++ Patch.empty == p (Person)") {
        check(genPerson, genPerson) { (old, new_) =>
          val schema = Schema[Person]
          val patch  = schema.diff(old, new_)
          val empty  = Patch.empty[Person]

          val composed = patch ++ empty
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3) (Person)") {
        check(genPerson, genPerson, genPerson, genPerson) { (v0, v1, v2, v3) =>
          val schema = Schema[Person]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)
          val p3     = schema.diff(v2, v3)

          val left  = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)

          val result1 = left(v0, PatchMode.Strict)
          val result2 = right(v0, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("left identity: Patch.empty ++ p == p (Vector[Int])") {
        check(Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector), Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector)) {
          (old, new_) =>
            val schema = Schema[Vector[Int]]
            val patch  = schema.diff(old, new_)
            val empty  = Patch.empty[Vector[Int]]

            val composed = empty ++ patch
            val result1  = composed(old, PatchMode.Strict)
            val result2  = patch(old, PatchMode.Strict)

            assertTrue(result1 == result2)
        }
      },
      test("right identity: p ++ Patch.empty == p (Vector[Int])") {
        check(Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector), Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector)) {
          (old, new_) =>
            val schema = Schema[Vector[Int]]
            val patch  = schema.diff(old, new_)
            val empty  = Patch.empty[Vector[Int]]

            val composed = patch ++ empty
            val result1  = composed(old, PatchMode.Strict)
            val result2  = patch(old, PatchMode.Strict)

            assertTrue(result1 == result2)
        }
      },
      test("left identity: Patch.empty ++ p == p (Map[String, Int])") {
        val genMap = Gen
          .listOfBounded(0, 5) {
            for {
              key   <- Gen.alphaNumericStringBounded(1, 10)
              value <- Gen.int
            } yield key -> value
          }
          .map(_.toMap)

        check(genMap, genMap) { (old, new_) =>
          val schema = Schema[Map[String, Int]]
          val patch  = schema.diff(old, new_)
          val empty  = Patch.empty[Map[String, Int]]

          val composed = empty ++ patch
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      }
    ),
    suite("DynamicPatch Monoid Laws")(
      test("left identity: DynamicPatch.empty ++ p == p") {
        check(genDynamicValue, genDynamicValue) { (old, new_) =>
          val patch = Differ.diff(old, new_)
          val empty = DynamicPatch.empty

          val composed = empty ++ patch
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("right identity: p ++ DynamicPatch.empty == p") {
        check(genDynamicValue, genDynamicValue) { (old, new_) =>
          val patch = Differ.diff(old, new_)
          val empty = DynamicPatch.empty

          val composed = patch ++ empty
          val result1  = composed(old, PatchMode.Strict)
          val result2  = patch(old, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        check(genDynamicValue, genDynamicValue, genDynamicValue, genDynamicValue) { (v0, v1, v2, v3) =>
          val p1 = Differ.diff(v0, v1)
          val p2 = Differ.diff(v1, v2)
          val p3 = Differ.diff(v2, v3)

          val left  = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)

          val result1 = left(v0, PatchMode.Strict)
          val result2 = right(v0, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      }
    ),
    suite("Roundtrip Law - Property Tests")(
      test("roundtrip for primitives: diff(old, new).apply(old) == Right(new) (Int)") {
        check(Gen.int, Gen.int) { (old, new_) =>
          val schema = Schema[Int]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for primitives: diff(old, new).apply(old) == Right(new) (String)") {
        check(Gen.alphaNumericStringBounded(0, 20), Gen.alphaNumericStringBounded(0, 20)) { (old, new_) =>
          val schema = Schema[String]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for primitives: diff(old, new).apply(old) == Right(new) (Long)") {
        check(Gen.long, Gen.long) { (old, new_) =>
          val schema = Schema[Long]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for primitives: diff(old, new).apply(old) == Right(new) (Double)") {
        check(Gen.double, Gen.double) { (old, new_) =>
          val schema = Schema[Double]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          // Handle NaN specially
          result match {
            case Right(value) if new_.isNaN => assertTrue(value.isNaN)
            case _                          => assertTrue(result == Right(new_))
          }
        }
      },
      test("roundtrip for primitives: diff(old, new).apply(old) == Right(new) (Boolean)") {
        check(Gen.boolean, Gen.boolean) { (old, new_) =>
          val schema = Schema[Boolean]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for records: diff(old, new).apply(old) == Right(new) (Person)") {
        check(genPerson, genPerson) { (old, new_) =>
          val schema = Schema[Person]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for records: diff(old, new).apply(old) == Right(new) (Address)") {
        check(genAddress, genAddress) { (old, new_) =>
          val schema = Schema[Address]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for nested records: diff(old, new).apply(old) == Right(new) (Company)") {
        check(genCompany, genCompany) { (old, new_) =>
          val schema = Schema[Company]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for sequences: diff(old, new).apply(old) == Right(new) (Vector[Int])") {
        check(Gen.listOfBounded(0, 10)(Gen.int).map(_.toVector), Gen.listOfBounded(0, 10)(Gen.int).map(_.toVector)) {
          (old, new_) =>
            val schema = Schema[Vector[Int]]
            val patch  = schema.diff(old, new_)
            val result = patch(old, PatchMode.Strict)

            assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for sequences: diff(old, new).apply(old) == Right(new) (Vector[String])") {
        check(
          Gen.listOfBounded(0, 5)(Gen.alphaNumericStringBounded(1, 10)).map(_.toVector),
          Gen.listOfBounded(0, 5)(Gen.alphaNumericStringBounded(1, 10)).map(_.toVector)
        ) { (old, new_) =>
          val schema = Schema[Vector[String]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for maps: diff(old, new).apply(old) == Right(new) (Map[String, Int])") {
        val genMap = Gen
          .listOfBounded(0, 5) {
            for {
              key   <- Gen.alphaNumericStringBounded(1, 10)
              value <- Gen.int
            } yield key -> value
          }
          .map(_.toMap)

        check(genMap, genMap) { (old, new_) =>
          val schema = Schema[Map[String, Int]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip for variants: diff(old, new).apply(old) == Right(new) (Status)") {
        check(genStatus, genStatus) { (old, new_) =>
          val schema = Schema[Status]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      }
    ),
    suite("Empty Patch Identity")(
      test("empty patch is true identity (no side effects) - Int") {
        check(Gen.int) { value =>
          val empty  = Patch.empty[Int]
          val result = empty(value, PatchMode.Strict)

          assertTrue(result == Right(value))
        }
      },
      test("empty patch is true identity (no side effects) - Person") {
        check(genPerson) { value =>
          val empty  = Patch.empty[Person]
          val result = empty(value, PatchMode.Strict)

          assertTrue(result == Right(value))
        }
      },
      test("empty patch is true identity (no side effects) - Vector[Int]") {
        check(Gen.listOfBounded(0, 10)(Gen.int).map(_.toVector)) { value =>
          val empty  = Patch.empty[Vector[Int]]
          val result = empty(value, PatchMode.Strict)

          assertTrue(result == Right(value))
        }
      },
      test("diff(x, x) produces empty patch - Int") {
        check(Gen.int) { value =>
          val schema = Schema[Int]
          val patch  = schema.diff(value, value)

          assertTrue(patch.isEmpty)
        }
      },
      test("diff(x, x) produces empty patch - Person") {
        check(genPerson) { value =>
          val schema = Schema[Person]
          val patch  = schema.diff(value, value)

          assertTrue(patch.isEmpty)
        }
      },
      test("diff(x, x) produces empty patch - Vector[Int]") {
        check(Gen.listOfBounded(0, 10)(Gen.int).map(_.toVector)) { value =>
          val schema = Schema[Vector[Int]]
          val patch  = schema.diff(value, value)

          assertTrue(patch.isEmpty)
        }
      }
    ),
    suite("Patch Composition Determinism")(
      test("composing same patches multiple times yields same result - Int") {
        check(Gen.int, Gen.int, Gen.int) { (v0, v1, v2) =>
          val schema = Schema[Int]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)

          val composed1 = p1 ++ p2
          val composed2 = p1 ++ p2

          val result1 = composed1(v0, PatchMode.Strict)
          val result2 = composed2(v0, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("composing same patches multiple times yields same result - Person") {
        check(genPerson, genPerson, genPerson) { (v0, v1, v2) =>
          val schema = Schema[Person]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)

          val composed1 = p1 ++ p2
          val composed2 = p1 ++ p2

          val result1 = composed1(v0, PatchMode.Strict)
          val result2 = composed2(v0, PatchMode.Strict)

          assertTrue(result1 == result2)
        }
      }
    ),
    suite("Sequential Composition Roundtrip")(
      test("composing sequential diffs preserves final value - Int") {
        check(Gen.int, Gen.int, Gen.int) { (v0, v1, v2) =>
          val schema = Schema[Int]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, PatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      },
      test("composing sequential diffs preserves final value - Person") {
        check(genPerson, genPerson, genPerson) { (v0, v1, v2) =>
          val schema = Schema[Person]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, PatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      },
      test("composing sequential diffs preserves final value - Vector[Int]") {
        check(
          Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector),
          Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector),
          Gen.listOfBounded(0, 5)(Gen.int).map(_.toVector)
        ) { (v0, v1, v2) =>
          val schema = Schema[Vector[Int]]
          val p1     = schema.diff(v0, v1)
          val p2     = schema.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, PatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      }
    ),
    suite("Edge Cases - Roundtrip Law")(
      test("roundtrip with empty strings") {
        check(Gen.const(""), Gen.alphaNumericStringBounded(0, 20)) { (old, new_) =>
          val schema = Schema[String]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with empty to empty string") {
        val schema = Schema[String]
        val patch  = schema.diff("", "")
        val result = patch("", PatchMode.Strict)
        assertTrue(result == Right("") && patch.isEmpty)
      },
      test("roundtrip with empty sequences") {
        check(Gen.const(Vector.empty[Int]), Gen.listOfBounded(0, 10)(Gen.int).map(_.toVector)) { (old, new_) =>
          val schema = Schema[Vector[Int]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with empty maps") {
        val genMap = Gen
          .listOfBounded(0, 5) {
            for {
              key   <- Gen.alphaNumericStringBounded(1, 10)
              value <- Gen.int
            } yield key -> value
          }
          .map(_.toMap)

        check(Gen.const(Map.empty[String, Int]), genMap) { (old, new_) =>
          val schema = Schema[Map[String, Int]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with very large integers") {
        check(Gen.const(Int.MaxValue - 100), Gen.const(Int.MaxValue)) { (old, new_) =>
          val schema = Schema[Int]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with very small integers") {
        check(Gen.const(Int.MinValue), Gen.const(Int.MinValue + 100)) { (old, new_) =>
          val schema = Schema[Int]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with extreme long values") {
        check(Gen.const(Long.MaxValue - 1000L), Gen.const(Long.MaxValue)) { (old, new_) =>
          val schema = Schema[Long]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with Float special values") {
        val schema = Schema[Float]

        // Positive infinity
        val p1 = schema.diff(0.0f, Float.PositiveInfinity)
        val r1 = p1(0.0f, PatchMode.Strict)

        // Negative infinity
        val p2 = schema.diff(0.0f, Float.NegativeInfinity)
        val r2 = p2(0.0f, PatchMode.Strict)

        // Max value
        val p3 = schema.diff(0.0f, Float.MaxValue)
        val r3 = p3(0.0f, PatchMode.Strict)

        // Min value
        val p4 = schema.diff(0.0f, Float.MinValue)
        val r4 = p4(0.0f, PatchMode.Strict)

        assertTrue(
          r1 == Right(Float.PositiveInfinity) &&
            r2 == Right(Float.NegativeInfinity) &&
            r3 == Right(Float.MaxValue) &&
            r4 == Right(Float.MinValue)
        )
      },
      test("roundtrip with Double special values") {
        val schema = Schema[Double]

        // Positive infinity
        val p1 = schema.diff(0.0, Double.PositiveInfinity)
        val r1 = p1(0.0, PatchMode.Strict)

        // Negative infinity
        val p2 = schema.diff(0.0, Double.NegativeInfinity)
        val r2 = p2(0.0, PatchMode.Strict)

        // Very small number
        val p3 = schema.diff(0.0, Double.MinPositiveValue)
        val r3 = p3(0.0, PatchMode.Strict)

        assertTrue(
          r1 == Right(Double.PositiveInfinity) &&
            r2 == Right(Double.NegativeInfinity) &&
            r3 == Right(Double.MinPositiveValue)
        )
      },
      test("roundtrip with NaN to NaN") {
        val schema = Schema[Double]
        val patch  = schema.diff(Double.NaN, Double.NaN)
        assertTrue(patch.isEmpty) // NaN == NaN is false, but diff should detect no change needed
      },
      test("roundtrip with unicode strings") {
        val old    = "Hello 世界"
        val new_   = "你好 World 🎉"
        val schema = Schema[String]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("roundtrip with deeply nested records") {
        case class Level3(value: Int)
        case class Level2(level3: Level3, x: String)
        case class Level1(level2: Level2, y: Int)
        implicit val level3Schema: Schema[Level3] = Schema.derived
        implicit val level2Schema: Schema[Level2] = Schema.derived
        implicit val level1Schema: Schema[Level1] = Schema.derived

        val old  = Level1(Level2(Level3(1), "a"), 10)
        val new_ = Level1(Level2(Level3(2), "b"), 20)

        val schema = Schema[Level1]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(new_))
      },
      test("roundtrip with sequence of records") {
        check(
          Gen.listOfBounded(0, 5)(genPerson).map(_.toVector),
          Gen.listOfBounded(0, 5)(genPerson).map(_.toVector)
        ) { (old, new_) =>
          val schema = Schema[Vector[Person]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with map of complex values") {
        case class ComplexValue(x: Int, y: String)
        implicit val complexValueSchema: Schema[ComplexValue] = Schema.derived

        val genComplexMap = Gen
          .listOfBounded(0, 3) {
            for {
              key <- Gen.alphaNumericStringBounded(1, 10)
              x   <- Gen.int
              y   <- Gen.alphaNumericStringBounded(1, 10)
            } yield key -> ComplexValue(x, y)
          }
          .map(_.toMap)

        check(genComplexMap, genComplexMap) { (old, new_) =>
          val schema = Schema[Map[String, ComplexValue]]
          val patch  = schema.diff(old, new_)
          val result = patch(old, PatchMode.Strict)
          assertTrue(result == Right(new_))
        }
      },
      test("roundtrip with variant switching between cases multiple times") {
        val schema     = Schema[Status]
        val v0: Status = Active("2023-01-01")
        val v1: Status = Inactive("maintenance")
        val v2: Status = Active("2023-12-31")
        val v3: Status = Inactive("upgrade")

        val p1 = schema.diff(v0, v1)
        val p2 = schema.diff(v1, v2)
        val p3 = schema.diff(v2, v3)

        val r1 = p1(v0, PatchMode.Strict)
        val r2 = p2(v1, PatchMode.Strict)
        val r3 = p3(v2, PatchMode.Strict)

        assertTrue(r1 == Right(v1) && r2 == Right(v2) && r3 == Right(v3))
      },
      test("roundtrip with Option[T] - None to Some") {
        case class WithOption(value: Option[Int])
        implicit val withOptionSchema: Schema[WithOption] = Schema.derived

        val old  = WithOption(None)
        val new_ = WithOption(Some(42))

        val schema = Schema[WithOption]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(new_))
      },
      test("roundtrip with Option[T] - Some to None") {
        case class WithOption(value: Option[Int])
        implicit val withOptionSchema: Schema[WithOption] = Schema.derived

        val old  = WithOption(Some(42))
        val new_ = WithOption(None)

        val schema = Schema[WithOption]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(new_))
      },
      test("roundtrip with BigInt extreme values") {
        val old    = BigInt("99999999999999999999999999999999999999")
        val new_   = BigInt("100000000000000000000000000000000000000")
        val schema = Schema[BigInt]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("roundtrip with BigDecimal extreme precision") {
        val old    = BigDecimal("3.141592653589793238462643383279502884197")
        val new_   = BigDecimal("2.718281828459045235360287471352662497757")
        val schema = Schema[BigDecimal]
        val patch  = schema.diff(old, new_)
        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(new_))
      }
    ),
    suite("Edge Cases - Monoid Laws")(
      test("monoid laws hold for empty collections") {
        val schema = Schema[Vector[Int]]
        val empty  = Vector.empty[Int]
        val p1     = schema.diff(empty, Vector(1, 2, 3))
        val p2     = schema.diff(Vector(1, 2, 3), Vector(1, 2, 3, 4, 5))

        val left  = (Patch.empty[Vector[Int]] ++ p1) ++ p2
        val right = Patch.empty[Vector[Int]] ++ (p1 ++ p2)

        val r1 = left(empty, PatchMode.Strict)
        val r2 = right(empty, PatchMode.Strict)

        assertTrue(r1 == r2)
      },
      test("associativity holds across different operation types") {
        val schema = Schema[Person]
        val v0     = Person("Alice", 30)
        val v1     = Person("Bob", 30)     // name change
        val v2     = Person("Bob", 35)     // age change
        val v3     = Person("Charlie", 35) // name change again

        val p1 = schema.diff(v0, v1)
        val p2 = schema.diff(v1, v2)
        val p3 = schema.diff(v2, v3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val r1 = left(v0, PatchMode.Strict)
        val r2 = right(v0, PatchMode.Strict)

        assertTrue(r1 == r2 && r1 == Right(v3))
      }
    ),
    suite("Operation.Patch Laws")(
      test("nested patch respects roundtrip law") {
        case class Address(street: String, city: String, zip: String)
        case class Person(name: String, address: Address)
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived

        val old  = Person("Alice", Address("123 Main", "NYC", "10001"))
        val new_ = Person("Alice", Address("456 Elm", "LA", "90002"))

        // Create a nested patch manually
        val addressPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("street"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("456 Elm")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("city"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("LA")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("zip"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("90002")))
            )
          )
        )

        val dynamicPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("address"))),
              DynamicPatch.Operation.Patch(addressPatch)
            )
          )
        )

        val patch = Patch(dynamicPatch, personSchema)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("nested patch composes with regular patches") {
        case class Stats(views: Int, likes: Int)
        case class Article(title: String, stats: Stats)
        implicit val statsSchema: Schema[Stats]     = Schema.derived
        implicit val articleSchema: Schema[Article] = Schema.derived

        val v0 = Article("Title 1", Stats(100, 10))
        val v1 = Article("Title 2", Stats(150, 15))
        val v2 = Article("Title 2", Stats(200, 20))

        // p1: change title using regular patch
        val p1 = articleSchema.diff(v0, v1)

        // p2: change stats using nested patch
        val statsPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("views"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(50))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("likes"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            )
          )
        )

        val p2DynamicPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("stats"))),
              DynamicPatch.Operation.Patch(statsPatch)
            )
          )
        )

        val p2 = Patch(p2DynamicPatch, articleSchema)

        // Compose and verify
        val composed = p1 ++ p2
        val result   = composed(v0, PatchMode.Strict)

        assertTrue(result == Right(v2))
      },
      test("nested patch respects associativity") {
        case class Inner(value: Int)
        case class Middle(inner: Inner, x: String)
        case class Outer(middle: Middle, y: Int)
        implicit val innerSchema: Schema[Inner]   = Schema.derived
        implicit val middleSchema: Schema[Middle] = Schema.derived
        implicit val outerSchema: Schema[Outer]   = Schema.derived

        val v0 = Outer(Middle(Inner(1), "a"), 10)
        val v1 = Outer(Middle(Inner(2), "a"), 10)
        val v2 = Outer(Middle(Inner(2), "b"), 10)
        val v3 = Outer(Middle(Inner(2), "b"), 20)

        val p1 = outerSchema.diff(v0, v1)
        val p2 = outerSchema.diff(v1, v2)
        val p3 = outerSchema.diff(v2, v3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val r1 = left(v0, PatchMode.Strict)
        val r2 = right(v0, PatchMode.Strict)

        assertTrue(r1 == r2 && r1 == Right(v3))
      },
      test("recursive nested patch respects roundtrip law") {
        case class Level3(value: Int)
        case class Level2(level3: Level3)
        case class Level1(level2: Level2)
        implicit val level3Schema: Schema[Level3] = Schema.derived
        implicit val level2Schema: Schema[Level2] = Schema.derived
        implicit val level1Schema: Schema[Level1] = Schema.derived

        val old  = Level1(Level2(Level3(1)))
        val new_ = Level1(Level2(Level3(100)))

        // Create 3-level nested patch
        val level3Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("value"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(100)))
            )
          )
        )

        val level2Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("level3"))),
              DynamicPatch.Operation.Patch(level3Patch)
            )
          )
        )

        val level1Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("level2"))),
              DynamicPatch.Operation.Patch(level2Patch)
            )
          )
        )

        val patch  = Patch(level1Patch, level1Schema)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(new_))
      },
      test("empty nested patch acts as identity") {
        case class Container(value: Int)
        implicit val containerSchema: Schema[Container] = Schema.derived

        val original = Container(42)

        // Empty nested patch
        val emptyNestedPatch = DynamicPatch(Chunk.empty)
        val dynamicPatch     = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("value"))),
              DynamicPatch.Operation.Patch(emptyNestedPatch)
            )
          )
        )

        val patch  = Patch(dynamicPatch, containerSchema)
        val result = patch(original, PatchMode.Strict)

        // Empty nested patch should leave value unchanged (apply empty patch to the field)
        assertTrue(result == Right(original))
      },
      test("nested patch with PatchMode.Lenient skips invalid operations") {
        case class Record(x: Int, y: Int)
        implicit val recordSchema: Schema[Record] = Schema.derived

        val original = Record(10, 20)

        // Nested patch with invalid field
        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("nonexistent"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(999)))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("x"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            )
          )
        )

        val dynamicPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val patch  = Patch(dynamicPatch, recordSchema)
        val result = patch(original, PatchMode.Lenient)

        // Should skip invalid operation and apply valid one
        assertTrue(result == Right(Record(15, 20)))
      }
    )
  )
}
