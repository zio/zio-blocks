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

object RenameSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  def spec: Spec[Any, Any] = suite("RenameSpec")(
    test("Rename renames a field in a record, preserving its value") {
      val original = personRecord("Alice", 30)
      val action   = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("years")).values.flatMap(_.headOption))
          == Some(intVal(30))
      ) &&
      assertTrue(result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values).fold(true)(_.isEmpty))
    },
    test("reverse of Rename swaps names: Rename(p.field(from), to).reverse == Rename(p.field(to), from)") {
      val action   = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.Rename(DynamicOptic.root.field("years"), "age")) &&
      assertTrue(reversed.reverse == action)
    },
    test("degenerate Rename at root reverses to itself (involutive fallback, total by construction)") {
      val action = MigrationAction.Rename(DynamicOptic.root, "x")
      assertTrue(action.reverse == action)
    },
    test("two renames compose: age -> years, then years -> months") {
      val original = personRecord("Alice", 30)
      val r1       = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
      val r2       = MigrationAction.Rename(DynamicOptic.root.field("years"), "months")
      val m        = new DynamicMigration(Chunk(r1, r2))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("months")).values.flatMap(_.headOption))
          == Some(intVal(30))
      )
    },
    // Regression test: renaming a non-terminal field
    // must preserve field order. Previously, `Rename` was implemented as
    // `delete + insert` and `DynamicValue.insertAtPath` on a Record always
    // appends, so a `Rename(.age, "years")` on Record(name, age, address)
    // would yield Record(name, address, years) instead of
    // Record(name, years, address).
    test("Rename preserves field order for a non-terminal field") {
      val original = DynamicValue.Record(
        Chunk(
          "name"    -> stringVal("Alice"),
          "age"     -> intVal(30),
          "address" -> stringVal("123 Main St")
        )
      )
      val action = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isRight) && {
        val keys = result.toOption.collect { case r: DynamicValue.Record => r.fields.map(_._1) }
        assertTrue(keys == Some(Chunk("name", "years", "address")))
      } &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("years")).values.flatMap(_.headOption))
          == Some(intVal(30))
      )
    }
  )
}
