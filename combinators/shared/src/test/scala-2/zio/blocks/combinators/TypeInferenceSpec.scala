package zio.blocks.combinators

import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox
import zio.test._

object TypeInferenceSpec extends ZIOSpecDefault {

  private val toolbox = currentMirror.mkToolBox()

  /**
   * Attempts to typecheck the given code and returns any error messages.
   * Uses the ToolBox to compile code at runtime.
   */
  private def typeCheckErrors(code: String): List[String] =
    try {
      toolbox.typecheck(toolbox.parse(code))
      Nil
    } catch {
      case e: scala.tools.reflect.ToolBoxError =>
        List(e.getMessage)
    }

  /**
   * Extracts the "found" type from a Scala 2 type mismatch error.
   * Scala 2 format: "type mismatch;\n found   : <type>\n required: <type>"
   * Note: multiple spaces between "found" and ":"
   */
  private def extractFoundType(errors: List[String]): Option[String] = {
    val foundPattern = """found\s+:\s+([^\n]+)""".r
    errors.flatMap { msg =>
      foundPattern.findFirstMatchIn(msg).map(_.group(1).trim)
    }.headOption
  }

  /**
   * Normalizes a type string by removing fully qualified package prefixes.
   */
  private def normalizeType(t: String): String =
    t.replaceAll("scala\\.util\\.", "")
      .replaceAll("scala\\.", "")

  /**
   * Checks that the inferred type exactly matches the expected type.
   */
  private def assertInferredType(code: String, expectedType: String) = {
    val errors         = typeCheckErrors(code)
    val foundType      = extractFoundType(errors)
    val normalizedType = foundType.map(normalizeType)
    assertTrue(
      errors.nonEmpty,
      normalizedType.isDefined,
      normalizedType.get == expectedType
    )
  }

  /**
   * Checks that the inferred type contains the expected substring.
   * Used when the exact type is complex or has path-dependent components.
   */
  private def assertInferredTypeContains(code: String, expectedSubstring: String) = {
    val errors         = typeCheckErrors(code)
    val foundType      = extractFoundType(errors)
    val normalizedType = foundType.map(normalizeType)
    assertTrue(
      errors.nonEmpty,
      normalizedType.isDefined,
      normalizedType.get.contains(expectedSubstring)
    )
  }

  def spec = suite("Type Inference - Wrong Annotation Strategy (Scala 2)")(
    suite("Tuples.combine type inference")(
      test("combine(1, \"a\") infers (Int, String)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val result: String = Tuples.combine(1, "a")
          """,
          "(Int, String)"
        )
      },
      test("combine((1, \"a\"), true) infers (Int, String, Boolean) with flattening") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val result: String = Tuples.combine((1, "a"), true)
          """,
          "(Int, String, Boolean)"
        )
      },
      test("combine(((1, \"a\"), true), (3.0, 'x')) infers ((Int, String), Boolean, (Double, Char)) - single-level flatten") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val result: String = Tuples.combine(((1, "a"), true), (3.0, 'x'))
          """,
          "((Int, String), Boolean, (Double, Char))"
        )
      },
      test("combine with literal Int and String infers (Int, String)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val result: Boolean = Tuples.combine(42, "hello")
          """,
          "(Int, String)"
        )
      },
      test("combine with Boolean and Double infers (Boolean, Double)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val result: String = Tuples.combine(true, 3.14)
          """,
          "(Boolean, Double)"
        )
      }
    ),
    suite("Tuples.separate type inference with explicit Separator")(
      test("separate((1, \"a\", true)) with explicit Separator infers (sep.Left, sep.Right)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            import zio.blocks.combinators.Tuples.Separator
            val sep = implicitly[Separator.WithTypes[(Int, String, Boolean), (Int, String), Boolean]]
            val result: String = sep.separate((1, "a", true))
          """,
          "(sep.Left, sep.Right)"
        )
      },
      test("separate((1, \"a\", true, 3.0)) with explicit Separator infers (sep.Left, sep.Right)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            import zio.blocks.combinators.Tuples.Separator
            val sep = implicitly[Separator.WithTypes[(Int, String, Boolean, Double), (Int, String, Boolean), Double]]
            val result: String = sep.separate((1, "a", true, 3.0))
          """,
          "(sep.Left, sep.Right)"
        )
      },
      test("separate on 5-element tuple with explicit Separator infers (sep.Left, sep.Right)") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            import zio.blocks.combinators.Tuples.Separator
            val sep = implicitly[Separator.WithTypes[(Int, Int, Int, Int, Int), (Int, Int, Int, Int), Int]]
            val result: String = sep.separate((1, 2, 3, 4, 5))
          """,
          "(sep.Left, sep.Right)"
        )
      }
    ),
    suite("Eithers.combine type inference")(
      test("combine on atomic Either[Int, String] infers Either[Int, String]") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.combine(Left(42): Either[Int, String])
          """,
          "Either[Int,String]"
        )
      },
      test("combine on Right(Right(true)): Either[Int, Either[String, Boolean]] infers Either[Either[Int, String], Boolean]") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.combine(Right(Right(true)): Either[Int, Either[String, Boolean]])
          """,
          "Either[Either[Int,String],Boolean]"
        )
      },
      test("combine on Right(Left(\"mid\")): Either[Int, Either[String, Boolean]] infers Either[Either[Int, String], Boolean]") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.combine(Right(Left("mid")): Either[Int, Either[String, Boolean]])
          """,
          "Either[Either[Int,String],Boolean]"
        )
      },
      test("combine on Left(42): Either[Int, Either[String, Boolean]] infers Either[Either[Int, String], Boolean]") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.combine(Left(42): Either[Int, Either[String, Boolean]])
          """,
          "Either[Either[Int,String],Boolean]"
        )
      },
      test("combine on deeply nested Either infers left-nested Either") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.combine(Right(Right(Right(3.14))): Either[Int, Either[String, Either[Boolean, Double]]])
          """,
          "Either[Either[Either[Int,String],Boolean],Double]"
        )
      }
    ),
    suite("Eithers.separate type inference")(
      test("separate(Either[Int, String]) returns Either type") {
        assertInferredTypeContains(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.separate(Left(42): Either[Int, String])
          """,
          "Either["
        )
      },
      test("separate(Either[Either[Int, String], Boolean]) returns Either type") {
        assertInferredTypeContains(
          """
            import zio.blocks.combinators.Eithers
            val result: String = Eithers.separate(Left(Left(42)): Either[Either[Int, String], Boolean])
          """,
          "Either["
        )
      }
    ),
    suite("Generic functions with type inference")(
      test("generic function using Tuples.Combiner shows c.Out in error") {
        val code = """
          import zio.blocks.combinators.Tuples
          def combineValues[L, R](l: L, r: R)(implicit c: Tuples.Combiner[L, R]): String = Tuples.combine(l, r)
          combineValues(1, "a")
        """
        val errors = typeCheckErrors(code)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.contains("c.Out"))
        )
      },
      test("generic function using Tuples.Separator shows path-dependent types in error") {
        val code = """
          import zio.blocks.combinators.Tuples
          def separateValue[A](a: A)(implicit s: Tuples.Separator[A]): String = Tuples.separate(a)
          separateValue((1, "a", true))
        """
        val errors = typeCheckErrors(code)
        assertTrue(
          errors.nonEmpty,
          errors.exists(e => e.contains("s.Left") && e.contains("s.Right"))
        )
      },
      test("generic function using Eithers.Combiner shows c.Out in error") {
        val code = """
          import zio.blocks.combinators.Eithers
          def canonicalize[L, R](e: Either[L, R])(implicit c: Eithers.Combiner[L, R]): String = Eithers.combine(e)
          canonicalize(Right(Right(true)): Either[Int, Either[String, Boolean]])
        """
        val errors = typeCheckErrors(code)
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.contains("c.Out"))
        )
      }
    ),
    suite("Edge cases and complex scenarios")(
      test("chained tuple combines maintain correct inference") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val step1: (Int, String) = Tuples.combine(1, "a")
            val result: Boolean = Tuples.combine(step1, true)
          """,
          "(Int, String, Boolean)"
        )
      },
      test("inference with method result types") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            def getInt: Int = 42
            def getString: String = "hello"
            val result: Boolean = Tuples.combine(getInt, getString)
          """,
          "(Int, String)"
        )
      },
      test("deeply nested Either canonicalization inference") {
        assertInferredType(
          """
            import zio.blocks.combinators.Eithers
            val deep: Either[Int, Either[String, Either[Boolean, Double]]] = Right(Right(Right(3.14)))
            val result: String = Eithers.combine(deep)
          """,
          "Either[Either[Either[Int,String],Boolean],Double]"
        )
      },
      test("Tuples.Combiner infers (Int, String, (Boolean, Double)) for mixed tuple inputs - left-only flatten") {
        assertInferredType(
          """
            import zio.blocks.combinators.Tuples
            val left: (Int, String) = (1, "a")
            val right: (Boolean, Double) = (true, 3.14)
            val result: String = Tuples.combine(left, right)
          """,
          "(Int, String, (Boolean, Double))"
        )
      }
    )
  )
}
