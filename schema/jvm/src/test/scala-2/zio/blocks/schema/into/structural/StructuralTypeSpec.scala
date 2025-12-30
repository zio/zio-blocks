package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/**
 * JVM-only tests for structural types that require reflection.
 *
 * These tests use ad-hoc structural types created with `new { def ... }` syntax
 * as SOURCE types. Reading fields from such types requires reflection
 * (getClass.getMethod), which only works on JVM.
 *
 * The Product → Structural direction works cross-platform (see DynamicTypeSpec)
 * because it generates an anonymous Dynamic class at compile time.
 */
object StructuralTypeSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)
  case class Employee(name: String, age: Int, department: String)

  // === Helper to create structural type instances ===
  def makePersonStruct(n: String, a: Int): { def name: String; def age: Int } = new {
    def name: String = n
    def age: Int = a
  }

  def makePointStruct(xVal: Int, yVal: Int): { def x: Int; def y: Int } = new {
    def x: Int = xVal
    def y: Int = yVal
  }

  def makeEmployeeStruct(n: String, a: Int, d: String): { def name: String; def age: Int; def department: String } = new {
    def name: String = n
    def age: Int = a
    def department: String = d
  }

  def spec: Spec[TestEnvironment, Any] = suite("StructuralTypeJVMOnlySpec")(
    suite("Structural Type to Case Class (JVM Only - requires reflection)")(
      test("converts structural type to case class with matching fields") {
        val source = makePersonStruct("Alice", 30)
        val into = Into.derived[{ def name: String; def age: Int }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Alice", 30))))
      },
      test("converts structural type with extra fields (subset mapping)") {
        val source = makeEmployeeStruct("Bob", 25, "Engineering")
        val into = Into.derived[{ def name: String; def age: Int; def department: String }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Bob", 25))))
      },
      test("converts structural type with all fields") {
        val source = makeEmployeeStruct("Carol", 35, "Sales")
        val into = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Employee("Carol", 35, "Sales"))))
      },
      test("converts Point structural type to case class") {
        val source = makePointStruct(10, 20)
        val into = Into.derived[{ def x: Int; def y: Int }, Point]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Point(10, 20))))
      }
    ),
    suite("Structural Round-Trip (JVM Only)")(
      test("structural to case class to structural") {
        val source = makeEmployeeStruct("Frank", 45, "HR")

        val toEmployee = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val toPersonStruct = Into.derived[Employee, { def name: String; def age: Int }]

        val intermediate = toEmployee.into(source)
        val result = intermediate.flatMap(emp => toPersonStruct.into(emp))

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Frank")) &&
            assert(r.age)(equalTo(45))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("case class to structural to case class round trip") {
        val original = Point(100, 200)

        val toStruct = Into.derived[Point, { def x: Int; def y: Int }]
        val fromStruct = Into.derived[{ def x: Int; def y: Int }, Point]

        val intermediate = toStruct.into(original)
        val result = intermediate.flatMap(s => fromStruct.into(s))

        assert(result)(isRight(equalTo(original)))
      }
    ),
    suite("Structural Type Edge Cases (JVM Only)")(
      test("handles structural type with single field") {
        case class Name(value: String)
        def makeNameStruct(v: String): { def value: String } = new {
          def value: String = v
        }

        val source = makeNameStruct("test")
        val into = Into.derived[{ def value: String }, Name]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Name("test"))))
      },
      test("handles structural type with numeric fields") {
        case class Numbers(a: Int, b: Long, c: Double)
        def makeNumbersStruct(aVal: Int, bVal: Long, cVal: Double): { def a: Int; def b: Long; def c: Double } = new {
          def a: Int = aVal
          def b: Long = bVal
          def c: Double = cVal
        }

        val source = makeNumbersStruct(1, 2L, 3.0)
        val into = Into.derived[{ def a: Int; def b: Long; def c: Double }, Numbers]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Numbers(1, 2L, 3.0))))
      }
    )
  )
}

