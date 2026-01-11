package zio.blocks.schema.structural.errors

import zio.test._

/**
 * Tests for unsupported type conversion and validates supported type behavior.
 *
 * ==Supported Types==
 *   - Case classes (product types)
 *   - Case objects
 *   - Tuples
 *   - Case classes with nested products
 *   - Case classes with collections (List, Set, Map, Vector, Option)
 *   - Large products (> 22 fields)
 *
 * ==Sum Types (Scala 2 vs Scala 3)==
 * Sum type error tests are in scala-2 specific: SumTypeErrorSpec.scala In Scala
 * 3, sealed traits/enums are supported via union types.
 *
 * ==Expected Error Format for Scala 2 Sum Types==
 * {{{
 * Cannot generate ToStructural for MyType.
 *
 * Only product types (case classes) and tuples are currently supported.
 * Sum types (sealed traits) are not supported in Scala 2.
 * }}}
 */
object UnsupportedTypeErrorSpec extends ZIOSpecDefault {

  def spec = suite("UnsupportedTypeErrorSpec")(
    suite("Supported Product Types")(
      test("simple case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Simple(x: Int, y: String)

          val schema = Schema.derived[Simple]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with all primitives converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Primitives(
            a: Int, b: Long, c: Float, d: Double,
            e: Boolean, f: Byte, g: Short, h: Char, i: String
          )

          val schema = Schema.derived[Primitives]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with Option converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithOption(required: String, optional: Option[Int])

          val schema = Schema.derived[WithOption]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with List converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithList(items: List[Int])

          val schema = Schema.derived[WithList]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with Set converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithSet(items: Set[String])

          val schema = Schema.derived[WithSet]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with Vector converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithVector(items: Vector[Double])

          val schema = Schema.derived[WithVector]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with Map converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithMap(mapping: Map[String, Int])

          val schema = Schema.derived[WithMap]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("nested case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Inner(value: Int)
          case class Outer(name: String, inner: Inner)

          val schema = Schema.derived[Outer]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("deeply nested case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Level3(value: Int)
          case class Level2(level3: Level3)
          case class Level1(level2: Level2)
          case class Root(level1: Level1)

          val schema = Schema.derived[Root]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("empty case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Empty()

          val schema = Schema.derived[Empty]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case object converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case object Singleton

          val schema = Schema.derived[Singleton.type]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    ),
    suite("Large Products (More Than 22 Fields)")(
      test("25 field case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Large25(
            f1: Int, f2: Int, f3: Int, f4: Int, f5: Int,
            f6: Int, f7: Int, f8: Int, f9: Int, f10: Int,
            f11: Int, f12: Int, f13: Int, f14: Int, f15: Int,
            f16: Int, f17: Int, f18: Int, f19: Int, f20: Int,
            f21: Int, f22: Int, f23: Int, f24: Int, f25: Int
          )

          val schema = Schema.derived[Large25]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("30 field case class with mixed types converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Large30(
            f1: Int, f2: String, f3: Long, f4: Double, f5: Boolean,
            f6: Int, f7: String, f8: Long, f9: Double, f10: Boolean,
            f11: Int, f12: String, f13: Long, f14: Double, f15: Boolean,
            f16: Int, f17: String, f18: Long, f19: Double, f20: Boolean,
            f21: Int, f22: String, f23: Long, f24: Double, f25: Boolean,
            f26: Int, f27: String, f28: Long, f29: Double, f30: Boolean
          )

          val schema = Schema.derived[Large30]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    ),
    suite("Tuple Types")(
      test("tuple2 converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          val schema = Schema.derived[(Int, String)]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("tuple3 converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          val schema = Schema.derived[(Int, String, Boolean)]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("nested tuple converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          val schema = Schema.derived[((Int, String), (Boolean, Double))]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("tuple with case class converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Point(x: Int, y: Int)
          val schema = Schema.derived[(Point, String)]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    ),
// Sum Types (Error Handling) tests are in Scala 2 specific: SumTypeErrorSpec.scala
    // Scala 3 supports sum types via union types, so those tests are in the Scala 3 specs
    suite("Either and Complex Nested Types")(
      test("case class with Either field converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class WithEither(result: Either[String, Int])

          val schema = Schema.derived[WithEither]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with nested Option[List[T]] converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class Complex(items: Option[List[Int]])

          val schema = Schema.derived[Complex]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      },
      test("case class with Map[String, List[T]] converts to structural") {
        typeCheck {
          """
          import zio.blocks.schema._

          case class ComplexMap(data: Map[String, List[Int]])

          val schema = Schema.derived[ComplexMap]
          schema.structural
          """
        }.map { result =>
          assertTrue(result.isRight)
        }
      }
    )
  )
}
