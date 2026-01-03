package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for Seq type conversions. */
object SeqConversionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("SeqConversionSpec")(
    test("Seq[Int] to List[Int]") {
      val result = Into[Seq[Int], List[Int]].into(Seq(1, 2, 3))
      assert(result)(isRight(equalTo(List(1, 2, 3))))
    },
    test("List[Int] to Seq[Long] with coercion") {
      val result = Into[List[Int], Seq[Long]].into(List(1, 2, 3))
      assert(result)(isRight(equalTo(Seq(1L, 2L, 3L))))
    },
    test("Seq[Int] to Vector[Long] with coercion") {
      val result = Into[Seq[Int], Vector[Long]].into(Seq(1, 2, 3))
      assert(result)(isRight(equalTo(Vector(1L, 2L, 3L))))
    },
    test("case class with Seq field to case class with List field") {
      case class Source(items: Seq[Int])
      case class Target(items: List[Long])

      val result = Into.derived[Source, Target].into(Source(Seq(1, 2, 3)))
      assert(result)(isRight(equalTo(Target(List(1L, 2L, 3L)))))
    },
    test("converts Seq[Seq[Int]] to List[List[Long]]") {
      val source: Seq[Seq[Int]] = Seq(Seq(1, 2), Seq(3, 4))
      val result                = Into[Seq[Seq[Int]], List[List[Long]]].into(source)

      assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L)))))
    },
    test("converts List[Seq[Int]] to Vector[Vector[Long]]") {
      val source: List[Seq[Int]] = List(Seq(1), Seq(2, 3))
      val result                 = Into[List[Seq[Int]], Vector[Vector[Long]]].into(source)

      assert(result)(isRight(equalTo(Vector(Vector(1L), Vector(2L, 3L)))))
    }
  )
}
