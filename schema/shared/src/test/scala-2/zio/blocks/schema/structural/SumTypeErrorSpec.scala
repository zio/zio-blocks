package zio.blocks.schema.structural

import zio.test._
import zio.test.Assertion._

/**
 * Scala 2 specific tests verifying that sum types (sealed traits, enums)
 * produce proper compile-time errors since they require union types which are
 * only available in Scala 3.
 */
object SumTypeErrorSpec extends ZIOSpecDefault {

  def spec = suite("SumTypeErrorSpec (Scala 2)")(
    suite("Sealed Traits with Case Classes")(
      test("sealed trait with case class variants fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed trait Result
          case class Success(value: Int) extends Result
          case class Failure(error: String) extends Result
          ToStructural.derived[Result]
        """)
        assertZIO(result)(isLeft(containsString("sum types")))
      },
      test("sealed trait with nested fields fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Address(city: String)
          sealed trait Person
          case class Employee(name: String, address: Address) extends Person
          case class Contractor(company: String) extends Person
          ToStructural.derived[Person]
        """)
        assertZIO(result)(isLeft(containsString("sum types")))
      }
    ),
    suite("Sealed Traits with Case Objects")(
      test("sealed trait with case objects fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed trait Status
          case class Active(since: String) extends Status
          case class Inactive(reason: String) extends Status
          case object Unknown extends Status
          ToStructural.derived[Status]
        """)
        assertZIO(result)(isLeft(containsString("sum types")))
      },
      test("sealed trait with only case objects fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed trait Color
          case object Red extends Color
          case object Green extends Color
          case object Blue extends Color
          ToStructural.derived[Color]
        """)
        assertZIO(result)(isLeft(containsString("sum types")))
      }
    ),
    suite("Sealed Abstract Class")(
      test("sealed abstract class fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed abstract class Event
          case class Click(x: Int, y: Int) extends Event
          case object Scroll extends Event
          ToStructural.derived[Event]
        """)
        assertZIO(result)(isLeft(containsString("sum types")))
      }
    ),
    suite("Either Type (requires union types)")(
      test("Either[String, Int] field fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class WithEither(value: Either[String, Int])
          ToStructural.derived[WithEither]
        """)
        assertZIO(result)(isLeft(containsString("Either")))
      },
      test("Either with case class types fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class Error(code: Int)
          case class Data(name: String)
          case class WithComplexEither(result: Either[Error, Data])
          ToStructural.derived[WithComplexEither]
        """)
        assertZIO(result)(isLeft(containsString("Either")))
      },
      test("nested Either in Option fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class WithOptionalEither(value: Option[Either[String, Int]])
          ToStructural.derived[WithOptionalEither]
        """)
        assertZIO(result)(isLeft(containsString("Either")))
      },
      test("Either in List fails") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class WithListOfEither(values: List[Either[String, Int]])
          ToStructural.derived[WithListOfEither]
        """)
        assertZIO(result)(isLeft(containsString("Either")))
      }
    ),
    suite("Error Message Quality")(
      test("sealed trait error mentions Scala 3") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed trait Result
          case class Ok(v: Int) extends Result
          ToStructural.derived[Result]
        """)
        assertZIO(result)(isLeft(containsString("Scala 3")))
      },
      test("sealed trait error mentions union types") {
        val result = typeCheck("""
          import zio.blocks.schema._
          sealed trait Result
          case class Ok(v: Int) extends Result
          ToStructural.derived[Result]
        """)
        assertZIO(result)(isLeft(containsString("union types")))
      },
      test("Either error mentions Scala 3") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class WithEither(e: Either[String, Int])
          ToStructural.derived[WithEither]
        """)
        assertZIO(result)(isLeft(containsString("Scala 3")))
      },
      test("Either error mentions union types") {
        val result = typeCheck("""
          import zio.blocks.schema._
          case class WithEither(e: Either[String, Int])
          ToStructural.derived[WithEither]
        """)
        assertZIO(result)(isLeft(containsString("union types")))
      }
    )
  )
}
