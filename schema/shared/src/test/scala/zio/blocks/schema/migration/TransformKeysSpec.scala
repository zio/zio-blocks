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

object TransformKeysSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(Chunk("name" -> stringVal(name), "age" -> intVal(age)))

  private def mapVal(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(Chunk.from(entries))

  def spec: Spec[Any, Any] = suite("TransformKeysSpec")(
    test("TransformKeys applies transform to each key, preserving values (happy path — single-entry map, no collision possible)") {
      // A single-entry map can never produce a collision regardless of the transform.
      // Use Literal to rename the key from stringVal("a") to stringVal("X"); value is preserved.
      val k0      = stringVal("a")
      val mapDv   = mapVal(k0 -> intVal(42))
      val rename: SchemaExpr[_, _] = SchemaExpr.Literal[DynamicValue, String]("X", Schema[String])
      val action  = MigrationAction.TransformKeys(DynamicOptic.root, rename)
      val m       = new DynamicMigration(Chunk.single(action))
      val result  = m.apply(mapDv)
      val newKey  = stringVal("X")
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.exists {
          case dvm: DynamicValue.Map =>
            dvm.entries.length == 1 &&
            dvm.entries(0)._1 == newKey &&
            dvm.entries(0)._2 == intVal(42)
          case _ => false
        }
      )
    },
    test("collisionAtIndex0: TransformKeys returns KeyCollision(path, srcKey) on immediate collision (entry 0 vs entry 1)/ ") {
      val k0      = stringVal("a")
      val k1      = stringVal("b")
      val mapDv   = mapVal(k0 -> intVal(1), k1 -> intVal(2))
      val collapse: SchemaExpr[_, _] = SchemaExpr.Literal[DynamicValue, String]("X", Schema[String])
      val action  = MigrationAction.TransformKeys(DynamicOptic.root, collapse)
      val m       = new DynamicMigration(Chunk.single(action))
      val result  = m.apply(mapDv)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists {
        case kc: MigrationError.KeyCollision => kc.key == k1  // first colliding source key (entry 1 collides with entry 0)
        case _                               => false
      })
    },
    /** Covers collision detection framed from the opposite end of the map:
     *  a 2-entry input where the LAST entry's transformed key collides with
     *  the first entry's transformed key already in the accumulator. The
     *  offending source key reported in `KeyCollision` is the LAST key.
     *
     *  Full last-of-N fuzzing is deferred until non-constant, non-injective
     *  `SchemaExpr` constructors (e.g. `Match` / `Cond` / table-mapping) exist
     *  in the migration-used subset; the currently-available constructors are
     *  either constant (`Literal`) or injective, so they cannot produce a
     *  `k0 -> X, k1 -> Y, k2 -> X` collision pattern. This test is retained as
     *  the hook for that future enhancement.
     */
    test("collisionAtLastIndex: 2-entry map — last entry collides with index 0 — fail-fast reports last key as offending") {
      val k0     = stringVal("a")
      val k1     = stringVal("b")  // LAST entry of a 2-entry map = at "last index"
      val mapDv  = mapVal(k0 -> intVal(1), k1 -> intVal(2))
      val collapse: SchemaExpr[_, _] = SchemaExpr.Literal[DynamicValue, String]("X", Schema[String])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, collapse)
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(mapDv)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists {
        case kc: MigrationError.KeyCollision => kc.key == k1  // LAST source key (offending; collides with k0's transformed image already in accumulator)
        case _                               => false
      })
    },
    test("TransformKeys.reverse == self (involutive)") {
      val literal = SchemaExpr.Literal[DynamicValue, String]("X", Schema[String])
      val action  = MigrationAction.TransformKeys(DynamicOptic.root, literal)
      assertTrue(action.reverse eq action)
    },
    test("TransformKeys on a non-Map yields SchemaMismatch") {
      val action = MigrationAction.TransformKeys(DynamicOptic.root, SchemaExpr.Literal[DynamicValue, String]("X", Schema[String]))
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(intVal(42))
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.SchemaMismatch]))
    }
  )
}
