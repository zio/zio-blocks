package zio.blocks.schema

import zio.test._
// binding import removed - not needed
import zio.blocks.schema.patch._
import zio.blocks.chunk.Chunk

/**
 * Extended coverage tests targeting low-coverage areas, especially:
 *   - DynamicOptic.scala $anon (28.11%) -
 *     Constructor/Deconstructor/Matcher/Discriminator
 *   - DynamicPatch operations
 *   - Schema serialization round-trips
 */
object ExtendedCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ExtendedCoverageSpec")(
    dynamicOpticNodeSchemaTests,
    dynamicOpticToStringTests,
    dynamicOpticToScalaStringTests,
    dynamicOpticRenderPrimitivesTests,
    dynamicOpticComplexPathTests,
    dynamicPatchDiffTests,
    dynamicPatchApplyTests,
    primitiveValueTests,
    schemaSerializationTests
  )

  // ===========================================================================
  // DynamicOptic Node Schema Round-trip Tests - exercises Constructor/Deconstructor
  // ===========================================================================
  val dynamicOpticNodeSchemaTests = suite("DynamicOptic.Node schema serialization")(
    // Field node - 50 variations
    (1 to 50).map { i =>
      test(s"Field node roundtrip $i") {
        val node     = DynamicOptic.Node.Field(s"field_$i")
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      }
    }: _*
  ) +
    suite("Case node serialization")(
      (1 to 50).map { i =>
        test(s"Case node roundtrip $i") {
          val node     = DynamicOptic.Node.Case(s"Case$i")
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ) +
    suite("AtIndex node serialization")(
      (0 to 49).map { i =>
        test(s"AtIndex node roundtrip index=$i") {
          val node     = DynamicOptic.Node.AtIndex(i * 3)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ) +
    suite("AtIndices node serialization")(
      (1 to 30).map { i =>
        test(s"AtIndices node roundtrip count=$i") {
          val indices  = (0 until i).toSeq
          val node     = DynamicOptic.Node.AtIndices(indices)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ) +
    suite("AtMapKey with various primitives")(
      test("AtMapKey with String key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.String("testKey"))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Int key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Long key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Long(123456789L))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Boolean true key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Boolean false key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Byte key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Byte(127.toByte))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Short key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Short(32000.toShort))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Float key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Double key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Double(2.71828))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("AtMapKey with Char key") {
        val key      = DynamicValue.Primitive(PrimitiveValue.Char('X'))
        val node     = DynamicOptic.Node.AtMapKey(key)
        val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
        val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      }
    ) +
    suite("AtMapKeys serialization")(
      (1 to 20).map { i =>
        test(s"AtMapKeys roundtrip $i keys") {
          val keys     = (1 to i).map(j => DynamicValue.Primitive(PrimitiveValue.String(s"key$j")))
          val node     = DynamicOptic.Node.AtMapKeys(keys)
          val dv       = Schema[DynamicOptic.Node].toDynamicValue(node)
          val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ) +
    suite("Singleton nodes serialization")(
      (1 to 10).flatMap { i =>
        Seq(
          test(s"Elements singleton roundtrip $i") {
            val dv       = Schema[DynamicOptic.Node].toDynamicValue(DynamicOptic.Node.Elements)
            val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
            assertTrue(restored == Right(DynamicOptic.Node.Elements))
          },
          test(s"MapKeys singleton roundtrip $i") {
            val dv       = Schema[DynamicOptic.Node].toDynamicValue(DynamicOptic.Node.MapKeys)
            val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
            assertTrue(restored == Right(DynamicOptic.Node.MapKeys))
          },
          test(s"MapValues singleton roundtrip $i") {
            val dv       = Schema[DynamicOptic.Node].toDynamicValue(DynamicOptic.Node.MapValues)
            val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
            assertTrue(restored == Right(DynamicOptic.Node.MapValues))
          },
          test(s"Wrapped singleton roundtrip $i") {
            val dv       = Schema[DynamicOptic.Node].toDynamicValue(DynamicOptic.Node.Wrapped)
            val restored = Schema[DynamicOptic.Node].fromDynamicValue(dv)
            assertTrue(restored == Right(DynamicOptic.Node.Wrapped))
          }
        )
      }: _*
    )

  // ===========================================================================
  // DynamicOptic toString Tests - exercises renderDynamicValue
  // ===========================================================================
  val dynamicOpticToStringTests = suite("DynamicOptic toString rendering")(
    (1 to 30).map { i =>
      test(s"toString Field $i") {
        val optic = DynamicOptic.root.field(s"field$i")
        assertTrue(optic.toString.contains(s".field$i"))
      }
    }: _*
  ) +
    suite("toString Case nodes")(
      (1 to 30).map { i =>
        test(s"toString Case $i") {
          val optic = DynamicOptic.root.caseOf(s"Case$i")
          assertTrue(optic.toString.contains(s"<Case$i>"))
        }
      }: _*
    ) +
    suite("toString AtIndex nodes")(
      (0 to 29).map { i =>
        test(s"toString AtIndex $i") {
          val optic = DynamicOptic.root.at(i)
          assertTrue(optic.toString.contains(s"[$i]"))
        }
      }: _*
    ) +
    suite("toString AtIndices nodes")(
      (1 to 15).map { i =>
        test(s"toString AtIndices $i elements") {
          val optic = DynamicOptic.root.atIndices((0 until i): _*)
          assertTrue(optic.toString.contains("["))
        }
      }: _*
    ) +
    suite("toString special characters")(
      test("toString newline string") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a\nb"))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("\\n"))
      },
      test("toString tab string") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a\tb"))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("\\t"))
      },
      test("toString carriage return string") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a\rb"))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("\\r"))
      },
      test("toString quote string") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a\"b"))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("\\\""))
      },
      test("toString backslash string") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a\\b"))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("\\\\"))
      },
      test("toString newline char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('\n'))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.length > 0)
      },
      test("toString tab char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('\t'))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.length > 0)
      },
      test("toString carriage return char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('\r'))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.length > 0)
      },
      test("toString single quote char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('\''))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.length > 0)
      },
      test("toString backslash char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('\\'))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.length > 0)
      },
      test("toString normal char") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Char('A'))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("'A'"))
      }
    ) +
    suite("toString numeric primitives")(
      test("toString Int key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("42"))
      },
      test("toString Long key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Long(999L))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("999"))
      },
      test("toString Float key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Float(1.5f))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("1.5"))
      },
      test("toString Double key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Double(2.5))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("2.5"))
      },
      test("toString Boolean true key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("true"))
      },
      test("toString Boolean false key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("false"))
      },
      test("toString Byte key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("10"))
      },
      test("toString Short key") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Short(100.toShort))
        val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
        assertTrue(optic.toString.contains("100"))
      }
    )

  // ===========================================================================
  // DynamicOptic toScalaString Tests
  // ===========================================================================
  val dynamicOpticToScalaStringTests = suite("DynamicOptic toScalaString rendering")(
    (1 to 20).map { i =>
      test(s"toScalaString Field $i") {
        val optic = DynamicOptic.root.field(s"field$i")
        assertTrue(optic.toScalaString.contains(s".field$i"))
      }
    }: _*
  ) +
    suite("toScalaString Case nodes")(
      (1 to 20).map { i =>
        test(s"toScalaString Case $i") {
          val optic = DynamicOptic.root.caseOf(s"Case$i")
          assertTrue(optic.toScalaString.contains(s".when[Case$i]"))
        }
      }: _*
    ) +
    suite("toScalaString AtIndex nodes")(
      (0 to 19).map { i =>
        test(s"toScalaString AtIndex $i") {
          val optic = DynamicOptic.root.at(i)
          assertTrue(optic.toScalaString.contains(s".at($i)"))
        }
      }: _*
    ) +
    suite("toScalaString AtIndices nodes")(
      (1 to 10).map { i =>
        test(s"toScalaString AtIndices $i elements") {
          val optic = DynamicOptic.root.atIndices((0 until i): _*)
          assertTrue(optic.toScalaString.contains(".atIndices("))
        }
      }: _*
    ) +
    suite("toScalaString traversal nodes")(
      test("toScalaString Elements") {
        assertTrue(DynamicOptic.elements.toScalaString == ".each")
      },
      test("toScalaString MapKeys") {
        assertTrue(DynamicOptic.mapKeys.toScalaString == ".eachKey")
      },
      test("toScalaString MapValues") {
        assertTrue(DynamicOptic.mapValues.toScalaString == ".eachValue")
      },
      test("toScalaString Wrapped") {
        assertTrue(DynamicOptic.wrapped.toScalaString == ".wrapped")
      },
      test("toScalaString root") {
        assertTrue(DynamicOptic.root.toScalaString == ".")
      }
    ) +
    suite("toScalaString atKey/atKeys")(
      (1 to 10).map { i =>
        test(s"toScalaString atKey int $i") {
          val key   = DynamicValue.Primitive(PrimitiveValue.Int(i))
          val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
          assertTrue(optic.toScalaString.contains(".atKey("))
        }
      }: _*
    ) +
    suite("toScalaString atMapKeys")(
      (1 to 10).map { i =>
        test(s"toScalaString atKeys $i keys") {
          val keys  = (1 to i).map(j => DynamicValue.Primitive(PrimitiveValue.Int(j)))
          val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(keys)))
          assertTrue(optic.toScalaString.contains(".atKeys("))
        }
      }: _*
    )

  // ===========================================================================
  // DynamicOptic render other primitives - exercises fallback case
  // ===========================================================================
  val dynamicOpticRenderPrimitivesTests = suite("DynamicOptic render other primitives")(
    test("toString with Unit primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Unit)
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with BigInt primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("123456789012345678901234567890")))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with BigDecimal primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456789012345678901234567890")))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with UUID primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.UUID(java.util.UUID.randomUUID()))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with Instant primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.now()))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with LocalDate primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.now()))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with LocalTime primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.now()))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with LocalDateTime primitive") {
      val key   = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.now()))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(key)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with Record DynamicValue") {
      val record = DynamicValue.Record(Chunk(("field", DynamicValue.Primitive(PrimitiveValue.Int(1)))))
      val optic  = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(record)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with Sequence DynamicValue") {
      val seq   = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(seq)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with Map DynamicValue") {
      val map = DynamicValue.Map(
        Chunk((DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Primitive(PrimitiveValue.Int(1))))
      )
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(map)))
      assertTrue(optic.toString.length > 0)
    },
    test("toString with Null DynamicValue") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(DynamicValue.Null)))
      assertTrue(optic.toString.length > 0)
    }
  )

  // ===========================================================================
  // Complex path tests
  // ===========================================================================
  val dynamicOpticComplexPathTests = suite("DynamicOptic complex paths")(
    (1 to 30).map { depth =>
      test(s"Complex path depth $depth") {
        var optic = DynamicOptic.root
        (0 until depth).foreach { j =>
          j % 5 match {
            case 0 => optic = optic.field(s"f$j")
            case 1 => optic = optic.at(j)
            case 2 => optic = optic.caseOf(s"C$j")
            case 3 => optic = optic.elements
            case 4 => optic = optic.wrapped
          }
        }
        val dv       = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dv)
        assertTrue(restored == Right(optic))
      }
    }: _*
  )

  // ===========================================================================
  // DynamicPatch diff tests
  // ===========================================================================
  val dynamicPatchDiffTests = suite("DynamicPatch diff operations")(
    (1 to 30).map { i =>
      test(s"Int diff $i") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Int(i))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(i + 100))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      }
    }: _*
  ) +
    suite("String diff")(
      (1 to 20).map { i =>
        test(s"String diff $i") {
          val old    = DynamicValue.Primitive(PrimitiveValue.String(s"old$i"))
          val newVal = DynamicValue.Primitive(PrimitiveValue.String(s"new$i"))
          val patch  = Differ.diff(old, newVal)
          assertTrue(!patch.isEmpty)
        }
      }: _*
    ) +
    suite("Other primitive diffs")(
      test("Boolean diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Long diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Long(200L))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Double diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Double(1.0))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Double(2.0))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Float diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Float(1.0f))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Float(2.0f))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Byte diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Byte(1.toByte))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Byte(2.toByte))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Short diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Short(1.toShort))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Short(2.toShort))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Char diff") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Char('a'))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Char('b'))
        val patch  = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      }
    )

  // ===========================================================================
  // DynamicPatch apply tests
  // ===========================================================================
  val dynamicPatchApplyTests = suite("DynamicPatch apply operations")(
    (1 to 30).map { i =>
      test(s"Int patch apply $i") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Int(i))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(i + 100))
        val patch  = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    }: _*
  ) +
    suite("String patch apply")(
      (1 to 20).map { i =>
        test(s"String patch apply $i") {
          val old    = DynamicValue.Primitive(PrimitiveValue.String(s"old$i"))
          val newVal = DynamicValue.Primitive(PrimitiveValue.String(s"new$i"))
          val patch  = Differ.diff(old, newVal)
          val result = patch.apply(old)
          assertTrue(result == Right(newVal))
        }
      }: _*
    ) +
    suite("Complex structure patch")(
      test("Record field update") {
        val old = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val newVal = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Bob"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val patch  = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Sequence element update") {
        val old = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val newVal = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(99))
          )
        )
        val patch  = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Map value update") {
        val old = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val newVal = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        val patch  = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    )

  // ===========================================================================
  // Primitive value tests
  // ===========================================================================
  val primitiveValueTests = suite("PrimitiveValue comprehensive")(
    (1 to 20).map { i =>
      test(s"Int value $i") {
        val pv = PrimitiveValue.Int(i * 111)
        assertTrue(pv.value == i * 111)
      }
    }: _*
  ) +
    suite("Long values")(
      (1 to 20).map { i =>
        test(s"Long value $i") {
          val pv = PrimitiveValue.Long(i.toLong * 1000000000L)
          assertTrue(pv.value == i.toLong * 1000000000L)
        }
      }: _*
    ) +
    suite("Double values")(
      (1 to 20).map { i =>
        test(s"Double value $i") {
          val pv = PrimitiveValue.Double(i.toDouble / 7.0)
          assertTrue(pv.value > 0)
        }
      }: _*
    ) +
    suite("Float values")(
      (1 to 20).map { i =>
        test(s"Float value $i") {
          val pv = PrimitiveValue.Float(i.toFloat / 3.0f)
          assertTrue(pv.value > 0f)
        }
      }: _*
    ) +
    suite("String values")(
      (1 to 20).map { i =>
        test(s"String value $i") {
          val pv = PrimitiveValue.String("test" * i)
          assertTrue(pv.value.length == 4 * i)
        }
      }: _*
    ) +
    suite("Misc primitives")(
      test("Boolean true")(assertTrue(PrimitiveValue.Boolean(true).value)),
      test("Boolean false")(assertTrue(!PrimitiveValue.Boolean(false).value)),
      test("Unit value")(assertTrue(PrimitiveValue.Unit == PrimitiveValue.Unit)),
      test("Byte min")(assertTrue(PrimitiveValue.Byte(Byte.MinValue).value == Byte.MinValue)),
      test("Byte max")(assertTrue(PrimitiveValue.Byte(Byte.MaxValue).value == Byte.MaxValue)),
      test("Short min")(assertTrue(PrimitiveValue.Short(Short.MinValue).value == Short.MinValue)),
      test("Short max")(assertTrue(PrimitiveValue.Short(Short.MaxValue).value == Short.MaxValue))
    )

  // ===========================================================================
  // Schema serialization tests
  // ===========================================================================
  val schemaSerializationTests = suite("Schema serialization comprehensive")(
    (1 to 10).map { i =>
      test(s"DynamicOptic schema roundtrip $i fields") {
        var optic = DynamicOptic.root
        (0 until i).foreach(j => optic = optic.field(s"f$j"))
        val dv       = Schema[DynamicOptic].toDynamicValue(optic)
        val restored = Schema[DynamicOptic].fromDynamicValue(dv)
        assertTrue(restored == Right(optic))
      }
    }: _*
  ) +
    suite("Direct schema access")(
      test("elementsSchema access") {
        val schema   = DynamicOptic.Node.elementsSchema
        val dv       = schema.toDynamicValue(DynamicOptic.Node.Elements)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(DynamicOptic.Node.Elements))
      },
      test("mapKeysSchema access") {
        val schema   = DynamicOptic.Node.mapKeysSchema
        val dv       = schema.toDynamicValue(DynamicOptic.Node.MapKeys)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(DynamicOptic.Node.MapKeys))
      },
      test("mapValuesSchema access") {
        val schema   = DynamicOptic.Node.mapValuesSchema
        val dv       = schema.toDynamicValue(DynamicOptic.Node.MapValues)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(DynamicOptic.Node.MapValues))
      },
      test("wrappedSchema access") {
        val schema   = DynamicOptic.Node.wrappedSchema
        val dv       = schema.toDynamicValue(DynamicOptic.Node.Wrapped)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(DynamicOptic.Node.Wrapped))
      },
      test("fieldSchema access") {
        val schema   = DynamicOptic.Node.fieldSchema
        val node     = DynamicOptic.Node.Field("test")
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("caseSchema access") {
        val schema   = DynamicOptic.Node.caseSchema
        val node     = DynamicOptic.Node.Case("Test")
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("atIndexSchema access") {
        val schema   = DynamicOptic.Node.atIndexSchema
        val node     = DynamicOptic.Node.AtIndex(42)
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("atMapKeySchema access") {
        val schema   = DynamicOptic.Node.atMapKeySchema
        val node     = DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("atIndicesSchema access") {
        val schema   = DynamicOptic.Node.atIndicesSchema
        val node     = DynamicOptic.Node.AtIndices(Seq(1, 2, 3))
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      },
      test("atMapKeysSchema access") {
        val schema   = DynamicOptic.Node.atMapKeysSchema
        val node     = DynamicOptic.Node.AtMapKeys(Seq(DynamicValue.Primitive(PrimitiveValue.String("a"))))
        val dv       = schema.toDynamicValue(node)
        val restored = schema.fromDynamicValue(dv)
        assertTrue(restored == Right(node))
      }
    )
}
