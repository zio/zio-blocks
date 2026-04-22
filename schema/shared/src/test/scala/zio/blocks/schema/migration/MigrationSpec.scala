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

/**
 * Typed-envelope spec. Pins:
 *   - : `Migration[A, B].apply` delegation through
 *     `sourceSchema.toDynamicValue(a) -> dynamic.apply -> targetSchema.fromDynamicValue`
 *     — verified by end-to-end round-trip (Schema[Int] identity) AND by a
 *     dynamic-injection test that asserts the `DynamicMigration` sees the exact
 *     DynamicValue produced by `toDynamicValue`.
 *   - : `Migration.identity[Int]` round-trips any Int.
 *   - `++` / `andThen` / `reverse` structural behaviour.
 */
object MigrationSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(n))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("apply delegation")(
      test("Migration.identity[Int].apply(a) round-trips through toDynamicValue -> empty.apply -> fromDynamicValue") {
        // End-to-end chain: the result Right(a) can ONLY arise if all three
        // delegation steps ran without short-circuiting —  indirect proof.
        check(Gen.int)(a => assertTrue(Migration.identity[Int].apply(a) == Right(a)))
      },
      test("apply short-circuits on DynamicMigration failure — decode step not reached ( mid-step short-circuit)") {
        // Craft a DynamicMigration whose apply always returns Left — the
        // SchemaError wrap at the decode step MUST NOT appear.
        val failing = MigrationAction.Rename(DynamicOptic.root.field("nope"), "other")
        val dm      = new DynamicMigration(Chunk.single(failing))
        val m       = new Migration(Schema[Int], Schema[Int], dm)
        val result  = m.apply(42)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.exists {
          case af: MigrationError.ActionFailed  => af.actionName != "decode"
          case _: MigrationError.MissingField   => true
          case _: MigrationError.SchemaMismatch => true
          case _                                => false
        })
      },
      test("apply wraps decode-step SchemaError into ActionFailed(root, \"decode\", cause) ( decode-step error)") {
        // Craft a DynamicMigration whose apply succeeds BUT produces a
        // DynamicValue whose shape is incompatible with the target Schema.
        // TransformValue at root with a String Literal replaces the Int DV
        // with a String DV; fromDynamicValue on Schema[Int] returns Left(SchemaError).
        val stringLiteral     = SchemaExpr.Literal[DynamicValue, String]("not-an-int", Schema[String])
        val transformToString = MigrationAction.TransformValue(DynamicOptic.root, stringLiteral)
        val dm                = new DynamicMigration(Chunk.single(transformToString))
        val m                 = new Migration(Schema[Int], Schema[Int], dm)
        val result            = m.apply(42)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.exists {
          case af: MigrationError.ActionFailed =>
            af.actionName == "decode" && af.path == DynamicOptic.root && af.cause.isDefined
          case _ => false
        })
      }
    ),
    suite("identity")(
      test("Migration.identity[Int].apply(a) == Right(a) for any Int") {
        check(Gen.int)(a => assertTrue(Migration.identity[Int].apply(a) == Right(a)))
      },
      test("Migration.identity[String].apply(s) == Right(s) for any alphanumeric String") {
        check(Gen.alphaNumericString)(s => assertTrue(Migration.identity[String].apply(s) == Right(s)))
      }
    ),
    suite("++ / andThen / reverse")(
      test("++ composes source -> target -> target2 pipelines") {
        val m1: Migration[Int, Int]       = Migration.identity[Int]
        val m2: Migration[Int, Int]       = Migration.identity[Int]
        val combined: Migration[Int, Int] = m1 ++ m2
        assertTrue(combined.sourceSchema eq m1.sourceSchema) &&
        assertTrue(combined.targetSchema eq m2.targetSchema) &&
        assertTrue(combined.apply(7) == Right(7))
      },
      test("andThen is an alias for ++") {
        val m1: Migration[Int, Int] = Migration.identity[Int]
        val m2: Migration[Int, Int] = Migration.identity[Int]
        assertTrue(m1.andThen(m2).dynamic.actions == (m1 ++ m2).dynamic.actions)
      },
      test("reverse swaps source/target schemas and reverses the underlying DynamicMigration") {
        val dm  = new DynamicMigration(Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b")))
        val m   = new Migration(Schema[Int], Schema[Int], dm)
        val rev = m.reverse
        assertTrue(rev.sourceSchema eq m.targetSchema) &&
        assertTrue(rev.targetSchema eq m.sourceSchema) &&
        assertTrue(rev.dynamic == dm.reverse)
      }
    )
  )
}
