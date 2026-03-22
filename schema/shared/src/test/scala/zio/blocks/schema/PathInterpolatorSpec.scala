package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._
import zio.test.Assertion._

object PathInterpolatorSpec extends SchemaBaseSpec {
  import DynamicOptic.Node

  def spec = suite("PathInterpolatorSpec")(
    suite("Record field access")(
      test("single field with leading dot") {
        assertTrue(p".foo" == DynamicOptic(Chunk(Node.Field("foo"))))
      },
      test("single field without leading dot") {
        assertTrue(p"foo" == DynamicOptic(Chunk(Node.Field("foo"))))
      },
      test("chained fields with leading dot") {
        assertTrue(
          p".foo.bar.baz" == DynamicOptic(
            Chunk(
              Node.Field("foo"),
              Node.Field("bar"),
              Node.Field("baz")
            )
          )
        )
      },
      test("chained fields without leading dot") {
        assertTrue(
          p"foo.bar.baz" == DynamicOptic(
            Chunk(
              Node.Field("foo"),
              Node.Field("bar"),
              Node.Field("baz")
            )
          )
        )
      },
      test("field with leading underscore") {
        assertTrue(p"._private" == DynamicOptic(Chunk(Node.Field("_private"))))
      },
      test("field underscore only") {
        assertTrue(p"._" == DynamicOptic(Chunk(Node.Field("_"))))
      },
      test("field with digits") {
        assertTrue(p".field123" == DynamicOptic(Chunk(Node.Field("field123"))))
      },
      test("field starting with underscore and digits") {
        assertTrue(p"._123" == DynamicOptic(Chunk(Node.Field("_123"))))
      },
      test("field that looks like keyword true") {
        assertTrue(p".true" == DynamicOptic(Chunk(Node.Field("true"))))
      },
      test("field that looks like keyword false") {
        assertTrue(p".false" == DynamicOptic(Chunk(Node.Field("false"))))
      },
      test("field that looks like keyword null") {
        assertTrue(p".null" == DynamicOptic(Chunk(Node.Field("null"))))
      },
      test("unicode field name") {
        assertTrue(p".café" == DynamicOptic(Chunk(Node.Field("café"))))
      },
      test("unicode field name without leading dot") {
        assertTrue(p"café" == DynamicOptic(Chunk(Node.Field("café"))))
      },
      test("field with many underscores") {
        assertTrue(p".__foo__bar__" == DynamicOptic(Chunk(Node.Field("__foo__bar__"))))
      }
    ),

    suite("Sequence index access - single index")(
      test("index zero") {
        assertTrue(p"[0]" == DynamicOptic(Chunk(Node.AtIndex(0))))
      },
      test("index positive") {
        assertTrue(p"[42]" == DynamicOptic(Chunk(Node.AtIndex(42))))
      },
      test("index large") {
        assertTrue(p"[999999]" == DynamicOptic(Chunk(Node.AtIndex(999999))))
      },
      test("index max int") {
        assertTrue(p"[2147483647]" == DynamicOptic(Chunk(Node.AtIndex(2147483647))))
      },
      test("index with leading zeros") {
        assertTrue(p"[007]" == DynamicOptic(Chunk(Node.AtIndex(7))))
      },
      test("index with many leading zeros") {
        assertTrue(p"[00000042]" == DynamicOptic(Chunk(Node.AtIndex(42))))
      },
      test("index with spaces") {
        assertTrue(p"[ 42 ]" == DynamicOptic(Chunk(Node.AtIndex(42))))
      }
    ),

    suite("Sequence index access - multiple indices")(
      test("two indices") {
        assertTrue(p"[0,1]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 1)))))
      },
      test("several indices") {
        assertTrue(p"[0,2,5,10]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 2, 5, 10)))))
      },
      test("indices with spaces") {
        assertTrue(p"[0, 2, 5]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 2, 5)))))
      },
      test("indices with inconsistent spacing") {
        assertTrue(p"[0 ,1, 2 ,3]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 1, 2, 3)))))
      },
      test("duplicate indices allowed") {
        assertTrue(p"[0,0,1,1]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 0, 1, 1)))))
      },
      test("out of order indices allowed") {
        assertTrue(p"[5,2,8,1]" == DynamicOptic(Chunk(Node.AtIndices(Seq(5, 2, 8, 1)))))
      },
      test("indices with leading zeros") {
        assertTrue(p"[001,002,003]" == DynamicOptic(Chunk(Node.AtIndices(Seq(1, 2, 3)))))
      }
    ),

    suite("Sequence index access - ranges")(
      test("range basic") {
        assertTrue(
          p"[0:5]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(0, 1, 2, 3, 4))
            )
          )
        )
      },
      test("range zero to ten") {
        assertTrue(
          p"[0:10]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            )
          )
        )
      },
      test("range with spaces") {
        assertTrue(
          p"[ 0 : 5 ]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(0, 1, 2, 3, 4))
            )
          )
        )
      },
      test("range starting nonzero") {
        assertTrue(
          p"[5:8]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(5, 6, 7))
            )
          )
        )
      },
      test("range single element") {
        assertTrue(
          p"[3:4]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(3))
            )
          )
        )
      },
      test("range empty when start equals end") {
        assertTrue(
          p"[5:5]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq.empty)
            )
          )
        )
      },
      test("range inverted produces empty") {
        assertTrue(
          p"[10:5]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq.empty)
            )
          )
        )
      },
      test("range with leading zeros") {
        assertTrue(
          p"[001:005]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(1, 2, 3, 4))
            )
          )
        )
      }
    ),

    suite("Sequence index access - element selectors")(
      test("select all elements with star") {
        assertTrue(p"[*]" == DynamicOptic(Chunk(Node.Elements)))
      },
      test("select all elements with colon star") {
        assertTrue(p"[:*]" == DynamicOptic(Chunk(Node.Elements)))
      },
      test("star with spaces") {
        assertTrue(p"[ * ]" == DynamicOptic(Chunk(Node.Elements)))
      },
      test("colon star with spaces") {
        assertTrue(p"[ :* ]" == DynamicOptic(Chunk(Node.Elements)))
      },
      test("chained single indices") {
        assertTrue(
          p"[0][1][2]" == DynamicOptic(
            Chunk(
              Node.AtIndex(0),
              Node.AtIndex(1),
              Node.AtIndex(2)
            )
          )
        )
      },
      test("chained elements selectors") {
        assertTrue(
          p"[*][*]" == DynamicOptic(
            Chunk(
              Node.Elements,
              Node.Elements
            )
          )
        )
      },
      test("chained colon star selectors") {
        assertTrue(
          p"[:*][:*]" == DynamicOptic(
            Chunk(
              Node.Elements,
              Node.Elements
            )
          )
        )
      },
      test("mixed elements and indices") {
        assertTrue(
          p"[*][0][*]" == DynamicOptic(
            Chunk(
              Node.Elements,
              Node.AtIndex(0),
              Node.Elements
            )
          )
        )
      }
    ),

    suite("Map key access - string keys")(
      test("simple string key") {
        assertTrue(
          p"""{"foo"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo")))
            )
          )
        )
      },
      test("string key with spaces") {
        assertTrue(
          p"""{"foo bar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo bar")))
            )
          )
        )
      },
      test("string key with escaped quote") {
        assertTrue(
          p"""{"foo\"bar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo\"bar")))
            )
          )
        )
      },
      test("string key with escaped backslash") {
        assertTrue(
          p"""{"foo\\bar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo\\bar")))
            )
          )
        )
      },
      test("string key with newline escape") {
        assertTrue(
          p"""{"foo\nbar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo\nbar")))
            )
          )
        )
      },
      test("string key with tab escape") {
        assertTrue(
          p"""{"foo\tbar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo\tbar")))
            )
          )
        )
      },
      test("string key with carriage return escape") {
        assertTrue(
          p"""{"foo\rbar"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo\rbar")))
            )
          )
        )
      },
      test("string key only backslash") {
        assertTrue(
          p"""{"\\"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("\\")))
            )
          )
        )
      },
      test("string key only quote") {
        assertTrue(
          p"""{"\""}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("\"")))
            )
          )
        )
      },
      test("empty string key") {
        assertTrue(
          p"""{""}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("")))
            )
          )
        )
      },
      test("string key with unicode") {
        assertTrue(
          p"""{"日本語"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("日本語")))
            )
          )
        )
      },
      test("string key with emoji") {
        assertTrue(
          p"""{"🎉"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("🎉")))
            )
          )
        )
      },
      test("multiple string keys") {
        assertTrue(
          p"""{"foo", "bar", "baz"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("foo")),
                  DynamicValue.Primitive(PrimitiveValue.String("bar")),
                  DynamicValue.Primitive(PrimitiveValue.String("baz"))
                )
              )
            )
          )
        )
      },
      test("two string keys") {
        assertTrue(
          p"""{"a", "b"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("a")),
                  DynamicValue.Primitive(PrimitiveValue.String("b"))
                )
              )
            )
          )
        )
      }
    ),

    suite("Map key access - integer keys")(
      test("positive integer") {
        assertTrue(
          p"{42}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(42)))
            )
          )
        )
      },
      test("zero") {
        assertTrue(
          p"{0}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )
      },
      test("negative integer") {
        assertTrue(
          p"{-42}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(-42)))
            )
          )
        )
      },
      test("max int") {
        assertTrue(
          p"{2147483647}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(2147483647)))
            )
          )
        )
      },
      test("min int") {
        assertTrue(
          p"{-2147483648}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(-2147483648)))
            )
          )
        )
      },
      test("leading zeros") {
        assertTrue(
          p"{007}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(7)))
            )
          )
        )
      },
      test("negative with leading zeros") {
        assertTrue(
          p"{-007}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(-7)))
            )
          )
        )
      },
      test("multiple integer keys") {
        assertTrue(
          p"{1, 2, 3}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  DynamicValue.Primitive(PrimitiveValue.Int(2)),
                  DynamicValue.Primitive(PrimitiveValue.Int(3))
                )
              )
            )
          )
        )
      },
      test("multiple integer keys with negatives") {
        assertTrue(
          p"{-1, 0, 1}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Int(-1)),
                  DynamicValue.Primitive(PrimitiveValue.Int(0)),
                  DynamicValue.Primitive(PrimitiveValue.Int(1))
                )
              )
            )
          )
        )
      }
    ),

    suite("Map key access - boolean keys")(
      test("true") {
        assertTrue(
          p"{true}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
            )
          )
        )
      },
      test("false") {
        assertTrue(
          p"{false}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
            )
          )
        )
      },
      test("multiple booleans") {
        assertTrue(
          p"{true, false}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(false))
                )
              )
            )
          )
        )
      },
      test("duplicate booleans") {
        assertTrue(
          p"{true, true, false}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(false))
                )
              )
            )
          )
        )
      }
    ),

    suite("Map key access - char keys")(
      test("simple char") {
        assertTrue(
          p"{'a'}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('a')))
            )
          )
        )
      },
      test("char space") {
        assertTrue(
          p"{' '}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char(' ')))
            )
          )
        )
      },
      test("char digit") {
        assertTrue(
          p"{'9'}" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('9')))
            )
          )
        )
      },
      test("char escaped newline") {
        assertTrue(
          p"""{'\n'}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\n')))
            )
          )
        )
      },
      test("char escaped tab") {
        assertTrue(
          p"""{'\t'}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\t')))
            )
          )
        )
      },
      test("char escaped carriage return") {
        assertTrue(
          p"""{'\r'}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\r')))
            )
          )
        )
      },
      test("char escaped single quote") {
        assertTrue(
          p"""{'\''}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\'')))
            )
          )
        )
      },
      test("char escaped backslash") {
        assertTrue(
          p"""{'\\'}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\\')))
            )
          )
        )
      },
      test("multiple char keys") {
        assertTrue(
          p"{'a', 'b', 'c'}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Char('a')),
                  DynamicValue.Primitive(PrimitiveValue.Char('b')),
                  DynamicValue.Primitive(PrimitiveValue.Char('c'))
                )
              )
            )
          )
        )
      }
    ),

    suite("Map key access - mixed types")(
      test("string and integer") {
        assertTrue(
          p"""{"foo", 42}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("foo")),
                  DynamicValue.Primitive(PrimitiveValue.Int(42))
                )
              )
            )
          )
        )
      },
      test("integer and boolean") {
        assertTrue(
          p"{1, true, 2, false}" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                  DynamicValue.Primitive(PrimitiveValue.Int(2)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(false))
                )
              )
            )
          )
        )
      },
      test("string and char") {
        assertTrue(
          p"""{"foo", 'x'}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("foo")),
                  DynamicValue.Primitive(PrimitiveValue.Char('x'))
                )
              )
            )
          )
        )
      },
      test("all supported primitive types") {
        assertTrue(
          p"""{"s", 'c', 42, true}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("s")),
                  DynamicValue.Primitive(PrimitiveValue.Char('c')),
                  DynamicValue.Primitive(PrimitiveValue.Int(42)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true))
                )
              )
            )
          )
        )
      },
      test("negative int with string and bool") {
        assertTrue(
          p"""{"key", -99, false}""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("key")),
                  DynamicValue.Primitive(PrimitiveValue.Int(-99)),
                  DynamicValue.Primitive(PrimitiveValue.Boolean(false))
                )
              )
            )
          )
        )
      }
    ),

    suite("Map key/value selectors")(
      test("select all values with star") {
        assertTrue(p"{*}" == DynamicOptic(Chunk(Node.MapValues)))
      },
      test("select all values with colon star") {
        assertTrue(p"{:*}" == DynamicOptic(Chunk(Node.MapValues)))
      },
      test("select all keys with star colon") {
        assertTrue(p"{*:}" == DynamicOptic(Chunk(Node.MapKeys)))
      },
      test("star with spaces") {
        assertTrue(p"{ * }" == DynamicOptic(Chunk(Node.MapValues)))
      },
      test("colon star with spaces") {
        assertTrue(p"{ :* }" == DynamicOptic(Chunk(Node.MapValues)))
      },
      test("star colon with spaces") {
        assertTrue(p"{ *: }" == DynamicOptic(Chunk(Node.MapKeys)))
      },
      test("chained map values") {
        assertTrue(
          p"{*}{*}" == DynamicOptic(
            Chunk(
              Node.MapValues,
              Node.MapValues
            )
          )
        )
      },
      test("chained map keys") {
        assertTrue(
          p"{*:}{*:}" == DynamicOptic(
            Chunk(
              Node.MapKeys,
              Node.MapKeys
            )
          )
        )
      },
      test("map values then map keys") {
        assertTrue(
          p"{*}{*:}" == DynamicOptic(
            Chunk(
              Node.MapValues,
              Node.MapKeys
            )
          )
        )
      }
    ),

    suite("Variant case access")(
      test("simple case") {
        assertTrue(p"<Left>" == DynamicOptic(Chunk(Node.Case("Left"))))
      },
      test("case with digits") {
        assertTrue(p"<Case1>" == DynamicOptic(Chunk(Node.Case("Case1"))))
      },
      test("case lowercase") {
        assertTrue(p"<none>" == DynamicOptic(Chunk(Node.Case("none"))))
      },
      test("case underscore prefix") {
        assertTrue(p"<_Empty>" == DynamicOptic(Chunk(Node.Case("_Empty"))))
      },
      test("case underscore only") {
        assertTrue(p"<_>" == DynamicOptic(Chunk(Node.Case("_"))))
      },
      test("case that looks like keyword true") {
        assertTrue(p"<true>" == DynamicOptic(Chunk(Node.Case("true"))))
      },
      test("case that looks like keyword false") {
        assertTrue(p"<false>" == DynamicOptic(Chunk(Node.Case("false"))))
      },
      test("case that looks like keyword null") {
        assertTrue(p"<null>" == DynamicOptic(Chunk(Node.Case("null"))))
      },
      test("case with spaces around name") {
        assertTrue(p"< Left >" == DynamicOptic(Chunk(Node.Case("Left"))))
      },
      test("unicode case name") {
        assertTrue(p"<Ñoño>" == DynamicOptic(Chunk(Node.Case("Ñoño"))))
      },
      test("unicode case name café") {
        assertTrue(p"<café>" == DynamicOptic(Chunk(Node.Case("café"))))
      },
      test("chained cases") {
        assertTrue(
          p"<A><B>" == DynamicOptic(
            Chunk(
              Node.Case("A"),
              Node.Case("B")
            )
          )
        )
      },
      test("chained cases three") {
        assertTrue(
          p"<A><B><C>" == DynamicOptic(
            Chunk(
              Node.Case("A"),
              Node.Case("B"),
              Node.Case("C")
            )
          )
        )
      }
    ),

    suite("Combined paths - field then sequence")(
      test("field then index") {
        assertTrue(
          p".items[0]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("field then indices") {
        assertTrue(
          p".items[0,1,2]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.AtIndices(Seq(0, 1, 2))
            )
          )
        )
      },
      test("field then range") {
        assertTrue(
          p".items[0:5]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.AtIndices(Seq(0, 1, 2, 3, 4))
            )
          )
        )
      },
      test("field then elements") {
        assertTrue(
          p".items[*]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.Elements
            )
          )
        )
      },
      test("field without dot then index") {
        assertTrue(
          p"items[0]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.AtIndex(0)
            )
          )
        )
      }
    ),

    suite("Combined paths - field then map")(
      test("field then string map key") {
        assertTrue(
          p""".config{"host"}""" == DynamicOptic(
            Chunk(
              Node.Field("config"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("host")))
            )
          )
        )
      },
      test("field then int map key") {
        assertTrue(
          p".lookup{42}" == DynamicOptic(
            Chunk(
              Node.Field("lookup"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(42)))
            )
          )
        )
      },
      test("field then map values") {
        assertTrue(
          p".lookup{*}" == DynamicOptic(
            Chunk(
              Node.Field("lookup"),
              Node.MapValues
            )
          )
        )
      },
      test("field then map keys") {
        assertTrue(
          p".lookup{*:}" == DynamicOptic(
            Chunk(
              Node.Field("lookup"),
              Node.MapKeys
            )
          )
        )
      },
      test("field without dot then map key") {
        assertTrue(
          p"""config{"host"}""" == DynamicOptic(
            Chunk(
              Node.Field("config"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("host")))
            )
          )
        )
      }
    ),

    suite("Combined paths - field then variant")(
      test("field then variant case") {
        assertTrue(
          p".result<Success>" == DynamicOptic(
            Chunk(
              Node.Field("result"),
              Node.Case("Success")
            )
          )
        )
      },
      test("field without dot then variant") {
        assertTrue(
          p"result<Success>" == DynamicOptic(
            Chunk(
              Node.Field("result"),
              Node.Case("Success")
            )
          )
        )
      }
    ),

    suite("Combined paths - nested structures")(
      test("record in sequence") {
        assertTrue(
          p".users[0].name" == DynamicOptic(
            Chunk(
              Node.Field("users"),
              Node.AtIndex(0),
              Node.Field("name")
            )
          )
        )
      },
      test("all elements then field") {
        assertTrue(
          p".users[*].email" == DynamicOptic(
            Chunk(
              Node.Field("users"),
              Node.Elements,
              Node.Field("email")
            )
          )
        )
      },
      test("map values then field") {
        assertTrue(
          p".lookup{*}.value" == DynamicOptic(
            Chunk(
              Node.Field("lookup"),
              Node.MapValues,
              Node.Field("value")
            )
          )
        )
      },
      test("map keys then field") {
        assertTrue(
          p".lookup{*:}.id" == DynamicOptic(
            Chunk(
              Node.Field("lookup"),
              Node.MapKeys,
              Node.Field("id")
            )
          )
        )
      },
      test("variant then field") {
        assertTrue(
          p".response<Ok>.body" == DynamicOptic(
            Chunk(
              Node.Field("response"),
              Node.Case("Ok"),
              Node.Field("body")
            )
          )
        )
      },
      test("variant then index") {
        assertTrue(
          p"<Right>[0]" == DynamicOptic(
            Chunk(
              Node.Case("Right"),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("variant then map access") {
        assertTrue(
          p"""<Some>{"key"}""" == DynamicOptic(
            Chunk(
              Node.Case("Some"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
            )
          )
        )
      },
      test("variant then elements") {
        assertTrue(
          p"<Items>[*]" == DynamicOptic(
            Chunk(
              Node.Case("Items"),
              Node.Elements
            )
          )
        )
      },
      test("variant then map values") {
        assertTrue(
          p"<Data>{*}" == DynamicOptic(
            Chunk(
              Node.Case("Data"),
              Node.MapValues
            )
          )
        )
      }
    ),

    suite("Combined paths - deeply nested")(
      test("four levels of fields") {
        assertTrue(
          p".a.b.c.d" == DynamicOptic(
            Chunk(
              Node.Field("a"),
              Node.Field("b"),
              Node.Field("c"),
              Node.Field("d")
            )
          )
        )
      },
      test("field sequence field sequence") {
        assertTrue(
          p".items[0].children[1]" == DynamicOptic(
            Chunk(
              Node.Field("items"),
              Node.AtIndex(0),
              Node.Field("children"),
              Node.AtIndex(1)
            )
          )
        )
      },
      test("deeply nested with elements") {
        assertTrue(
          p""".root.children[*].metadata{"tags"}[0]""" == DynamicOptic(
            Chunk(
              Node.Field("root"),
              Node.Field("children"),
              Node.Elements,
              Node.Field("metadata"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("tags"))),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("complex with variant") {
        assertTrue(
          p".data<Some>.items[0,1,2].props{*:}" == DynamicOptic(
            Chunk(
              Node.Field("data"),
              Node.Case("Some"),
              Node.Field("items"),
              Node.AtIndices(Seq(0, 1, 2)),
              Node.Field("props"),
              Node.MapKeys
            )
          )
        )
      },
      test("all node types in sequence") {
        assertTrue(
          p""".a[0]{"k"}<V>.b[*]{*}.c{*:}""" == DynamicOptic(
            Chunk(
              Node.Field("a"),
              Node.AtIndex(0),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
              Node.Case("V"),
              Node.Field("b"),
              Node.Elements,
              Node.MapValues,
              Node.Field("c"),
              Node.MapKeys
            )
          )
        )
      },
      test("alternating field and index") {
        assertTrue(
          p".a[0].b[1].c[2]" == DynamicOptic(
            Chunk(
              Node.Field("a"),
              Node.AtIndex(0),
              Node.Field("b"),
              Node.AtIndex(1),
              Node.Field("c"),
              Node.AtIndex(2)
            )
          )
        )
      },
      test("alternating field and map key") {
        assertTrue(
          p""".a{"x"}.b{"y"}.c{"z"}""" == DynamicOptic(
            Chunk(
              Node.Field("a"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("x"))),
              Node.Field("b"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("y"))),
              Node.Field("c"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("z")))
            )
          )
        )
      }
    ),

    suite("Root and empty paths")(
      test("empty string is root") {
        assertTrue(p"" == DynamicOptic.root)
      },
      test("root equals empty vector") {
        assertTrue(p"" == DynamicOptic(Vector.empty))
      }
    ),

    suite("Whitespace handling")(
      test("spaces in index list") {
        assertTrue(p"[ 0 , 1 , 2 ]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 1, 2)))))
      },
      test("spaces in range") {
        assertTrue(p"[ 0 : 5 ]" == DynamicOptic(Chunk(Node.AtIndices(Seq(0, 1, 2, 3, 4)))))
      },
      test("spaces in key list") {
        assertTrue(
          p"""{ "a" , "b" }""" == DynamicOptic(
            Chunk(
              Node.AtMapKeys(
                Seq(
                  DynamicValue.Primitive(PrimitiveValue.String("a")),
                  DynamicValue.Primitive(PrimitiveValue.String("b"))
                )
              )
            )
          )
        )
      },
      test("spaces around single index") {
        assertTrue(p"[ 0 ]" == DynamicOptic(Chunk(Node.AtIndex(0))))
      },
      test("spaces around single key") {
        assertTrue(
          p"""{ "foo" }""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("foo")))
            )
          )
        )
      },
      test("spaces in variant") {
        assertTrue(p"< Foo >" == DynamicOptic(Chunk(Node.Case("Foo"))))
      },
      test("mixed spacing in complex path") {
        assertTrue(
          p""".foo[ 0 ].bar{ "key" }<Baz>""" == DynamicOptic(
            Chunk(
              Node.Field("foo"),
              Node.AtIndex(0),
              Node.Field("bar"),
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key"))),
              Node.Case("Baz")
            )
          )
        )
      }
    ),

    suite("Parser robustness - unusual but valid")(
      test("index then field") {
        assertTrue(
          p"[0].foo" == DynamicOptic(
            Chunk(
              Node.AtIndex(0),
              Node.Field("foo")
            )
          )
        )
      },
      test("map key then field") {
        assertTrue(
          p"""{"k"}.foo""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
              Node.Field("foo")
            )
          )
        )
      },
      test("variant then variant") {
        assertTrue(
          p"<A><B>" == DynamicOptic(
            Chunk(
              Node.Case("A"),
              Node.Case("B")
            )
          )
        )
      },
      test("elements then elements") {
        assertTrue(
          p"[*][*]" == DynamicOptic(
            Chunk(
              Node.Elements,
              Node.Elements
            )
          )
        )
      },
      test("map values then map values") {
        assertTrue(
          p"{*}{*}" == DynamicOptic(
            Chunk(
              Node.MapValues,
              Node.MapValues
            )
          )
        )
      },
      test("map keys then map keys") {
        assertTrue(
          p"{*:}{*:}" == DynamicOptic(
            Chunk(
              Node.MapKeys,
              Node.MapKeys
            )
          )
        )
      },
      test("elements then map values") {
        assertTrue(
          p"[*]{*}" == DynamicOptic(
            Chunk(
              Node.Elements,
              Node.MapValues
            )
          )
        )
      },
      test("map values then elements") {
        assertTrue(
          p"{*}[*]" == DynamicOptic(
            Chunk(
              Node.MapValues,
              Node.Elements
            )
          )
        )
      },
      test("variant at start then index") {
        assertTrue(
          p"<Some>[0]" == DynamicOptic(
            Chunk(
              Node.Case("Some"),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("index at start then variant") {
        assertTrue(
          p"[0]<Some>" == DynamicOptic(
            Chunk(
              Node.AtIndex(0),
              Node.Case("Some")
            )
          )
        )
      },
      test("map key at start") {
        assertTrue(
          p"""{"key"}""" == DynamicOptic(
            Chunk(
              Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
            )
          )
        )
      },
      test("index at start") {
        assertTrue(p"[0]" == DynamicOptic(Chunk(Node.AtIndex(0))))
      },
      test("variant at start") {
        assertTrue(p"<Foo>" == DynamicOptic(Chunk(Node.Case("Foo"))))
      },
      test("elements at start") {
        assertTrue(p"[*]" == DynamicOptic(Chunk(Node.Elements)))
      },
      test("map values at start") {
        assertTrue(p"{*}" == DynamicOptic(Chunk(Node.MapValues)))
      },
      test("map keys at start") {
        assertTrue(p"{*:}" == DynamicOptic(Chunk(Node.MapKeys)))
      },
      test("long chain of same type") {
        assertTrue(
          p".a.b.c.d.e.f.g.h" == DynamicOptic(
            Chunk(
              Node.Field("a"),
              Node.Field("b"),
              Node.Field("c"),
              Node.Field("d"),
              Node.Field("e"),
              Node.Field("f"),
              Node.Field("g"),
              Node.Field("h")
            )
          )
        )
      },
      test("many indices in sequence") {
        assertTrue(
          p"[0][1][2][3][4]" == DynamicOptic(
            Chunk(
              Node.AtIndex(0),
              Node.AtIndex(1),
              Node.AtIndex(2),
              Node.AtIndex(3),
              Node.AtIndex(4)
            )
          )
        )
      },
      test("large index list") {
        assertTrue(
          p"[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]" == DynamicOptic(
            Chunk(
              Node.AtIndices(Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
            )
          )
        )
      }
    ),

    suite("Search nodes")(
      test("simple nominal type") {
        assertTrue(
          p"#Person" == DynamicOptic(Chunk(Node.SchemaSearch(SchemaRepr.Nominal("Person"))))
        )
      },
      test("primitive type string") {
        assertTrue(
          p"#string" == DynamicOptic(Chunk(Node.SchemaSearch(SchemaRepr.Primitive("string"))))
        )
      },
      test("primitive type int") {
        assertTrue(
          p"#int" == DynamicOptic(Chunk(Node.SchemaSearch(SchemaRepr.Primitive("int"))))
        )
      },
      test("wildcard") {
        assertTrue(
          p"#_" == DynamicOptic(Chunk(Node.SchemaSearch(SchemaRepr.Wildcard)))
        )
      },
      test("record with single field") {
        assertTrue(
          p"#record { name: string }" == DynamicOptic(
            Chunk(Node.SchemaSearch(SchemaRepr.Record(Chunk("name" -> SchemaRepr.Primitive("string")))))
          )
        )
      },
      test("record with multiple fields") {
        assertTrue(
          p"#record { name: string, age: int }" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(
                SchemaRepr.Record(
                  Chunk(
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
          p"#variant { Left: int, Right: string }" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(
                SchemaRepr.Variant(
                  Chunk(
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
          p"#list(string)" == DynamicOptic(
            Chunk(Node.SchemaSearch(SchemaRepr.Sequence(SchemaRepr.Primitive("string"))))
          )
        )
      },
      test("map type") {
        assertTrue(
          p"#map(string, int)" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int")))
            )
          )
        )
      },
      test("option type") {
        assertTrue(
          p"#option(Person)" == DynamicOptic(
            Chunk(Node.SchemaSearch(SchemaRepr.Optional(SchemaRepr.Nominal("Person"))))
          )
        )
      },
      test("nested schema") {
        assertTrue(
          p"#record { items: list(Person) }" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(
                SchemaRepr.Record(
                  Chunk("items" -> SchemaRepr.Sequence(SchemaRepr.Nominal("Person")))
                )
              )
            )
          )
        )
      },
      test("search followed by field") {
        assertTrue(
          p"#Person.name" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(SchemaRepr.Nominal("Person")),
              Node.Field("name")
            )
          )
        )
      },
      test("field followed by search") {
        assertTrue(
          p".users#Person" == DynamicOptic(
            Chunk(
              Node.Field("users"),
              Node.SchemaSearch(SchemaRepr.Nominal("Person"))
            )
          )
        )
      },
      test("search in complex path") {
        assertTrue(
          p".items[*]#Person.name" == DynamicOptic(
            Chunk(
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
          p"#list(Person)#Person" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(SchemaRepr.Sequence(SchemaRepr.Nominal("Person"))),
              Node.SchemaSearch(SchemaRepr.Nominal("Person"))
            )
          )
        )
      },
      test("search followed by index") {
        assertTrue(
          p"#Person[0]" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(SchemaRepr.Nominal("Person")),
              Node.AtIndex(0)
            )
          )
        )
      },
      test("search followed by variant case") {
        assertTrue(
          p"#Either<Right>" == DynamicOptic(
            Chunk(
              Node.SchemaSearch(SchemaRepr.Nominal("Either")),
              Node.Case("Right")
            )
          )
        )
      },
      test("roundtrip toString for nominal search") {
        val optic = p"#Person"
        assertTrue(optic.toString == "#Person")
      },
      test("roundtrip toString for primitive search") {
        val optic = p"#string"
        assertTrue(optic.toString == "#string")
      },
      test("roundtrip toString for record search") {
        val optic = p"#record { name: string }"
        assertTrue(optic.toString == "#record { name: string }")
      },
      test("roundtrip toString for complex path with search") {
        val optic = p".users[*]#Person.name"
        assertTrue(optic.toString == ".users[*]#Person.name")
      }
    ),

    suite("Compile-time validation")(
      test("rejects runtime interpolation arguments") {
        typeCheck {
          """import zio.blocks.schema._
             val x = "foo"
             val path: DynamicOptic = p".$x"
          """
        }.map(
          assert(_)(
            isLeft(
              (containsString("Path interpolator does not support runtime arguments") &&
                containsString("Use only literal strings")) ||
                containsString("Recursive value") // Scala 3.5+
            )
          )
        )
      },
      test("rejects interpolated values in middle") {
        typeCheck {
          """import zio.blocks.schema._
             val field = "name"
             val path: DynamicOptic = p".users[0].$field"
          """
        }.map(
          assert(_)(
            isLeft(
              (containsString("Path interpolator does not support runtime arguments") &&
                containsString("Use only literal strings")) ||
                containsString("Recursive value") // Scala 3.5+
            )
          )
        )
      },
      test("rejects interpolated index") {
        typeCheck {
          """import zio.blocks.schema._
             val idx = 5
             val path: DynamicOptic = p"[$idx]"
          """
        }.map(
          assert(_)(
            isLeft(
              (containsString("Path interpolator does not support runtime arguments") &&
                containsString("Use only literal strings")) ||
                containsString("Recursive value") // Scala 3.5+
            )
          )
        )
      }
    )
  )
}
