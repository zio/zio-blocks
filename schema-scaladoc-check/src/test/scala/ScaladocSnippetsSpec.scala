/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import zio.test._

import scala.util.Try

object ScaladocSnippetsSpec extends ZIOSpecDefault {
  private val path = "schema/shared/src/main/scala/zio/blocks/schema/A.scala"

  private def source(lines: String*): String = lines.mkString("\n")

  private def failure(content: String): Option[String] =
    Try(ScaladocSnippets.extract(path, content)).failed.toOption.map(_.getMessage)

  def spec: Spec[TestEnvironment, Any] = suite("ScaladocSnippets")(
    test("extract returns a {{{ block with the line of its opening marker and the margin removed") {
      val content = source(
        "package p",
        "/**",
        " * Area of a book cover:",
        " * {{{",
        " * val area = width * height",
        " *   .toString",
        " *",
        " * }}}",
        " */",
        "object A"
      )
      assertTrue(
        ScaladocSnippets.extract(path, content) == List(
          ScaladocSnippets.Snippet(path, 4, List("val area = width * height", "  .toString", ""), None)
        )
      )
    },
    test("extract returns a ```scala fence") {
      val content = source("/**", " * ```scala", " * val title = \"Zionomicon\"", " * ```", " */")
      assertTrue(
        ScaladocSnippets.extract(path, content) ==
          List(ScaladocSnippets.Snippet(path, 2, List("val title = \"Zionomicon\""), None))
      )
    },
    test("extract ignores blocks outside Scaladoc comments") {
      val content = source("/* plain comment", " * {{{", " * val x = 1", " * }}}", " */", "// {{{")
      assertTrue(ScaladocSnippets.extract(path, content) == Nil)
    },
    test("extract keeps scanning after a one-line Scaladoc") {
      val content = source("/** One line. */", "def f = 1", "/**", " * {{{", " * val y = 2", " * }}}", " */")
      assertTrue(ScaladocSnippets.extract(path, content).map(_.line) == List(4))
    },
    test("extract skips a block whose first line is // no-check with a reason") {
      val content = source("/**", " * {{{", " * // no-check: needs a running database", " * db.run()", " * }}}", " */")
      assertTrue(ScaladocSnippets.extract(path, content) == Nil)
    },
    test("extract rejects // no-check without a reason") {
      val content = source("/**", " * {{{", " * // no-check", " * db.run()", " * }}}", " */")
      assertTrue(
        failure(content) == Some(
          s"$path:2: `// no-check` needs a reason, as in `// no-check: needs a running database`"
        )
      )
    },
    test("extract rejects a marker-like first line that is not an exact marker") {
      val content = source("/**", " * {{{", " * // scala-3-only: uses given", " * given Int = 1", " * }}}", " */")
      assertTrue(
        failure(content) == Some(
          s"$path:2: unknown marker `// scala-3-only: uses given`; use `// scala-3-only`, `// scala-2-only` or `// no-check: <reason>`"
        )
      )
    },
    test("extract rejects a misspelled marker") {
      val content = source("/**", " * {{{", " * // scala3-only", " * given Int = 1", " * }}}", " */")
      assertTrue(
        failure(content) == Some(
          s"$path:2: unknown marker `// scala3-only`; use `// scala-3-only`, `// scala-2-only` or `// no-check: <reason>`"
        )
      )
    },
    test("extract fails with path and line when the comment ends before the block closes") {
      val content = source("/**", " * {{{", " * val x = 1", " */", "object A")
      assertTrue(failure(content) == Some(s"$path:2: code block is never closed"))
    },
    test("extract fails with path and line when the file ends before the block closes") {
      val content = source("/**", " * {{{", " * val x = 1")
      assertTrue(failure(content) == Some(s"$path:2: code block is never closed"))
    },
    test("extract records scala-3-only and scala-2-only markers") {
      val content = source(
        "/**",
        " * {{{",
        " * // scala-3-only",
        " * given Int = 1",
        " * }}}",
        " * {{{",
        " * // scala-2-only",
        " * implicit val i: Int = 1",
        " * }}}",
        " */"
      )
      assertTrue(ScaladocSnippets.extract(path, content).map(_.onlyScalaMajor) == List(Some(3), Some(2)))
    },
    test("stripMargin removes only the leading margin") {
      assertTrue(
        ScaladocSnippets.stripMargin("   *   val y = 2 * 3") == "  val y = 2 * 3",
        ScaladocSnippets.stripMargin("  *") == "",
        ScaladocSnippets.stripMargin("  */") == "  */",
        ScaladocSnippets.stripMargin("val z = 1") == "val z = 1"
      )
    },
    test("objectName drops src/main and the schema package and keeps the line") {
      assertTrue(
        ScaladocSnippets.objectName("schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala", 28) ==
          "Snippet_schema_shared_scala_3_DerivedOptics_L28",
        ScaladocSnippets.objectName("schema/jvm/src/main/scala/zio/blocks/schema/json/JsonReader.scala", 7) ==
          "Snippet_schema_jvm_scala_json_JsonReader_L7"
      )
    },
    test("render wraps the code in an object with the schema import and the source location") {
      val snippet = ScaladocSnippets.Snippet(path, 4, List("val a = 1", "", "  || b"), None)
      assertTrue(
        ScaladocSnippets.render(snippet) ==
          s"""// Generated from $path:4 by schema-scaladoc-check. Do not edit.
             |package scaladoccheck
             |
             |import zio.blocks.schema._
             |
             |object Snippet_schema_shared_scala_A_L4 {
             |  val a = 1
             |
             |    || b
             |}
             |""".stripMargin
      )
    },
    test("generate keeps scala-3-only snippets for Scala 3 only") {
      val content = source("/**", " * {{{", " * // scala-3-only", " * given Int = 1", " * }}}", " */")
      assertTrue(
        ScaladocSnippets.generate(Seq(path -> content), 2) == Nil,
        ScaladocSnippets.generate(Seq(path -> content), 3).map(_._1) == List("Snippet_schema_shared_scala_A_L2.scala")
      )
    }
  )
}
