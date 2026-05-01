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
    suite("DynamicMigration utilities")(
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
    )
  )
}
