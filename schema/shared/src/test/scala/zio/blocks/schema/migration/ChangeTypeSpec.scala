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

object ChangeTypeSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  def spec: Spec[Any, Any] = suite("ChangeTypeSpec")(
    test("ChangeType.reverse is involutive — reverse equals self") {
      val action = MigrationAction.ChangeType(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, String]("thirty", Schema[String])
      )
      assertTrue(action.reverse == action) &&
      assertTrue(action.reverse.reverse == action)
    },
    test("ChangeType with a String Literal replaces the value at `at` (type-changing shape)") {
      val original = personRecord("Alice", 30)
      val action = MigrationAction.ChangeType(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, String]("thirty", Schema[String])
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values.flatMap(_.headOption))
          == Some(stringVal("thirty"))
      )
    },
    test("ChangeType composes with TransformValue in a single DynamicMigration") {
      val original = personRecord("Alice", 30)
      val ct = MigrationAction.ChangeType(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, String]("30", Schema[String])
      )
      val tv = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, String]("thirty", Schema[String])
      )
      val m      = new DynamicMigration(Chunk(ct, tv))
      val result = m.apply(original)
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values.flatMap(_.headOption))
          == Some(stringVal("thirty"))
      )
    }
  )
}
