package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Selectable structural type conversions.
 *
 * Selectable is a Scala 3 feature that enables structural type member access
 * at compile time without reflection. This means these conversions work on
 * all platforms (JVM, JS, Native).
 */
object SelectableStructuralSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Point(x: Int, y: Int)
  case class Person(name: String, age: Int)
  case class PersonWithDefault(name: String, age: Int, active: Boolean = true)
  case class PersonWithOptional(name: String, age: Int, nickname: Option[String] = None)

  // === Selectable implementations ===
  
  // A simple record-like class that extends Selectable
  class Record(elems: (String, Any)*) extends Selectable {
    private val fields = elems.toMap
    def selectDynamic(name: String): Any = fields(name)
  }

  // Type aliases for structural types backed by Record
  type PointLike = Record { def x: Int; def y: Int }
  type PersonLike = Record { def name: String; def age: Int }
  type PersonWithDeptLike = Record { def name: String; def age: Int; def department: String }

  // Factory methods
  def makePoint(x: Int, y: Int): PointLike =
    new Record("x" -> x, "y" -> y).asInstanceOf[PointLike]

  def makePerson(name: String, age: Int): PersonLike =
    new Record("name" -> name, "age" -> age).asInstanceOf[PersonLike]

  def makePersonWithDept(name: String, age: Int, dept: String): PersonWithDeptLike =
    new Record("name" -> name, "age" -> age, "department" -> dept).asInstanceOf[PersonWithDeptLike]

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
    )
  )
}

