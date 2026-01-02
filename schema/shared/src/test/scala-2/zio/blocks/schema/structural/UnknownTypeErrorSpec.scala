package zio.blocks.schema.structural

import zio.test._
import zio.test.Assertion._

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
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("RegularClass") &&
              containsString("ToStructural only supports")
          )
        )
      },
      test("nested regular class fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class Inner(val value: String)
          case class Outer(inner: Inner)
          case class Root(outer: Outer)
          ToStructural.derived[Root]
        """)
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("Inner") &&
              containsString("not supported")
          )
        )
      },
      test("regular class in Option fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: Option[RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("RegularClass")
          )
        )
      },
      test("regular class in List fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: List[RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("RegularClass")
          )
        )
      },
      test("regular class in Map value fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: Map[String, RegularClass])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("RegularClass")
          )
        )
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
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("UnsealedTrait") &&
              containsString("Case classes")
          )
        )
      },
      test("non-sealed trait in collection fails to compile") {
        val result = typeCheck("""
          import zio.blocks.schema._
          trait UnsealedTrait { def x: Int }
          case class Foo(m: Vector[UnsealedTrait])
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("UnsealedTrait")
          )
        )
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
        assertZIO(result)(
          isLeft(
            containsString("unsupported type") &&
              containsString("AbstractBase") &&
              containsString("not supported")
          )
        )
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
        assertZIO(result)(
          isLeft(
            containsString("MyCustomClass") &&
              containsString("unsupported type")
          )
        )
      },
      test("error message mentions supported types") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: RegularClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("Case classes") &&
              containsString("ToStructural only supports")
          )
        )
      },
      test("error message suggests converting to case class") {
        val result = typeCheck("""
          import zio.blocks.schema._
          class RegularClass(val x: Int)
          case class Foo(m: RegularClass)
          ToStructural.derived[Foo]
        """)
        assertZIO(result)(
          isLeft(
            containsString("case class") &&
              containsString("consider converting")
          )
        )
      }
    )
  )
}
