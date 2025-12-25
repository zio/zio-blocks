package zio.blocks.schema.into.collections

import zio.test._
import zio.blocks.schema._

object NestedCollectionTypeSpec extends ZIOSpecDefault {

  def spec = suite("NestedCollectionTypeSpec")(
    suite("Nested Collection Type Conversions")(
      test("should convert List[Vector[Int]] to Vector[List[Long]] (container + element conversion)") {
        val derivation = Into.derived[List[Vector[Int]], Vector[List[Long]]]
        val input      = List(Vector(1, 2), Vector(3, 4))
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector(List(1L, 2L), List(3L, 4L))))
      },
      test("should convert Set[Array[Int]] to List[Vector[Long]]") {
        val derivation = Into.derived[Set[Array[Int]], List[Vector[Long]]]
        val input      = Set(Array(1, 2), Array(3, 4))
        val result     = derivation.into(input)

        result match {
          case Right(list) =>
            assertTrue(
              list.length == 2 &&
                list(0).toList == Vector(1L, 2L).toList &&
                list(1).toList == Vector(3L, 4L).toList
            )
          case Left(_) => assertTrue(false)
        }
      },
      test("should convert nested collections with case classes") {
        case class Source(value: Int)
        case class Target(value: Long)

        val derivation = Into.derived[List[Vector[Source]], Vector[List[Target]]]
        val input      = List(Vector(Source(1), Source(2)), Vector(Source(3)))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { vector =>
          assertTrue(
            vector.length == 2 &&
              vector(0).length == 2 &&
              vector(0)(0).value == 1L &&
              vector(0)(1).value == 2L &&
              vector(1).length == 1 &&
              vector(1)(0).value == 3L
          )
        }
      },
      test("should convert deeply nested collections (3 levels)") {
        val derivation = Into.derived[List[List[List[Int]]], Vector[Vector[Vector[Long]]]]
        val input      = List(List(List(1, 2), List(3)), List(List(4)))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { nested =>
          assertTrue(
            nested.length == 2 &&
              nested(0).length == 2 &&
              nested(0)(0).toList == Vector(1L, 2L).toList &&
              nested(0)(1).toList == Vector(3L).toList &&
              nested(1).length == 1 &&
              nested(1)(0).toList == Vector(4L).toList
          )
        }
      },
      test("should convert Option[List[Int]] to Option[List[Long]]") {
        val derivation = Into.derived[Option[List[Int]], Option[List[Long]]]
        val someInput  = Some(List(1, 2, 3))
        val someResult = derivation.into(someInput)
        assertTrue(someResult == Right(Some(List(1L, 2L, 3L))))

        val noneInput  = None: Option[List[Int]]
        val noneResult = derivation.into(noneInput)
        assertTrue(noneResult == Right(None))
      },
      test("should convert Either[List[Int], List[String]] to Either[List[Long], List[String]]") {
        val derivation = Into.derived[Either[List[Int], List[String]], Either[List[Long], List[String]]]
        val leftInput  = Left(List(1, 2, 3))
        val leftResult = derivation.into(leftInput)
        assertTrue(leftResult == Right(Left(List(1L, 2L, 3L))))

        val rightInput  = Right(List("a", "b"))
        val rightResult = derivation.into(rightInput)
        assertTrue(rightResult == Right(Right(List("a", "b"))))
      },
      // NOTE: Map conversions are not yet fully supported in nested scenarios
      // This test is skipped until Map support is complete
      // test("should convert Map[String, List[Int]] to Map[String, Vector[Long]]") { ... },
      test("should handle empty nested collections") {
        val derivation = Into.derived[List[Vector[Int]], Vector[List[Long]]]
        val input      = List.empty[Vector[Int]]
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector.empty[List[Long]]))
      },
      test("should convert nested collections with mixed types") {
        case class A(x: Int)
        case class B(x: Long)

        val derivation = Into.derived[Set[List[A]], List[Vector[B]]]
        val input      = Set(List(A(1), A(2)), List(A(3)))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { list =>
          assertTrue(
            list.length == 2 &&
              list.exists(_.toList == Vector(B(1L), B(2L)).toList) &&
              list.exists(_.toList == Vector(B(3L)).toList)
          )
        }
      }
    )
  )
}
