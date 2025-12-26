package zio.blocks.schema

import zio.test.*

object DerivedOpticsSpec extends ZIOSpecDefault {

  // ===== Test Types =====

  // Basic case class
  final case class Person(name: String, age: Int)
  object Person extends DerivedOptics[Person] {
    given schema: Schema[Person] = Schema.derived
  }

  // Nested case class
  final case class Address(street: String, city: String)
  object Address extends DerivedOptics[Address] {
    given schema: Schema[Address] = Schema.derived
  }

  final case class Employee(name: String, address: Address)
  object Employee extends DerivedOptics[Employee] {
    given schema: Schema[Employee] = Schema.derived
  }

  // Single field case class
  final case class Wrapper(value: Int)
  object Wrapper extends DerivedOptics[Wrapper] {
    given schema: Schema[Wrapper] = Schema.derived
  }

  // Optional fields
  final case class OptionalFields(required: String, optional: Option[Int])
  object OptionalFields extends DerivedOptics[OptionalFields] {
    given schema: Schema[OptionalFields] = Schema.derived
  }

  // Large record (10+ fields)
  final case class LargeRecord(
    f1: Int,
    f2: String,
    f3: Double,
    f4: Boolean,
    f5: Long,
    f6: Char,
    f7: Short,
    f8: Byte,
    f9: Float,
    f10: List[Int]
  )
  object LargeRecord extends DerivedOptics[LargeRecord] {
    given schema: Schema[LargeRecord] = Schema.derived
  }

  // Sealed trait with case classes and case object
  sealed trait Shape
  final case class Circle(radius: Double)                   extends Shape
  final case class Rectangle(width: Double, height: Double) extends Shape
  case object Point                                         extends Shape
  object Shape                                              extends DerivedOptics[Shape] {
    given schema: Schema[Shape] = Schema.derived
  }

  // Scala 3 enum
  enum Color {
    case Red, Green, Blue
    case Custom(r: Int, g: Int, b: Int)
  }
  object Color extends DerivedOptics[Color] {
    given schema: Schema[Color] = Schema.derived
  }

  // Nested sealed trait
  sealed trait Outer
  final case class OuterA(inner: Inner, value: Int) extends Outer
  final case class OuterB(value: String)            extends Outer
  object Outer                                      extends DerivedOptics[Outer] {
    given schema: Schema[Outer] = Schema.derived
  }

  sealed trait Inner
  final case class InnerX(x: Int)    extends Inner
  final case class InnerY(y: String) extends Inner
  object Inner                       extends DerivedOptics[Inner] {
    given schema: Schema[Inner] = Schema.derived
  }

  // Case class extending trait
  trait HasId { def id: Int }
  final case class Entity(id: Int, name: String) extends HasId
  object Entity                                  extends DerivedOptics[Entity] {
    given schema: Schema[Entity] = Schema.derived
  }

  // Case class with keyword field names
  final case class Keywords(`type`: String, `class`: Int, `val`: Boolean)
  object Keywords extends DerivedOptics[Keywords] {
    given schema: Schema[Keywords] = Schema.derived
  }

  // Empty case class
  final case class Empty()
  object Empty extends DerivedOptics[Empty] {
    given schema: Schema[Empty] = Schema.derived
  }

  // Case class with private constructor
  final case class Private private (value: Int)
  object Private extends DerivedOptics[Private] {
    given schema: Schema[Private] = Schema.derived
    def create(v: Int): Private   = Private(v)
  }

  // Generic case class (using concrete type for test)
  final case class Box[A](value: A)
  object BoxInt extends DerivedOptics[Box[Int]] {
    given boxIntSchema: Schema[Box[Int]] = Schema.derived
  }
  object BoxString extends DerivedOptics[Box[String]] {
    given boxStringSchema: Schema[Box[String]] = Schema.derived
  }

  // Generic sealed trait
  sealed trait GenericResult[T]
  final case class Success[T](value: T)    extends GenericResult[T]
  final case class Failure[T](msg: String) extends GenericResult[T]

  object ResultInt extends DerivedOptics[GenericResult[Int]] {
    given schema: Schema[GenericResult[Int]] = Schema.derived
  }
  object ResultString extends DerivedOptics[GenericResult[String]] {
    given schema: Schema[GenericResult[String]] = Schema.derived
  }

  // Schema derivation test helper
  final case class User(id: Int, email: String)
  object User {
    given schema: Schema[User] = Schema.derived
  }

  final case class TestPoint(x: Int, y: Int)
  object TestPoint {
    given schema: Schema[TestPoint] = Schema.derived
  }

  // ===== Type Alias Test Types =====

  // Pattern 1: Companion object uses DerivedOptics with a type alias of its own type
  // This tests: type P = Person; object Person extends DerivedOptics[P]
  final case class AliasedPerson(name: String, age: Int)
  type AP = AliasedPerson
  object AliasedPerson extends DerivedOptics[AP] {
    given schema: Schema[AP] = Schema.derived
  }

  // Pattern 2: Sealed trait with type alias
  sealed trait AliasedShape
  final case class AliasedCircle(radius: Double)                   extends AliasedShape
  final case class AliasedRectangle(width: Double, height: Double) extends AliasedShape
  type AS = AliasedShape
  object AliasedShape extends DerivedOptics[AS] {
    given schema: Schema[AS] = Schema.derived
  }

  // Pattern 3: Scala 3 enum with type alias
  enum AliasedColor {
    case Red, Green, Blue
    case Custom(r: Int, g: Int, b: Int)
  }
  type AC = AliasedColor
  object AliasedColor extends DerivedOptics[AC] {
    given schema: Schema[AC] = Schema.derived
  }

  // Pattern 4: Type alias for a variant (case class within sealed trait)
  type CircleAlias = Circle
  object CircleAlias extends DerivedOptics[CircleAlias] {
    given schema: Schema[CircleAlias] = Schema.derived
  }

  // Pattern 5: Type alias for a parameterized enum case
  type CustomColorAlias = Color.Custom
  object CustomColorAlias extends DerivedOptics[CustomColorAlias] {
    given schema: Schema[CustomColorAlias] = Schema.derived
  }

  // ===== Special Character (Backtick-Escaped) Test Types =====

  // Case class with backtick-escaped field names containing special characters
  final case class SpecialFields(`my funny name`: String, `field-with-dashes`: Int, `field.with.dots`: Boolean)
  object SpecialFields extends DerivedOptics[SpecialFields] {
    given schema: Schema[SpecialFields] = Schema.derived
  }

  // Sealed trait with backtick-escaped case names
  sealed trait SpecialCases
  final case class `my-special-case`(value: Int)        extends SpecialCases
  final case class `another.special.case`(name: String) extends SpecialCases
  case object `weird case name`                         extends SpecialCases
  object SpecialCases                                   extends DerivedOptics[SpecialCases] {
    given schema: Schema[SpecialCases] = Schema.derived
  }

  // ===== Opaque Type (Newtype) Test Types =====
  // Note: Opaque types require manually providing the schema since Schema.derived
  // cannot derive for primitive types. The macro should handle Reflect.Wrapper schemas.

  opaque type Age = Int
  object Age extends DerivedOptics[Age] {
    def apply(i: Int): Age            = i
    extension (a: Age) def value: Int = a
    // For opaque types, use wrapTotal to enable Lens derivation
    given schema: Schema[Age] = Schema.wrapTotal(Age.apply, _.value)
  }

  opaque type Email <: String = String
  object Email extends DerivedOptics[Email] {
    def apply(s: String): Email            = s
    extension (e: Email) def value: String = e
    // For opaque types with subtype bound, use wrapTotal
    given schema: Schema[Email] = Schema.wrapTotal(Email.apply, _.value)
  }

  // ===== Test Suites =====

  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsSpec")(
    lensTestSuite,
    prismTestSuite,
    enumTestSuite,
    typeAliasTestSuite,
    specialCharacterTestSuite,
    opaqueTypeTestSuite,
    cachingTestSuite,
    typeSafetyTestSuite,
    schemaTestSuite,
    edgeCasesTestSuite
  )

  // ===== Lens Tests =====
  val lensTestSuite: Spec[Any, Nothing] = suite("Lens generation for case classes")(
    test("get field value") {
      val person = Person("Alice", 30)
      assertTrue(Person.optics.name.get(person) == "Alice") &&
      assertTrue(Person.optics.age.get(person) == 30)
    },
    test("replace field value") {
      val person = Person("Alice", 30)
      assertTrue(Person.optics.name.replace(person, "Bob") == Person("Bob", 30)) &&
      assertTrue(Person.optics.age.replace(person, 25) == Person("Alice", 25))
    },
    test("modify field value") {
      val person = Person("Alice", 30)
      assertTrue(Person.optics.name.modify(person, _.toUpperCase) == Person("ALICE", 30)) &&
      assertTrue(Person.optics.age.modify(person, _ + 1) == Person("Alice", 31))
    },
    test("compose lenses for nested access") {
      val emp        = Employee("Alice", Address("123 Main", "Springfield"))
      val streetLens = Employee.optics.address.apply(Address.optics.street)
      assertTrue(streetLens.get(emp) == "123 Main") &&
      assertTrue(streetLens.replace(emp, "456 Oak") == Employee("Alice", Address("456 Oak", "Springfield")))
    },
    test("lens for single-field case class") {
      val w = Wrapper(42)
      assertTrue(Wrapper.optics.value.get(w) == 42) &&
      assertTrue(Wrapper.optics.value.replace(w, 100) == Wrapper(100))
    },
    test("lens for optional fields") {
      val withOpt    = OptionalFields("a", Some(1))
      val withoutOpt = OptionalFields("b", None)
      assertTrue(OptionalFields.optics.optional.get(withOpt) == Some(1)) &&
      assertTrue(OptionalFields.optics.optional.get(withoutOpt) == None)
    },
    test("lens for case class with many fields") {
      val record = LargeRecord(1, "a", 2.0, true, 3L, 'x', 4, 5, 6.0f, List(7, 8, 9))
      assertTrue(LargeRecord.optics.f1.get(record) == 1) &&
      assertTrue(LargeRecord.optics.f2.get(record) == "a") &&
      assertTrue(LargeRecord.optics.f3.get(record) == 2.0) &&
      assertTrue(LargeRecord.optics.f4.get(record) == true) &&
      assertTrue(LargeRecord.optics.f5.get(record) == 3L) &&
      assertTrue(LargeRecord.optics.f10.get(record) == List(7, 8, 9))
    },
    test("lens for case class extending trait") {
      val e = Entity(1, "test")
      assertTrue(Entity.optics.id.get(e) == 1) &&
      assertTrue(Entity.optics.name.get(e) == "test")
    },
    test("lens for fields with keyword names") {
      val kw = Keywords("test", 42, true)
      assertTrue(Keywords.optics.`type`.get(kw) == "test") &&
      assertTrue(Keywords.optics.`class`.get(kw) == 42) &&
      assertTrue(Keywords.optics.`val`.get(kw) == true)
    },
    test("lens for generic case class") {
      import BoxInt.boxIntSchema
      import BoxString.boxStringSchema
      val boxInt    = Box(42)
      val boxString = Box("hello")

      val intOptics    = BoxInt.optics
      val stringOptics = BoxString.optics

      // Verify optics work and don't collide
      assertTrue(intOptics.value.get(boxInt) == 42) &&
      assertTrue(stringOptics.value.get(boxString) == "hello") &&
      assertTrue(!(intOptics eq stringOptics)) // Should be different cached objects
    },
    test("prism for generic sealed trait") {
      import ResultInt.schema
      import ResultString.schema

      val intOptics = ResultInt.optics
      val strOptics = ResultString.optics

      val s1 = Success(42)
      val s2 = Success("hello")

      assertTrue(intOptics.Success.getOption(s1) == Some(Success(42))) &&
      assertTrue(strOptics.Success.getOption(s2) == Some(Success("hello"))) &&
      assertTrue(!(intOptics eq strOptics)) &&
      assertTrue(intOptics.Success eq intOptics.Success) // Test stability with eq
    }
  )

  // ===== Prism Tests =====
  val prismTestSuite: Spec[Any, Nothing] = suite("Prism generation for sealed traits")(
    test("getOption returns Some for matching variant") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.Circle.getOption(circle) == Some(Circle(5.0)))
    },
    test("getOption returns None for non-matching variant") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.Rectangle.getOption(circle) == None) &&
      assertTrue(Shape.optics.Point.getOption(circle) == None)
    },
    test("reverseGet constructs the variant") {
      val circle = Shape.optics.Circle.reverseGet(Circle(3.0))
      assertTrue(circle == Circle(3.0))
    },
    test("prism for case object") {
      val point: Shape = Point
      assertTrue(Shape.optics.Point.getOption(point) == Some(Point))
    },
    test("prism replace when matching") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.Circle.replace(circle, Circle(10.0)) == Circle(10.0))
    },
    test("prism replace when not matching returns original") {
      val rect: Shape = Rectangle(3.0, 4.0)
      assertTrue(Shape.optics.Circle.replace(rect, Circle(10.0)) == rect)
    },
    test("prism modify when matching") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.Circle.modify(circle, c => Circle(c.radius * 2)) == Circle(10.0))
    },
    test("prism for nested sealed traits") {
      val outer: Outer = OuterA(InnerX(42), 10)
      assertTrue(Outer.optics.OuterA.getOption(outer).isDefined) &&
      assertTrue(Outer.optics.OuterB.getOption(outer).isEmpty)
    }
  )

  // ===== Enum Tests (Scala 3 specific) =====
  val enumTestSuite: Spec[Any, Nothing] = suite("Prism generation for Scala 3 enums")(
    test("prism for simple enum cases") {
      val red: Color = Color.Red
      assertTrue(Color.optics.Red.getOption(red).isDefined) &&
      assertTrue(Color.optics.Green.getOption(red).isEmpty) &&
      assertTrue(Color.optics.Blue.getOption(red).isEmpty)
    },
    test("prism for parameterized enum case") {
      val custom: Color = Color.Custom(255, 128, 0)
      assertTrue(Color.optics.Custom.getOption(custom) == Some(Color.Custom(255, 128, 0)))
    },
    test("prism reverseGet for enum") {
      val green = Color.optics.Green.reverseGet(Color.Green)
      assertTrue(green == Color.Green)
    },
    test("prism modify for enum") {
      val custom: Color = Color.Custom(255, 128, 0)
      val modified      = Color.optics.Custom.modify(custom, c => Color.Custom(c.r / 2, c.g / 2, c.b))
      assertTrue(modified == Color.Custom(127, 64, 0))
    }
  )

  // ===== Type Alias Tests =====
  val typeAliasTestSuite: Spec[Any, Nothing] = suite("Type alias support")(
    test("lens works when companion uses type alias of its own type (case class)") {
      // Tests: type AP = AliasedPerson; object AliasedPerson extends DerivedOptics[AP]
      val person: AP = AliasedPerson("Alice", 30)
      assertTrue(AliasedPerson.optics.name.get(person) == "Alice") &&
      assertTrue(AliasedPerson.optics.age.get(person) == 30) &&
      assertTrue(AliasedPerson.optics.name.replace(person, "Bob") == AliasedPerson("Bob", 30))
    },
    test("prism works when companion uses type alias of its own type (sealed trait)") {
      // Tests: type AS = AliasedShape; object AliasedShape extends DerivedOptics[AS]
      val circle: AS = AliasedCircle(5.0)
      val rect: AS   = AliasedRectangle(3.0, 4.0)
      assertTrue(AliasedShape.optics.AliasedCircle.getOption(circle) == Some(AliasedCircle(5.0))) &&
      assertTrue(AliasedShape.optics.AliasedRectangle.getOption(rect) == Some(AliasedRectangle(3.0, 4.0))) &&
      assertTrue(AliasedShape.optics.AliasedCircle.getOption(rect) == None)
    },
    test("prism works when companion uses type alias of its own type (Scala 3 enum)") {
      // Tests: type AC = AliasedColor; object AliasedColor extends DerivedOptics[AC]
      val red: AC    = AliasedColor.Red
      val custom: AC = AliasedColor.Custom(255, 0, 0)
      assertTrue(AliasedColor.optics.Red.getOption(red).isDefined) &&
      assertTrue(AliasedColor.optics.Custom.getOption(custom) == Some(AliasedColor.Custom(255, 0, 0))) &&
      assertTrue(AliasedColor.optics.Green.getOption(red) == None)
    },
    test("lens works with type alias for variant (case class in sealed trait)") {
      import CircleAlias.schema
      val circle: CircleAlias = Circle(7.5)
      assertTrue(CircleAlias.optics.radius.get(circle) == 7.5) &&
      assertTrue(CircleAlias.optics.radius.replace(circle, 10.0) == Circle(10.0))
    },
    test("lens works with type alias for parameterized enum case") {
      import CustomColorAlias.schema
      val custom: CustomColorAlias = Color.Custom(100, 150, 200)
      assertTrue(CustomColorAlias.optics.r.get(custom) == 100) &&
      assertTrue(CustomColorAlias.optics.g.get(custom) == 150) &&
      assertTrue(CustomColorAlias.optics.b.get(custom) == 200) &&
      assertTrue(CustomColorAlias.optics.r.replace(custom, 50) == Color.Custom(50, 150, 200))
    }
  )

  // ===== Special Character (Backtick-Escaped) Tests =====
  val specialCharacterTestSuite: Spec[Any, Nothing] = suite("Special character (backtick-escaped) names")(
    test("macro derivation succeeds for case class with backtick-escaped field names") {
      // The key test is that this compiles and the optics object is created without error
      val sf     = SpecialFields("hello", 42, true)
      val optics = SpecialFields.optics
      assertTrue(optics != null && sf.`my funny name` == "hello")
    },
    test("macro derivation succeeds for sealed trait with backtick-escaped case names") {
      // The key test is that this compiles and the optics object is created without error
      val case1: SpecialCases = `my-special-case`(10)
      val optics              = SpecialCases.optics
      assertTrue(optics != null && case1 == `my-special-case`(10))
    }
  )

  // ===== Opaque Type (Newtype) Tests =====
  // Note: For opaque types, the schema must be provided manually.
  // The derived schema will be a primitive or Wrapper, not a Record.
  val opaqueTypeTestSuite: Spec[Any, Nothing] = suite("Opaque type (newtype) support")(
    test("opaque type with Lens works") {
      val age = Age(25)
      // Verify that 'value' lens exists and works
      assertTrue(Age.optics.value.get(age) == 25) &&
      assertTrue(Age.optics.value.replace(age, 30) == 30)
    },
    test("opaque type with subtype bound and Lens works") {
      val email = Email("test@example.com")
      // Verify that 'value' lens exists and works
      assertTrue(Email.optics.value.get(email) == "test@example.com") &&
      assertTrue(Email.optics.value.replace(email, "changed@example.com") == "changed@example.com")
    }
  )

  // ===== Caching Tests =====
  val cachingTestSuite: Spec[Any, Nothing] = suite("Caching behavior")(
    test("optics object is cached (referential equality)") {
      val optics1 = Person.optics
      val optics2 = Person.optics
      assertTrue(optics1 eq optics2)
    },
    test("lens from cached optics is stable (referential equality)") {
      val lens1 = Person.optics.name
      val lens2 = Person.optics.name
      // The lenses MUST be the same reference
      assertTrue(lens1 eq lens2)
    },
    test("different types have different cached objects") {
      val personOptics  = Person.optics
      val addressOptics = Address.optics
      // They should be different objects
      assertTrue(!(personOptics eq addressOptics))
    }
  )

  // ===== Type Safety Tests =====
  val typeSafetyTestSuite: Spec[Any, Nothing] = suite("Type safety")(
    test("lens has correct types (compile-time check)") {
      // These lines verify correct types at compile time
      val nameLens: Lens[Person, String] = Person.optics.name
      val ageLens: Lens[Person, Int]     = Person.optics.age
      // Verify we can use the lens
      val person = Person("Test", 25)
      assertTrue(nameLens.get(person) == "Test" && ageLens.get(person) == 25)
    },
    test("prism has correct types (compile-time check)") {
      val circlePrism: Prism[Shape, Circle]       = Shape.optics.Circle
      val rectanglePrism: Prism[Shape, Rectangle] = Shape.optics.Rectangle
      // Verify we can use the prism
      val s1: Shape = Circle(1.0)
      val s2: Shape = Rectangle(2.0, 3.0)
      assertTrue(circlePrism.getOption(s1).isDefined && rectanglePrism.getOption(s2).isDefined)
    },
    test("nested lens composition maintains correct types") {
      val streetLens: Lens[Employee, String] = Employee.optics.address.apply(Address.optics.street)
      val emp                                = Employee("Test", Address("Main", "City"))
      assertTrue(streetLens.get(emp) == "Main")
    },
    test("structural type is fully refined") {
      // This tests that we get IDE completion and compile-time checking
      val optics = Person.optics
      // optics.nonexistent // Would NOT compile - no such field
      assertTrue(optics.name.get(Person("Test", 1)) == "Test")
    }
  )

  // ===== Schema Tests =====
  val schemaTestSuite: Spec[Any, Nothing] = suite("Schema.derived")(
    test("generates Schema for case class") {
      val sch = summon[Schema[User]]

      assertTrue(sch.reflect.typeName.name == "User") &&
      assertTrue(sch.reflect.asRecord.isDefined)
    },
    test("schema can convert to and from DynamicValue") {
      val sch      = summon[Schema[TestPoint]]
      val point    = TestPoint(10, 20)
      val dynamic  = sch.toDynamicValue(point)
      val restored = sch.fromDynamicValue(dynamic)

      assertTrue(restored == Right(point))
    }
  )

  // ===== Edge Cases =====
  val edgeCasesTestSuite: Spec[Any, Nothing] = suite("Edge cases")(
    test("multiple field modifications are independent") {
      val person   = Person("Alice", 30)
      val renamed  = Person.optics.name.replace(person, "Bob")
      val reaged   = Person.optics.age.replace(person, 25)
      val original = person

      assertTrue(renamed == Person("Bob", 30)) &&
      assertTrue(reaged == Person("Alice", 25)) &&
      assertTrue(original == Person("Alice", 30))
    },
    test("lens modify preserves other fields") {
      val record   = LargeRecord(1, "a", 2.0, true, 3L, 'x', 4, 5, 6.0f, List(7, 8, 9))
      val modified = LargeRecord.optics.f1.replace(record, 100)

      assertTrue(modified.f1 == 100) &&
      assertTrue(modified.f2 == record.f2) &&
      assertTrue(modified.f3 == record.f3) &&
      assertTrue(modified.f10 == record.f10)
    },
    test("prism works with all sealed trait variants") {
      val shapes: List[Shape] = List(Circle(1.0), Rectangle(2.0, 3.0), Point)

      val circles    = shapes.flatMap(Shape.optics.Circle.getOption)
      val rectangles = shapes.flatMap(Shape.optics.Rectangle.getOption)
      val points     = shapes.flatMap(Shape.optics.Point.getOption)

      assertTrue(circles == List(Circle(1.0))) &&
      assertTrue(rectangles == List(Rectangle(2.0, 3.0))) &&
      assertTrue(points == List(Point))
    },
    test("deeply nested lens composition") {
      // Test Employee -> address -> street
      val emp = Employee("Alice", Address("Main St", "NYC"))

      val addressLens = Employee.optics.address
      val streetLens  = Address.optics.street
      val cityLens    = Address.optics.city

      val combinedStreet = addressLens.apply(streetLens)
      val combinedCity   = addressLens.apply(cityLens)

      assertTrue(combinedStreet.get(emp) == "Main St") &&
      assertTrue(combinedCity.get(emp) == "NYC") &&
      assertTrue(combinedStreet.replace(emp, "Oak Ave").address.street == "Oak Ave")
    },
    test("empty case class produces empty optics") {
      val empty = Empty()
      // Should compile but have no fields to access
      val _ = Empty.optics
      assertTrue(empty == Empty())
    },
    test("case class with private constructor") {
      val p = Private.create(42)
      assertTrue(Private.optics.value.get(p) == 42) &&
      assertTrue(Private.optics.value.replace(p, 100).value == 100)
    }
  )
}
