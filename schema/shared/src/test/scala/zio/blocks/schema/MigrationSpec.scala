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

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  private val root                              = DynamicOptic.root
  private def field(name: String): DynamicOptic = root.field(name)

  private case class PersonV1(name: String, age: Int)
  private case class PersonV2(fullName: String, age: Int)

  private implicit val schemaPersonV1: Schema[PersonV1] = Schema.derived[PersonV1]
  private implicit val schemaPersonV2: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      suite("Identity")(
        test("empty.apply(v) == Right(v) for Record") {
          val v = DynamicValue.Record(("a", DynamicValue.int(1)), ("b", DynamicValue.string("x")))
          assertTrue(DynamicMigration.empty(v) == Right(v))
        },
        test("empty ++ m has same actions as m") {
          val m = DynamicMigration(Chunk.single(MigrationAction.RenameField(field("x"), field("y"))))
          assertTrue((DynamicMigration.empty ++ m).actions == m.actions)
        },
        test("m ++ empty has same actions as m") {
          val m = DynamicMigration(Chunk.single(MigrationAction.RenameField(field("x"), field("y"))))
          assertTrue((m ++ DynamicMigration.empty).actions == m.actions)
        }
      ),
      suite("Associativity")(
        test("(m1 ++ m2) ++ m3 and m1 ++ (m2 ++ m3) give same result") {
          val v  = DynamicValue.Record(("f", DynamicValue.int(42)))
          val m1 = DynamicMigration(Chunk.single(MigrationAction.RenameField(field("f"), field("g"))))
          val m2 = DynamicMigration(
            Chunk.single(
              MigrationAction.TransformValue(field("g"), MigrationExpr.IntToString, MigrationExpr.StringToInt)
            )
          )
          val m3 = DynamicMigration(
            Chunk.single(MigrationAction.AddField(field("h"), MigrationExpr.Literal(DynamicValue.boolean(true))))
          )
          val r1 = ((m1 ++ m2) ++ m3)(v)
          val r2 = (m1 ++ (m2 ++ m3))(v)
          assertTrue(r1 == r2)
        }
      ),
      suite("Structural reverse")(
        test("m.reverse.reverse.actions length equals m.actions length") {
          val m = DynamicMigration(
            Chunk(
              MigrationAction.RenameField(field("a"), field("b")),
              MigrationAction.AddField(field("c"), MigrationExpr.Literal(DynamicValue.int(0)))
            )
          )
          assertTrue(m.reverse.reverse.actions.length == m.actions.length)
        }
      ),
      suite("RenameField round-trip")(
        test("apply then reverse recovers original") {
          val v = DynamicValue.Record(
            ("firstName", DynamicValue.string("Ada")),
            ("lastName", DynamicValue.string("Lovelace"))
          )
          val m       = DynamicMigration(Chunk.single(MigrationAction.RenameField(field("firstName"), field("first"))))
          val applied = m(v)
          val round   = applied.flatMap(m.reverse(_))
          assertTrue(round.map(_.sortFields) == Right(v.sortFields))
        }
      ),
      suite("AddField / DropField round-trip")(
        test("AddField then reverse (DropField) recovers original") {
          val v = DynamicValue.Record(("x", DynamicValue.int(1)))
          val m = DynamicMigration(
            Chunk.single(MigrationAction.AddField(field("y"), MigrationExpr.Literal(DynamicValue.int(2))))
          )
          val applied = m(v)
          val round   = applied.flatMap(m.reverse(_))
          assertTrue(round == Right(v))
        }
      ),
      suite("TransformValue round-trip")(
        test("IntToString then StringToInt recovers original") {
          val v = DynamicValue.Record(("age", DynamicValue.int(36)))
          val m = DynamicMigration(
            Chunk.single(
              MigrationAction.TransformValue(field("age"), MigrationExpr.IntToString, MigrationExpr.StringToInt)
            )
          )
          val applied = m(v)
          val round   = applied.flatMap(m.reverse(_))
          assertTrue(round == Right(v))
        }
      ),
      suite("RenameCase round-trip")(
        test("RenameCase then reverse recovers original") {
          val v       = DynamicValue.Variant("Active", DynamicValue.Record(("since", DynamicValue.string("2020-01-01"))))
          val m       = DynamicMigration(Chunk.single(MigrationAction.RenameCase(root, "Active", "Enabled")))
          val applied = m(v)
          val round   = applied.flatMap(m.reverse(_))
          assertTrue(round == Right(v))
        }
      )
    ),
    suite("Migration typed API")(
      test("identity.apply(a) == Right(a) for Int") {
        assertTrue(Migration.identity[Int].apply(42) == Right(42))
      },
      test("identity.apply(a) == Right(a) for String") {
        assertTrue(Migration.identity[String].apply("hello") == Right("hello"))
      },
      test("typed round-trip: RenameField via Migration[PersonV1, PersonV2]") {
        val migration = MigrationBuilder[PersonV1, PersonV2]
          .renameField(field("name"), field("fullName"))
          .build
        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("selector-based renameField: PersonV1 -> PersonV2") {
        val migration = MigrationBuilder[PersonV1, PersonV2]
          .renameField(_.name, _.fullName)
          .build
        val result = migration.apply(PersonV1("Bob", 25))
        assertTrue(result == Right(PersonV2("Bob", 25)))
      }
    )
  )
}
