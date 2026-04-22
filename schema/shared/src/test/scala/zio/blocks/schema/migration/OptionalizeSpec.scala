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

object OptionalizeSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def defaultIntExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  def spec: Spec[Any, Any] = suite("OptionalizeSpec")(
    test("Optionalize wraps T as Variant(\"Some\", Record(Chunk(\"value\" -> v))) per Pitfall #5 (Reflect.scala:1610-1620)") {
      val original = DynamicValue.Record(Chunk("x" -> intVal(7)))
      val action   = MigrationAction.Optionalize(DynamicOptic.root.field("x"), SchemaRepr.Primitive("int"))
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      val expected = DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> intVal(7))))
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("x")).values.flatMap(_.headOption))
          == Some(expected)
      )
    },
    test("Optionalize(at, S).reverse == Mandate(at, SchemaExpr.DefaultValue(at, S))") {
      val at     = DynamicOptic.root.field("x")
      val s      = SchemaRepr.Primitive("int")
      val action = MigrationAction.Optionalize(at, s)
      assertTrue(action.reverse == MigrationAction.Mandate(at, SchemaExpr.DefaultValue(at, s)))
    },
    test("Optionalize.reverse.reverse == Optionalize ( canonical-pair closure for /)") {
      val at     = DynamicOptic.root.field("x")
      val s      = SchemaRepr.Primitive("int")
      val action = MigrationAction.Optionalize(at, s)
      assertTrue(action.reverse.reverse == action)
    },
    test("Optionalize on a path with no value yields SchemaMismatch (negative path)") {
      val original = DynamicValue.Record(Chunk("x" -> intVal(7)))
      val action   = MigrationAction.Optionalize(DynamicOptic.root.field("nonexistent"), SchemaRepr.Primitive("int"))
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    }
  )
}
