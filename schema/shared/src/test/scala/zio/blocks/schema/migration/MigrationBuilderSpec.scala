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
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {
  private def str(v: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(v))

  def spec: Spec[Any, Any] = suite("MigrationBuilderSpec")(
    test("builder methods accumulate actions and handled top level fields") {
      val nested = Migration[Int, Int](
        DynamicMigration(Vector(Rename(DynamicOptic.root.field("inner"), "value"))),
        Schema[Int],
        Schema[Int]
      )

      val builder = Migration
        .newBuilder[Int, Int]
        .addField(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(1)))
        .dropField(DynamicOptic.root.field("legacy"))
        .renameField(DynamicOptic.root.field("name"), DynamicOptic.root.field("fullName"))
        .transformField(
          DynamicOptic.root.field("count"),
          DynamicOptic.root.field("countLabel"),
          MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToString)
        )
        .mandateField(
          DynamicOptic.root.field("country"),
          DynamicOptic.root.field("country"),
          MigrationExpr.Literal(str("IN"))
        )
        .optionalizeField(DynamicOptic.root.field("nickname"), DynamicOptic.root.field("nickname"))
        .changeFieldType(
          DynamicOptic.root.field("score"),
          DynamicOptic.root.field("score"),
          MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
        )
        .renameCase("OldCase", "NewCase")
        .inField(DynamicOptic.root.field("before"), DynamicOptic.root.field("child"), nested)
        .transformElements(DynamicOptic.root, MigrationExpr.Identity)
        .transformKeys(DynamicOptic.root, MigrationExpr.Identity)
        .transformValues(DynamicOptic.root, MigrationExpr.Identity)

      val partial = builder.buildPartial

      assertTrue(
        partial.dynamicMigration.actions == Vector(
          AddField(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(1))),
          DropField(DynamicOptic.root.field("legacy"), MigrationExpr.DefaultValue),
          Rename(DynamicOptic.root.field("name"), "fullName"),
          TransformValue(
            DynamicOptic.root.field("count"),
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToString)
          ),
          Mandate(DynamicOptic.root.field("country"), MigrationExpr.Literal(str("IN"))),
          Optionalize(DynamicOptic.root.field("nickname")),
          ChangeType(
            DynamicOptic.root.field("score"),
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
          ),
          RenameCase(DynamicOptic.root, "OldCase", "NewCase"),
          NestedMigration(DynamicOptic.root.field("child"), nested.dynamicMigration),
          TransformElements(DynamicOptic.root, MigrationExpr.Identity),
          TransformKeys(DynamicOptic.root, MigrationExpr.Identity),
          TransformValues(DynamicOptic.root, MigrationExpr.Identity)
        ) &&
          builder.handledFields == Set("age", "fullName", "countLabel", "country", "nickname", "score", "child")
      )
    }
  )
}
