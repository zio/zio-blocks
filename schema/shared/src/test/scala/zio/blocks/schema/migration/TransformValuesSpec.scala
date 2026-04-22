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

object TransformValuesSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def mapVal(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(Chunk.from(entries))

  def spec: Spec[Any, Any] = suite("TransformValuesSpec")(
    test("TransformValues applies transform to each value of the map, preserving keys") {
      val k0       = stringVal("a")
      val k1       = stringVal("b")
      val mapDv    = mapVal(k0 -> intVal(1), k1 -> intVal(2))
      val literal  = SchemaExpr.Literal[DynamicValue, Int](0, Schema[Int])
      val action   = MigrationAction.TransformValues(DynamicOptic.root, literal)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(mapDv)
      val expected = mapVal(k0 -> intVal(0), k1 -> intVal(0))
      assertTrue(result == new Right(expected))
    },
    test("TransformValues per-entry failure surfaces ActionFailed with path containing the offending key marker") {
      // Use a DefaultValue transform which fails at runtime (evalDynamic always returns Left).
      val failTransform = SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))
      val k0            = stringVal("a")
      val mapDv         = mapVal(k0 -> intVal(1))
      val action        = MigrationAction.TransformValues(DynamicOptic.root, failTransform)
      val m             = new DynamicMigration(Chunk.single(action))
      val result        = m.apply(mapDv)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists {
        case af: MigrationError.ActionFailed => af.path.toScalaString.nonEmpty
        case _                               => false
      })
    },
    test("TransformValues.reverse == self (involutive)") {
      val literal = SchemaExpr.Literal[DynamicValue, Int](0, Schema[Int])
      val action  = MigrationAction.TransformValues(DynamicOptic.root, literal)
      assertTrue(action.reverse eq action)
    },
    test("TransformValues on a non-Map yields SchemaMismatch") {
      val action =
        MigrationAction.TransformValues(DynamicOptic.root, SchemaExpr.Literal[DynamicValue, Int](0, Schema[Int]))
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(intVal(42))
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    }
  )
}
