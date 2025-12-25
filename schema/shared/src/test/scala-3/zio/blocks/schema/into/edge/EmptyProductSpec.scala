package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

object EmptyProductSpec extends ZIOSpecDefault {

  def spec = suite("EmptyProductSpec")(
    suite("Empty Case Classes")(
      test("should convert empty case class to empty case class (identity)") {
        case class Empty1()
        case class Empty2()

        val derivation = Into.derived[Empty1, Empty2]
        val input      = Empty1()
        val result     = derivation.into(input)

        assertTrue(result == Right(Empty2()))
      },
      test("should convert empty case class to itself") {
        case class Empty()

        val derivation = Into.derived[Empty, Empty]
        val input      = Empty()
        val result     = derivation.into(input)

        assertTrue(result == Right(Empty()))
      },
      test("should handle empty case class in nested structure") {
        case class Empty1()
        case class Empty2()
        case class Container1(value: Empty1)
        case class Container2(value: Empty2)

        val derivation = Into.derived[Container1, Container2]
        val input      = Container1(Empty1())
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { container =>
          assertTrue(container.value == Empty2())
        }
      },
      test("should convert empty case class to EmptyTuple") {
        case class Empty()

        val derivation = Into.derived[Empty, EmptyTuple]
        val input      = Empty()
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result == Right(EmptyTuple))
      },
      test("should convert EmptyTuple to empty case class") {
        case class Empty()

        val derivation = Into.derived[EmptyTuple, Empty]
        val input      = EmptyTuple
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result == Right(Empty()))
      }
    )
  )
}
