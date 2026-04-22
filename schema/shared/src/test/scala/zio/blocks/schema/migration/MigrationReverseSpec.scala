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
 * Structural-pair reverse spec for [[MigrationAction.Join]] and
 * [[MigrationAction.Split]] ( / ).
 *
 * names this spec specifically. Pins:
 *   - Join.reverse == Split(at, sourcePaths, combiner) — same Chunk reference
 *   - Split.reverse == Join(at, targetPaths, splitter) — symmetric
 *   - Referential Chunk equality:
 *     `join.reverse.asInstanceOf[Split].targetPaths eq join.sourcePaths`
 *   - Involution: `a.reverse.reverse == a` for arbitrary Join/Split instances
 *     (>= 100 samples)
 */
object MigrationReverseSpec extends SchemaBaseSpec {

  private val genDynamicOptic: Gen[Any, DynamicOptic] = Gen.elements(
    DynamicOptic.root,
    DynamicOptic.root.field("a"),
    DynamicOptic.root.field("a").field("b"),
    DynamicOptic.root.elements,
    DynamicOptic.root.field("m").mapKeys
  )

  private val genPaths: Gen[Any, Chunk[DynamicOptic]] =
    for {
      xs <- Gen.listOfBounded(0, 3)(genDynamicOptic)
    } yield Chunk.from(xs)

  private val genSchemaExpr: Gen[Any, SchemaExpr[_, _]] = Gen.elements(
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int")),
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string")),
    SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
  )

  private val genJoin: Gen[Any, MigrationAction.Join] =
    for { at <- genDynamicOptic; paths <- genPaths; combiner <- genSchemaExpr } yield MigrationAction.Join(
      at,
      paths,
      combiner
    )

  private val genSplit: Gen[Any, MigrationAction.Split] =
    for { at <- genDynamicOptic; paths <- genPaths; splitter <- genSchemaExpr } yield MigrationAction.Split(
      at,
      paths,
      splitter
    )

  def spec: Spec[TestEnvironment, Any] = suite("MigrationReverseSpec")(
    test("Join.reverse == Split(at, sourcePaths, combiner) — structural pair") {
      val at       = DynamicOptic.root.field("full")
      val paths    = Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b"))
      val combiner = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val join     = MigrationAction.Join(at, paths, combiner)
      assertTrue(join.reverse == MigrationAction.Split(at, paths, combiner))
    },
    test("Split.reverse == Join(at, targetPaths, splitter) — structural pair ( symmetric)") {
      val at       = DynamicOptic.root.field("full")
      val paths    = Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b"))
      val splitter = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val split    = MigrationAction.Split(at, paths, splitter)
      assertTrue(split.reverse == MigrationAction.Join(at, paths, splitter))
    },
    test("Join.reverse passes the SAME Chunk reference through ( referential equality)") {
      val at       = DynamicOptic.root.field("full")
      val paths    = Chunk(DynamicOptic.root.field("a"))
      val combiner = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val join     = MigrationAction.Join(at, paths, combiner)
      join.reverse match {
        case s: MigrationAction.Split => assertTrue(s.targetPaths eq paths)
        case _                        => assertTrue(false)
      }
    },
    test("Split.reverse passes the SAME Chunk reference through ( symmetric)") {
      val at       = DynamicOptic.root.field("full")
      val paths    = Chunk(DynamicOptic.root.field("a"))
      val splitter = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val split    = MigrationAction.Split(at, paths, splitter)
      split.reverse match {
        case j: MigrationAction.Join => assertTrue(j.sourcePaths eq paths)
        case _                       => assertTrue(false)
      }
    },
    test("Join.reverse.reverse == self — involution at >= 100 samples ( partial)") {
      check(genJoin)(a => assertTrue(a.reverse.reverse == a))
    },
    test("Split.reverse.reverse == self — involution at >= 100 samples ( partial)") {
      check(genSplit)(a => assertTrue(a.reverse.reverse == a))
    }
  )
}
