package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.test._

object DynamicOpticToStringSpec extends ZIOSpecDefault {
  def spec = suite("DynamicOpticToStringSpec")(
    suite("root and empty paths")(
      test("renders root as '.'") {
        assertTrue(DynamicOptic.root.toString == ".")
      },
      test("renders empty DynamicOptic as '.'") {
        assertTrue(DynamicOptic(Vector.empty).toString == ".")
      }
    ),
    suite("Field access")(
      test("renders simple field") {
        val optic = DynamicOptic.root.field("name")
        assertTrue(optic.toString == ".name")
      },
      test("renders chained fields") {
        val optic = DynamicOptic.root.field("address").field("street")
        assertTrue(optic.toString == ".address.street")
      },
      test("renders deeply nested fields") {
        val optic = DynamicOptic.root
          .field("user")
          .field("profile")
          .field("settings")
          .field("theme")
        assertTrue(optic.toString == ".user.profile.settings.theme")
      },
      test("renders fields with underscores") {
        val optic = DynamicOptic.root.field("_private")
        assertTrue(optic.toString == "._private")
      },
      test("renders fields with digits") {
        val optic = DynamicOptic.root.field("field123")
        assertTrue(optic.toString == ".field123")
      },
      test("renders unicode field names") {
        val optic = DynamicOptic.root.field("café")
        assertTrue(optic.toString == ".café")
      },
      test("renders keyword-like field names") {
        val optic = DynamicOptic.root.field("true").field("false").field("null")
        assertTrue(optic.toString == ".true.false.null")
      }
    ),
    suite("Case access")(
      test("renders simple case") {
        val optic = DynamicOptic.root.caseOf("Some")
        assertTrue(optic.toString == "<Some>")
      },
      test("renders chained cases") {
        val optic = DynamicOptic.root.caseOf("Left").caseOf("Error")
        assertTrue(optic.toString == "<Left><Error>")
      },
      test("renders case after field") {
        val optic = DynamicOptic.root.field("result").caseOf("Success")
        assertTrue(optic.toString == ".result<Success>")
      },
      test("renders common variant cases") {
        assertTrue(
          DynamicOptic.root.caseOf("None").toString == "<None>",
          DynamicOptic.root.caseOf("Some").toString == "<Some>",
          DynamicOptic.root.caseOf("Left").toString == "<Left>",
          DynamicOptic.root.caseOf("Right").toString == "<Right>"
        )
      },
      test("renders case with underscore") {
        val optic = DynamicOptic.root.caseOf("_Empty")
        assertTrue(optic.toString == "<_Empty>")
      },
      test("renders case with digits") {
        val optic = DynamicOptic.root.caseOf("Case1")
        assertTrue(optic.toString == "<Case1>")
      }
    ),
    suite("Index access")(
      test("renders single index - zero") {
        val optic = DynamicOptic.root.at(0)
        assertTrue(optic.toString == "[0]")
      },
      test("renders single index - positive") {
        val optic = DynamicOptic.root.at(42)
        assertTrue(optic.toString == "[42]")
      },
      test("renders single index - large") {
        val optic = DynamicOptic.root.at(Int.MaxValue)
        assertTrue(optic.toString == s"[${Int.MaxValue}]")
      },
      test("renders multiple indices") {
        val optic = DynamicOptic.root.atIndices(0, 2, 5)
        assertTrue(optic.toString == "[0,2,5]")
      },
      test("renders multiple indices - single element") {
        val optic = DynamicOptic.root.atIndices(7)
        assertTrue(optic.toString == "[7]")
      },
      test("renders multiple indices - many elements") {
        val optic = DynamicOptic.root.atIndices(1, 3, 5, 7, 9, 11)
        assertTrue(optic.toString == "[1,3,5,7,9,11]")
      },
      test("renders empty indices") {
        val optic = DynamicOptic.root.atIndices()
        assertTrue(optic.toString == "[]")
      },
      test("renders index after field") {
        val optic = DynamicOptic.root.field("items").at(0)
        assertTrue(optic.toString == ".items[0]")
      }
    ),
    suite("Element selector")(
      test("renders elements wildcard") {
        val optic = DynamicOptic.elements
        assertTrue(optic.toString == "[*]")
      },
      test("renders elements after field") {
        val optic = DynamicOptic.root.field("users").elements
        assertTrue(optic.toString == ".users[*]")
      },
      test("renders nested element selectors") {
        val optic = DynamicOptic.root.elements.elements
        assertTrue(optic.toString == "[*][*]")
      },
      test("renders element then field") {
        val optic = DynamicOptic.root.field("users").elements.field("email")
        assertTrue(optic.toString == ".users[*].email")
      }
    ),
    suite("Map access - string keys")(
      test("renders simple string key") {
        val optic = DynamicOptic.root.atKey("host")
        assertTrue(optic.toString == """{"host"}""")
      },
      test("renders string key with spaces") {
        val optic = DynamicOptic.root.atKey("foo bar")
        assertTrue(optic.toString == """{"foo bar"}""")
      },
      test("renders unicode string key") {
        val optic = DynamicOptic.root.atKey("日本語")
        assertTrue(optic.toString == """{"日本語"}""")
      },
      test("renders emoji string key") {
        val optic = DynamicOptic.root.atKey("🎉")
        assertTrue(optic.toString == """{"🎉"}""")
      },
      test("renders empty string key") {
        val optic = DynamicOptic.root.atKey("")
        assertTrue(optic.toString == """{""}""")
      },
      test("renders string key with escape sequences - newline") {
        val optic = DynamicOptic.root.atKey("foo\nbar")
        assertTrue(optic.toString == """{"foo\nbar"}""")
      },
      test("renders string key with escape sequences - tab") {
        val optic = DynamicOptic.root.atKey("foo\tbar")
        assertTrue(optic.toString == """{"foo\tbar"}""")
      },
      test("renders string key with escape sequences - carriage return") {
        val optic = DynamicOptic.root.atKey("foo\rbar")
        assertTrue(optic.toString == """{"foo\rbar"}""")
      },
      test("renders string key with escape sequences - quote") {
        val optic = DynamicOptic.root.atKey("foo\"bar")
        assertTrue(optic.toString == """{"foo\"bar"}""")
      },
      test("renders string key with escape sequences - backslash") {
        val optic = DynamicOptic.root.atKey("foo\\bar")
        assertTrue(optic.toString == """{"foo\\bar"}""")
      },
      test("renders multiple string keys") {
        val optic = DynamicOptic.root.atKeys("foo", "bar", "baz")
        assertTrue(optic.toString == """{"foo", "bar", "baz"}""")
      },
      test("renders map key after field") {
        val optic = DynamicOptic.root.field("config").atKey("host")
        assertTrue(optic.toString == """.config{"host"}""")
      }
    ),
    suite("Map access - integer keys")(
      test("renders zero integer key") {
        val optic = DynamicOptic.root.atKey(0)
        assertTrue(optic.toString == "{0}")
      },
      test("renders positive integer key") {
        val optic = DynamicOptic.root.atKey(42)
        assertTrue(optic.toString == "{42}")
      },
      test("renders negative integer key") {
        val optic = DynamicOptic.root.atKey(-42)
        assertTrue(optic.toString == "{-42}")
      },
      test("renders max integer key") {
        val optic = DynamicOptic.root.atKey(Int.MaxValue)
        assertTrue(optic.toString == s"{${Int.MaxValue}}")
      },
      test("renders min integer key") {
        val optic = DynamicOptic.root.atKey(Int.MinValue)
        assertTrue(optic.toString == s"{${Int.MinValue}}")
      },
      test("renders multiple integer keys") {
        val optic = DynamicOptic.root.atKeys(1, 2, 3)
        assertTrue(optic.toString == "{1, 2, 3}")
      },
      test("renders integer key after field") {
        val optic = DynamicOptic.root.field("ports").atKey(80)
        assertTrue(optic.toString == ".ports{80}")
      }
    ),
    suite("Map access - boolean keys")(
      test("renders true boolean key") {
        val optic = DynamicOptic.root.atKey(true)
        assertTrue(optic.toString == "{true}")
      },
      test("renders false boolean key") {
        val optic = DynamicOptic.root.atKey(false)
        assertTrue(optic.toString == "{false}")
      },
      test("renders multiple boolean keys") {
        val optic = DynamicOptic.root.atKeys(true, false)
        assertTrue(optic.toString == "{true, false}")
      }
    ),
    suite("Map access - char keys")(
      test("renders simple char key") {
        val optic = DynamicOptic.root.atKey('a')
        assertTrue(optic.toString == "{'a'}")
      },
      test("renders space char key") {
        val optic = DynamicOptic.root.atKey(' ')
        assertTrue(optic.toString == "{' '}")
      },
      test("renders digit char key") {
        val optic = DynamicOptic.root.atKey('9')
        assertTrue(optic.toString == "{'9'}")
      },
      test("renders char key with escape - newline") {
        val optic = DynamicOptic.root.atKey('\n')
        assertTrue(optic.toString == """{'\\n'}""")
      },
      test("renders char key with escape - tab") {
        val optic = DynamicOptic.root.atKey('\t')
        assertTrue(optic.toString == """{'\\t'}""")
      },
      test("renders char key with escape - carriage return") {
        val optic = DynamicOptic.root.atKey('\r')
        assertTrue(optic.toString == """{'\\r'}""")
      },
      test("renders char key with escape - single quote") {
        val optic = DynamicOptic.root.atKey('\'')
        assertTrue(optic.toString == """{'\\''}""")
      },
      test("renders char key with escape - backslash") {
        val optic = DynamicOptic.root.atKey('\\')
        assertTrue(optic.toString == """{'\\\\'}""")
      }
    ),
    suite("Map selectors")(
      test("renders map values wildcard") {
        val optic = DynamicOptic.mapValues
        assertTrue(optic.toString == "{*}")
      },
      test("renders map keys wildcard") {
        val optic = DynamicOptic.mapKeys
        assertTrue(optic.toString == "{*:}")
      },
      test("renders map values after field") {
        val optic = DynamicOptic.root.field("lookup").mapValues
        assertTrue(optic.toString == ".lookup{*}")
      },
      test("renders map keys after field") {
        val optic = DynamicOptic.root.field("ports").mapKeys
        assertTrue(optic.toString == ".ports{*:}")
      },
      test("renders nested map values") {
        val optic = DynamicOptic.root.mapValues.mapValues
        assertTrue(optic.toString == "{*}{*}")
      },
      test("renders nested map keys") {
        val optic = DynamicOptic.root.mapKeys.mapKeys
        assertTrue(optic.toString == "{*:}{*:}")
      }
    ),
    suite("Wrapped")(
      test("renders wrapped") {
        val optic = DynamicOptic.wrapped
        assertTrue(optic.toString == ".~")
      },
      test("renders wrapped after field") {
        val optic = DynamicOptic.root.field("userId").wrapped
        assertTrue(optic.toString == ".userId.~")
      }
    ),
    suite("Combined paths")(
      test("field then sequence index") {
        val optic = DynamicOptic.root.field("items").at(0)
        assertTrue(optic.toString == ".items[0]")
      },
      test("field then sequence wildcard") {
        val optic = DynamicOptic.root.field("items").elements
        assertTrue(optic.toString == ".items[*]")
      },
      test("field then map string key") {
        val optic = DynamicOptic.root.field("config").atKey("host")
        assertTrue(optic.toString == """.config{"host"}""")
      },
      test("field then map integer key") {
        val optic = DynamicOptic.root.field("settings").atKey(42)
        assertTrue(optic.toString == ".settings{42}")
      },
      test("field then variant case") {
        val optic = DynamicOptic.root.field("result").caseOf("Success")
        assertTrue(optic.toString == ".result<Success>")
      },
      test("sequence then field") {
        val optic = DynamicOptic.root.field("users").at(0).field("name")
        assertTrue(optic.toString == ".users[0].name")
      },
      test("sequence wildcard then field") {
        val optic = DynamicOptic.root.field("users").elements.field("email")
        assertTrue(optic.toString == ".users[*].email")
      },
      test("map values then field") {
        val optic = DynamicOptic.root.field("lookup").mapValues.field("value")
        assertTrue(optic.toString == ".lookup{*}.value")
      },
      test("variant then field") {
        val optic = DynamicOptic.root.field("response").caseOf("Ok").field("body")
        assertTrue(optic.toString == ".response<Ok>.body")
      }
    ),
    suite("Complex nested paths")(
      test("deeply nested mixed navigation") {
        val optic = DynamicOptic.root
          .field("root")
          .field("children")
          .elements
          .field("metadata")
          .atKey("tags")
          .at(0)
        assertTrue(optic.toString == """.root.children[*].metadata{"tags"}[0]""")
      },
      test("all node types in single path") {
        val optic = DynamicOptic.root
          .field("a")
          .at(0)
          .atKey("k")
          .caseOf("V")
          .field("b")
          .elements
          .mapValues
          .field("c")
          .mapKeys
        assertTrue(optic.toString == """.a[0]{"k"}<V>.b[*]{*}.c{*:}""")
      },
      test("multiple indices in complex path") {
        val optic = DynamicOptic.root
          .field("data")
          .atIndices(0, 2, 5)
          .field("value")
        assertTrue(optic.toString == ".data[0,2,5].value")
      },
      test("multiple keys in complex path") {
        val optic = DynamicOptic.root
          .field("config")
          .atKeys("host", "port", "timeout")
        assertTrue(optic.toString == """.config{"host", "port", "timeout"}""")
      },
      test("real-world example: API response") {
        val optic = DynamicOptic.root
          .field("items")
          .at(0)
          .field("metadata")
          .field("version")
        assertTrue(optic.toString == ".items[0].metadata.version")
      },
      test("real-world example: all user emails") {
        val optic = DynamicOptic.root
          .field("users")
          .elements
          .field("email")
        assertTrue(optic.toString == ".users[*].email")
      },
      test("real-world example: first tag of each item") {
        val optic = DynamicOptic.root
          .field("items")
          .elements
          .field("metadata")
          .field("tags")
          .at(0)
        assertTrue(optic.toString == ".items[*].metadata.tags[0]")
      },
      test("real-world example: config lookup") {
        val optic = DynamicOptic.root
          .field("config")
          .atKey("api_key")
        assertTrue(optic.toString == """.config{"api_key"}""")
      },
      test("real-world example: variant navigation") {
        val optic = DynamicOptic.root
          .field("result")
          .caseOf("Success")
          .field("value")
        assertTrue(optic.toString == ".result<Success>.value")
      }
    ),
    suite("Edge cases")(
      test("very long field chain") {
        val optic = DynamicOptic.root
          .field("a")
          .field("b")
          .field("c")
          .field("d")
          .field("e")
          .field("f")
          .field("g")
          .field("h")
        assertTrue(optic.toString == ".a.b.c.d.e.f.g.h")
      },
      test("many indices") {
        val indices  = (0 to 20).toSeq
        val optic    = DynamicOptic.root.atIndices(indices: _*)
        val expected = s"[${indices.mkString(",")}]"
        assertTrue(optic.toString == expected)
      },
      test("many keys") {
        val keys     = Seq("a", "b", "c", "d", "e")
        val optic    = DynamicOptic.root.atKeys(keys: _*)
        val expected = s"""{${keys.map(k => s""""$k"""").mkString(", ")}}"""
        assertTrue(optic.toString == expected)
      },
      test("string with all escape sequences") {
        val str   = "line1\nline2\tindented\rcarriage\"quote\\backslash"
        val optic = DynamicOptic.root.atKey(str)
        assertTrue(optic.toString == """{"line1\nline2\tindented\rcarriage\"quote\\backslash"}""")
      },
      test("char with all escape sequences") {
        assertTrue(
          DynamicOptic.root.atKey('\n').toString == """{'\\n'}""",
          DynamicOptic.root.atKey('\t').toString == """{'\\t'}""",
          DynamicOptic.root.atKey('\r').toString == """{'\\r'}""",
          DynamicOptic.root.atKey('\'').toString == """{'\\''}""",
          DynamicOptic.root.atKey('\\').toString == """{'\\\\'}"""
        )
      }
    ),
    suite("Copy-pasteable to path interpolator")(
      test("simple paths should be copy-pasteable") {
        // These demonstrate that toString output should be valid path interpolator input
        // We can't actually test p"..." here since it's a macro, but we document the expected format
        val examples = Seq(
          DynamicOptic.root.field("name").toString                     -> ".name",
          DynamicOptic.root.field("address").field("street").toString  -> ".address.street",
          DynamicOptic.root.field("name").caseOf("Some").toString      -> ".name<Some>",
          DynamicOptic.root.field("users").at(0).toString              -> ".users[0]",
          DynamicOptic.root.field("users").atIndices(0, 2, 5).toString -> ".users[0,2,5]",
          DynamicOptic.root.field("items").elements.toString           -> ".items[*]",
          DynamicOptic.root.field("config").atKey("host").toString     -> """.config{"host"}""",
          DynamicOptic.root.field("ports").atKey(80).toString          -> ".ports{80}",
          DynamicOptic.root.field("lookup").mapValues.toString         -> ".lookup{*}",
          DynamicOptic.root.field("ports").mapKeys.toString            -> ".ports{*:}"
        )
        assertTrue(examples.forall { case (actual, expected) => actual == expected })
      }
    )
  )
}
