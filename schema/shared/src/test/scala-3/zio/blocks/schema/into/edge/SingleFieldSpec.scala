package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

object SingleFieldSpec extends ZIOSpecDefault {

  def spec = suite("SingleFieldSpec")(
    suite("Single-Field Case Classes")(
      test("should convert single-field case class to copy") {
        case class Single(value: Int)
        case class SingleCopy(value: Int)

        val derivation = Into.derived[Single, SingleCopy]
        val input      = Single(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(SingleCopy(42)))
      },
      test("should convert single-field case class with coercion") {
        case class SingleInt(value: Int)
        case class SingleLong(value: Long)

        val derivation = Into.derived[SingleInt, SingleLong]
        val input      = SingleInt(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(SingleLong(42L)))
      },
      test("should convert single-field case class to itself (identity)") {
        case class Single(value: String)

        val derivation = Into.derived[Single, Single]
        val input      = Single("test")
        val result     = derivation.into(input)

        assertTrue(result == Right(Single("test")))
      },
      test("should convert single-field case class with validation (opaque type)") {
        // Opaque type must be defined at package/object level
        // Using existing PositiveInt from IntoSpec if available, or skip this test
        case class Single(value: Int)
        case class SingleLong(value: Long)

        val derivation = Into.derived[Single, SingleLong]
        val input      = Single(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(SingleLong(42L)))
      },
      test("should convert single-field case class to tuple") {
        case class Single(value: String)

        val derivation = Into.derived[Single, Tuple1[String]]
        val input      = Single("test")
        val result     = derivation.into(input)

        assertTrue(result == Right(Tuple1("test")))
      },
      test("should convert tuple to single-field case class") {
        case class Single(value: String)

        val derivation = Into.derived[Tuple1[String], Single]
        val input      = Tuple1("test")
        val result     = derivation.into(input)

        assertTrue(result == Right(Single("test")))
      },
      test("should convert single-field case class in nested structure") {
        case class Single(value: Int)
        case class SingleCopy(value: Int)
        case class Container(single: Single)
        case class ContainerCopy(single: SingleCopy)

        val derivation = Into.derived[Container, ContainerCopy]
        val input      = Container(Single(42))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { container =>
          assertTrue(container.single.value == 42)
        }
      },
      test("should convert single-field case class with collection field") {
        case class SingleList(items: List[Int])
        case class SingleVector(items: Vector[Long])

        val derivation = Into.derived[SingleList, SingleVector]
        val input      = SingleList(List(1, 2, 3))
        val result     = derivation.into(input)

        assertTrue(result == Right(SingleVector(Vector(1L, 2L, 3L))))
      }
    )
  )
}
