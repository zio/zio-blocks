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

object DropFieldSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  private def defaultIntExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  def spec: Spec[Any, Any] = suite("DropFieldSpec")(
    test("applies DropField to a record field, removing it") {
      val original = personRecord("Alice", 30)
      val action   = MigrationAction.DropField(DynamicOptic.root, "age", defaultIntExpr)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values).fold(true)(_.isEmpty))
    },
    test("reverse of DropField is AddField with defaultForReverse promoted to default") {
      val action   = MigrationAction.DropField(DynamicOptic.root, "age", defaultIntExpr)
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.AddField(DynamicOptic.root, "age", defaultIntExpr)) &&
      assertTrue(reversed.reverse == action)
    },
    test("DropField on a non-existent field yields MigrationError.MissingField") {
      val original = personRecord("Alice", 30)
      val action   = MigrationAction.DropField(DynamicOptic.root, "nonexistent", defaultIntExpr)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.MissingField]))
    }
  )
}
