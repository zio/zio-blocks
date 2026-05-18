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

package zio.blocks.config.hocon

import zio.test._

object HoconParserSpec extends ZIOSpecDefault {

  private def parsed(input: String): HoconValue =
    HoconParser.parse(input) match {
      case Right(v) => v
      case Left(e)  => throw e
    }

  private def parseFails(input: String): Boolean =
    HoconParser.parse(input).isLeft

  def spec: Spec[Any, Any] = suite("HoconParser")(
    suite("basic objects")(
      test("parses simple object with = separator") {
        val result = parsed("""{ key = "value" }""")
        assertTrue(result == HoconValue.Obj(Map("key" -> HoconValue.Str("value"))))
      },
      test("parses simple object with : separator") {
        val result = parsed("""{ key: "value" }""")
        assertTrue(result == HoconValue.Obj(Map("key" -> HoconValue.Str("value"))))
      },
      test("parses multiple fields") {
        val result = parsed("""{ a = 1, b = 2 }""")
        assertTrue(
          result == HoconValue.Obj(Map("a" -> HoconValue.Num(1.0), "b" -> HoconValue.Num(2.0)))
        )
      },
      test("parses empty object") {
        val result = parsed("{}")
        assertTrue(result == HoconValue.Obj(Map.empty))
      }
    ),
    suite("arrays")(
      test("parses simple array") {
        val result = parsed("""{ arr = [1, 2, 3] }""")
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "arr" -> HoconValue.Arr(
                Seq(
                  HoconValue.Num(1.0),
                  HoconValue.Num(2.0),
                  HoconValue.Num(3.0)
                )
              )
            )
          )
        )
      },
      test("parses array of strings") {
        val result = parsed("""{ arr = ["a", "b"] }""")
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "arr" -> HoconValue.Arr(
                Seq(
                  HoconValue.Str("a"),
                  HoconValue.Str("b")
                )
              )
            )
          )
        )
      },
      test("parses empty array") {
        val result = parsed("{ arr = [] }")
        assertTrue(result == HoconValue.Obj(Map("arr" -> HoconValue.Arr(Seq.empty))))
      },
      test("parses newline-separated array elements") {
        val input =
          """{
            |  arr = [
            |    1
            |    2
            |    3
            |  ]
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "arr" -> HoconValue.Arr(
                Seq(
                  HoconValue.Num(1.0),
                  HoconValue.Num(2.0),
                  HoconValue.Num(3.0)
                )
              )
            )
          )
        )
      }
    ),
    suite("strings")(
      test("parses quoted strings with escapes") {
        val result = parsed("""{ s = "hello\nworld" }""")
        assertTrue(result == HoconValue.Obj(Map("s" -> HoconValue.Str("hello\nworld"))))
      },
      test("parses multi-line triple-quoted strings") {
        val input  = "{ s = \"\"\"line1\nline2\"\"\" }"
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("s" -> HoconValue.Str("line1\nline2"))))
      },
      test("parses unquoted strings as values") {
        val result = parsed("{ path = /usr/local/bin }")
        assertTrue(result == HoconValue.Obj(Map("path" -> HoconValue.Str("/usr/local/bin"))))
      }
    ),
    suite("numbers")(
      test("parses integers") {
        val result = parsed("{ n = 42 }")
        assertTrue(result == HoconValue.Obj(Map("n" -> HoconValue.Num(42.0))))
      },
      test("parses decimals") {
        val result = parsed("{ n = 3.14 }")
        assertTrue(result == HoconValue.Obj(Map("n" -> HoconValue.Num(3.14))))
      },
      test("parses negative numbers") {
        val result = parsed("{ n = -10 }")
        assertTrue(result == HoconValue.Obj(Map("n" -> HoconValue.Num(-10.0))))
      }
    ),
    suite("booleans and null")(
      test("parses true") {
        val result = parsed("{ b = true }")
        assertTrue(result == HoconValue.Obj(Map("b" -> HoconValue.Bool(true))))
      },
      test("parses false") {
        val result = parsed("{ b = false }")
        assertTrue(result == HoconValue.Obj(Map("b" -> HoconValue.Bool(false))))
      },
      test("parses null") {
        val result = parsed("{ n = null }")
        assertTrue(result == HoconValue.Obj(Map("n" -> HoconValue.Null)))
      }
    ),
    suite("comments")(
      test("skips # comments") {
        val input =
          """# This is a comment
            |{ key = "value" }""".stripMargin
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("key" -> HoconValue.Str("value"))))
      },
      test("skips // comments") {
        val input =
          """// This is a comment
            |{ key = "value" }""".stripMargin
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("key" -> HoconValue.Str("value"))))
      },
      test("skips inline comments after values") {
        val input =
          """{
            |  a = 1 # inline comment
            |  b = 2 // another inline comment
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(Map("a" -> HoconValue.Num(1.0), "b" -> HoconValue.Num(2.0)))
        )
      }
    ),
    suite("key path syntax")(
      test("a.b.c = 1 desugars to nested objects") {
        val result = parsed("a.b.c = 1")
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "a" -> HoconValue.Obj(
                Map(
                  "b" -> HoconValue.Obj(
                    Map(
                      "c" -> HoconValue.Num(1.0)
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("key path with braces") {
        val result = parsed("""{ a.b = "val" }""")
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "a" -> HoconValue.Obj(
                Map(
                  "b" -> HoconValue.Str("val")
                )
              )
            )
          )
        )
      }
    ),
    suite("object merging")(
      test("later keys override earlier ones") {
        val input =
          """{
            |  a = 1
            |  a = 2
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("a" -> HoconValue.Num(2.0))))
      },
      test("nested objects deep-merge") {
        val input =
          """{
            |  db { host = "localhost" }
            |  db { port = 5432 }
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "db" -> HoconValue.Obj(
                Map(
                  "host" -> HoconValue.Str("localhost"),
                  "port" -> HoconValue.Num(5432.0)
                )
              )
            )
          )
        )
      },
      test("key path deep-merges with explicit object") {
        val input =
          """{
            |  a.b = 1
            |  a.c = 2
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "a" -> HoconValue.Obj(
                Map(
                  "b" -> HoconValue.Num(1.0),
                  "c" -> HoconValue.Num(2.0)
                )
              )
            )
          )
        )
      }
    ),
    suite("substitutions")(
      test("resolves simple substitution") {
        val input =
          """{
            |  host = "localhost"
            |  url = ${host}
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "host" -> HoconValue.Str("localhost"),
              "url"  -> HoconValue.Str("localhost")
            )
          )
        )
      },
      test("resolves nested path substitution") {
        val input =
          """{
            |  db { host = "localhost" }
            |  url = ${db.host}
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "db"  -> HoconValue.Obj(Map("host" -> HoconValue.Str("localhost"))),
              "url" -> HoconValue.Str("localhost")
            )
          )
        )
      },
      test("optional substitution resolves to null when absent") {
        val input =
          """{
            |  val = ${?missing}
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("val" -> HoconValue.Null)))
      },
      test("circular substitution produces error") {
        val input =
          """{
            |  a = ${b}
            |  b = ${a}
            |}""".stripMargin
        assertTrue(parseFails(input))
      }
    ),
    suite("+= append")(
      test("appends to existing array") {
        val input =
          """{
            |  arr = [1, 2]
            |  arr += 3
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "arr" -> HoconValue.Arr(
                Seq(
                  HoconValue.Num(1.0),
                  HoconValue.Num(2.0),
                  HoconValue.Num(3.0)
                )
              )
            )
          )
        )
      },
      test("creates array when appending to non-existent key") {
        val input =
          """{
            |  arr += 1
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(Map("arr" -> HoconValue.Arr(Seq(HoconValue.Num(1.0)))))
        )
      }
    ),
    suite("root braces optional")(
      test("parses top-level key = value lines without braces") {
        val input =
          """host = "localhost"
            |port = 8080""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "host" -> HoconValue.Str("localhost"),
              "port" -> HoconValue.Num(8080.0)
            )
          )
        )
      },
      test("parses empty input as empty object") {
        val result = parsed("")
        assertTrue(result == HoconValue.Obj(Map.empty))
      }
    ),
    suite("comma optional")(
      test("newline-separated fields are valid") {
        val input =
          """{
            |  a = 1
            |  b = 2
            |  c = 3
            |}""".stripMargin
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "a" -> HoconValue.Num(1.0),
              "b" -> HoconValue.Num(2.0),
              "c" -> HoconValue.Num(3.0)
            )
          )
        )
      }
    ),
    suite("error cases")(
      test("unterminated string reports error") {
        assertTrue(parseFails("""{ key = "unterminated }"""))
      },
      test("unterminated object reports error") {
        assertTrue(parseFails("{ key = 1"))
      },
      test("error contains line and column") {
        val result = HoconParser.parse("{\n  key = \"bad")
        result match {
          case Left(err) =>
            assertTrue(err.line > 0 && err.column > 0)
          case Right(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("flatten")(
      test("flattens nested object to dot-separated map") {
        val input =
          """{
            |  db {
            |    host = "localhost"
            |    port = 5432
            |  }
            |  app.name = "myapp"
            |}""".stripMargin
        val result = parsed(input)
        val flat   = HoconValue.flatten(result)
        assertTrue(
          flat == Map(
            "db.host"  -> "localhost",
            "db.port"  -> "5432",
            "app.name" -> "myapp"
          )
        )
      },
      test("flattens arrays with numeric indices") {
        val input  = """{ items = ["a", "b", "c"] }"""
        val result = parsed(input)
        val flat   = HoconValue.flatten(result)
        assertTrue(
          flat == Map(
            "items.0" -> "a",
            "items.1" -> "b",
            "items.2" -> "c"
          )
        )
      },
      test("skips null values in flatten") {
        val input  = """{ a = 1, b = null }"""
        val result = parsed(input)
        val flat   = HoconValue.flatten(result)
        assertTrue(flat == Map("a" -> "1"))
      },
      test("flattens booleans as strings") {
        val input  = """{ enabled = true, disabled = false }"""
        val result = parsed(input)
        val flat   = HoconValue.flatten(result)
        assertTrue(flat == Map("enabled" -> "true", "disabled" -> "false"))
      }
    ),
    suite("include")(
      test("includes content via callback") {
        val included = """extra = "included_value""""
        val input    = """include "other.conf"
                         |main = "main_value"""".stripMargin
        val result = HoconParser.parse(
          input,
          resource =>
            if (resource == "other.conf") Some(included)
            else None
        )
        assertTrue(result.isRight) && {
          val v = result.toOption.get
          assertTrue(
            v == HoconValue.Obj(
              Map(
                "extra" -> HoconValue.Str("included_value"),
                "main"  -> HoconValue.Str("main_value")
              )
            )
          )
        }
      },
      test("silently ignores missing includes") {
        val input = """include "missing.conf"
                      |key = "value"""".stripMargin
        val result = parsed(input)
        assertTrue(result == HoconValue.Obj(Map("key" -> HoconValue.Str("value"))))
      }
    ),
    suite("nested object without separator")(
      test("key followed by { is a nested object") {
        val input  = """{ server { host = "localhost", port = 80 } }"""
        val result = parsed(input)
        assertTrue(
          result == HoconValue.Obj(
            Map(
              "server" -> HoconValue.Obj(
                Map(
                  "host" -> HoconValue.Str("localhost"),
                  "port" -> HoconValue.Num(80.0)
                )
              )
            )
          )
        )
      }
    ),
    suite("value with trailing whitespace")(
      test("extra spaces before closing brace") {
        val result = parsed("""{ a = 1   }""")
        assertTrue(result == HoconValue.Obj(Map("a" -> HoconValue.Num(1.0))))
      }
    )
  )
}
