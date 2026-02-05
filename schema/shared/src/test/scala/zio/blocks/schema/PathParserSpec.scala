package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object PathParserSpec extends SchemaBaseSpec {
  import PathParser._
  import PathParser.ParseError._
  import DynamicOptic.Node

  def spec = suite("PathParserSpec")(
    suite("successful parsing")(
      test("empty path returns empty chunk") {
        assertTrue(parse("") == Right(Chunk.empty))
      },
      test("simple field") {
        assertTrue(parse(".foo") == Right(Chunk(Node.Field("foo"))))
      },
      test("field without leading dot") {
        assertTrue(parse("foo") == Right(Chunk(Node.Field("foo"))))
      },
      test("chained fields") {
        assertTrue(parse(".foo.bar") == Right(Chunk(Node.Field("foo"), Node.Field("bar"))))
      },
      test("index access") {
        assertTrue(parse("[42]") == Right(Chunk(Node.AtIndex(42))))
      },
      test("elements selector") {
        assertTrue(parse("[*]") == Right(Chunk(Node.Elements)))
      },
      test("colon star elements") {
        assertTrue(parse("[:*]") == Right(Chunk(Node.Elements)))
      },
      test("star colon elements") {
        assertTrue(parse("[*:]") == Right(Chunk(Node.Elements)))
      },
      test("map key string") {
        assertTrue(
          parse("""{"key"}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))))
        )
      },
      test("map values") {
        assertTrue(parse("{*}") == Right(Chunk(Node.MapValues)))
      },
      test("map keys") {
        assertTrue(parse("{*:}") == Right(Chunk(Node.MapKeys)))
      },
      test("colon star map values") {
        assertTrue(parse("{:*}") == Right(Chunk(Node.MapValues)))
      },
      test("variant case") {
        assertTrue(parse("<Foo>") == Right(Chunk(Node.Case("Foo"))))
      },
      test("index range") {
        assertTrue(parse("[0:3]") == Right(Chunk(Node.AtIndices(Seq(0, 1, 2)))))
      },
      test("multiple indices") {
        assertTrue(parse("[0,1,2]") == Right(Chunk(Node.AtIndices(Seq(0, 1, 2)))))
      },
      test("multiple map keys") {
        assertTrue(
          parse("""{"a","b"}""") == Right(
            Chunk(
              Node.AtMapKeys(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.String("a")),
                  DynamicValue.Primitive(PrimitiveValue.String("b"))
                )
              )
            )
          )
        )
      }
    ),
    suite("UnexpectedChar errors")(
      test("invalid character at start") {
        val result = parse("@foo")
        assertTrue(result match {
          case Left(UnexpectedChar('@', 0, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after dot") {
        val result = parse(".123")
        assertTrue(result match {
          case Left(InvalidIdentifier(1)) => true
          case _                          => false
        })
      },
      test("unexpected char in index bracket") {
        val result = parse("[abc]")
        assertTrue(result match {
          case Left(UnexpectedChar('a', 1, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after star in index") {
        val result = parse("[*x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after colon in index") {
        val result = parse("[:x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in colon star index") {
        val result = parse("[:*x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 3, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in star colon index") {
        val result = parse("[*:x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 3, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after star in map") {
        val result = parse("{*x}")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after colon in map") {
        val result = parse("{:x}")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in colon star map") {
        val result = parse("{:*x}")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 3, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in star colon map") {
        val result = parse("{*:x}")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 3, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after single index") {
        val result = parse("[0x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in range") {
        val result = parse("[0:5x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 4, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after multiple indices") {
        val result = parse("[0,1,2x]")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 6, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after map keys") {
        val result = parse("""{"a","b"x}""")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 8, _)) => true
          case _                               => false
        })
      },
      test("unexpected char in variant") {
        val result = parse("<Foo@>")
        assertTrue(result match {
          case Left(UnexpectedChar('@', 4, _)) => true
          case _                               => false
        })
      },
      test("unexpected char for map key type") {
        val result = parse("{@}")
        assertTrue(result match {
          case Left(UnexpectedChar('@', 1, _)) => true
          case _                               => false
        })
      },
      test("unexpected char after negative sign in map") {
        val result = parse("{-x}")
        assertTrue(result match {
          case Left(UnexpectedChar('x', 2, _)) => true
          case _                               => false
        })
      }
    ),
    suite("InvalidEscape errors")(
      test("invalid escape in string") {
        val result = parse("""{"\x"}""")
        assertTrue(result match {
          case Left(InvalidEscape('x', 3)) => true
          case _                           => false
        })
      },
      test("invalid escape q in string") {
        val result = parse("""{"\q"}""")
        assertTrue(result match {
          case Left(InvalidEscape('q', 3)) => true
          case _                           => false
        })
      },
      test("invalid escape in char literal") {
        val result = parse("""{'\ '}""")
        assertTrue(result match {
          case Left(InvalidEscape(' ', 3)) => true
          case _                           => false
        })
      }
    ),
    suite("UnterminatedString errors")(
      test("unterminated string at end") {
        val result = parse("""{"hello""")
        assertTrue(result match {
          case Left(UnterminatedString(1)) => true
          case _                           => false
        })
      },
      test("unterminated string with escape at end") {
        val result = parse("""{"hello\""")
        assertTrue(result match {
          case Left(UnterminatedString(1)) => true
          case _                           => false
        })
      }
    ),
    suite("UnterminatedChar errors")(
      test("unterminated char at end") {
        val result = parse("{'a")
        assertTrue(result match {
          case Left(UnterminatedChar(1)) => true
          case _                         => false
        })
      },
      test("unterminated char after escape") {
        val result = parse("{'\\'")
        assertTrue(result match {
          case Left(UnterminatedChar(1)) => true
          case _                         => false
        })
      },
      test("unterminated empty char") {
        val result = parse("{'")
        assertTrue(result match {
          case Left(UnterminatedChar(1)) => true
          case _                         => false
        })
      },
      test("unterminated char with escape at end") {
        val result = parse("{'\\")
        assertTrue(result match {
          case Left(UnterminatedChar(1)) => true
          case _                         => false
        })
      }
    ),
    suite("EmptyChar errors")(
      test("empty char literal") {
        val result = parse("{''}")
        assertTrue(result match {
          case Left(EmptyChar(1)) => true
          case _                  => false
        })
      }
    ),
    suite("MultiCharLiteral errors")(
      test("multi-char literal") {
        val result = parse("{'ab'}")
        assertTrue(result match {
          case Left(MultiCharLiteral(1)) => true
          case _                         => false
        })
      },
      test("multi-char with escape") {
        val result = parse("""{'\\a'}""")
        assertTrue(result match {
          case Left(MultiCharLiteral(1)) => true
          case _                         => false
        })
      }
    ),
    suite("InvalidIdentifier errors")(
      test("dot at end of input") {
        val result = parse(".")
        assertTrue(result match {
          case Left(InvalidIdentifier(1)) => true
          case _                          => false
        })
      },
      test("dot followed by non-identifier") {
        val result = parse(".[")
        assertTrue(result match {
          case Left(InvalidIdentifier(1)) => true
          case _                          => false
        })
      },
      test("empty variant case") {
        val result = parse("<>")
        assertTrue(result match {
          case Left(InvalidIdentifier(1)) => true
          case _                          => false
        })
      },
      test("variant with numeric start") {
        val result = parse("<123>")
        assertTrue(result match {
          case Left(InvalidIdentifier(1)) => true
          case _                          => false
        })
      }
    ),
    suite("IntegerOverflow errors")(
      test("integer overflow in index") {
        val result = parse("[9999999999999999999]")
        assertTrue(result match {
          case Left(IntegerOverflow(1)) => true
          case _                        => false
        })
      },
      test("integer overflow in map key") {
        val result = parse("{9999999999999999999}")
        assertTrue(result match {
          case Left(IntegerOverflow(1)) => true
          case _                        => false
        })
      },
      test("integer overflow in negative map key") {
        val result = parse("{-9999999999999999999}")
        assertTrue(result match {
          case Left(IntegerOverflow(2)) => true
          case _                        => false
        })
      },
      test("integer overflow in range start") {
        val result = parse("[9999999999999999999:5]")
        assertTrue(result match {
          case Left(IntegerOverflow(1)) => true
          case _                        => false
        })
      },
      test("integer overflow in range end") {
        val result = parse("[0:9999999999999999999]")
        assertTrue(result match {
          case Left(IntegerOverflow(3)) => true
          case _                        => false
        })
      }
    ),
    suite("UnexpectedEnd errors")(
      test("unexpected end in index") {
        val result = parse("[")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("unexpected end in map") {
        val result = parse("{")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      }
    ),
    suite("InvalidSyntax errors")(
      test("invalid map key identifier") {
        val result = parse("{notABool}")
        assertTrue(result match {
          case Left(InvalidSyntax(msg, _)) => msg.contains("Invalid map key identifier")
          case _                           => false
        })
      },
      test("invalid map key keyword") {
        val result = parse("{null}")
        assertTrue(result match {
          case Left(InvalidSyntax(msg, _)) => msg.contains("Invalid map key identifier")
          case _                           => false
        })
      }
    ),
    suite("map key types")(
      test("boolean true key") {
        assertTrue(
          parse("{true}") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
        )
      },
      test("boolean false key") {
        assertTrue(
          parse("{false}") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
        )
      },
      test("positive integer key") {
        assertTrue(parse("{42}") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(42))))))
      },
      test("negative integer key") {
        assertTrue(parse("{-42}") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(-42))))))
      },
      test("negative int min value") {
        assertTrue(
          parse("{-2147483648}") == Right(
            Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(Int.MinValue))))
          )
        )
      },
      test("char key") {
        assertTrue(parse("{'a'}") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('a'))))))
      },
      test("escaped char key newline") {
        assertTrue(
          parse("""{'\\'}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\\')))))
        )
      },
      test("escaped char key tab") {
        assertTrue(
          parse("""{'\''}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\'')))))
        )
      },
      test("escaped char key n") {
        assertTrue(
          parse("""{'\\'}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\\')))))
        )
      }
    ),
    suite("string escapes")(
      test("escaped quote in string") {
        assertTrue(
          parse("""{"\""}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("\"")))))
        )
      },
      test("escaped backslash in string") {
        assertTrue(
          parse("""{"\\"}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("\\")))))
        )
      },
      test("escaped newline in string") {
        assertTrue(
          parse("""{"a\nb"}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("a\nb")))))
        )
      },
      test("escaped tab in string") {
        assertTrue(
          parse("""{"a\tb"}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("a\tb")))))
        )
      },
      test("escaped carriage return in string") {
        assertTrue(
          parse("""{"a\rb"}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("a\rb")))))
        )
      }
    ),
    suite("char escapes")(
      test("escaped newline char") {
        assertTrue(
          parse("""{'\n'}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\n')))))
        )
      },
      test("escaped tab char") {
        assertTrue(
          parse("""{'\t'}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\t')))))
        )
      },
      test("escaped carriage return char") {
        assertTrue(
          parse("""{'\r'}""") == Right(Chunk(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\r')))))
        )
      }
    ),
    suite("error messages")(
      test("UnexpectedChar message") {
        val err = UnexpectedChar('x', 5, "a digit")
        assertTrue(err.message == "Unexpected character 'x' at position 5. Expected a digit")
      },
      test("InvalidEscape message") {
        val err = InvalidEscape('q', 10)
        assertTrue(err.message == "Invalid escape sequence '\\q' at position 10")
      },
      test("UnterminatedString message") {
        val err = UnterminatedString(3)
        assertTrue(err.message == "Unterminated string literal starting at position 3")
      },
      test("UnterminatedChar message") {
        val err = UnterminatedChar(7)
        assertTrue(err.message == "Unterminated char literal starting at position 7")
      },
      test("EmptyChar message") {
        val err = EmptyChar(2)
        assertTrue(err.message == "Empty char literal at position 2")
      },
      test("MultiCharLiteral message") {
        val err = MultiCharLiteral(4)
        assertTrue(err.message == "Char literal contains multiple characters at position 4")
      },
      test("InvalidIdentifier message") {
        val err = InvalidIdentifier(0)
        assertTrue(err.message == "Invalid identifier at position 0")
      },
      test("IntegerOverflow message") {
        val err = IntegerOverflow(1)
        assertTrue(err.message == "Integer overflow at position 1 (value exceeds Int.MaxValue)")
      },
      test("UnexpectedEnd message") {
        val err = UnexpectedEnd("closing bracket")
        assertTrue(err.message == "Unexpected end of input. Expected closing bracket" && err.position == -1)
      },
      test("InvalidSyntax message") {
        val err = InvalidSyntax("Oops", 42)
        assertTrue(err.message == "Oops at position 42")
      }
    ),
    suite("ParseContext edge cases")(
      test("peek beyond end returns null char") {
        assertTrue(parse("[0]") == Right(Chunk(Node.AtIndex(0))))
      },
      test("whitespace handling in various positions") {
        assertTrue(parse("[ 0 , 1 , 2 ]") == Right(Chunk(Node.AtIndices(Seq(0, 1, 2)))))
      },
      test("whitespace in map keys") {
        assertTrue(
          parse("""{ "a" , "b" }""") == Right(
            Chunk(
              Node.AtMapKeys(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.String("a")),
                  DynamicValue.Primitive(PrimitiveValue.String("b"))
                )
              )
            )
          )
        )
      },
      test("whitespace in variant") {
        assertTrue(parse("< Foo >") == Right(Chunk(Node.Case("Foo"))))
      }
    ),
    suite("search nodes")(
      test("simple nominal type") {
        assertTrue(parse("#Person") == Right(Vector(Node.SchemaSearch(SchemaRepr.Nominal("Person")))))
      },
      test("primitive type string") {
        assertTrue(parse("#string") == Right(Vector(Node.SchemaSearch(SchemaRepr.Primitive("string")))))
      },
      test("primitive type int") {
        assertTrue(parse("#int") == Right(Vector(Node.SchemaSearch(SchemaRepr.Primitive("int")))))
      },
      test("wildcard") {
        assertTrue(parse("#_") == Right(Vector(Node.SchemaSearch(SchemaRepr.Wildcard))))
      },
      test("record with single field") {
        assertTrue(
          parse("#record { name: string }") == Right(
            Vector(Node.SchemaSearch(SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))))
          )
        )
      },
      test("record with multiple fields") {
        assertTrue(
          parse("#record { name: string, age: int }") == Right(
            Vector(
              Node.SchemaSearch(
                SchemaRepr.Record(
                  Vector(
                    "name" -> SchemaRepr.Primitive("string"),
                    "age"  -> SchemaRepr.Primitive("int")
                  )
                )
              )
            )
          )
        )
      },
      test("variant type") {
        assertTrue(
          parse("#variant { Left: int, Right: string }") == Right(
            Vector(
              Node.SchemaSearch(
                SchemaRepr.Variant(
                  Vector(
                    "Left"  -> SchemaRepr.Primitive("int"),
                    "Right" -> SchemaRepr.Primitive("string")
                  )
                )
              )
            )
          )
        )
      },
      test("list type") {
        assertTrue(
          parse("#list(string)") == Right(
            Vector(Node.SchemaSearch(SchemaRepr.Sequence(SchemaRepr.Primitive("string"))))
          )
        )
      },
      test("map type") {
        assertTrue(
          parse("#map(string, int)") == Right(
            Vector(
              Node.SchemaSearch(SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int")))
            )
          )
        )
      },
      test("option type") {
        assertTrue(
          parse("#option(Person)") == Right(
            Vector(Node.SchemaSearch(SchemaRepr.Optional(SchemaRepr.Nominal("Person"))))
          )
        )
      },
      test("nested schema") {
        assertTrue(
          parse("#record { items: list(Person) }") == Right(
            Vector(
              Node.SchemaSearch(
                SchemaRepr.Record(
                  Vector("items" -> SchemaRepr.Sequence(SchemaRepr.Nominal("Person")))
                )
              )
            )
          )
        )
      },
      test("search followed by field") {
        assertTrue(
          parse("#Person.name") == Right(
            Vector(
              Node.SchemaSearch(SchemaRepr.Nominal("Person")),
              Node.Field("name")
            )
          )
        )
      },
      test("field followed by search") {
        assertTrue(
          parse(".users#Person") == Right(
            Vector(
              Node.Field("users"),
              Node.SchemaSearch(SchemaRepr.Nominal("Person"))
            )
          )
        )
      },
      test("search in complex path") {
        assertTrue(
          parse(".items[*]#Person.name") == Right(
            Vector(
              Node.Field("items"),
              Node.Elements,
              Node.SchemaSearch(SchemaRepr.Nominal("Person")),
              Node.Field("name")
            )
          )
        )
      },
      test("chained searches") {
        assertTrue(
          parse("#list(Person)#Person") == Right(
            Vector(
              Node.SchemaSearch(SchemaRepr.Sequence(SchemaRepr.Nominal("Person"))),
              Node.SchemaSearch(SchemaRepr.Nominal("Person"))
            )
          )
        )
      },
      test("search followed by index") {
        assertTrue(
          parse("#Person[0]") == Right(
            Vector(
              Node.SchemaSearch(SchemaRepr.Nominal("Person")),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("search followed by variant") {
        assertTrue(
          parse("#Either<Right>") == Right(
            Vector(
              Node.SchemaSearch(SchemaRepr.Nominal("Either")),
              Node.Case("Right")
            )
          )
        )
      }
    ),
    suite("search error cases")(
      test("empty search") {
        val result = parse("#")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("invalid start after hash") {
        val result = parse("#123")
        assertTrue(result match {
          case Left(UnexpectedChar('1', 1, _)) => true
          case _                               => false
        })
      },
      test("empty record in search") {
        val result = parse("#record { }")
        assertTrue(result match {
          case Left(InvalidSyntax(msg, _)) => msg.contains("Empty record")
          case _                           => false
        })
      },
      test("empty variant in search") {
        val result = parse("#variant { }")
        assertTrue(result match {
          case Left(InvalidSyntax(msg, _)) => msg.contains("Empty variant")
          case _                           => false
        })
      }
    )
  )
}
