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
 * Per-action spec for [[MigrationAction.Split]].
 *
 * Pins:
 *   - Happy path: splitter.evalDynamic returns a DynamicValue.Record whose
 *     fields align positionally with targetPaths; each field dispatched via
 *     setOrFail.
 *   - Shape-mismatch emission: arity or type mismatch →
 *     MigrationError.Irreversible.
 *   - structural pair: `Split.reverse == Join(at, targetPaths, splitter)`.
 */
object SplitSpec extends SchemaBaseSpec {

  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))

  // Splitter that returns a 2-field Record { "a" -> "x", "b" -> "y" } regardless of input.
  private def twoFieldRecordSplitter: SchemaExpr[_, _] =
    SchemaExpr.Literal[DynamicValue, DynamicValue](
      DynamicValue.Record(Chunk("a" -> stringVal("x"), "b" -> stringVal("y"))),
      Schema[DynamicValue]
    )

  // Splitter that returns a bare Primitive (NOT a Record) — forces Irreversible.
  private def stringSplitter: SchemaExpr[_, _] =
    SchemaExpr.Literal[DynamicValue, String]("not-a-record", Schema[String])

  def spec: Spec[Any, Any] = suite("SplitSpec")(
    test("Split dispatches a 2-field Record splitter result into 2 target paths via setOrFail ( happy path)") {
      val at          = DynamicOptic.root.field("fullName")
      val targetPaths = Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName"))
      val action      = MigrationAction.Split(at, targetPaths, twoFieldRecordSplitter)
      // Start with a record that has fullName + placeholder firstName/lastName fields.
      val original = DynamicValue.Record(
        Chunk(
          "fullName"  -> stringVal("Jane Smith"),
          "firstName" -> stringVal(""),
          "lastName"  -> stringVal("")
        )
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("firstName")).values.flatMap(_.headOption))
          == Some(stringVal("x"))
      ) &&
      assertTrue(
        result.toOption.flatMap(_.get(DynamicOptic.root.field("lastName")).values.flatMap(_.headOption))
          == Some(stringVal("y"))
      )
    },
    test("Split with non-Record splitter result emits MigrationError.Irreversible ( shape-mismatch path)") {
      val at          = DynamicOptic.root.field("fullName")
      val targetPaths = Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName"))
      val action      = MigrationAction.Split(at, targetPaths, stringSplitter)
      val original    = DynamicValue.Record(
        Chunk(
          "fullName"  -> stringVal("Jane Smith"),
          "firstName" -> stringVal(""),
          "lastName"  -> stringVal("")
        )
      )
      val m      = new DynamicMigration(Chunk.single(action))
      val result = m.apply(original)
      assertTrue(result.isLeft) &&
      assertTrue(result.swap.toOption.exists {
        case ir: MigrationError.Irreversible =>
          ir.path == at && ir.cause.contains("split result shape mismatch")
        case _ => false
      })
    },
    test("Split.reverse == Join(at, targetPaths, splitter) — structural pair ( symmetric)") {
      val at          = DynamicOptic.root.field("fullName")
      val targetPaths = Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b"))
      val splitter    = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val split       = MigrationAction.Split(at, targetPaths, splitter)
      val expected    = MigrationAction.Join(at, targetPaths, splitter)
      assertTrue(split.reverse == expected) &&
      assertTrue(split.reverse.reverse == split)
    }
  )
}
