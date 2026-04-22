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

object MandateSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def someInt(n: Int): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> intVal(n))))
  private val noneVal: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record.empty)
  private def defaultIntExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  def spec: Spec[Any, Any] = suite("MandateSpec")(
    test("Mandate unwraps Some(v) to v at the addressed path ( first bullet — Some branch)") {
      val original = DynamicValue.Record(Chunk("x" -> someInt(7)))
      val action   = MigrationAction.Mandate(DynamicOptic.root.field("x"), defaultIntExpr)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("x")).values.flatMap(_.headOption))
          == Some(intVal(7))
      )
    },
    test("Mandate fills None with the resolved default ( first bullet — None branch)") {
      val original = DynamicValue.Record(Chunk("x" -> noneVal))
      val action   = MigrationAction.Mandate(DynamicOptic.root.field("x"), defaultIntExpr)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("x")).values.flatMap(_.headOption))
          == Some(intVal(0))
      )
    },
    test("Mandate(at, DefaultValue(_, S)).reverse == Optionalize(at, S) ( canonical branch)") {
      val at = DynamicOptic.root.field("x")
      val s  = SchemaRepr.Primitive("int")
      // DefaultValue must use `at` (not root) so round-trip Optionalize.reverse produces the same DefaultValue(at, s)
      val defaultExpr = SchemaExpr.DefaultValue(at, s)
      val action      = MigrationAction.Mandate(at, defaultExpr)
      assertTrue(action.reverse == MigrationAction.Optionalize(at, s)) &&
      assertTrue(action.reverse.reverse == action)
    },
    test("Mandate(at, non-DefaultValue).reverse == self ( fallback involutive branch)") {
      val at                        = DynamicOptic.root.field("x")
      val literal: SchemaExpr[_, _] = SchemaExpr.Literal[DynamicValue, Int](42, Schema[Int])
      val action                    = MigrationAction.Mandate(at, literal)
      assertTrue(action.reverse eq action)
    }
  )
}
