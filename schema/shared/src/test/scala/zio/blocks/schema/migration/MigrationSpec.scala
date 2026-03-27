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
import zio.test._

object MigrationSpec extends SchemaBaseSpec {
  final case class PersonV1(name: String, age: Int)
  final case class PersonV2(fullName: String, age: Int)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[Any, Any] = suite("MigrationSpec")(
    test("simple typed migration rename field") {
      val dynamic = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))
      )
      val mig = Migration[PersonV1, PersonV2](dynamic, personV1Schema, personV2Schema)
      assertTrue(mig(PersonV1("Ann", 10)) == Right(PersonV2("Ann", 10)))
    },
    test("composition via ++") {
      val m1 = Migration[PersonV1, PersonV2](
        DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))),
        personV1Schema,
        personV2Schema
      )
      val m2 = Migration.identity[PersonV2](personV2Schema)
      assertTrue((m1 ++ m2)(PersonV1("A", 1)) == Right(PersonV2("A", 1)))
    },
    test("typed migration reverse works for rename") {
      val m = Migration[PersonV1, PersonV2](
        DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))),
        personV1Schema,
        personV2Schema
      )
      val in = PersonV1("Ann", 10)
      assertTrue(m(in).flatMap(m.reverse(_)) == Right(in))
    },
    test("typed migration andThen composes") {
      val m1 = Migration[PersonV1, PersonV2](
        DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))),
        personV1Schema,
        personV2Schema
      )
      val m2 = Migration.identity[PersonV2](personV2Schema)
      assertTrue(m1.andThen(m2)(PersonV1("A", 1)) == Right(PersonV2("A", 1)))
    },
    test("Migration.identity returns unchanged value") {
      val id = Migration.identity[PersonV1](personV1Schema)
      assertTrue(id(PersonV1("Ann", 2)) == Right(PersonV1("Ann", 2)))
    },
    suite("MigrationAction schema serialization")(
      test("AddField can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("DropField can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.DropField(
          DynamicOptic.root.field("x"),
          MigrationExpr.DefaultValue
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("Rename can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.Rename(DynamicOptic.root.field("old"), "new")
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("TransformValue can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.TransformValue(
          DynamicOptic.root.field("x"),
          MigrationExpr.Identity
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("Mandate can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.Mandate(
          DynamicOptic.root.field("x"),
          MigrationExpr.DefaultValue
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("Optionalize can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.Optionalize(DynamicOptic.root.field("x"))
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("RenameCase can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("TransformCase can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.TransformCase(
          DynamicOptic.root,
          "MyCase",
          Vector(MigrationAction.Rename(DynamicOptic.root.field("x"), "y"))
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("ChangeType can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.ChangeType(
          DynamicOptic.root.field("age"),
          MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
        )
        val encoded = MigrationAction.schema.toDynamicValue(action)
        val decoded = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("TransformElements can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.TransformElements(DynamicOptic.root, MigrationExpr.Identity)
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("TransformKeys can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.TransformKeys(DynamicOptic.root, MigrationExpr.Identity)
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("TransformValues can be encoded and decoded via Schema") {
        val action: MigrationAction = MigrationAction.TransformValues(DynamicOptic.root, MigrationExpr.Identity)
        val encoded                 = MigrationAction.schema.toDynamicValue(action)
        val decoded                 = MigrationAction.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(action))
      },
      test("DynamicMigration can be encoded and decoded via Schema") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
            MigrationAction.AddField(
              DynamicOptic.root.field("age"),
              MigrationExpr.Literal(
                DynamicValue.Primitive(PrimitiveValue.Int(0))
              )
            )
          )
        )
        val encoded = DynamicMigration.schema.toDynamicValue(m)
        val decoded = DynamicMigration.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(m))
      },
      test("MigrationExpr Concat can be encoded and decoded via Schema") {
        val expr: MigrationExpr = MigrationExpr.Concat(
          Vector(
            MigrationExpr.Identity,
            MigrationExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String("_suffix"))
            )
          ),
          ""
        )
        val encoded = MigrationExpr.schema.toDynamicValue(expr)
        val decoded = MigrationExpr.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(expr))
      },
      test("MigrationExpr FieldAccess can be encoded and decoded via Schema") {
        val expr: MigrationExpr = MigrationExpr.FieldAccess(DynamicOptic.root.field("name"))
        val encoded             = MigrationExpr.schema.toDynamicValue(expr)
        val decoded             = MigrationExpr.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(expr))
      },
      test("MigrationExpr Convert can be encoded and decoded via Schema") {
        val expr: MigrationExpr = MigrationExpr.Convert(
          MigrationExpr.Identity,
          MigrationExpr.PrimitiveConversion.LongToInt
        )
        val encoded = MigrationExpr.schema.toDynamicValue(expr)
        val decoded = MigrationExpr.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(expr))
      },
      test("MigrationExpr Compose can be encoded and decoded via Schema") {
        val expr: MigrationExpr = MigrationExpr.Compose(MigrationExpr.Identity, MigrationExpr.DefaultValue)
        val encoded             = MigrationExpr.schema.toDynamicValue(expr)
        val decoded             = MigrationExpr.schema.fromDynamicValue(encoded)
        assertTrue(decoded == Right(expr))
      }
    )
  )
}
