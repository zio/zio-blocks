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

object TransformElementsSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def seqVal(values: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(Chunk.from(values))

  def spec: Spec[Any, Any] = suite("TransformElementsSpec")(
    test("TransformElements applies transform to each element of the sequence") {
      val original = seqVal(intVal(1), intVal(2), intVal(3))
      val literal  = SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])
      val action   = MigrationAction.TransformElements(DynamicOptic.root, literal)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(original)
      val expected = seqVal(intVal(99), intVal(99), intVal(99))
      assertTrue(result == new Right(expected))
    },
    test("TransformElements per-element failure surfaces ActionFailed with path containing index marker") {
      // Use a DefaultValue transform which fails (evalDynamic on DefaultValue always fails at runtime).
      val failTransform = SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))
      val original      = seqVal(intVal(1), intVal(2))
      val action        = MigrationAction.TransformElements(DynamicOptic.root, failTransform)
      val m             = new DynamicMigration(Chunk.single(action))
      val result        = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists {
        case af: MigrationError.ActionFailed => af.path.toScalaString.contains(".at(")
        case _                               => false
      })
    },
    test("TransformElements.reverse == self (involutive)") {
      val at      = DynamicOptic.root
      val literal = SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])
      val action  = MigrationAction.TransformElements(at, literal)
      assertTrue(action.reverse eq action)
    },
    test("TransformElements on a non-Sequence yields SchemaMismatch") {
      val original = intVal(42)
      val action   =
        MigrationAction.TransformElements(DynamicOptic.root, SchemaExpr.Literal[DynamicValue, Int](0, Schema[Int]))
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    }
  )
}
