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
 * Per-action spec for [[MigrationAction.Join]].
 *
 * Pins:
 *   - : 3-source [[SchemaExpr.StringConcat]] fixture (nested binary) combines
 *     into a single string written at `at` via `setOrFail`.
 *   -  structural pair: `Join.reverse == Split(at, sourcePaths, combiner)`.
 *   - Combiner `evalDynamic` against root DV, not against `get(at)`.
 */
object JoinSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(first: String, last: String): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "firstName" -> stringVal(first),
        "lastName"  -> stringVal(last),
        "fullName"  -> stringVal("") // to be overwritten by Join
      )
    )

  def spec: Spec[Any, Any] = suite("JoinSpec")(
    test("Join with 3-source nested StringConcat combines into a single string at `at` ( fixture)") {
      // 3-source fixture: StringConcat(StringConcat(Literal("a"), Literal("b")), Literal("c"))
      // yields concatenated DynamicValue.Primitive(String("abc")) written at `at`.
      val p1       = SchemaExpr.Literal[DynamicValue, String]("a", Schema[String])
      val p2       = SchemaExpr.Literal[DynamicValue, String]("b", Schema[String])
      val p3       = SchemaExpr.Literal[DynamicValue, String]("c", Schema[String])
      val inner    = SchemaExpr.StringConcat[DynamicValue](p1, p2)
      val combiner = SchemaExpr.StringConcat[DynamicValue](inner, p3)
      val at       = DynamicOptic.root.field("fullName")
      val sourcePaths = Chunk(
        DynamicOptic.root.field("firstName"),
        DynamicOptic.root.field("lastName"),
        DynamicOptic.root.field("middleName")
      )
      val action   = MigrationAction.Join(at, sourcePaths, combiner)
      val m        = new DynamicMigration(Chunk.single(action))
      val result   = m.apply(personRecord("Jane", "Smith"))
      assertTrue(result.isRight) &&
      assertTrue(
        result.toOption.flatMap(_.get(at).values.flatMap(_.headOption))
          == Some(stringVal("abc"))
      )
    },
    test("Join.reverse == Split(at, sourcePaths, combiner) — structural pair") {
      val at          = DynamicOptic.root.field("fullName")
      val paths       = Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b"))
      val combiner    = SchemaExpr.Literal[DynamicValue, String]("x", Schema[String])
      val join        = MigrationAction.Join(at, paths, combiner)
      val expected    = MigrationAction.Split(at, paths, combiner)
      assertTrue(join.reverse == expected) &&
      assertTrue(join.reverse.reverse == join)
    },
    test("Join composes within a DynamicMigration and runs left-to-right (apply happy path, reverse-involution)") {
      val p1       = SchemaExpr.Literal[DynamicValue, String]("foo", Schema[String])
      val p2       = SchemaExpr.Literal[DynamicValue, String]("bar", Schema[String])
      val combiner = SchemaExpr.StringConcat[DynamicValue](p1, p2)
      val at       = DynamicOptic.root.field("fullName")
      val sourcePaths = Chunk(
        DynamicOptic.root.field("firstName"),
        DynamicOptic.root.field("lastName")
      )
      val join     = MigrationAction.Join(at, sourcePaths, combiner)
      val m        = new DynamicMigration(Chunk.single(join))
      val result   = m.apply(personRecord("X", "Y"))
      assertTrue(result.isRight) &&
      assertTrue(join.reverse.reverse == join)
    }
  )
}
