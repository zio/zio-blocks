package zio.blocks.schema.structural

import zio.test._
import zio.test.Assertion._

/**
 * Tests verifying that unknown/unsupported types produce proper compile-time errors.
 * Unknown types include regular classes, traits (non-sealed), and other types that
 * are not primitives, case classes, collections, or tuples.
 */
object UnknownTypeErrorSpec extends ZIOSpecDefault {

  def spec = suite("UnknownTypeErrorSpec (Scala 2)")(
    suite("Regular Classes (not case classes)")(
      test("regular class field fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: RegularClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      },
      test("nested regular class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class Inner(val value: String)
          case class Outer(inner: Inner)
          case class Root(outer: Outer)
          ToStructural.derived[Root]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      },
      test("regular class in Option fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: Option[RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      },
      test("regular class in List fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: List[RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      },
      test("regular class in Map value fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: Map[String, RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      }
    ),
    suite("Non-Sealed Traits")(
      test("non-sealed trait field fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          trait UnsealedTrait { def x: Int }
          case class Foo(m: UnsealedTrait)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      },
      test("non-sealed trait in collection fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          trait UnsealedTrait { def x: Int }
          case class Foo(m: Vector[UnsealedTrait])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      }
    ),
    suite("Abstract Classes (non-sealed)")(
      test("abstract class field fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          abstract class AbstractBase(val x: Int)
          case class Foo(m: AbstractBase)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("unsupported type")))
      }
    ),
    suite("Error Message Quality")(
      test("error message mentions the type name") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class MyCustomClass(val x: Int)
          case class Foo(m: MyCustomClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("MyCustomClass")))
      },
      test("error message mentions supported types") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: RegularClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("Case classes")))
      },
      test("error message suggests converting to case class") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: RegularClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(isLeft(containsString("case class")))
      }
    )
  )
}
