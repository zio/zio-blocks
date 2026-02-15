package zio.blocks.combinators

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object TypeInferenceSpec extends ZIOSpecDefault {

  def spec = suite("Type Inference - Wrong Annotation Strategy")(
    suite("Tuples.combine type inference")(
      test("combine(1, \"a\") infers (Int, String)") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.combine(1, "a")
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("combine((1, \"a\"), true) infers (Int, String, Boolean) with flattening") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.combine((1, "a"), true)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String") && e.message.contains("Boolean"))
        )
      },
      test("combine(((1, \"a\"), true), (3.0, 'x')) infers (Int, String, Boolean, Double, Char) deeply flattened") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.combine(((1, "a"), true), (3.0, 'x'))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e =>
            e.message.contains("Int") && e.message.contains("String") && e.message.contains("Boolean") && e.message
              .contains("Double")
          )
        )
      },
      test("combine with literal Int and String infers (Int, String)") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: Boolean = Tuples.combine(42, "hello")
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("combine with Boolean and Double infers (Boolean, Double)") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.combine(true, 3.14)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Boolean") && e.message.contains("Double"))
        )
      }
    ),
    suite("Tuples.separate type inference")(
      test("separate((1, \"a\")) infers (Int, String)") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.separate((1, "a"))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("separate((1, \"a\", true)) returns tuple of components via Separator") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.separate((1, "a", true))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Separator"))
        )
      },
      test("separate((1, \"a\", true, 3.0)) infers 4-tuple with proper Separator") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.separate((1, "a", true, 3.0))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Separator"))
        )
      },
      test("separate on 5-element tuple infers proper Separator with Left and Right") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val result: String = Tuples.separate((1, 2, 3, 4, 5))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Separator"))
        )
      }
    ),
    suite("Eithers.combine type inference")(
      test("combine on atomic Either[Int, String] infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.combine(Left(42): Either[Int, String])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      },
      test("combine on Right(Right(true)): Either[Int, Either[String, Boolean]] infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.combine(Right(Right(true)): Either[Int, Either[String, Boolean]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Either") && e.message.contains("Combiner"))
        )
      },
      test("combine on Right(Left(\"mid\")): Either[Int, Either[String, Boolean]] infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.combine(Right(Left("mid")): Either[Int, Either[String, Boolean]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Either") && e.message.contains("Combiner"))
        )
      },
      test("combine on Left(42): Either[Int, Either[String, Boolean]] infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.combine(Left(42): Either[Int, Either[String, Boolean]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      },
      test("combine on deeply nested Either infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.combine(Right(Right(Right(3.14))): Either[Int, Either[String, Either[Boolean, Double]]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Either") && e.message.contains("Combiner"))
        )
      }
    ),
    suite("Eithers.separate type inference")(
      test("separate(Either[Int, String]) returns Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.separate(Left(42): Either[Int, String])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      },
      test("separate(Either[Either[Int, String], Boolean]) separates rightmost component") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val result: String = Eithers.separate(Left(Left(42)): Either[Either[Int, String], Boolean])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      }
    ),
    suite("Unions.combine type inference")(
      test("combine(Left(42): Either[Int, String]) infers union with Int and String") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.combine(Left(42): Either[Int, String])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("combine(Right(\"hello\"): Either[Int, String]) infers union with Int and String") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.combine(Right("hello"): Either[Int, String])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("combine with Boolean | Double infers union type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.combine(Left(true): Either[Boolean, Double])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Boolean") && e.message.contains("Double"))
        )
      }
    ),
    suite("Unions.separate type inference")(
      test("separate(Int | String) with explicit Separator infers Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.separate(42: (Int | String))(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      },
      test("separate preserves union structure in Either type") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.separate("hello": (Int | String))(using summon[Unions.Separator.WithTypes[Int | String, Int, String]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Either"))
        )
      }
    ),
    suite("Generic functions with type inference")(
      test("generic function using Tuples.Combiner shows type inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          def combine_values[L, R](l: L, r: R)(using c: Tuples.Combiner[L, R]): String = Tuples.combine(l, r)
          combine_values(1, "a")
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Found") && e.message.contains("c.Out"))
        )
      },
      test("generic function using Tuples.Separator shows type inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          def separate_value[A](a: A)(using s: Tuples.Separator[A]): String = Tuples.separate(a)
          separate_value((1, "a", true))
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Found") && e.message.contains("Left") && e.message.contains("Right"))
        )
      },
      test("generic function using Eithers.Combiner shows type inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          def canonicalize[L, R](e: Either[L, R])(using c: Eithers.Combiner[L, R]): String = Eithers.combine(e)
          canonicalize(Right(Right(true)): Either[Int, Either[String, Boolean]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Found") && e.message.contains("c.Out"))
        )
      },
      test("generic function using Unions.Combiner shows type inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          def to_union[L, R](e: Either[L, R])(using c: Unions.Combiner[L, R]): String = Unions.combine(e)
          to_union(Left(42): Either[Int, String])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Found") && e.message.contains("c.Out"))
        )
      }
    ),
    suite("Edge cases and complex scenarios")(
      test("chained tuple combines maintain correct inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val step1: (Int, String) = Tuples.combine(1, "a")
          val result: Boolean = Tuples.combine(step1, true)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Combiner"))
        )
      },
      test("roundtrip combine/separate preserves type relationship") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val combined: String = Tuples.combine(1, "a")
          val separated: Boolean = Tuples.separate(combined)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("inference with method result types") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          def getInt: Int = 42
          def getString: String = "hello"
          val result: Boolean = Tuples.combine(getInt, getString)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Int") && e.message.contains("String"))
        )
      },
      test("deeply nested Either canonicalization inference") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val deep: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
          val result: String = Eithers.combine(deep)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Either") && e.message.contains("Combiner"))
        )
      },
      test("union type inference with Either source") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Unions
          val result: String = Unions.combine(Left(42): Either[Int, Either[String, Boolean]])
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Combiner"))
        )
      },
      test("tuple inference with method calls") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          def getInt: Int = 42
          def getString: String = "hello"
          val result: String = Tuples.combine(getInt, getString)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Combiner"))
        )
      },
      test("Eithers inference maintains Either structure in nested case") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Eithers
          val nested: Either[Int, Either[String, Boolean]] = Right(Left("test"))
          val result: String = Eithers.combine(nested)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.message.contains("Either") && e.message.contains("Combiner"))
        )
      },
      test("Tuples.Combiner infers correct types for mixed tuple inputs") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.Tuples
          val left: (Int, String) = (1, "a")
          val right: (Boolean, Double) = (true, 3.14)
          val result: String = Tuples.combine(left, right)
        """)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e =>
            e.message.contains("Combiner") && e.message.contains("Int") && e.message.contains("String") && e.message
              .contains("Boolean") && e.message.contains("Double")
          )
        )
      }
    )
  )
}
