package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Selectable structural type conversions.
 *
 * Selectable is a Scala 3 feature that enables structural type member access
 * at compile time without runtime reflection. This means these conversions work on
 * all platforms (JVM, JS, Native).
 * 
 * Supported conversions:
 * - Selectable source → Case class target (using selectDynamic)
 * - Case class source → Selectable target (using Map constructor or companion apply)
 * 
 * Requirements for cross-platform Product → Selectable:
 * - Selectable class must have either:
 *   1. A constructor taking Map[String, Any] (primary or secondary)
 *   2. A companion object with apply(Map[String, Any])
 */
object SelectableStructuralSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Point(x: Int, y: Int)
  case class Person(name: String, age: Int)
  case class PersonWithDefault(name: String, age: Int, active: Boolean = true)
  case class PersonWithOptional(name: String, age: Int, nickname: Option[String] = None)

  // === Selectable implementations ===

  // A simple record-like case class that extends Selectable
  case class Record(fields: Map[String, Any]) extends Selectable {
    def selectDynamic(name: String): Any = fields(name)
  }

  // A Selectable class that uses companion apply instead of constructor
  case class RecordWithApply(fields: List[(String, Any)]) extends Selectable {
    private val fieldsMap: Map[String, Any] = fields.toMap
    def selectDynamic(name: String): Any = fieldsMap(name)
  }

  object RecordWithApply {
    def apply(map: Map[String, Any]): RecordWithApply = RecordWithApply(map.toList)
  }

  // Type aliases for structural types backed by Record
  type PointLike = Record { def x: Int; def y: Int }
  type PersonLike = Record { def name: String; def age: Int }
  type PersonWithDeptLike = Record { def name: String; def age: Int; def department: String }

  // Type aliases for structural types backed by RecordWithApply
  type PointLikeApply = RecordWithApply { def x: Int; def y: Int }
  type PersonLikeApply = RecordWithApply { def name: String; def age: Int }

  def makePoint(x: Int, y: Int): PointLike =
    Record(Map("x" -> x, "y" -> y)).asInstanceOf[PointLike]

  def makePerson(name: String, age: Int): PersonLike =
    Record(Map("name" -> name, "age" -> age)).asInstanceOf[PersonLike]

  def makePersonWithDept(name: String, age: Int, dept: String): PersonWithDeptLike =
    Record(Map("name" -> name, "age" -> age, "department" -> dept)).asInstanceOf[PersonWithDeptLike]

  def spec: Spec[TestEnvironment, Any] = suite("SelectableStructuralSpec")(
    suite("Selectable to Case Class - Exact Match")(
      test("converts Selectable structural type with matching fields") {
        val source = makePoint(10, 20)
        val into = Into.derived[PointLike, Point]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Point(10, 20))))
      },
      test("converts Selectable person to case class") {
        val source = makePerson("Alice", 30)
        val into = Into.derived[PersonLike, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Alice", 30))))
      }
    ),
    suite("Selectable to Case Class - Extra Source Fields")(
      test("converts Selectable with extra fields (drops extras)") {
        val source = makePersonWithDept("Bob", 25, "Engineering")
        val into = Into.derived[PersonWithDeptLike, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Bob", 25))))
      }
    ),
    suite("Selectable to Case Class - Target with Defaults")(
      test("converts Selectable with fewer fields using defaults") {
        val source = makePerson("Carol", 28)
        val into = Into.derived[PersonLike, PersonWithDefault]
        val result = into.into(source)

        assert(result)(isRight(equalTo(PersonWithDefault("Carol", 28, true))))
      }
    ),
    suite("Selectable to Case Class - Target with Optional")(
      test("converts Selectable with fewer fields using None for optional") {
        val source = makePerson("Dave", 35)
        val into = Into.derived[PersonLike, PersonWithOptional]
        val result = into.into(source)

        assert(result)(isRight(equalTo(PersonWithOptional("Dave", 35, None))))
      }
    ),
    suite("Multiple conversions")(
      test("converts multiple values") {
        val source1 = makePoint(1, 2)
        val source2 = makePoint(3, 4)
        val into = Into.derived[PointLike, Point]

        val result1 = into.into(source1)
        val result2 = into.into(source2)

        assertTrue(
          result1 == Right(Point(1, 2)),
          result2 == Right(Point(3, 4))
        )
      }
    ),
    suite("Case Class to Selectable - Using Map Constructor")(
      test("converts case class Point to PointLike Selectable") {
        val source = Point(10, 20)
        val into = Into.derived[Point, PointLike]
        val result = into.into(source)

        result match {
          case Right(selectable) =>
            assertTrue(
              selectable.x == 10,
              selectable.y == 20
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("converts case class Person to PersonLike Selectable") {
        val source = Person("Alice", 30)
        val into = Into.derived[Person, PersonLike]
        val result = into.into(source)

        result match {
          case Right(selectable) =>
            assertTrue(
              selectable.name == "Alice",
              selectable.age == 30
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("converts case class with extra fields to Selectable (ignores extras)") {
        // PersonWithDefault has extra 'active' field not in PersonLike
        val source = PersonWithDefault("Bob", 25, true)
        val into = Into.derived[PersonWithDefault, PersonLike]
        val result = into.into(source)
        
        result match {
          case Right(selectable) =>
            assertTrue(
              selectable.name == "Bob",
              selectable.age == 25
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      }
    ),
    suite("Case Class to Selectable - Using Companion Apply")(
      test("converts case class Point to PointLikeApply using companion apply") {
        val source = Point(100, 200)
        val into = Into.derived[Point, PointLikeApply]
        val result = into.into(source)
        
        result match {
          case Right(selectable) =>
            assertTrue(
              selectable.x == 100,
              selectable.y == 200
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("converts case class Person to PersonLikeApply using companion apply") {
        val source = Person("Charlie", 45)
        val into = Into.derived[Person, PersonLikeApply]
        val result = into.into(source)
        
        result match {
          case Right(selectable) =>
            assertTrue(
              selectable.name == "Charlie",
              selectable.age == 45
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      }
    )
  )
}

