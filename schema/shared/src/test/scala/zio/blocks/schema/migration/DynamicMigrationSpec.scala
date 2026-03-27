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
import zio.test._
import zio.test.Assertion._

object DynamicMigrationSpec extends SchemaBaseSpec {

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  def boolVal(b: Boolean): DynamicValue  = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def record(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(Chunk.fromIterable(fields))

  def variant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  def seq(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(Chunk.fromIterable(elements))

  def map(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(Chunk.fromIterable(entries))

  val root: DynamicOptic = DynamicOptic.root

  // ---------------------------------------------------------------------------
  // Specs
  // ---------------------------------------------------------------------------

  def spec: Spec[Any, Any] = suite("DynamicMigrationSpec")(
    suite("identity")(
      test("identity migration returns value unchanged") {
        val value = record("name" -> stringVal("Alice"), "age" -> intVal(30))
        assertTrue(DynamicMigration.identity(value) == Right(value))
      }
    ),
    suite("AddField")(
      test("adds a field to a record") {
        val original = record("name" -> stringVal("Alice"))
        val expected = record("name" -> stringVal("Alice"), "email" -> stringVal(""))
        val m        = DynamicMigration.single(MigrationAction.AddField(root, "email", stringVal("")))
        assertTrue(m(original) == Right(expected))
      },
      test("fails when field already exists") {
        val original = record("name" -> stringVal("Alice"))
        val m        = DynamicMigration.single(MigrationAction.AddField(root, "name", stringVal("")))
        assertTrue(m(original).isLeft)
      },
      test("adds a field to a nested record") {
        val original = record("person" -> record("name" -> stringVal("Alice")))
        val expected = record("person" -> record("name" -> stringVal("Alice"), "age" -> intVal(0)))
        val path     = DynamicOptic.root.field("person")
        val m        = DynamicMigration.single(MigrationAction.AddField(path, "age", intVal(0)))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("DropField")(
      test("drops a field from a record") {
        val original = record("name" -> stringVal("Alice"), "age" -> intVal(30))
        val expected = record("name" -> stringVal("Alice"))
        val m        = DynamicMigration.single(MigrationAction.DropField(root, "age"))
        assertTrue(m(original) == Right(expected))
      },
      test("fails when field does not exist") {
        val original = record("name" -> stringVal("Alice"))
        val m        = DynamicMigration.single(MigrationAction.DropField(root, "missing"))
        assertTrue(m(original).isLeft)
      }
    ),
    suite("RenameField")(
      test("renames a field in a record") {
        val original = record("name" -> stringVal("Alice"), "age" -> intVal(30))
        val expected = record("fullName" -> stringVal("Alice"), "age" -> intVal(30))
        val m        = DynamicMigration.single(MigrationAction.RenameField(root, "name", "fullName"))
        assertTrue(m(original) == Right(expected))
      },
      test("fails when old field does not exist") {
        val original = record("name" -> stringVal("Alice"))
        val m        = DynamicMigration.single(MigrationAction.RenameField(root, "missing", "other"))
        assertTrue(m(original).isLeft)
      },
      test("fails when new name already exists") {
        val original = record("name" -> stringVal("Alice"), "age" -> intVal(30))
        val m        = DynamicMigration.single(MigrationAction.RenameField(root, "name", "age"))
        assertTrue(m(original).isLeft)
      }
    ),
    suite("SetValue")(
      test("replaces the value at a path") {
        val original = record("name" -> stringVal("Alice"))
        val path     = DynamicOptic.root.field("name")
        val m        = DynamicMigration.single(MigrationAction.SetValue(path, stringVal("Bob")))
        val expected = record("name" -> stringVal("Bob"))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("RenameCase")(
      test("renames a variant case") {
        val original = variant("OldName", record("x" -> intVal(1)))
        val expected = variant("NewName", record("x" -> intVal(1)))
        val m        = DynamicMigration.single(MigrationAction.RenameCase(root, "OldName", "NewName"))
        assertTrue(m(original) == Right(expected))
      },
      test("leaves non-matching cases unchanged") {
        val original = variant("Other", record("x" -> intVal(1)))
        val m        = DynamicMigration.single(MigrationAction.RenameCase(root, "OldName", "NewName"))
        assertTrue(m(original) == Right(original))
      }
    ),
    suite("TransformCase")(
      test("applies migration to matching case inner value") {
        val innerMigration = DynamicMigration.single(MigrationAction.AddField(root, "y", intVal(0)))
        val original       = variant("MyCase", record("x" -> intVal(1)))
        val expected       = variant("MyCase", record("x" -> intVal(1), "y" -> intVal(0)))
        val m              = DynamicMigration.single(MigrationAction.TransformCase(root, "MyCase", innerMigration))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("TransformElements")(
      test("applies migration to every element of a sequence") {
        val innerMigration = DynamicMigration.single(MigrationAction.AddField(root, "active", boolVal(true)))
        val original       = seq(record("name" -> stringVal("A")), record("name" -> stringVal("B")))
        val expected       = seq(
          record("name" -> stringVal("A"), "active" -> boolVal(true)),
          record("name" -> stringVal("B"), "active" -> boolVal(true))
        )
        val m = DynamicMigration.single(MigrationAction.TransformElements(root, innerMigration))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("TransformKeys")(
      test("applies migration to every key of a map") {
        val innerMigration = DynamicMigration.single(MigrationAction.AddField(root, "extra", intVal(0)))
        val original       = map(record("k" -> stringVal("a")) -> intVal(1))
        val expected       = map(record("k" -> stringVal("a"), "extra" -> intVal(0)) -> intVal(1))
        val m              = DynamicMigration.single(MigrationAction.TransformKeys(root, innerMigration))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("TransformValues")(
      test("applies migration to every value of a map") {
        val innerMigration = DynamicMigration.single(MigrationAction.AddField(root, "flag", boolVal(false)))
        val original       = map(stringVal("k1") -> record("x" -> intVal(1)))
        val expected       = map(stringVal("k1") -> record("x" -> intVal(1), "flag" -> boolVal(false)))
        val m              = DynamicMigration.single(MigrationAction.TransformValues(root, innerMigration))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("ReorderFields")(
      test("reorders fields according to spec") {
        val original = record("c" -> intVal(3), "a" -> intVal(1), "b" -> intVal(2))
        val expected = record("a" -> intVal(1), "b" -> intVal(2), "c" -> intVal(3))
        val m        = DynamicMigration.single(MigrationAction.ReorderFields(root, IndexedSeq("a", "b", "c")))
        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("andThen composition")(
      test("applies actions in sequence") {
        val m1 = DynamicMigration.single(MigrationAction.RenameField(root, "name", "fullName"))
        val m2 = DynamicMigration.single(MigrationAction.AddField(root, "email", stringVal("")))
        val m3 = DynamicMigration.single(MigrationAction.DropField(root, "age"))

        val composed = m1.andThen(m2).andThen(m3)
        val original = record("name" -> stringVal("Alice"), "age" -> intVal(30))
        val expected = record("fullName" -> stringVal("Alice"), "email" -> stringVal(""))

        assertTrue(composed(original) == Right(expected))
      },
      test("associativity: (m1 andThen m2) andThen m3 == m1 andThen (m2 andThen m3)") {
        val m1 = DynamicMigration.single(MigrationAction.RenameField(root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.AddField(root, "c", intVal(0)))
        val m3 = DynamicMigration.single(MigrationAction.DropField(root, "d"))

        val original = record("a" -> intVal(1), "d" -> intVal(4))
        val left     = (m1.andThen(m2)).andThen(m3)
        val right    = m1.andThen(m2.andThen(m3))

        assertTrue(left(original) == right(original))
      }
    ),
    suite("TransformValue")(
      test("applies nested migration at a path") {
        val innerMigration = DynamicMigration.single(MigrationAction.RenameField(root, "city", "town"))
        val path           = DynamicOptic.root.field("address")
        val m              = DynamicMigration.single(MigrationAction.TransformValue(path, innerMigration))

        val original = record("name" -> stringVal("Alice"), "address" -> record("city" -> stringVal("NYC")))
        val expected = record("name" -> stringVal("Alice"), "address" -> record("town" -> stringVal("NYC")))

        assertTrue(m(original) == Right(expected))
      }
    ),
    suite("error cases")(
      test("AddField on non-record returns TypeMismatch") {
        val m = DynamicMigration.single(MigrationAction.AddField(root, "x", intVal(0)))
        assertTrue(m(intVal(42)).isLeft)
      },
      test("RenameField on non-record returns TypeMismatch") {
        val m = DynamicMigration.single(MigrationAction.RenameField(root, "a", "b"))
        assertTrue(m(stringVal("hello")).isLeft)
      },
      test("DropField on non-record returns TypeMismatch") {
        val m = DynamicMigration.single(MigrationAction.DropField(root, "x"))
        assertTrue(m(intVal(1)).isLeft)
      },
      test("RenameCase on non-variant returns TypeMismatch") {
        val m = DynamicMigration.single(MigrationAction.RenameCase(root, "A", "B"))
        assertTrue(m(intVal(1)).isLeft)
      },
      test("TransformElements on non-sequence returns TypeMismatch") {
        val m = DynamicMigration.single(
          MigrationAction.TransformElements(root, DynamicMigration.identity)
        )
        assertTrue(m(intVal(1)).isLeft)
      },
      test("invalid path returns error") {
        val path = DynamicOptic.root.field("missing")
        val m    = DynamicMigration.single(MigrationAction.AddField(path, "x", intVal(0)))
        assertTrue(m(record("name" -> stringVal("Alice"))).isLeft)
      }
    )
  )
}
