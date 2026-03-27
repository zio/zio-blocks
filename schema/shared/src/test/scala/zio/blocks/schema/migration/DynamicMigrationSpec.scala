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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {
  private def str(v: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(v))

  def spec: Spec[Any, Any] = suite("DynamicMigrationSpec")(
    test("AddField adds field with default") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(AddField(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(10)))))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> str("Ann"), "age" -> int(10)))))
    },
    test("DropField removes field") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann"), "age" -> int(10)))
      val mig    = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), MigrationExpr.DefaultValue)))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> str("Ann")))))
    },
    test("Rename renames field in root record") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("fullName" -> str("Ann")))))
    },
    test("TransformValue action updates field value") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(
        Vector(
          TransformValue(
            DynamicOptic.root.field("name"),
            MigrationExpr.Concat(Vector(MigrationExpr.Identity, MigrationExpr.Literal(str(" Smith"))), "")
          )
        )
      )
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> str("Ann Smith")))))
    },
    test("Mandate action unwraps Some and uses default for None") {
      val someInput = DynamicValue.Record(Chunk("nickname" -> DynamicValue.Variant("Some", str("Ann"))))
      val noneInput = DynamicValue.Record(Chunk("nickname" -> DynamicValue.Variant("None", DynamicValue.Null)))
      val mig       = DynamicMigration(
        Vector(Mandate(DynamicOptic.root.field("nickname"), MigrationExpr.Literal(str("Unknown"))))
      )
      assertTrue(
        mig(someInput).contains(DynamicValue.Record(Chunk("nickname" -> str("Ann")))) &&
          mig(noneInput).contains(DynamicValue.Record(Chunk("nickname" -> str("Unknown"))))
      )
    },
    test("Optionalize action wraps value in Some variant") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(Optionalize(DynamicOptic.root.field("name"))))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> DynamicValue.Variant("Some", str("Ann"))))))
    },
    test("RenameCase action renames matching variant case") {
      val source = DynamicValue.Variant("Old", str("x"))
      val mig    = DynamicMigration(Vector(RenameCase(DynamicOptic.root, "Old", "New")))
      assertTrue(mig(source).contains(DynamicValue.Variant("New", str("x"))))
    },
    test("TransformCase action applies nested actions to payload") {
      val payload = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val source  = DynamicValue.Variant("Person", payload)
      val mig     = DynamicMigration(
        Vector(
          TransformCase(
            DynamicOptic.root,
            "Person",
            Vector(Rename(DynamicOptic.root.field("name"), "fullName"))
          )
        )
      )
      assertTrue(
        mig(source).contains(DynamicValue.Variant("Person", DynamicValue.Record(Chunk("fullName" -> str("Ann")))))
      )
    },
    test("ChangeType action converts Int to Long") {
      val source = DynamicValue.Record(Chunk("age" -> int(10)))
      val mig    = DynamicMigration(
        Vector(
          ChangeType(
            DynamicOptic.root.field("age"),
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
          )
        )
      )
      assertTrue(
        mig(source).contains(
          DynamicValue.Record(Chunk("age" -> DynamicValue.Primitive(PrimitiveValue.Long(10L))))
        )
      )
    },
    test("NestedMigration action applies nested migration at path") {
      val source = DynamicValue.Record(
        Chunk("address" -> DynamicValue.Record(Chunk("zip" -> str("10001"))))
      )
      val nested = DynamicMigration(Vector(Rename(DynamicOptic.root.field("zip"), "postalCode")))
      val mig    = DynamicMigration(Vector(NestedMigration(DynamicOptic.root.field("address"), nested)))
      assertTrue(
        mig(source).contains(
          DynamicValue.Record(Chunk("address" -> DynamicValue.Record(Chunk("postalCode" -> str("10001")))))
        )
      )
    },
    test("Join action combines source paths into target field") {
      val source = DynamicValue.Record(Chunk("first" -> str("Ann"), "last" -> str("Smith")))
      val mig    = DynamicMigration(
        Vector(
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
          )
        )
      )
      assertTrue(
        mig(source).contains(
          DynamicValue.Record(Chunk("first" -> str("Ann"), "last" -> str("Smith"), "fullName" -> str("Ann Smith")))
        )
      )
    },
    test("Split action writes splitter output to all targets") {
      val source = DynamicValue.Record(Chunk("full" -> str("Ann Smith")))
      val mig    = DynamicMigration(
        Vector(
          Split(
            DynamicOptic.root.field("full"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            MigrationExpr.FieldAccess(DynamicOptic.root.field("full"))
          )
        )
      )
      assertTrue(
        mig(source).contains(
          DynamicValue.Record(
            Chunk("full" -> str("Ann Smith"), "first" -> str("Ann Smith"), "last" -> str("Ann Smith"))
          )
        )
      )
    },
    test("TransformElements maps over sequence") {
      val source = DynamicValue.Sequence(Chunk(str("a"), str("b")))
      val mig    = DynamicMigration(
        Vector(TransformElements(DynamicOptic.root, MigrationExpr.Concat(Vector(MigrationExpr.Identity), "!")))
      )
      assertTrue(mig(source).isRight)
    },
    test("wrong DynamicValue type at path returns Left") {
      val source = str("not-a-record")
      val mig    = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(mig(source).isLeft)
    },
    test("missing path yields Left with path info") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), MigrationExpr.DefaultValue)))
      assertTrue(mig(source).isLeft)
    }
  )
}
