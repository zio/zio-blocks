package zio.blocks.schema.into

import zio.test._
import zio._
import zio.blocks.schema.Into

object IntoCollectionSpec extends ZIOSpecDefault {

  // Case classes for complex recursion tests
  case class Source(x: Int)
  case class Target(x: Long)

  def spec = suite("Into Collection Support")(
    suite("Primitive Element Conversion")(
      test("Should convert List[Int] to List[Long]") {
        val derivation = Into.derived[List[Int], List[Long]]
        val input      = List(1, 2, 3, 4, 5)
        val result     = derivation.into(input)

        assertTrue(result == Right(List(1L, 2L, 3L, 4L, 5L)))
      },
      test("Should convert List[String] to List[String] (Identity)") {
        val derivation = Into.derived[List[String], List[String]]
        val input      = List("a", "b", "c")
        val result     = derivation.into(input)

        assertTrue(result == Right(List("a", "b", "c")))
      }
    ),
    suite("Container Conversion")(
      test("Should convert List[Int] to Vector[Int]") {
        val derivation = Into.derived[List[Int], Vector[Int]]
        val input      = List(1, 2, 3)
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector(1, 2, 3)))
      },
      test("Should convert Seq[String] to List[String]") {
        val derivation = Into.derived[Seq[String], List[String]]
        val input      = Seq("x", "y", "z")
        val result     = derivation.into(input)

        assertTrue(result == Right(List("x", "y", "z")))
      },
      test("Should convert List[Int] to Set[Int]") {
        val derivation = Into.derived[List[Int], Set[Int]]
        val input      = List(1, 2, 3, 2, 1)
        val result     = derivation.into(input)

        // Set deduplicates, so result should be Set(1, 2, 3)
        assertTrue(result == Right(Set(1, 2, 3)))
      }
    ),
    suite("Array Support")(
      test("Should convert List[Int] to Array[Int] (with ClassTag)") {
        val derivation = Into.derived[List[Int], Array[Int]]
        val input      = List(10, 20, 30)
        val result     = derivation.into(input)

        result match {
          case Right(arr) => assertTrue(arr.toList == List(10, 20, 30))
          case Left(_)    => assertTrue(false)
        }
      },
      test("Should convert Array[Int] to List[Int]") {
        val derivation = Into.derived[Array[Int], List[Int]]
        val input      = Array(5, 10, 15)
        val result     = derivation.into(input)

        assertTrue(result == Right(List(5, 10, 15)))
      },
      test("Should convert List[String] to Array[String]") {
        val derivation = Into.derived[List[String], Array[String]]
        val input      = List("hello", "world")
        val result     = derivation.into(input)

        result match {
          case Right(arr) => assertTrue(arr.toList == List("hello", "world"))
          case Left(_)    => assertTrue(false)
        }
      }
    ),
    suite("Complex Recursion")(
      test("Should convert List[Source] to Vector[Target] (element + container conversion)") {
        val derivation = Into.derived[List[Source], Vector[Target]]
        val input      = List(Source(1), Source(2), Source(3))
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector(Target(1L), Target(2L), Target(3L))))
      },
      test("Should convert List[Source] to List[Target] (element conversion only)") {
        val derivation = Into.derived[List[Source], List[Target]]
        val input      = List(Source(10), Source(20))
        val result     = derivation.into(input)

        assertTrue(result == Right(List(Target(10L), Target(20L))))
      },
      test("Should convert Vector[Source] to List[Target]") {
        val derivation = Into.derived[Vector[Source], List[Target]]
        val input      = Vector(Source(100), Source(200))
        val result     = derivation.into(input)

        assertTrue(result == Right(List(Target(100L), Target(200L))))
      }
    ),
    suite("Combined Conversions")(
      test("Should convert List[Int] to Vector[Long] (element + container)") {
        val derivation = Into.derived[List[Int], Vector[Long]]
        val input      = List(1, 2, 3)
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector(1L, 2L, 3L)))
      },
      test("Should convert Seq[Int] to Set[Long]") {
        val derivation = Into.derived[Seq[Int], Set[Long]]
        val input      = Seq(1, 2, 3, 2, 1)
        val result     = derivation.into(input)

        assertTrue(result == Right(Set(1L, 2L, 3L)))
      }
    ),
    suite("Edge Cases")(
      test("Should handle empty List[Int] to List[Long]") {
        val derivation = Into.derived[List[Int], List[Long]]
        val input      = List.empty[Int]
        val result     = derivation.into(input)

        assertTrue(result == Right(List.empty[Long]))
      },
      test("Should handle empty List[Source] to Vector[Target]") {
        val derivation = Into.derived[List[Source], Vector[Target]]
        val input      = List.empty[Source]
        val result     = derivation.into(input)

        assertTrue(result == Right(Vector.empty[Target]))
      }
    )
  )
}
