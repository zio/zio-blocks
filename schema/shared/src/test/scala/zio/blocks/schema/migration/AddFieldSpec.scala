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

object AddFieldSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  private def defaultIntExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))
  private def defaultStringExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))

  def spec: Spec[Any, Any] = suite("AddFieldSpec")(
    test("applies AddField with SchemaExpr.DefaultValue(int) to a record, inserting zero value") {
      val original = personRecord("Alice", 30)
      val action   = MigrationAction.AddField(DynamicOptic.root, "score", defaultIntExpr)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("score")).values.flatMap(_.headOption))
          == Some(intVal(0))
      )
    },
    test("reverse of AddField is DropField with the same default payload carried as defaultForReverse") {
      val action   = MigrationAction.AddField(DynamicOptic.root, "score", defaultIntExpr)
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.DropField(DynamicOptic.root, "score", defaultIntExpr)) &&
      assertTrue(reversed.reverse == action)
    },
    test("two AddFields compose via DynamicMigration ++, inserting both fields") {
      val original = personRecord("Alice", 30)
      val add1     = MigrationAction.AddField(DynamicOptic.root, "score", defaultIntExpr)
      val add2     = MigrationAction.AddField(DynamicOptic.root, "nickname", defaultStringExpr)
      val m        = new DynamicMigration(Chunk.single(add1)) ++ new DynamicMigration(Chunk.single(add2))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("score")).values.flatMap(_.headOption))
          == Some(intVal(0))
      ) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("nickname")).values.flatMap(_.headOption))
          == Some(stringVal(""))
      )
    }
  )
}
