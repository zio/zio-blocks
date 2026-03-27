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

import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.MigrationError._
import zio.blocks.schema.migration.MigrationExpr.PrimitiveConversion
import zio.test._

object MigrationSchemaRoundTripSpec extends SchemaBaseSpec {
  private def str(v: String): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Int(v))
  private def long(v: Long): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Long(v))
  private def bool(v: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(v))

  private def roundTrip[A](value: A)(implicit schema: Schema[A]): Either[String, A] =
    schema.fromDynamicValue(schema.toDynamicValue(value)).left.map(_.message)

  def spec: Spec[Any, Any] = suite("MigrationSchemaRoundTripSpec")(
    test("PrimitiveConversion schema round trips every conversion case") {
      val conversions = Vector[PrimitiveConversion](
        PrimitiveConversion.IntToLong,
        PrimitiveConversion.LongToInt,
        PrimitiveConversion.IntToString,
        PrimitiveConversion.StringToInt,
        PrimitiveConversion.LongToString,
        PrimitiveConversion.StringToLong,
        PrimitiveConversion.DoubleToString,
        PrimitiveConversion.StringToDouble,
        PrimitiveConversion.FloatToDouble,
        PrimitiveConversion.DoubleToFloat,
        PrimitiveConversion.BooleanToString,
        PrimitiveConversion.StringToBoolean
      )

      assertTrue(conversions.forall(c => roundTrip(c) == Right(c)))
    },
    test("MigrationExpr schema round trips nested expressions") {
      val expressions = Vector[MigrationExpr](
        MigrationExpr.Identity,
        MigrationExpr.Literal(int(1)),
        MigrationExpr.FieldAccess(DynamicOptic.root.field("name")),
        MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToString),
        MigrationExpr.Concat(
          Vector(
            MigrationExpr.FieldAccess(DynamicOptic.root.field("first")),
            MigrationExpr.Literal(str("suffix"))
          ),
          "-"
        ),
        MigrationExpr.Compose(
          MigrationExpr.FieldAccess(DynamicOptic.root.field("age")),
          MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToLong)
        ),
        MigrationExpr.DefaultValue
      )

      assertTrue(expressions.forall(expr => roundTrip(expr) == Right(expr)))
    },
    test("MigrationAction schema round trips every action variant") {
      val nested  = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      val actions = Vector[MigrationAction](
        AddField(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(1))),
        DropField(DynamicOptic.root.field("legacy"), MigrationExpr.DefaultValue),
        Rename(DynamicOptic.root.field("name"), "fullName"),
        TransformValue(
          DynamicOptic.root.field("age"),
          MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToLong)
        ),
        Mandate(DynamicOptic.root.field("nickname"), MigrationExpr.Literal(str("guest"))),
        Optionalize(DynamicOptic.root.field("enabled")),
        Join(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          MigrationExpr.Concat(
            Vector(
              MigrationExpr.FieldAccess(DynamicOptic.root.field("first")),
              MigrationExpr.FieldAccess(DynamicOptic.root.field("last"))
            ),
            " "
          )
        ),
        Split(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          MigrationExpr.FieldAccess(DynamicOptic.root.field("fullName"))
        ),
        ChangeType(
          DynamicOptic.root.field("count"),
          MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToString)
        ),
        RenameCase(DynamicOptic.root, "Old", "New"),
        TransformCase(DynamicOptic.root, "User", Vector(Rename(DynamicOptic.root.field("name"), "fullName"))),
        TransformElements(DynamicOptic.root, MigrationExpr.Identity),
        TransformKeys(
          DynamicOptic.root,
          MigrationExpr.Concat(Vector(MigrationExpr.Identity, MigrationExpr.Literal(str("key"))), "-")
        ),
        TransformValues(
          DynamicOptic.root,
          MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToLong)
        ),
        NestedMigration(DynamicOptic.root.field("child"), nested)
      )

      assertTrue(roundTrip(actions) == Right(actions))
    },
    test("MigrationError variants expose stable messages and paths") {
      val missing   = MissingField(DynamicOptic.root.field("name"), "name")
      val mismatch  = TypeMismatch(DynamicOptic.root.field("age"), "Int", "String")
      val invalid   = InvalidValue(DynamicOptic.root, "boom")
      val composite = CompositeError(Vector(InvalidValue(DynamicOptic.root.field("right"), "bad")))

      assertTrue(
        missing.message == "Missing field 'name'" &&
          mismatch.message == "Type mismatch. Expected: Int, actual: String" &&
          invalid.message == "boom" &&
          composite.message == "bad" &&
          composite.path == DynamicOptic.root.field("right")
      )
    },
    test("DynamicMigration schema round trips a non empty action list") {
      val migration = DynamicMigration(
        Vector(
          AddField(DynamicOptic.root.field("active"), MigrationExpr.Literal(bool(true))),
          ChangeType(
            DynamicOptic.root.field("age"),
            MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToLong)
          ),
          NestedMigration(
            DynamicOptic.root.field("child"),
            DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
          )
        )
      )

      assertTrue(roundTrip(migration) == Right(migration))
    }
  )
}
