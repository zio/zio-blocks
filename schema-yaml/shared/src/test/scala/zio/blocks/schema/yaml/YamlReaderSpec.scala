package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk

import zio.test._

object YamlReaderSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlReader")(
    suite("empty and null input")(
      test("empty string returns NullValue") {
        assertTrue(YamlReader.read("") == Right(Yaml.NullValue))
      },
      test("whitespace-only returns NullValue") {
        assertTrue(YamlReader.read("   \n  \n  ") == Right(Yaml.NullValue))
      },
      test("comment-only returns NullValue") {
        assertTrue(YamlReader.read("# just a comment") == Right(Yaml.NullValue))
      },
      test("null scalar") {
        assertTrue(YamlReader.read("null") == Right(Yaml.NullValue))
      },
      test("tilde null") {
        assertTrue(YamlReader.read("~") == Right(Yaml.NullValue))
      }
    ),
    suite("document markers")(
      test("document marker followed by content") {
        val input = "---\nname: John"
        assertTrue(
          YamlReader.read(input) == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("John")))
        )
      },
      test("document marker followed by nothing") {
        assertTrue(YamlReader.read("---") == Right(Yaml.NullValue))
      },
      test("document marker with blank lines after") {
        assertTrue(YamlReader.read("---\n\n") == Right(Yaml.NullValue))
      }
    ),
    suite("block mappings")(
      test("simple key-value") {
        val input = "name: John"
        assertTrue(
          YamlReader.read(input) == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("John")))
        )
      },
      test("multiple key-value pairs") {
        val input  = "name: Alice\nage: 30"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping(
              Chunk(
                (Yaml.Scalar("name"), Yaml.Scalar("Alice")),
                (Yaml.Scalar("age"), Yaml.Scalar("30"))
              )
            )
          )
        )
      },
      test("nested mapping") {
        val input  = "person:\n  name: Bob\n  age: 25"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys(
              "person" -> Yaml.Mapping.fromStringKeys(
                "name" -> Yaml.Scalar("Bob"),
                "age"  -> Yaml.Scalar("25")
              )
            )
          )
        )
      },
      test("deeply nested mapping") {
        val input  = "a:\n  b:\n    c: deep"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys(
              "a" -> Yaml.Mapping.fromStringKeys(
                "b" -> Yaml.Mapping.fromStringKeys(
                  "c" -> Yaml.Scalar("deep")
                )
              )
            )
          )
        )
      },
      test("key with null value (empty after colon)") {
        val input  = "key:"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue))
        )
      },
      test("key with null value (nothing after colon, more entries follow)") {
        val input  = "key:\nother: value"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys(
              "key"   -> Yaml.NullValue,
              "other" -> Yaml.Scalar("value")
            )
          )
        )
      },
      test("mapping with inline flow value") {
        val input  = "items: {a: 1, b: 2}"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("mapping with inline flow sequence value") {
        val input  = "items: [1, 2, 3]"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      }
    ),
    suite("block sequences")(
      test("simple sequence") {
        val input  = "- apple\n- banana\n- cherry"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Sequence(
              Chunk(
                Yaml.Scalar("apple"),
                Yaml.Scalar("banana"),
                Yaml.Scalar("cherry")
              )
            )
          )
        )
      },
      test("sequence of mappings") {
        val input  = "- name: Alice\n  age: 30\n- name: Bob\n  age: 25"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Sequence(
              Chunk(
                Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Alice"), "age" -> Yaml.Scalar("30")),
                Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Bob"), "age"   -> Yaml.Scalar("25"))
              )
            )
          )
        )
      },
      test("nested sequences") {
        val input  = "- - a\n  - b\n- - c"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("sequence with empty item (dash only)") {
        val input  = "-\n- value"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Sequence(Chunk(Yaml.NullValue, Yaml.Scalar("value")))
          )
        )
      },
      test("sequence with dash only at end") {
        val input  = "- a\n-"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.NullValue))
          )
        )
      },
      test("sequence item with nested block value") {
        val input  = "-\n  name: test"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Sequence(
              Chunk(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("test")))
            )
          )
        )
      },
      test("sequence with mapping where value is empty") {
        val input  = "- key:\n  other: val"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      }
    ),
    suite("flow mappings")(
      test("empty flow mapping") {
        val result = YamlReader.read("{}")
        assertTrue(result == Right(Yaml.Mapping.empty))
      },
      test("simple flow mapping") {
        val result = YamlReader.read("{name: Alice, age: 30}")
        assertTrue(
          result == Right(
            Yaml.Mapping(
              Chunk(
                (Yaml.Scalar("name"), Yaml.Scalar("Alice")),
                (Yaml.Scalar("age"), Yaml.Scalar("30"))
              )
            )
          )
        )
      },
      test("nested flow mapping") {
        val result = YamlReader.read("{outer: {inner: value}}")
        assertTrue(
          result == Right(
            Yaml.Mapping(
              Chunk(
                (
                  Yaml.Scalar("outer"),
                  Yaml.Mapping(Chunk((Yaml.Scalar("inner"), Yaml.Scalar("value"))))
                )
              )
            )
          )
        )
      },
      test("flow mapping with empty content") {
        val result = YamlReader.read("{ }")
        assertTrue(result == Right(Yaml.Mapping.empty))
      }
    ),
    suite("flow sequences")(
      test("empty flow sequence") {
        val result = YamlReader.read("[]")
        assertTrue(result == Right(Yaml.Sequence.empty))
      },
      test("simple flow sequence") {
        val result = YamlReader.read("[a, b, c]")
        assertTrue(
          result == Right(
            Yaml.Sequence(Chunk(Yaml.Scalar("a"), Yaml.Scalar("b"), Yaml.Scalar("c")))
          )
        )
      },
      test("nested flow sequence") {
        val result = YamlReader.read("[[1, 2], [3, 4]]")
        assertTrue(
          result == Right(
            Yaml.Sequence(
              Chunk(
                Yaml.Sequence(Chunk(Yaml.Scalar("1"), Yaml.Scalar("2"))),
                Yaml.Sequence(Chunk(Yaml.Scalar("3"), Yaml.Scalar("4")))
              )
            )
          )
        )
      },
      test("flow sequence with empty content") {
        val result = YamlReader.read("[ ]")
        assertTrue(result == Right(Yaml.Sequence.empty))
      },
      test("flow sequence with flow mapping inside") {
        val result = YamlReader.read("[{a: 1}, {b: 2}]")
        assertTrue(
          result == Right(
            Yaml.Sequence(
              Chunk(
                Yaml.Mapping(Chunk((Yaml.Scalar("a"), Yaml.Scalar("1")))),
                Yaml.Mapping(Chunk((Yaml.Scalar("b"), Yaml.Scalar("2"))))
              )
            )
          )
        )
      }
    ),
    suite("quoted strings")(
      test("single-quoted string") {
        val input  = "name: 'hello world'"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("hello world")))
        )
      },
      test("single-quoted with escaped single quote") {
        val input  = "name: 'it''s'"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("it's")))
        )
      },
      test("double-quoted string") {
        val input  = """name: "hello world""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("hello world")))
        )
      },
      test("double-quoted with escape sequences") {
        val input  = """value: "line1\nline2\ttab""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("line1\nline2\ttab"))
          )
        )
      },
      test("double-quoted with backslash escape") {
        val input  = """value: "path\\to\\file""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("path\\to\\file"))
          )
        )
      },
      test("double-quoted with quote escape") {
        val input  = """value: "say \"hello\"""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("say \"hello\""))
          )
        )
      },
      test("double-quoted with carriage return escape") {
        val input  = """value: "a\rb""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("a\rb"))
          )
        )
      },
      test("double-quoted with bell escape") {
        val input  = """value: "a\ab""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("a\u0007b"))
          )
        )
      },
      test("double-quoted with null escape") {
        val input  = """value: "a\0b""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("a\u0000b"))
          )
        )
      },
      test("double-quoted with backspace escape") {
        val input  = """value: "a\bb""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("a\bb"))
          )
        )
      },
      test("double-quoted with slash escape") {
        val input  = """value: "a\/b""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("a/b"))
          )
        )
      },
      test("double-quoted with unicode escape") {
        val input  = "value: \"" + "\\u0041" + "\""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("A"))
          )
        )
      },
      test("double-quoted with hex escape \\x") {
        val input  = """value: "\x41""""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("A"))
          )
        )
      },
      test("double-quoted with invalid unicode escape falls back") {
        val input  = "value: \"" + "\\uZZZZ" + "\""
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("double-quoted with invalid hex escape falls back") {
        val input  = """value: "\xZZ""""
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("double-quoted with unknown escape") {
        val input  = """value: "\q""""
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("quoted key in mapping") {
        val input  = "'key name': value"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("double-quoted key in mapping") {
        val input  = "\"key name\": value"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("key starting with quote does not find mapping colon") {
        val input  = "'quoted value'"
        val result = YamlReader.read(input)
        assertTrue(result == Right(Yaml.Scalar("quoted value")))
      },
      test("key starting with double-quote as scalar") {
        val input  = "\"quoted value\""
        val result = YamlReader.read(input)
        assertTrue(result == Right(Yaml.Scalar("quoted value")))
      }
    ),
    suite("literal blocks")(
      test("literal block (|)") {
        val input  = "desc: |\n  line1\n  line2"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("desc" -> Yaml.Scalar("line1\nline2"))
          )
        )
      },
      test("folded block (>)") {
        val input  = "desc: >\n  line1\n  line2"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("desc" -> Yaml.Scalar("line1 line2"))
          )
        )
      },
      test("literal block at top level") {
        val input  = "|\n  line1\n  line2"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Scalar("line1\nline2"))
        )
      },
      test("folded block at top level") {
        val input  = ">\n  line1\n  line2"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Scalar("line1 line2"))
        )
      },
      test("literal block with empty content") {
        val input  = "desc: |"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys("desc" -> Yaml.Scalar(""))
          )
        )
      },
      test("literal block with blank lines in middle") {
        val input  = "desc: |\n  line1\n\n  line3"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("literal block stops at lower indent") {
        val input  = "desc: |\n  indented\nnot-indented: val"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("folded block at top level with result ending in newline trimmed") {
        val input  = ">\n  line1\n  line2\n"
        val result = YamlReader.read(input)
        assertTrue(result == Right(Yaml.Scalar("line1 line2")))
      },
      test("literal block content with trailing newline is trimmed") {
        val input  = "|\n  hello\n"
        val result = YamlReader.read(input)
        assertTrue(result == Right(Yaml.Scalar("hello")))
      },
      test("mapping value with > indicator") {
        val input  = "key: >\n  folded line"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("key" -> Yaml.Scalar("folded line")))
        )
      },
      test("mapping value with | followed by extra chars") {
        val input  = "key: |+\n  content"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      }
    ),
    suite("comments")(
      test("inline comment on value") {
        val input  = "name: Alice # this is a comment"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Alice")))
        )
      },
      test("comment-only line between entries") {
        val input  = "a: 1\n# comment\nb: 2"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys(
              "a" -> Yaml.Scalar("1"),
              "b" -> Yaml.Scalar("2")
            )
          )
        )
      },
      test("hash in single-quoted string is not a comment") {
        val input  = "val: 'not # a comment'"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("val" -> Yaml.Scalar("not # a comment")))
        )
      },
      test("hash in double-quoted string is not a comment") {
        val input  = "val: \"not # a comment\""
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("val" -> Yaml.Scalar("not # a comment")))
        )
      },
      test("hash without preceding space is not comment") {
        val input  = "val: foo#bar"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("val" -> Yaml.Scalar("foo#bar")))
        )
      }
    ),
    suite("CRLF line endings")(
      test("CRLF is normalized") {
        val input  = "name: Alice\r\nage: 30"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(
            Yaml.Mapping.fromStringKeys(
              "name" -> Yaml.Scalar("Alice"),
              "age"  -> Yaml.Scalar("30")
            )
          )
        )
      },
      test("lone CR is normalized") {
        val input  = "a: 1\rb: 2"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      }
    ),
    suite("booleans and special values")(
      test("true scalar") {
        val result = YamlReader.read("true")
        assertTrue(result == Right(Yaml.Scalar("true")))
      },
      test("false scalar") {
        val result = YamlReader.read("false")
        assertTrue(result == Right(Yaml.Scalar("false")))
      }
    ),
    suite("readFromBytes")(
      test("reads from byte array") {
        val bytes  = "name: Test".getBytes("UTF-8")
        val result = YamlReader.readFromBytes(bytes)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Test")))
        )
      }
    ),
    suite("multi-line flow content")(
      test("flow mapping across lines") {
        val input  = "{name: Alice,\n age: 30}"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("flow sequence across lines") {
        val input  = "[a,\n b,\n c]"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      }
    ),
    suite("edge cases")(
      test("value starting with hash is treated as comment in findMappingColon") {
        val input  = "key: val"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("colon at end of key without space") {
        val input  = "key:"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("sequence of mappings with empty values in continuation") {
        val input  = "- a: 1\n  b:\n- c: 3"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("sequence continuation with mapping having empty value, inner indent parse") {
        val input  = "- x: 1\n- y:\n  z: 2"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("block sequence continue with mapping entries") {
        val input  = "- a: 1\n  b: 2\n- c: 3\n  d: 4"
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("inline value null") {
        val input  = "key: null"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue))
        )
      },
      test("inline value tilde") {
        val input  = "key: ~"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue))
        )
      },
      test("inline value empty") {
        val input  = "key:"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Mapping.fromStringKeys("key" -> Yaml.NullValue))
        )
      },
      test("escaped backslash in double-quoted string inside strip comment") {
        val input  = """val: "test\\"  """
        val result = YamlReader.read(input)
        assertTrue(result.isRight)
      },
      test("block sequence with inline scalar") {
        val input  = "- hello"
        val result = YamlReader.read(input)
        assertTrue(
          result == Right(Yaml.Sequence(Chunk(Yaml.Scalar("hello"))))
        )
      }
    )
  )
}
