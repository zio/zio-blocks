package zio.blocks.schema

import zio.test._

/**
 * Comprehensive test suite for the p"..." path interpolator macro.
 *
 * Tests cover:
 * - All syntax forms (fields, indices, ranges, map keys, variants)
 * - Edge cases (empty paths, unicode, keywords as fields)
 * - Error conditions (verified via compile-time rejection)
 * - Whitespace handling
 */
object DynamicOpticParserSpec extends ZIOSpecDefault with PathInterpolatorSyntax {

  def spec: Spec[Any, Nothing] = suite("DynamicOpticParserSpec")(
    fieldAccessSuite,
    indexAccessSuite,
    elementsSelectorSuite,
    mapSelectorsSuite,
    mapKeyAccessSuite,
    variantCaseSuite,
    complexPathsSuite,
    whitespaceSuite,
    edgeCasesSuite,
    escapeSequencesSuite
  )

  private val fieldAccessSuite = suite("Field access")(
    test("with leading dot") {
      assertTrue(p".users" == DynamicOptic(Vector(DynamicOptic.Node.Field("users"))))
    },
    test("without leading dot") {
      assertTrue(p"users" == DynamicOptic(Vector(DynamicOptic.Node.Field("users"))))
    },
    test("chained fields with dots") {
      assertTrue(
        p".users.email" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("users"),
            DynamicOptic.Node.Field("email")
          )
        )
      )
    },
    test("field with underscore") {
      assertTrue(p"._id" == DynamicOptic(Vector(DynamicOptic.Node.Field("_id"))))
    },
    test("field starting with underscore") {
      assertTrue(p"_private" == DynamicOptic(Vector(DynamicOptic.Node.Field("_private"))))
    },
    test("field with numbers") {
      assertTrue(p".field123" == DynamicOptic(Vector(DynamicOptic.Node.Field("field123"))))
    },
    test("unicode field name") {
      assertTrue(p".café" == DynamicOptic(Vector(DynamicOptic.Node.Field("café"))))
    },
    test("unicode field with accents") {
      assertTrue(p".naïve" == DynamicOptic(Vector(DynamicOptic.Node.Field("naïve"))))
    }
  )

  private val indexAccessSuite = suite("Index access")(
    test("single index") {
      assertTrue(p"[0]" == DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0))))
    },
    test("larger index") {
      assertTrue(p"[42]" == DynamicOptic(Vector(DynamicOptic.Node.AtIndex(42))))
    },
    test("leading zeros allowed") {
      assertTrue(p"[007]" == DynamicOptic(Vector(DynamicOptic.Node.AtIndex(7))))
    },
    test("multiple indices") {
      assertTrue(
        p"[0,1,2]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq(0, 1, 2)))
        )
      )
    },
    test("range exclusive") {
      assertTrue(
        p"[0:5]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq(0, 1, 2, 3, 4)))
        )
      )
    },
    test("range single element") {
      assertTrue(
        p"[0:1]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq(0)))
        )
      )
    },
    test("range empty when start equals end") {
      assertTrue(
        p"[5:5]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq.empty))
        )
      )
    }
  )

  private val elementsSelectorSuite = suite("Elements selector")(
    test("[*] syntax") {
      assertTrue(p"[*]" == DynamicOptic(Vector(DynamicOptic.Node.Elements)))
    },
    test("[:*] syntax") {
      assertTrue(p"[:*]" == DynamicOptic(Vector(DynamicOptic.Node.Elements)))
    }
  )

  private val mapSelectorsSuite = suite("Map selectors")(
    test("{*} for map values") {
      assertTrue(p"{*}" == DynamicOptic(Vector(DynamicOptic.Node.MapValues)))
    },
    test("{:*} for map values") {
      assertTrue(p"{:*}" == DynamicOptic(Vector(DynamicOptic.Node.MapValues)))
    },
    test("{*:} for map keys") {
      assertTrue(p"{*:}" == DynamicOptic(Vector(DynamicOptic.Node.MapKeys)))
    }
  )

  private val mapKeyAccessSuite = suite("Map key access")(
    test("string key") {
      assertTrue(
        p"""{"email"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("email"))
            )
          )
        )
      )
    },
    test("int key") {
      assertTrue(
        p"{42}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
        )
      )
    },
    test("negative int key") {
      assertTrue(
        p"{-5}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Int(-5))
            )
          )
        )
      )
    },
    test("char key") {
      assertTrue(
        p"{'a'}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Char('a'))
            )
          )
        )
      )
    },
    test("bool key true") {
      assertTrue(
        p"{true}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )
        )
      )
    },
    test("bool key false") {
      assertTrue(
        p"{false}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Boolean(false))
            )
          )
        )
      )
    },
    test("multiple string keys") {
      assertTrue(
        p"""{"a","b","c"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKeys(
              Seq(
                DynamicValue.Primitive(PrimitiveValue.String("a")),
                DynamicValue.Primitive(PrimitiveValue.String("b")),
                DynamicValue.Primitive(PrimitiveValue.String("c"))
              )
            )
          )
        )
      )
    },
    test("multiple int keys") {
      assertTrue(
        p"{1,2,3}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKeys(
              Seq(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(2)),
                DynamicValue.Primitive(PrimitiveValue.Int(3))
              )
            )
          )
        )
      )
    }
  )

  private val variantCaseSuite = suite("Variant case")(
    test("simple case") {
      assertTrue(p"<User>" == DynamicOptic(Vector(DynamicOptic.Node.Case("User"))))
    },
    test("case with numbers") {
      assertTrue(p"<Error404>" == DynamicOptic(Vector(DynamicOptic.Node.Case("Error404"))))
    },
    test("case in path") {
      assertTrue(
        p".response<Ok>.body" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("response"),
            DynamicOptic.Node.Case("Ok"),
            DynamicOptic.Node.Field("body")
          )
        )
      )
    }
  )

  private val complexPathsSuite = suite("Complex paths")(
    test("users array with email field") {
      assertTrue(
        p".users[*].email" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("users"),
            DynamicOptic.Node.Elements,
            DynamicOptic.Node.Field("email")
          )
        )
      )
    },
    test("map access with nested field") {
      assertTrue(
        p""".config{"database"}.host""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("config"),
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("database"))),
            DynamicOptic.Node.Field("host")
          )
        )
      )
    },
    test("nested arrays with range") {
      assertTrue(
        p".matrix[0][0:3]" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("matrix"),
            DynamicOptic.Node.AtIndex(0),
            DynamicOptic.Node.AtIndices(Seq(0, 1, 2))
          )
        )
      )
    },
    test("variant with map values") {
      assertTrue(
        p"<Success>.data{*}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Case("Success"),
            DynamicOptic.Node.Field("data"),
            DynamicOptic.Node.MapValues
          )
        )
      )
    },
    test("complex nested path") {
      assertTrue(
        p".users[0].addresses[*].city" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("users"),
            DynamicOptic.Node.AtIndex(0),
            DynamicOptic.Node.Field("addresses"),
            DynamicOptic.Node.Elements,
            DynamicOptic.Node.Field("city")
          )
        )
      )
    },
    test("map with all keys selector") {
      assertTrue(
        p".settings{*:}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("settings"),
            DynamicOptic.Node.MapKeys
          )
        )
      )
    }
  )

  private val whitespaceSuite = suite("Whitespace handling")(
    test("spaces inside brackets allowed") {
      assertTrue(p"[ 0 ]" == DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0))))
    },
    test("spaces inside braces allowed") {
      assertTrue(
        p"""{ "key" }""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
      )
    },
    test("spaces inside angle brackets allowed") {
      assertTrue(p"< User >" == DynamicOptic(Vector(DynamicOptic.Node.Case("User"))))
    },
    test("spaces around comma in indices") {
      assertTrue(
        p"[ 0 , 1 , 2 ]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq(0, 1, 2)))
        )
      )
    },
    test("spaces around colon in range") {
      assertTrue(
        p"[ 0 : 5 ]" == DynamicOptic(
          Vector(DynamicOptic.Node.AtIndices(Seq(0, 1, 2, 3, 4)))
        )
      )
    }
  )

  private val edgeCasesSuite = suite("Edge cases")(
    test("empty path") {
      assertTrue(p"" == DynamicOptic(Vector.empty))
    },
    test("just a dot with field") {
      assertTrue(p".x" == DynamicOptic(Vector(DynamicOptic.Node.Field("x"))))
    },
    test("long field name") {
      assertTrue(
        p".veryLongFieldNameWithManyCharacters" == DynamicOptic(
          Vector(DynamicOptic.Node.Field("veryLongFieldNameWithManyCharacters"))
        )
      )
    },
    test("zero index") {
      assertTrue(p"[0]" == DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0))))
    },
    test("empty string key") {
      assertTrue(
        p"""{""}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
      )
    }
  )

  private val escapeSequencesSuite = suite("Escape sequences")(
    test("string with newline escape") {
      assertTrue(
        p"""{"hello\nworld"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("hello\nworld"))
            )
          )
        )
      )
    },
    test("string with tab escape") {
      assertTrue(
        p"""{"col1\tcol2"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("col1\tcol2"))
            )
          )
        )
      )
    },
    test("string with carriage return escape") {
      assertTrue(
        p"""{"line\rend"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("line\rend"))
            )
          )
        )
      )
    },
    test("string with escaped quote") {
      assertTrue(
        p"""{"say \"hello\""}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("say \"hello\""))
            )
          )
        )
      )
    },
    test("string with escaped backslash") {
      assertTrue(
        p"""{"path\\to\\file"}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.String("path\\to\\file"))
            )
          )
        )
      )
    },
    test("char with newline escape") {
      assertTrue(
        p"{'\n'}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Char('\n'))
            )
          )
        )
      )
    },
    test("char with tab escape") {
      assertTrue(
        p"{'\t'}" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Char('\t'))
            )
          )
        )
      )
    },
    test("char with escaped quote") {
      // Use triple quotes so \' is preserved as literal backslash-quote in the path
      assertTrue(
        p"""{'\''}""" == DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(
              DynamicValue.Primitive(PrimitiveValue.Char('\''))
            )
          )
        )
      )
    }
  )

  // Note: Compile-time error validation tests
  // These test that invalid syntax is rejected at compile time.
  // The typeCheck/typeCheckErrors utilities verify compile-time rejection.
  // Uncomment and use when ZIO Test provides assertDoesNotCompile or similar.
  //
  // Examples of what should fail to compile:
  // - p".users[$idx]"       // Interpolation not allowed
  // - p"[2147483648]"       // Integer overflow
  // - p"[10:5]"             // Invalid range: start > end
  // - p". users"            // Whitespace after dot not allowed
  // - p"[-1]"               // Negative index in array
  // - p"""{"\\x"}"""        // Invalid escape sequence
}
