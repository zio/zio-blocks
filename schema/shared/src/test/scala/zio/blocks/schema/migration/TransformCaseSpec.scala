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

object TransformCaseSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def variantVal(caseName: String, inner: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, inner)

  def spec: Spec[Any, Any] = suite("TransformCaseSpec")(
    test("TransformCase applies inner actions when variant matches the terminal Case node") {
      val inner    = DynamicValue.Record(Chunk("name" -> stringVal("Alice"), "age" -> intVal(30)))
      val original = variantVal("Foo", inner)
      val at       = DynamicOptic.root.caseOf("Foo")
      val action   = MigrationAction.TransformCase(
        at,
        Chunk(MigrationAction.Rename(at.field("age"), "years"))
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(at.field("years")).values.flatMap(_.headOption))
          == Some(intVal(30))
      )
    },
    test("TransformCase pass-through on non-matching variant case — Right(input) unchanged") {
      val original = variantVal("Bar", intVal(1))
      val at       = DynamicOptic.root.caseOf("Foo")
      val action   = MigrationAction.TransformCase(
        at,
        Chunk(MigrationAction.TransformValue(at, SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int])))
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result == new Right(original))
    },
    test("TransformCase.reverse == TransformCase(at, actions.reverse.map(_.reverse)) at depth 1") {
      val at       = DynamicOptic.root.caseOf("Foo")
      val a1       = MigrationAction.RenameCase(at, "X", "Y")
      val a2       = MigrationAction.TransformValue(at, SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int]))
      val action   = MigrationAction.TransformCase(at, Chunk(a1, a2))
      val expected = MigrationAction.TransformCase(at, Chunk(a2.reverse, a1.reverse))
      assertTrue(action.reverse == expected)
    },
    test("reverseNestingDepth2: TransformCase containing TransformCase reverses recursively") {
      val innerAt     = DynamicOptic.root.caseOf("Inner")
      val outerAt     = DynamicOptic.root.caseOf("Outer")
      val innerAction = MigrationAction.RenameCase(innerAt, "A", "B")
      val inner       = MigrationAction.TransformCase(innerAt, Chunk(innerAction))
      val outerLeaf   = MigrationAction.TransformValue(outerAt, SchemaExpr.Literal[DynamicValue, Int](99, Schema[Int]))
      val outer       = MigrationAction.TransformCase(outerAt, Chunk(inner, outerLeaf))
      assertTrue(outer.reverse.reverse == outer)
    }
  )
}
