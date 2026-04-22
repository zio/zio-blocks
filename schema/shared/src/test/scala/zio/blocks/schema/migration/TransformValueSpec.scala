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

object TransformValueSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  def spec: Spec[Any, Any] = suite("TransformValueSpec")(
    test("TransformValue.reverse is involutive ( / ) — reverse equals self") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])
      )
      assertTrue(action.reverse == action) &&
      assertTrue(action.reverse.reverse == action)
    },
    test("TransformValue with a Literal SchemaExpr replaces the value at `at`") {
      val original = personRecord("Alice", 30)
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values.flatMap(_.headOption))
          == Some(intVal(99))
      )
    },
    test("two TransformValues compose — run in order") {
      val original = personRecord("Alice", 30)
      val t1 = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, Int](50, Schema[Int])
      )
      val t2 = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])
      )
      val m      = new DynamicMigration(Chunk(t1, t2))
      val result = m.apply(original)
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("age")).values.flatMap(_.headOption))
          == Some(intVal(99))
      )
    }
  )
}
