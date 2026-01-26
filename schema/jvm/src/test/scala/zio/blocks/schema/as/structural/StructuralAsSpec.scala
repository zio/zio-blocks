package zio.blocks.schema.as.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/** JVM-only tests for As with pure structural types (requires reflection). */
object StructuralAsSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)
  case class Employee(name: String, age: Int, department: String)

  case class PersonWithOptDept(name: String, age: Int, department: Option[String])

  case class PersonWithDefaultDept(name: String, age: Int, department: String = "Unknown")

  def spec: Spec[TestEnvironment, Any] = suite("StructuralAsJVMOnlySpec")(
    suite("As with Pure Structural Types (JVM Only)")(
      test("As between case class and pure structural type") {
        val as       = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Carol", 35)

        val toResult = as.into(original)

        toResult match {
          case Right(r) =>
            assertTrue(r.name == "Carol", r.age == 35)
          case Left(err) =>
            assertTrue(err.toString == "should not fail")
        }
      },
      test("As[Person, Structural] round-trip works") {
        val as       = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Dave", 40)

        val roundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("As[Point, Structural] round-trip works") {
        val as       = As.derived[Point, { def x: Int; def y: Int }]
        val original = Point(100, 200)

        val roundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("As with subset of fields fails to compile for non-optional fields") {
        // This test verifies that the code DOES NOT compile.
        // The As.derived[Employee, { def name: String; def age: Int }] call should fail
        // because 'department' is neither Optional nor has a default.
        //
        // We test this using typeCheck which returns Left if compilation fails.
        // Note: We use the Employee class defined at module level to ensure proper macro expansion.
        for {
          // Test the reverse Into direction which should fail
          intoChecked <- typeCheck(
                           """
            import zio.blocks.schema._
            import zio.blocks.schema.as.structural.StructuralAsSpec.Employee
            Into.derived[{ def name: String; def age: Int }, Employee]
            """
                         )
          // Also test the As which should fail for the same reason
          asChecked <- typeCheck(
                         """
            import zio.blocks.schema._
            import zio.blocks.schema.as.structural.StructuralAsSpec.Employee
            As.derived[Employee, { def name: String; def age: Int }]
            """
                       )
        } yield assertTrue(intoChecked.isLeft, asChecked.isLeft)
      }
    ),
    suite("As with Optional fields (JVM Only)")(
      test("As works when source has extra Optional field") {
        val as       = As.derived[PersonWithOptDept, { def name: String; def age: Int }]
        val original = PersonWithOptDept("Alice", 30, Some("Engineering"))

        val roundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        // After round-trip, department becomes None
        assert(roundTrip)(isRight(equalTo(PersonWithOptDept("Alice", 30, None))))
      },
      test("As works when source Optional field is None") {
        val as       = As.derived[PersonWithOptDept, { def name: String; def age: Int }]
        val original = PersonWithOptDept("Bob", 25, None)

        val roundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("As structural round-trip chains (JVM Only)")(
      test("Person ↔ Structural round-trip") {
        val as       = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Frank", 45)

        // Person → Structural → Person
        val personRoundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        // Structural → Person → Structural
        val struct              = as.into(original).toOption.get
        val structuralRoundTrip = for {
          person     <- as.from(struct)
          structBack <- as.into(person)
        } yield structBack

        assert(personRoundTrip)(isRight(equalTo(original))) &&
        assert(structuralRoundTrip.map(s => (s.name, s.age)))(isRight(equalTo((struct.name, struct.age))))
      },
      test("Point ↔ Structural round-trip") {
        val as       = As.derived[Point, { def x: Int; def y: Int }]
        val original = Point(15, 25)

        // Point → Structural → Point
        val pointRoundTrip = for {
          struct <- as.into(original)
          back   <- as.from(struct)
        } yield back

        // Structural → Point → Structural
        val struct              = as.into(original).toOption.get
        val structuralRoundTrip = for {
          point      <- as.from(struct)
          structBack <- as.into(point)
        } yield structBack

        assert(pointRoundTrip)(isRight(equalTo(original))) &&
        assert(structuralRoundTrip.map(s => (s.x, s.y)))(isRight(equalTo((struct.x, struct.y))))
      }
    ),
    suite("Deep nested structural types (JVM Only)")(
      test("case class with nested case class - all fields round-trip") {
        case class Inner(x: Int, y: Int)
        case class Outer(name: String, inner: Inner)
        val as       = As.derived[Outer, { def name: String; def inner: { def x: Int; def y: Int } }]
        val original = Outer("test", Inner(1, 2))
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
      },
      test("multiple primitive types round-trip") {
        case class Multi(s: String, i: Int, l: Long, b: Boolean)
        val as       = As.derived[Multi, { def s: String; def i: Int; def l: Long; def b: Boolean }]
        val original = Multi("x", 1, 2L, true)
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
      },
      test("3-level nested structural type round-trip") {
        case class Level3(value: Int)
        case class Level2(name: String, level3: Level3)
        case class Level1(id: Long, level2: Level2)

        type Struct = {
          def id: Long
          def level2: {
            def name: String
            def level3: { def value: Int }
          }
        }

        val as       = As.derived[Level1, Struct]
        val original = Level1(1L, Level2("test", Level3(42)))

        // Level1 → Struct → Level1
        val level1RoundTrip = as.into(original).flatMap(as.from)

        // Struct → Level1 → Struct
        val struct              = as.into(original).toOption.get
        val structuralRoundTrip = as.from(struct).flatMap(as.into)

        assert(level1RoundTrip)(isRight(equalTo(original))) &&
        assert(structuralRoundTrip.map(s => (s.id, s.level2.name, s.level2.level3.value)))(
          isRight(equalTo((struct.id, struct.level2.name, struct.level2.level3.value)))
        )
      },
      test("4-level nested structural type round-trip") {
        case class Level4(code: String)
        case class Level3(value: Int, level4: Level4)
        case class Level2(name: String, level3: Level3)
        case class Level1(id: Long, level2: Level2)

        type Struct = {
          def id: Long
          def level2: {
            def name: String
            def level3: {
              def value: Int
              def level4: { def code: String }
            }
          }
        }

        val as       = As.derived[Level1, Struct]
        val original = Level1(1L, Level2("test", Level3(42, Level4("ABC"))))

        // Level1 → Struct → Level1
        val level1RoundTrip = as.into(original).flatMap(as.from)

        // Struct → Level1 → Struct
        val struct              = as.into(original).toOption.get
        val structuralRoundTrip = as.from(struct).flatMap(as.into)

        assert(level1RoundTrip)(isRight(equalTo(original))) &&
        assert(
          structuralRoundTrip.map(s => (s.id, s.level2.name, s.level2.level3.value, s.level2.level3.level4.code))
        )(
          isRight(
            equalTo(
              (struct.id, struct.level2.name, struct.level2.level3.value, struct.level2.level3.level4.code)
            )
          )
        )
      }
    )
  )
}
