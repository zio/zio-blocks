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
  private def str(v: String): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Int(v))
  private def long(v: Long): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Long(v))
  private def bool(v: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(v))

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
    test("TransformElements maps over sequence") {
      val source = DynamicValue.Sequence(Chunk(str("a"), str("b")))
      val mig    = DynamicMigration(
        Vector(TransformElements(DynamicOptic.root, MigrationExpr.Concat(Vector(MigrationExpr.Identity), "!")))
      )
      assertTrue(mig(source).isRight)
    },
    test("missing path yields Left with path info") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), MigrationExpr.DefaultValue)))
      assertTrue(mig(source).isLeft)
    },
    test("Mandate unwraps Some payload") {
      val source = DynamicValue.Record(Chunk("age" -> DynamicValue.Variant("Some", int(10))))
      val mig    = DynamicMigration(Vector(Mandate(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(0)))))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("age" -> int(10)))))
    },
    test("Mandate uses default for None and Null") {
      val noneSource = DynamicValue.Record(Chunk("age" -> DynamicValue.Variant("None", DynamicValue.Null)))
      val nullSource = DynamicValue.Record(Chunk("age" -> DynamicValue.Null))
      val mig        = DynamicMigration(Vector(Mandate(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(18)))))
      assertTrue(
        mig(noneSource).contains(DynamicValue.Record(Chunk("age" -> int(18)))) &&
          mig(nullSource).contains(DynamicValue.Record(Chunk("age" -> int(18))))
      )
    },
    test("Optionalize wraps field in Some") {
      val source = DynamicValue.Record(Chunk("enabled" -> bool(true)))
      val mig    = DynamicMigration(Vector(Optionalize(DynamicOptic.root.field("enabled"))))
      assertTrue(
        mig(source).contains(DynamicValue.Record(Chunk("enabled" -> DynamicValue.Variant("Some", bool(true)))))
      )
    },
    test("Join stores combined value at target path") {
      val source = DynamicValue.Record(
        Chunk(
          "first" -> str("Ann"),
          "last"  -> str("Lee"),
          "full"  -> str("")
        )
      )
      val mig = DynamicMigration(
        Vector(
          Join(
            DynamicOptic.root.field("full"),
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
          DynamicValue.Record(
            Chunk(
              "first" -> str("Ann"),
              "last"  -> str("Lee"),
              "full"  -> str("Ann Lee")
            )
          )
        )
      )
    },
    test("Split writes splitter output to each target path") {
      val source = DynamicValue.Record(
        Chunk(
          "full"  -> str("Ann Lee"),
          "first" -> str(""),
          "last"  -> str("")
        )
      )
      val mig = DynamicMigration(
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
            Chunk(
              "full"  -> str("Ann Lee"),
              "first" -> str("Ann Lee"),
              "last"  -> str("Ann Lee")
            )
          )
        )
      )
    },
    test("ChangeType converts an existing field") {
      val source = DynamicValue.Record(Chunk("age" -> int(10)))
      val mig    = DynamicMigration(
        Vector(
          ChangeType(
            DynamicOptic.root.field("age"),
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
          )
        )
      )
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("age" -> long(10L)))))
    },
    test("RenameCase renames matching variants and leaves non matching values untouched") {
      val variantMig = DynamicMigration(Vector(RenameCase(DynamicOptic.root, "Old", "New")))
      assertTrue(
        variantMig(DynamicValue.Variant("Old", str("x"))).contains(DynamicValue.Variant("New", str("x"))) &&
          variantMig(int(1)).contains(int(1))
      )
    },
    test("TransformCase transforms matching case payload") {
      val mig = DynamicMigration(
        Vector(
          TransformCase(
            DynamicOptic.root,
            "Person",
            Vector(Rename(DynamicOptic.root.field("name"), "fullName"))
          )
        )
      )
      val source = DynamicValue.Variant("Person", DynamicValue.Record(Chunk("name" -> str("Ann"))))
      assertTrue(
        mig(source).contains(DynamicValue.Record(Chunk("fullName" -> str("Ann"))))
      )
    },
    test("TransformElements leaves non sequences unchanged") {
      val mig = DynamicMigration(
        Vector(TransformElements(DynamicOptic.root, MigrationExpr.Concat(Vector(MigrationExpr.Identity), "!")))
      )
      assertTrue(mig(str("value")).contains(str("value")))
    },
    test("TransformKeys and TransformValues map over records") {
      val source = DynamicValue.Map(Chunk(str("a") -> int(1), str("b") -> int(2)))
      val keyMig = DynamicMigration(
        Vector(
          TransformKeys(
            DynamicOptic.root,
            MigrationExpr.Concat(Vector(MigrationExpr.Identity, MigrationExpr.Literal(str("key"))), "-")
          )
        )
      )
      val valueMig = DynamicMigration(
        Vector(
          TransformValues(
            DynamicOptic.root,
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
          )
        )
      )
      assertTrue(
        keyMig(source).contains(DynamicValue.Map(Chunk(str("a-key") -> int(1), str("b-key") -> int(2)))) &&
          valueMig(source).contains(DynamicValue.Map(Chunk(str("a") -> long(1L), str("b") -> long(2L))))
      )
    },
    test("NestedMigration applies nested record changes") {
      val source = DynamicValue.Record(
        Chunk(
          "user" -> DynamicValue.Record(Chunk("name" -> str("Ann")))
        )
      )
      val mig = DynamicMigration(
        Vector(
          NestedMigration(
            DynamicOptic.root.field("user"),
            DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
          )
        )
      )
      assertTrue(
        mig(source).contains(
          DynamicValue.Record(
            Chunk(
              "user" -> DynamicValue.Record(Chunk("fullName" -> str("Ann")))
            )
          )
        )
      )
    },
    test("Rename reports invalid root shape") {
      val mig = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(mig(int(1)).isLeft)
    },
    test("Rename reports missing field in record roots") {
      val source = DynamicValue.Record(Chunk("other" -> str("Ann")))
      val mig    = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(mig(source).isLeft)
    },
    test("Rename rejects non field paths") {
      val source = DynamicValue.Sequence(Chunk(str("a"), str("b")))
      val mig    = DynamicMigration(Vector(Rename(DynamicOptic.root.elements, "value")))
      assertTrue(mig(source).isLeft)
    },
    test("Join fails when one of the source paths is missing") {
      val source = DynamicValue.Record(Chunk("first" -> str("Ann"), "full" -> str("")))
      val mig    = DynamicMigration(
        Vector(
          Join(
            DynamicOptic.root.field("full"),
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
      assertTrue(mig(source).isLeft)
    },
    test("TransformCase, TransformKeys, and TransformValues leave unsupported values unchanged") {
      val caseMig = DynamicMigration(
        Vector(
          TransformCase(
            DynamicOptic.root,
            "Person",
            Vector(Rename(DynamicOptic.root.field("name"), "fullName"))
          )
        )
      )
      val keyMig = DynamicMigration(
        Vector(
          TransformKeys(
            DynamicOptic.root,
            MigrationExpr.Concat(Vector(MigrationExpr.Identity, MigrationExpr.Literal(str("key"))), "-")
          )
        )
      )
      val valueMig = DynamicMigration(
        Vector(
          TransformValues(
            DynamicOptic.root,
            MigrationExpr.Convert(MigrationExpr.Identity, MigrationExpr.PrimitiveConversion.IntToLong)
          )
        )
      )
      assertTrue(
        caseMig(DynamicValue.Variant("Other", DynamicValue.Record(Chunk("name" -> str("Ann")))))
          .contains(DynamicValue.Variant("Other", DynamicValue.Record(Chunk("name" -> str("Ann"))))) &&
          keyMig(str("value")).contains(str("value")) &&
          valueMig(str("value")).contains(str("value"))
      )
    }
  )
}
