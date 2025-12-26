package zio.blocks.schema

import scala.language.reflectiveCalls
import zio.test._

object DerivedOpticsSpec extends ZIOSpecDefault {

  // ===== Test Types =====

  // Basic case class
  final case class Person(name: String, age: Int)
  object Person extends DerivedOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
  }

  // Nested case class
  final case class Address(street: String, city: String)
  object Address extends DerivedOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
  }

  final case class Employee(name: String, address: Address)
  object Employee extends DerivedOptics[Employee] {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  // Single field case class
  final case class Wrapper(value: Int)
  object Wrapper extends DerivedOptics[Wrapper] {
    implicit val schema: Schema[Wrapper] = Schema.derived
  }

  // Optional fields
  final case class OptionalFields(required: String, optional: Option[Int])
  object OptionalFields extends DerivedOptics[OptionalFields] {
    implicit val schema: Schema[OptionalFields] = Schema.derived
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
    implicit val schema: Schema[LargeRecord] = Schema.derived
  }

  // Sealed trait with case classes and case object
  sealed trait Shape
  final case class Circle(radius: Double)                   extends Shape
  final case class Rectangle(width: Double, height: Double) extends Shape
  case object Point                                         extends Shape
  object Shape                                              extends DerivedOptics[Shape] {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  // Nested sealed trait
  sealed trait Outer
  final case class OuterA(inner: Inner, value: Int) extends Outer
  final case class OuterB(value: String)            extends Outer
  object Outer                                      extends DerivedOptics[Outer] {
    implicit val schema: Schema[Outer] = Schema.derived
  }

  sealed trait Inner
  final case class InnerX(x: Int)    extends Inner
  final case class InnerY(y: String) extends Inner
  object Inner                       extends DerivedOptics[Inner] {
    implicit val schema: Schema[Inner] = Schema.derived
  }

  // Case class extending trait
  trait HasId { def id: Int }
  final case class Entity(id: Int, name: String) extends HasId
  object Entity                                  extends DerivedOptics[Entity] {
    implicit val schema: Schema[Entity] = Schema.derived
  }

  // Case class with keyword field names
  final case class Keywords(`type`: String, `class`: Int, `val`: Boolean)
  object Keywords extends DerivedOptics[Keywords] {
    implicit val schema: Schema[Keywords] = Schema.derived
  }

  // Empty case class
  final case class Empty()
  object Empty extends DerivedOptics[Empty] {
    implicit val schema: Schema[Empty] = Schema.derived
  }

  // ===== Field Named 'optics' Collision Test =====
  final case class OpticsCollision(optics: String, other: Int)
  object OpticsCollision extends DerivedOptics[OpticsCollision] {
    implicit val schema: Schema[OpticsCollision] = Schema.derived
  }

  // Case class with private constructor
  final case class Private private (value: Int)
  object Private extends DerivedOptics[Private] {
    implicit val schema: Schema[Private] = Schema.derived
    def create(v: Int): Private          = Private(v)
  }

  // Generic case class (using concrete type for test)
  final case class Box[A](value: A)
  object BoxInt extends DerivedOptics[Box[Int]] {
    implicit val boxIntSchema: Schema[Box[Int]] = Schema.derived
  }
  object BoxString extends DerivedOptics[Box[String]] {
    implicit val boxStringSchema: Schema[Box[String]] = Schema.derived
  }

  // Generic sealed trait
  sealed trait GenericResult[T]
  final case class Success[T](value: T)    extends GenericResult[T]
  final case class Failure[T](msg: String) extends GenericResult[T]

  object ResultInt extends DerivedOptics[GenericResult[Int]] {
    implicit val successSchema: Schema[Success[Int]] = Schema.derived
    implicit val failureSchema: Schema[Failure[Int]] = Schema.derived
    implicit val schema: Schema[GenericResult[Int]]  = Schema.derived
  }
  object ResultString extends DerivedOptics[GenericResult[String]] {
    implicit val successSchema: Schema[Success[String]] = Schema.derived
    implicit val failureSchema: Schema[Failure[String]] = Schema.derived
    implicit val schema: Schema[GenericResult[String]]  = Schema.derived
  }

  // Schema derivation test helper
  final case class User(id: Int, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  final case class TestPoint(x: Int, y: Int)
  object TestPoint {
    implicit val schema: Schema[TestPoint] = Schema.derived
  }

  // ===== Type Alias Tests =====
  // Pattern 1: Case class with type alias passed to DerivedOptics
  type AP = AliasedPerson
  final case class AliasedPerson(name: String, age: Int)
  object AliasedPerson extends DerivedOptics[AP] {
    implicit val schema: Schema[AP] = Schema.derived
  }

  // Pattern 2: Sealed trait with type alias
  type AS = AliasedShape
  sealed trait AliasedShape
  final case class AliasedCircle(radius: Double)                   extends AliasedShape
  final case class AliasedRectangle(width: Double, height: Double) extends AliasedShape
  object AliasedShape                                              extends DerivedOptics[AS] {
    implicit val schema: Schema[AliasedShape] = Schema.derived
  }

  // Pattern 3: Type alias for a variant (case class in sealed trait)
  type AV = AliasedCircle
  object AliasedCircleAlias extends DerivedOptics[AV] {
    implicit val schema: Schema[AliasedCircle] = Schema.derived
  }

  // Pattern 4: Nested type alias
  type AA  = AliasedPerson
  type AAA = AA
  object TripleAliasedPerson extends DerivedOptics[AAA] {
    implicit val schema: Schema[AliasedPerson] = Schema.derived
  }

  // Pattern 5: Type alias for another variant
  type AR = AliasedRectangle
  object AliasedRectangleAlias extends DerivedOptics[AR] {
    implicit val schema: Schema[AliasedRectangle] = Schema.derived
  }

  // ===== Special Character Tests =====
  // Case class with backtick-escaped field names containing special characters
  final case class SpecialFields(`my funny name`: String, `field-with-dashes`: Int)
  object SpecialFields extends DerivedOptics[SpecialFields] {
    implicit val schema: Schema[SpecialFields] = Schema.derived
  }

  // Sealed trait with backtick-escaped case names
  sealed trait SpecialCases
  final case class `my-special-case`(value: Int)       extends SpecialCases
  final case class `another.special.case`(msg: String) extends SpecialCases
  case object `weird case name`                        extends SpecialCases
  object SpecialCases                                  extends DerivedOptics[SpecialCases] {
    implicit val schema: Schema[SpecialCases] = Schema.derived
  }

  // DerivedOptics_ (underscore prefix) test type
  case class PersonUnderscore(name: String, age: Int)
  object PersonUnderscore extends DerivedOptics_[PersonUnderscore] {
    implicit val schema: Schema[PersonUnderscore] = Schema.derived
  }

  // ===== Traversal Test Types =====
  final case class ListContainer(items: List[Int])
  object ListContainer extends DerivedOptics[ListContainer] {
    implicit val schema: Schema[ListContainer] = Schema.derived
  }

  final case class OptionContainer(maybe: Option[String])
  object OptionContainer extends DerivedOptics[OptionContainer] {
    implicit val schema: Schema[OptionContainer] = Schema.derived
  }

  // ===== Recursive Type =====
  final case class Tree(value: Int, children: List[Tree])
  object Tree extends DerivedOptics[Tree] {
    implicit val schema: Schema[Tree] = Schema.derived
  }

  // ===== Default Parameters =====
  final case class WithDefaults(required: String, optional: Int = 42)
  object WithDefaults extends DerivedOptics[WithDefaults] {
    implicit val schema: Schema[WithDefaults] = Schema.derived
  }

  // ===== External Type Alias Pattern =====
  final case class ExternalPerson(name: String, age: Int)
  type EP = ExternalPerson
  object ExternalPerson extends DerivedOptics[EP] {
    implicit val schema: Schema[ExternalPerson] = Schema.derived
  }

  // ===== Test Suites =====

  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsSpec")(
    lensTestSuite,
    prismTestSuite,
    typeAliasTestSuite,
    specialCharacterTestSuite,
    underscorePrefixTestSuite,
    cachingTestSuite,
    typeSafetyTestSuite,
    schemaTestSuite,
    edgeCasesTestSuite,
    traversalTestSuite,
    compositionTestSuite,
    definitionOfDoneTestSuite
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

      val intOptics    = BoxInt.optics
      val stringOptics = BoxString.optics

      val boxInt    = Box(42)
      val boxString = Box("hello")

      // Verify optics work and don't collide
      val i = intOptics.value.get(boxInt).asInstanceOf[Int]
      val s = stringOptics.value.get(boxString).asInstanceOf[String]

      assertTrue(i == 42) &&
      assertTrue(s == "hello") &&
      assertTrue(!(intOptics eq stringOptics)) &&
      assertTrue(intOptics.value eq intOptics.value) // Test stability with eq
    },
    test("prism for generic sealed trait") {
      implicit val intSchema: Schema[GenericResult[Int]]    = ResultInt.schema
      implicit val strSchema: Schema[GenericResult[String]] = ResultString.schema

      val intOptics = ResultInt.optics
      val strOptics = ResultString.optics

      val s1: GenericResult[Int]    = Success(42)
      val s2: GenericResult[String] = Success("hello")

      assertTrue(intOptics.success.getOption(s1) == Some(Success(42))) &&
      assertTrue(strOptics.success.getOption(s2) == Some(Success("hello"))) &&
      assertTrue(!(intOptics eq strOptics)) &&
      assertTrue(intOptics.success eq intOptics.success) // Test stability with eq
    }
  )

  // ===== Prism Tests =====
  val prismTestSuite: Spec[Any, Nothing] = suite("Prism generation for sealed traits")(
    test("getOption returns Some for matching variant") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.circle.getOption(circle) == Some(Circle(5.0)))
    },
    test("getOption returns None for non-matching variant") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.rectangle.getOption(circle) == None) &&
      assertTrue(Shape.optics.point.getOption(circle) == None)
    },
    test("reverseGet constructs the variant") {
      val circle = Shape.optics.circle.reverseGet(Circle(3.0))
      assertTrue(circle == Circle(3.0))
    },
    test("prism for case object") {
      val point: Shape = Point
      assertTrue(Shape.optics.point.getOption(point) == Some(Point))
    },
    test("prism replace when matching") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.circle.replace(circle, Circle(10.0)) == Circle(10.0))
    },
    test("prism replace when not matching returns original") {
      val rect: Shape = Rectangle(3.0, 4.0)
      assertTrue(Shape.optics.circle.replace(rect, Circle(10.0)) == rect)
    },
    test("prism modify when matching") {
      val circle: Shape = Circle(5.0)
      assertTrue(Shape.optics.circle.modify(circle, c => Circle(c.radius * 2)) == Circle(10.0))
    },
    test("prism for nested sealed traits") {
      val outer: Outer = OuterA(InnerX(42), 10)
      assertTrue(Outer.optics.outerA.getOption(outer).isDefined) &&
      assertTrue(Outer.optics.outerB.getOption(outer).isEmpty)
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
      val circlePrism: Prism[Shape, Circle]       = Shape.optics.circle
      val rectanglePrism: Prism[Shape, Rectangle] = Shape.optics.rectangle
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
      val sch = implicitly[Schema[User]]

      assertTrue(sch.reflect.typeName.name == "User") &&
      assertTrue(sch.reflect.asRecord.isDefined)
    },
    test("schema can convert to and from DynamicValue") {
      val sch      = implicitly[Schema[TestPoint]]
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

      val circles    = shapes.flatMap(Shape.optics.circle.getOption)
      val rectangles = shapes.flatMap(Shape.optics.rectangle.getOption)
      val points     = shapes.flatMap(Shape.optics.point.getOption)

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
      assertTrue(AliasedShape.optics.aliasedCircle.getOption(circle) == Some(AliasedCircle(5.0))) &&
      assertTrue(AliasedShape.optics.aliasedRectangle.getOption(rect) == Some(AliasedRectangle(3.0, 4.0))) &&
      assertTrue(AliasedShape.optics.aliasedCircle.getOption(rect) == None)
    },
    test("lens works with type alias for variant (case class in sealed trait)") {
      import AliasedCircleAlias.schema
      val circle: AV = AliasedCircle(7.5)
      assertTrue(AliasedCircleAlias.optics.radius.get(circle) == 7.5) &&
      assertTrue(AliasedCircleAlias.optics.radius.replace(circle, 10.0) == AliasedCircle(10.0))
    },
    test("lens works with nested type aliases") {
      import TripleAliasedPerson.schema
      val person: AAA = AliasedPerson("Alice", 30)
      assertTrue(TripleAliasedPerson.optics.name.get(person) == "Alice")
    },
    test("lens works with another type alias for variant") {
      import AliasedRectangleAlias.schema
      val rect: AR = AliasedRectangle(10.0, 20.0)
      assertTrue(AliasedRectangleAlias.optics.width.get(rect) == 10.0)
    }
  )

  // ===== Special Character Tests =====
  val specialCharacterTestSuite: Spec[Any, Nothing] = suite("Special character (backtick-escaped) names")(
    test("macro derivation succeeds for case class with backtick-escaped field names") {
      // This test verifies that we can successfully derive optics for types with special names
      val fields = SpecialFields("hello", 42)
      // Just verify the macro derivation succeeded and optics object exists
      val optics = SpecialFields.optics
      assertTrue(optics != null && fields.`my funny name` == "hello")
    },
    test("macro derivation succeeds for sealed trait with backtick-escaped case names") {
      // This test verifies that the macro does not crash when deriving optics
      val case1: SpecialCases = `my-special-case`(10)
      // Just verify derivation succeeded
      val optics = SpecialCases.optics
      assertTrue(optics != null && case1.isInstanceOf[SpecialCases])
    }
  )

  // ===== DerivedOptics_ (underscore prefix) Tests =====
  val underscorePrefixTestSuite: Spec[Any, Nothing] = suite("DerivedOptics_ (underscore prefix)")(
    test("lens accessors are prefixed with underscore") {
      val person = PersonUnderscore("Alice", 30)
      // Accessors should have underscore prefix
      assertTrue(PersonUnderscore.optics._name.get(person) == "Alice") &&
      assertTrue(PersonUnderscore.optics._age.get(person) == 30)
    },
    test("underscore-prefixed lens replace works") {
      val person = PersonUnderscore("Alice", 30)
      assertTrue(PersonUnderscore.optics._name.replace(person, "Bob") == PersonUnderscore("Bob", 30))
    },
    test("underscore-prefixed optics are cached separately") {
      val optics1 = PersonUnderscore.optics
      val optics2 = PersonUnderscore.optics
      assertTrue(optics1 eq optics2)
    }
  )

  // ===== Traversal Tests =====
  val traversalTestSuite: Spec[Any, Nothing] = suite("Traversal integration")(
    test("traverse list field") {
      val lc = ListContainer(List(1, 2, 3))
      assertTrue(ListContainer.optics.items.get(lc) == List(1, 2, 3)) &&
      assertTrue(ListContainer.optics.items.replace(lc, List(4, 5)) == ListContainer(List(4, 5)))
    },
    test("traverse option field") {
      val oc1 = OptionContainer(Some("hello"))
      val oc2 = OptionContainer(None)
      assertTrue(OptionContainer.optics.maybe.get(oc1) == Some("hello")) &&
      assertTrue(OptionContainer.optics.maybe.get(oc2) == None)
    }
  )

  // ===== Composition Tests =====
  val compositionTestSuite: Spec[Any, Nothing] = suite("Optic composition")(
    test("lens andThen lens (explicit andThen syntax)") {
      val emp        = Employee("Bob", Address("Oak St", "LA"))
      val streetLens = Employee.optics.address.andThen(Address.optics.street)
      assertTrue(streetLens.get(emp) == "Oak St") &&
      assertTrue(streetLens.replace(emp, "Elm St").address.street == "Elm St")
    }
  )

  // ===== Definition of Done Edge Cases =====
  val definitionOfDoneTestSuite: Spec[Any, Nothing] = suite("Definition of Done edge cases")(
    test("recursive type (Tree) derives optics") {
      val tree = Tree(1, List(Tree(2, Nil)))
      assertTrue(Tree.optics.value.get(tree) == 1) &&
      assertTrue(Tree.optics.children.get(tree).size == 1)
    },
    test("case class with default parameters") {
      val wd = WithDefaults("hello")
      assertTrue(WithDefaults.optics.required.get(wd) == "hello") &&
      assertTrue(WithDefaults.optics.optional.get(wd) == 42)
    },
    test("external type alias pattern: type P = Person; object Person extends DerivedOptics[P]") {
      val ep                                 = ExternalPerson("Alice", 25)
      val lens: Lens[ExternalPerson, String] = ExternalPerson.optics.name
      assertTrue(lens.get(ep) == "Alice") &&
      assertTrue(lens.replace(ep, "Bob") == ExternalPerson("Bob", 25))
    },
    test("field named 'optics' does not collide with optics accessor") {
      val oc = OpticsCollision("field value", 42)
      assertTrue(OpticsCollision.optics.optics.get(oc) == "field value") &&
      assertTrue(OpticsCollision.optics.other.get(oc) == 42)
    }
  )
}
