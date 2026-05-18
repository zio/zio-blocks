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

package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object DynamicMigrationSpec extends ZIOSpecDefault {

  private def dynamicLiteral[A: Schema](value: A): DynamicSchemaExpr =
    DynamicSchemaExpr.Literal(Schema[A].toDynamicValue(value))

  private val root = DynamicOptic.root

  private val sampleRecord = DynamicValue.Record(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("executeMandate")(
      test("unwraps Some variant") {
        val input = DynamicValue.Record(
          "status" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.MandateField(root.field("status"), dynamicLiteral(0))
        )
        assertTrue(
          migration(input) == Right(
            DynamicValue.Record("status" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
      },
      test("replaces None variant with default") {
        val input = DynamicValue.Record(
          "status" -> DynamicValue.Variant("None", DynamicValue.Record())
        )
        val migration = DynamicMigration.single(
          MigrationAction.MandateField(root.field("status"), dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Record("status" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
      },
      test("passes through non-Option value unchanged") {
        val input = DynamicValue.Record(
          "status" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
        )
        val migration = DynamicMigration.single(
          MigrationAction.MandateField(root.field("status"), dynamicLiteral(0))
        )
        assertTrue(
          migration(input) == Right(
            DynamicValue.Record("status" -> DynamicValue.Primitive(PrimitiveValue.Int(10)))
          )
        )
      }
    ),
    suite("executeOptionalize")(
      test("wraps value in Some variant") {
        val input = DynamicValue.Record(
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
        val migration = DynamicMigration.single(
          MigrationAction.OptionalizeField(root.field("age"))
        )
        assertTrue(
          migration(input) == Right(
            DynamicValue.Record(
              "age" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(25)))
            )
          )
        )
      }
    ),
    suite("executeRenameField")(
      test("renames a field in a record") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(root.field("name"), "fullName")
        )
        assertTrue(
          migration(sampleRecord) == Right(
            DynamicValue.Record(
              "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          )
        )
      },
      test("fails when target field already exists") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(root.field("name"), "age")
        )
        assertTrue(migration(sampleRecord).isLeft)
      },
      test("fails when source field not found") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(root.field("missing"), "other")
        )
        assertTrue(migration(sampleRecord).isLeft)
      },
      test("fails when path does not end with Field node") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(root, "other")
        )
        assertTrue(migration(sampleRecord).isLeft)
      }
    ),
    suite("executeChangeType")(
      test("changes field value via converter expression") {
        val input = DynamicValue.Record(
          "score" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val migration = DynamicMigration.single(
          MigrationAction.ChangeFieldType(root.field("score"), dynamicLiteral("42"))
        )
        assertTrue(
          migration(input) == Right(
            DynamicValue.Record("score" -> DynamicValue.Primitive(PrimitiveValue.String("42")))
          )
        )
      }
    ),
    suite("executeRenameCase")(
      test("renames matching case") {
        val input     = DynamicValue.Variant("Active", DynamicValue.Record())
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(root, "Active", "Enabled")
        )
        assertTrue(migration(input) == Right(DynamicValue.Variant("Enabled", DynamicValue.Record())))
      },
      test("leaves non-matching case unchanged") {
        val input     = DynamicValue.Variant("Inactive", DynamicValue.Record())
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(root, "Active", "Enabled")
        )
        assertTrue(migration(input) == Right(input))
      },
      test("fails on non-Variant value") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(root, "Active", "Enabled")
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      }
    ),
    suite("executeTransformCase")(
      test("applies actions to variant inner value") {
        val input = DynamicValue.Variant(
          "User",
          DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            root,
            zio.blocks.chunk.Chunk(
              MigrationAction.AddField(DynamicOptic.root.field("age"), dynamicLiteral(0))
            )
          )
        )
        val result  = migration(input)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(result.isRight && variant.caseNameValue == "User")
      },
      test("fails on non-Variant value") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(root, zio.blocks.chunk.Chunk.empty)
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      }
    ),
    suite("Irreversible")(
      test("executing Irreversible action fails") {
        val migration = DynamicMigration.single(
          MigrationAction.Irreversible(root, "TransformField")
        )
        assertTrue(migration(sampleRecord).isLeft)
      }
    ),
    suite("modifyAtPath error branches")(
      test("Field node on non-Record value fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration.single(
          MigrationAction.OptionalizeField(root.field("x"))
        )
        assertTrue(migration(input).isLeft)
      },
      test("Case node on non-matching case fails") {
        val input     = DynamicValue.Variant("Other", DynamicValue.Record())
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("Expected"), DynamicOptic.Node.Field("x"))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(input).isLeft)
      },
      test("Case node on non-Variant fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("X"), DynamicOptic.Node.Field("x"))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("Elements node on non-Sequence fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.Elements)),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("MapKeys node on non-Map fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.MapKeys)),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("MapValues node on non-Map fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.MapValues)),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("AtIndex on non-Sequence fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.AtIndex(0))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("AtIndex out of bounds fails") {
        val input     = DynamicValue.Sequence(zio.blocks.chunk.Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.AtIndex(5))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(input).isLeft)
      },
      test("AtMapKey on non-Map fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("AtMapKey with missing key fails") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(
            DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("z"))))),
            dynamicLiteral(0)
          )
        )
        assertTrue(migration(input).isLeft)
      }
    ),
    suite("TransformElements/Keys/Values execution")(
      test("TransformElements on non-Sequence fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(root, dynamicLiteral(0))
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("TransformElements replaces all elements") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(root, dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              zio.blocks.chunk.Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(99)),
                DynamicValue.Primitive(PrimitiveValue.Int(99))
              )
            )
          )
        )
      },
      test("TransformKeys on non-Map fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(root, dynamicLiteral("k"))
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("TransformKeys replaces all keys") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(root, dynamicLiteral("newKey"))
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("TransformValues on non-Map fails") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(root, dynamicLiteral(0))
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("TransformValues replaces all values") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(root, dynamicLiteral(0))
        )
        val result = migration(input)
        assertTrue(result.isRight)
      }
    ),
    suite("AddField/DropField error paths")(
      test("AddField with path not ending in Field fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(root, dynamicLiteral(0))
        )
        assertTrue(migration(sampleRecord).isLeft)
      },
      test("DropField with path not ending in Field fails") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(root, dynamicLiteral(0))
        )
        assertTrue(migration(sampleRecord).isLeft)
      },
      test("AddField on non-Record fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(root.field("x"), dynamicLiteral(0))
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("DropField for nonexistent field fails") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(root.field("missing"), dynamicLiteral(0))
        )
        assertTrue(migration(sampleRecord).isLeft)
      },
      test("DropField on non-Record fails") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(root.field("x"), dynamicLiteral(0))
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      },
      test("AddField fails when field already exists") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(root.field("name"), dynamicLiteral("dup"))
        )
        assertTrue(migration(sampleRecord).isLeft)
      }
    ),
    suite("MigrationError.message coverage")(
      test("all MigrationErrorKind variants produce messages") {
        import SchemaError.MigrationErrorKind._
        val path   = DynamicOptic.root
        val errors = List(
          SchemaError.MigrationError(path, PathNotFound),
          SchemaError.MigrationError(path, TypeMismatch("Record", "Int")),
          SchemaError.MigrationError(path, MissingDefault("age")),
          SchemaError.MigrationError(path, TransformFailed("reason")),
          SchemaError.MigrationError(path, FieldNotFound("name")),
          SchemaError.MigrationError(path, FieldAlreadyExists("name")),
          SchemaError.MigrationError(path, CaseNotFound("Active")),
          SchemaError.MigrationError(path, InvalidValue("bad")),
          SchemaError.MigrationError(path, MandateFailed("missing"))
        )
        assertTrue(errors.forall(_.message.nonEmpty))
      }
    ),
    suite("ConversionType coverage")(
      test("StringToInt success and failure") {
        val conv = SchemaExpr.ConversionType.StringToInt
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("42")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToLong success and failure") {
        val conv = SchemaExpr.ConversionType.StringToLong
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("100")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToDouble success and failure") {
        val conv = SchemaExpr.ConversionType.StringToDouble
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("3.14")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToFloat success and failure") {
        val conv = SchemaExpr.ConversionType.StringToFloat
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("1.5")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToShort success and failure") {
        val conv = SchemaExpr.ConversionType.StringToShort
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("10")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToByte success and failure") {
        val conv = SchemaExpr.ConversionType.StringToByte
        val ok   = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("5")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("abc")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(ok.isRight && fail.isLeft && bad.isLeft)
      },
      test("StringToBoolean success and failure") {
        val conv = SchemaExpr.ConversionType.StringToBoolean
        val t    = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("true")))
        val f    = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("false")))
        val fail = conv.convert(DynamicValue.Primitive(PrimitiveValue.String("maybe")))
        val bad  = conv.convert(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(t.isRight && f.isRight && fail.isLeft && bad.isLeft)
      },
      test("widening conversions") {
        import SchemaExpr.ConversionType._
        val results = List(
          ByteToShort.convert(DynamicValue.Primitive(PrimitiveValue.Byte(1))),
          ByteToInt.convert(DynamicValue.Primitive(PrimitiveValue.Byte(1))),
          ByteToLong.convert(DynamicValue.Primitive(PrimitiveValue.Byte(1))),
          ByteToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Byte(1))),
          ByteToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Byte(1))),
          ShortToInt.convert(DynamicValue.Primitive(PrimitiveValue.Short(1))),
          ShortToLong.convert(DynamicValue.Primitive(PrimitiveValue.Short(1))),
          ShortToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Short(1))),
          ShortToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Short(1))),
          IntToLong.convert(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          IntToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          IntToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          LongToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          LongToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          FloatToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.0f)))
        )
        assertTrue(results.forall(_.isRight))
      },
      test("narrowing conversions succeed when exact and in range") {
        import SchemaExpr.ConversionType._
        val results = List(
          ShortToByte.convert(DynamicValue.Primitive(PrimitiveValue.Short(1))),
          IntToByte.convert(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          IntToShort.convert(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          LongToByte.convert(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          LongToShort.convert(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          LongToInt.convert(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          DoubleToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.0))),
          DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.0))),
          DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.0))),
          FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))),
          FloatToLong.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))),
          IntToChar.convert(DynamicValue.Primitive(PrimitiveValue.Int(65)))
        )
        assertTrue(results.forall(_.isRight))
      },
      test("narrowing conversions fail when lossy or out of range") {
        import SchemaExpr.ConversionType._
        val results = List(
          ShortToByte.convert(DynamicValue.Primitive(PrimitiveValue.Short(128))),
          IntToByte.convert(DynamicValue.Primitive(PrimitiveValue.Int(128))),
          IntToShort.convert(DynamicValue.Primitive(PrimitiveValue.Int(Short.MaxValue.toInt + 1))),
          LongToByte.convert(DynamicValue.Primitive(PrimitiveValue.Long(128L))),
          LongToShort.convert(DynamicValue.Primitive(PrimitiveValue.Long(Short.MaxValue.toLong + 1L))),
          LongToInt.convert(DynamicValue.Primitive(PrimitiveValue.Long(Int.MaxValue.toLong + 1L))),
          DoubleToFloat.convert(DynamicValue.Primitive(PrimitiveValue.Double(0.1d))),
          DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.5d))),
          DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.5d))),
          FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          FloatToLong.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          IntToChar.convert(DynamicValue.Primitive(PrimitiveValue.Int(Char.MaxValue.toInt + 1)))
        )
        assertTrue(results.forall(_.isLeft))
      },
      test("toString conversions") {
        import SchemaExpr.ConversionType._
        val results = List(
          IntToString.convert(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          LongToString.convert(DynamicValue.Primitive(PrimitiveValue.Long(42L))),
          DoubleToString.convert(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          FloatToString.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          BooleanToString.convert(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          ShortToString.convert(DynamicValue.Primitive(PrimitiveValue.Short(10))),
          ByteToString.convert(DynamicValue.Primitive(PrimitiveValue.Byte(5))),
          CharToString.convert(DynamicValue.Primitive(PrimitiveValue.Char('a'))),
          CharToInt.convert(DynamicValue.Primitive(PrimitiveValue.Char('a')))
        )
        assertTrue(results.forall(_.isRight))
      },
      test("conversion type mismatch errors") {
        import SchemaExpr.ConversionType._
        val wrong   = DynamicValue.Primitive(PrimitiveValue.String("x"))
        val results = List(
          ByteToShort.convert(wrong),
          ShortToInt.convert(wrong),
          IntToLong.convert(wrong),
          LongToInt.convert(wrong),
          FloatToInt.convert(wrong),
          DoubleToInt.convert(wrong),
          CharToInt.convert(wrong),
          IntToChar.convert(wrong),
          ByteToFloat.convert(wrong),
          ShortToFloat.convert(wrong),
          FloatToLong.convert(wrong),
          DoubleToFloat.convert(wrong),
          ShortToByte.convert(wrong),
          IntToByte.convert(wrong),
          IntToShort.convert(wrong),
          LongToByte.convert(wrong),
          LongToShort.convert(wrong),
          LongToFloat.convert(wrong),
          ByteToDouble.convert(wrong),
          ShortToDouble.convert(wrong),
          IntToFloat.convert(wrong),
          IntToDouble.convert(wrong),
          LongToDouble.convert(wrong),
          FloatToDouble.convert(wrong),
          DoubleToLong.convert(wrong),
          IntToString.convert(wrong),
          LongToString.convert(wrong),
          DoubleToString.convert(wrong),
          FloatToString.convert(wrong),
          BooleanToString.convert(wrong),
          ShortToString.convert(wrong),
          ByteToString.convert(wrong),
          CharToString.convert(wrong)
        )
        assertTrue(results.forall(_.isLeft))
      }
    ),
    suite("PrimitiveConversion via DynamicSchemaExpr")(
      test("PrimitiveConversion evaluates correctly") {
        val expr   = DynamicSchemaExpr.PrimitiveConversion(SchemaExpr.ConversionType.IntToString)
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("42")))))
      },
      test("PrimitiveConversion returns error for wrong type") {
        val expr   = DynamicSchemaExpr.PrimitiveConversion(SchemaExpr.ConversionType.IntToString)
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.String("x")))
        assertTrue(result.isLeft)
      }
    ),
    suite("MigrationAction.reverse coverage")(
      test("TransformField reverse is Irreversible") {
        val action = MigrationAction.TransformField(root.field("x"), dynamicLiteral(0))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("OptionalizeField reverse is Irreversible") {
        val action = MigrationAction.OptionalizeField(root.field("x"))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("ChangeFieldType reverse is Irreversible") {
        val action = MigrationAction.ChangeFieldType(root.field("x"), dynamicLiteral(0))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformElements reverse is Irreversible") {
        val action = MigrationAction.TransformElements(root, dynamicLiteral(0))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformKeys reverse is Irreversible") {
        val action = MigrationAction.TransformKeys(root, dynamicLiteral(0))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformValues reverse is Irreversible") {
        val action = MigrationAction.TransformValues(root, dynamicLiteral(0))
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Irreversible])
      },
      test("RenameCase reverse swaps from and to") {
        val action   = MigrationAction.RenameCase(root, "A", "B")
        val reversed = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reversed.from == "B" && reversed.to == "A")
      },
      test("TransformCase reverse reverses inner actions") {
        val action = MigrationAction.TransformCase(
          root,
          zio.blocks.chunk.Chunk(
            MigrationAction.AddField(root.field("a"), dynamicLiteral(1)),
            MigrationAction.RenameField(root.field("b"), "c")
          )
        )
        val reversed = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(reversed.actions.length == 2)
      },
      test("Irreversible reverse is itself") {
        val action = MigrationAction.Irreversible(root, "test")
        assertTrue(action.reverse eq action)
      },
      test("RenameField reverse with valid path") {
        val action   = MigrationAction.RenameField(root.field("old"), "new")
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.RenameField])
      },
      test("RenameField reverse with invalid path gives Irreversible") {
        val action   = MigrationAction.RenameField(root, "new")
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      }
    ),
    suite("Rename on non-Record value")(
      test("RenameField fails on non-Record") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(root.field("x"), "y")
        )
        assertTrue(migration(DynamicValue.Primitive(PrimitiveValue.Int(1))).isLeft)
      }
    ),
    suite("DynamicSchemaExpr evaluation")(
      test("Select field from record") {
        val expr   = DynamicSchemaExpr.Select(DynamicOptic.root.field("name"))
        val result = expr.eval(sampleRecord)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
      },
      test("Select missing field returns empty") {
        val expr   = DynamicSchemaExpr.Select(DynamicOptic.root.field("missing"))
        val result = expr.eval(sampleRecord)
        assertTrue(result == Right(Seq.empty))
      },
      test("Select field from non-Record returns empty") {
        val expr   = DynamicSchemaExpr.Select(DynamicOptic.root.field("x"))
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(result == Right(Seq.empty))
      },
      test("Select Case matching") {
        val input  = DynamicValue.Variant("Active", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("Active"))))
        val result = expr.eval(input)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
      },
      test("Select Case non-matching returns error") {
        val input  = DynamicValue.Variant("Inactive", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("Active"))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select Case on None variant") {
        val input  = DynamicValue.Variant("None", DynamicValue.Record())
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("Some"))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select Case on Some variant") {
        val input  = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Case("Other"))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select Elements from Sequence") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Elements)))
        val result = expr.eval(input)
        assertTrue(result.isRight && result.toOption.get.size == 2)
      },
      test("Select Elements from empty Sequence returns error") {
        val input  = DynamicValue.Sequence(zio.blocks.chunk.Chunk.empty)
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Elements)))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select AtIndex success") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(10)),
            DynamicValue.Primitive(PrimitiveValue.Int(20))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtIndex(1))))
        val result = expr.eval(input)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(20)))))
      },
      test("Select AtIndex out of bounds") {
        val input  = DynamicValue.Sequence(zio.blocks.chunk.Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtIndex(5))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select AtIndices") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(10)),
            DynamicValue.Primitive(PrimitiveValue.Int(20)),
            DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtIndices(Seq(0, 2)))))
        val result = expr.eval(input)
        assertTrue(result.isRight && result.toOption.get.size == 2)
      },
      test("Select MapKeys from Map") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.MapKeys)))
        val result = expr.eval(input)
        assertTrue(result.isRight && result.toOption.get.size == 1)
      },
      test("Select MapKeys from empty Map returns error") {
        val input  = DynamicValue.Map(zio.blocks.chunk.Chunk.empty)
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.MapKeys)))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select MapValues from Map") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.MapValues)))
        val result = expr.eval(input)
        assertTrue(result.isRight && result.toOption.get.size == 1)
      },
      test("Select MapValues from empty Map returns error") {
        val input  = DynamicValue.Map(zio.blocks.chunk.Chunk.empty)
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.MapValues)))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select AtMapKey success") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (key, DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(key))))
        val result = expr.eval(input)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
      },
      test("Select AtMapKey missing") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val key    = DynamicValue.Primitive(PrimitiveValue.String("z"))
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(key))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Select AtMapKeys") {
        val k1    = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val k2    = DynamicValue.Primitive(PrimitiveValue.String("b"))
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (k1, DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (k2, DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKeys(Seq(k1)))))
        val result = expr.eval(input)
        assertTrue(result.isRight && result.toOption.get.size == 1)
      },
      test("Select Wrapped passes through") {
        val expr   = DynamicSchemaExpr.Select(DynamicOptic(IndexedSeq(DynamicOptic.Node.Wrapped)))
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("Relational operators") {
        val lit1  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val lit2  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.RelationalOperator._
        val results = List(
          DynamicSchemaExpr.Relational(lit1, lit2, LessThan).eval(dummy),
          DynamicSchemaExpr.Relational(lit1, lit2, LessThanOrEqual).eval(dummy),
          DynamicSchemaExpr.Relational(lit1, lit2, GreaterThan).eval(dummy),
          DynamicSchemaExpr.Relational(lit1, lit2, GreaterThanOrEqual).eval(dummy),
          DynamicSchemaExpr.Relational(lit1, lit2, Equal).eval(dummy),
          DynamicSchemaExpr.Relational(lit1, lit2, NotEqual).eval(dummy)
        )
        assertTrue(results.forall(_.isRight))
      },
      test("Logical operators") {
        val t     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val f     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.LogicalOperator._
        val andResult = DynamicSchemaExpr.Logical(t, f, And).eval(dummy)
        val orResult  = DynamicSchemaExpr.Logical(t, f, Or).eval(dummy)
        val notResult = DynamicSchemaExpr.Not(t).eval(dummy)
        assertTrue(andResult.isRight && orResult.isRight && notResult.isRight)
      },
      test("StringConcat") {
        val a      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val b      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" world")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringConcat(a, b).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello world")))))
      },
      test("StringLength") {
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringLength(s).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(5)))))
      },
      test("StringTrim") {
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("  hi  ")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringTrim(s).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hi")))))
      },
      test("StringToUpperCase and StringToLowerCase") {
        val s     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello")))
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val upper = DynamicSchemaExpr.StringToUpperCase(s).eval(dummy)
        val lower = DynamicSchemaExpr.StringToLowerCase(s).eval(dummy)
        assertTrue(
          upper == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("HELLO")))) &&
            lower == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
        )
      },
      test("StringSubstring") {
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello world")))
        val start  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val end    = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringSubstring(s, start, end).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello")))))
      },
      test("StringSubstring fails for invalid indices") {
        val s     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello world")))
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))

        val negative = DynamicSchemaExpr
          .StringSubstring(
            s,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(-1))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
          )
          .eval(dummy)

        val reversed = DynamicSchemaExpr
          .StringSubstring(
            s,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(6))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
          )
          .eval(dummy)

        val tooLong = DynamicSchemaExpr
          .StringSubstring(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(20)))
          )
          .eval(dummy)

        assertTrue(negative.isLeft && reversed.isLeft && tooLong.isLeft)
      },
      test("StringReplace") {
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello world")))
        val target = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("world")))
        val repl   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("there")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringReplace(s, target, repl).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello there")))))
      },
      test("StringStartsWith and StringEndsWith") {
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val prefix = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("he")))
        val suffix = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("lo")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val starts = DynamicSchemaExpr.StringStartsWith(s, prefix).eval(dummy)
        val ends   = DynamicSchemaExpr.StringEndsWith(s, suffix).eval(dummy)
        assertTrue(starts.isRight && ends.isRight)
      },
      test("StringContains and StringIndexOf") {
        val s        = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello world")))
        val sub      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("world")))
        val dummy    = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val contains = DynamicSchemaExpr.StringContains(s, sub).eval(dummy)
        val indexOf  = DynamicSchemaExpr.StringIndexOf(s, sub).eval(dummy)
        assertTrue(contains.isRight && indexOf.isRight)
      },
      test("StringRegexMatch") {
        val regex  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("h.*o")))
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringRegexMatch(regex, s).eval(dummy)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("StringRegexMatch fails for invalid regex") {
        val regex  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("(")))
        val s      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.StringRegexMatch(regex, s).eval(dummy)
        assertTrue(result.isLeft)
      },
      test("Arithmetic operations with all numeric types") {
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.{ArithmeticOperator, NumericTypeTag}

        def testTag(tag: NumericTypeTag, a: DynamicSchemaExpr, b: DynamicSchemaExpr): Boolean = {
          val ops = List(
            ArithmeticOperator.Add,
            ArithmeticOperator.Subtract,
            ArithmeticOperator.Multiply,
            ArithmeticOperator.Divide,
            ArithmeticOperator.Modulo,
            ArithmeticOperator.Pow
          )
          ops.forall(op => DynamicSchemaExpr.Arithmetic(a, b, op, tag).eval(dummy).isRight)
        }

        val intA   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10)))
        val intB   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val longA  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(10L)))
        val longB  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(3L)))
        val floatA = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(10.0f)))
        val floatB = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.0f)))
        val dblA   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(10.0)))
        val dblB   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.0)))
        val byteA  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(10)))
        val byteB  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(3)))
        val shortA = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(10)))
        val shortB = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(3)))
        val bigIA  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(10))))
        val bigIB  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(3))))
        val bigDA  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10))))
        val bigDB  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3))))

        assertTrue(
          testTag(NumericTypeTag.IntTag, intA, intB) &&
            testTag(NumericTypeTag.LongTag, longA, longB) &&
            testTag(NumericTypeTag.FloatTag, floatA, floatB) &&
            testTag(NumericTypeTag.DoubleTag, dblA, dblB) &&
            testTag(NumericTypeTag.ByteTag, byteA, byteB) &&
            testTag(NumericTypeTag.ShortTag, shortA, shortB) &&
            testTag(NumericTypeTag.BigIntTag, bigIA, bigIB) &&
            testTag(NumericTypeTag.BigDecimalTag, bigDA, bigDB)
        )
      },
      test("Arithmetic type mismatch errors") {
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.{ArithmeticOperator, NumericTypeTag}
        val wrong   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val results = List(
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.IntTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.LongTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.FloatTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.DoubleTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.ByteTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.ShortTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.BigIntTag).eval(dummy),
          DynamicSchemaExpr.Arithmetic(wrong, wrong, ArithmeticOperator.Add, NumericTypeTag.BigDecimalTag).eval(dummy)
        )
        assertTrue(results.forall(_.isLeft))
      },
      test("Arithmetic divide and modulo reject zero divisors") {
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.{ArithmeticOperator, NumericTypeTag}

        val results = List(
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
              ArithmeticOperator.Divide,
              NumericTypeTag.IntTag
            )
            .eval(dummy),
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(10L))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0L))),
              ArithmeticOperator.Modulo,
              NumericTypeTag.LongTag
            )
            .eval(dummy),
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(0.0f))),
              ArithmeticOperator.Divide,
              NumericTypeTag.FloatTag
            )
            .eval(dummy),
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(10.0))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(0.0))),
              ArithmeticOperator.Modulo,
              NumericTypeTag.DoubleTag
            )
            .eval(dummy),
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(10)))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))),
              ArithmeticOperator.Divide,
              NumericTypeTag.BigIntTag
            )
            .eval(dummy),
          DynamicSchemaExpr
            .Arithmetic(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10)))),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))),
              ArithmeticOperator.Modulo,
              NumericTypeTag.BigDecimalTag
            )
            .eval(dummy)
        )

        assertTrue(results.forall(_.isLeft))
      },
      test("Arithmetic power rejects unsafe exponents") {
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.{ArithmeticOperator, NumericTypeTag}

        val negativeExponent = DynamicSchemaExpr
          .Arithmetic(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(2)))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(-1)))),
            ArithmeticOperator.Pow,
            NumericTypeTag.BigIntTag
          )
          .eval(dummy)

        val hugeExponent = DynamicSchemaExpr
          .Arithmetic(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2)))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10001)))),
            ArithmeticOperator.Pow,
            NumericTypeTag.BigDecimalTag
          )
          .eval(dummy)

        assertTrue(negativeExponent.isLeft && hugeExponent.isLeft)
      },
      test("Bitwise operations") {
        val a     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0xff)))
        val b     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0x0f)))
        val dummy = DynamicValue.Primitive(PrimitiveValue.Int(0))
        import DynamicSchemaExpr.BitwiseOperator._
        val results = List(
          DynamicSchemaExpr.Bitwise(a, b, And).eval(dummy),
          DynamicSchemaExpr.Bitwise(a, b, Or).eval(dummy),
          DynamicSchemaExpr.Bitwise(a, b, Xor).eval(dummy),
          DynamicSchemaExpr.Bitwise(a, b, LeftShift).eval(dummy),
          DynamicSchemaExpr.Bitwise(a, b, RightShift).eval(dummy),
          DynamicSchemaExpr.Bitwise(a, b, UnsignedRightShift).eval(dummy)
        )
        assertTrue(results.forall(_.isRight))
      },
      test("BitwiseNot on various integral types") {
        val dummy   = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val results = List(
          DynamicSchemaExpr
            .BitwiseNot(DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(1))))
            .eval(dummy),
          DynamicSchemaExpr
            .BitwiseNot(DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(1))))
            .eval(dummy),
          DynamicSchemaExpr
            .BitwiseNot(DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))))
            .eval(dummy),
          DynamicSchemaExpr
            .BitwiseNot(DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(1L))))
            .eval(dummy)
        )
        assertTrue(results.forall(_.isRight))
      },
      test("BitwiseNot on non-integral fails") {
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr
          .BitwiseNot(DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x"))))
          .eval(dummy)
        assertTrue(result.isLeft)
      },
      test("Bitwise with Long operands") {
        val a      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0xffL)))
        val b      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0x0fL)))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.Bitwise(a, b, DynamicSchemaExpr.BitwiseOperator.And).eval(dummy)
        assertTrue(result.isRight)
      },
      test("Bitwise shift with Long operand") {
        val a      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(1L)))
        val b      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val dummy  = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result = DynamicSchemaExpr.Bitwise(a, b, DynamicSchemaExpr.BitwiseOperator.LeftShift).eval(dummy)
        assertTrue(result.isRight)
      },
      test("extractIntegral with Byte and Short") {
        val byteExpr  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(5)))
        val shortExpr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(10)))
        val dummy     = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val r1        = DynamicSchemaExpr.Bitwise(byteExpr, shortExpr, DynamicSchemaExpr.BitwiseOperator.And).eval(dummy)
        assertTrue(r1.isRight)
      },
      test("extractIntegral failure on non-integral") {
        val strExpr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val intExpr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val dummy   = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val result  = DynamicSchemaExpr.Bitwise(strExpr, intExpr, DynamicSchemaExpr.BitwiseOperator.And).eval(dummy)
        assertTrue(result.isLeft)
      }
    ),
    suite("DynamicMigration utilities")(
      test("expression evaluation fails when traversal returns multiple values") {
        val input = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            zio.blocks.chunk.Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            root.field("value"),
            DynamicSchemaExpr.Select(DynamicOptic.root.field("items").elements)
          )
        )

        assertTrue(
          migration(input).left.exists(_.message.contains("must return exactly one value, got 2"))
        )
      },
      test("empty migration is identity") {
        assertTrue(DynamicMigration.empty(sampleRecord) == Right(sampleRecord))
      },
      test("isEmpty and size") {
        assertTrue(DynamicMigration.empty.isEmpty && DynamicMigration.empty.size == 0)
        val m = DynamicMigration.single(MigrationAction.OptionalizeField(root.field("x")))
        assertTrue(!m.isEmpty && m.size == 1)
      },
      test("andThen composes") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(root.field("x"), dynamicLiteral(1)))
        val m2       = DynamicMigration.single(MigrationAction.DropField(root.field("x"), dynamicLiteral(1)))
        val composed = m1.andThen(m2)
        assertTrue(composed.size == 2)
      },
      test("reverse reverses action order") {
        val m = DynamicMigration(
          MigrationAction.AddField(root.field("a"), dynamicLiteral(1)),
          MigrationAction.AddField(root.field("b"), dynamicLiteral(2))
        )
        val rev = m.reverse
        assertTrue(rev.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("multi-action migration stops on first error") {
        val m = DynamicMigration(
          MigrationAction.DropField(root.field("nonexistent"), dynamicLiteral(0)),
          MigrationAction.AddField(root.field("x"), dynamicLiteral(1))
        )
        assertTrue(m(sampleRecord).isLeft)
      }
    ),
    suite("MigrateField execution")(
      test("applies nested migration to a field value") {
        val input = DynamicValue.Record(
          "address" -> DynamicValue.Record(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("Main St"))
          )
        )
        val nestedMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("city"), dynamicLiteral("NYC"))
        )
        val migration = DynamicMigration.single(
          MigrationAction.MigrateField(root.field("address"), nestedMigration)
        )
        val result = migration(input)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val addr   = record.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
        assertTrue(addr.fields.exists(_._1 == "city"))
      },
      test("MigrateField at root applies migration directly") {
        val nestedMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("extra"), dynamicLiteral(true))
        )
        val migration = DynamicMigration.single(
          MigrationAction.MigrateField(root, nestedMigration)
        )
        val result = migration(sampleRecord)
        assertTrue(result.isRight)
      },
      test("MigrateField with failing nested migration propagates error") {
        val failingMigration = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), dynamicLiteral(0))
        )
        val migration = DynamicMigration.single(
          MigrationAction.MigrateField(root.field("name"), failingMigration)
        )
        assertTrue(migration(sampleRecord).isLeft)
      }
    ),
    suite("modifyAtPath nested navigation")(
      test("navigate through Case node to modify nested field") {
        val input = DynamicValue.Variant(
          "Active",
          DynamicValue.Record("status" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.Case("Active"),
            DynamicOptic.Node.Field("status")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "Active",
              DynamicValue.Record("status" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
            )
          )
        )
      },
      test("navigate through Elements to modify nested fields in each element") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))),
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.Elements,
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              zio.blocks.chunk.Chunk(
                DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(0))),
                DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(0)))
              )
            )
          )
        )
      },
      test("navigate through MapValues to modify nested fields") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("k1")),
              DynamicValue.Record("v" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.MapValues,
            DynamicOptic.Node.Field("v")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(42))
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("navigate through MapKeys to modify nested structure") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (
              DynamicValue.Record("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1))),
              DynamicValue.Primitive(PrimitiveValue.String("val"))
            )
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.MapKeys,
            DynamicOptic.Node.Field("id")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("navigate through AtIndex to modify nested field") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(10))),
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(20)))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.AtIndex(1),
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              zio.blocks.chunk.Chunk(
                DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(10))),
                DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
              )
            )
          )
        )
      },
      test("navigate through AtMapKey to modify nested field") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (key, DynamicValue.Record("v" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.AtMapKey(key),
            DynamicOptic.Node.Field("v")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(42))
        )
        val result = migration(input)
        assertTrue(result.isRight)
      },
      test("AtMapKey with non-DynamicValue key fails safely") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Record("v" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        val badNode =
          DynamicOptic.Node
            .AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("placeholder")))
            .asInstanceOf[DynamicOptic.Node.AtMapKey]
        val path = DynamicOptic(
          IndexedSeq(
            badNode.copy(key = null.asInstanceOf[DynamicValue]),
            DynamicOptic.Node.Field("v")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(42))
        )
        assertTrue(migration(input).isLeft)
      },
      test("navigate through Wrapped node") {
        val input = DynamicValue.Record(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(5))
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.Wrapped,
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(99))
        )
        val result = migration(input)
        assertTrue(
          result == Right(
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
      },
      test("unsupported path node (AtIndices) fails in modifyAtPath") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.AtIndices(Seq(0))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      },
      test("unsupported path node (AtMapKeys) fails in modifyAtPath") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (key, DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.AtMapKeys(Seq(key))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      },
      test("exceeding max path depth returns error") {
        val deepNodes                             = (0 until 65).map(i => DynamicOptic.Node.Field(s"f$i"))
        val path                                  = DynamicOptic(deepNodes.toIndexedSeq)
        def buildRecord(depth: Int): DynamicValue =
          if (depth >= 65) DynamicValue.Primitive(PrimitiveValue.Int(0))
          else DynamicValue.Record(s"f$depth" -> buildRecord(depth + 1))
        val input     = buildRecord(0)
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(99))
        )
        assertTrue(migration(input).isLeft)
      },
      test("Elements fold stops on first error in sequence") {
        val input = DynamicValue.Sequence(
          zio.blocks.chunk.Chunk(
            DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.Elements,
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      },
      test("MapKeys fold stops on first error") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(10)))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.MapKeys,
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      },
      test("MapValues fold stops on first error") {
        val input = DynamicValue.Map(
          zio.blocks.chunk.Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val path = DynamicOptic(
          IndexedSeq(
            DynamicOptic.Node.MapValues,
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(path, dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      },
      test("navigate through nested record fields (2 levels deep)") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "inner" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(root.field("outer").field("inner"), dynamicLiteral(42))
        )
        assertTrue(
          migration(input) == Right(
            DynamicValue.Record(
              "outer" -> DynamicValue.Record(
                "inner" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
      },
      test("nested field not found at intermediate level") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(root.field("outer").field("missing"), dynamicLiteral(0))
        )
        assertTrue(migration(input).isLeft)
      }
    ),
    suite("evalExpr edge cases")(
      test("expression returning empty sequence fails") {
        val input = DynamicValue.Record(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val selectMissing = DynamicSchemaExpr.Select(DynamicOptic.root.field("nonexistent"))
        val migration     = DynamicMigration.single(
          MigrationAction.TransformField(root.field("x"), selectMissing)
        )
        assertTrue(migration(input).isLeft)
      }
    ),
    suite("MigrationAction.reverse additional")(
      test("MandateField reverse is OptionalizeField") {
        val action   = MigrationAction.MandateField(root.field("x"), dynamicLiteral(0))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.OptionalizeField])
      },
      test("MigrateField reverse reverses nested migration") {
        val nested = DynamicMigration(
          MigrationAction.AddField(root.field("a"), dynamicLiteral(1)),
          MigrationAction.AddField(root.field("b"), dynamicLiteral(2))
        )
        val action   = MigrationAction.MigrateField(root.field("x"), nested)
        val reversed = action.reverse.asInstanceOf[MigrationAction.MigrateField]
        assertTrue(reversed.migration.actions.length == 2)
        assertTrue(reversed.migration.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("AddField reverse is DropField") {
        val action   = MigrationAction.AddField(root.field("x"), dynamicLiteral(0))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField reverse is AddField") {
        val action   = MigrationAction.DropField(root.field("x"), dynamicLiteral(0))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
      }
    ),
    suite("DynamicMigration composition")(
      test("++ composes two migrations") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(root.field("x"), dynamicLiteral(1)))
        val m2       = DynamicMigration.single(MigrationAction.AddField(root.field("y"), dynamicLiteral(2)))
        val composed = m1 ++ m2
        assertTrue(composed.size == 2)
        val result = composed(DynamicValue.Record())
        assertTrue(result.isRight)
        val rec = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(rec.fields.length == 2)
      },
      test("multi-action migration applies all in sequence") {
        val m = DynamicMigration(
          MigrationAction.AddField(root.field("x"), dynamicLiteral(1)),
          MigrationAction.AddField(root.field("y"), dynamicLiteral(2)),
          MigrationAction.AddField(root.field("z"), dynamicLiteral(3))
        )
        val result = m(DynamicValue.Record())
        assertTrue(result.isRight)
        val rec = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(rec.fields.length == 3)
      }
    )
  )
}
