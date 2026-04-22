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

object RenameCaseSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def variantVal(caseName: String, inner: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, inner)

  def spec: Spec[Any, Any] = suite("RenameCaseSpec")(
    test("RenameCase rewrites Variant.caseNameValue from `from` to `to`") {
      val original = variantVal("Foo", intVal(1))
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "Foo", "Bar")
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result == new Right(variantVal("Bar", intVal(1))))
    },
    test("RenameCase(at, from, to).reverse == RenameCase(at, to, from)") {
      val at     = DynamicOptic.root.caseOf("Foo")
      val action = MigrationAction.RenameCase(at, "Foo", "Bar")
      assertTrue(action.reverse == MigrationAction.RenameCase(at, "Bar", "Foo")) &&
      assertTrue(action.reverse.reverse == action)
    },
    test("RenameCase on a non-matching case yields SchemaMismatch (interpreter degradation per Recipe #3)") {
      val original = variantVal("Bar", intVal(1))
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "Foo", "Baz")
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    },
    test("RenameCase on a non-Variant value yields SchemaMismatch") {
      val original = intVal(42)
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "Foo", "Bar")
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    }
  )
}
