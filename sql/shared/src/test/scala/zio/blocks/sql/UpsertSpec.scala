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

package zio.blocks.sql

import zio.test._
import zio.blocks.schema._

object UpsertSpec extends ZIOSpecDefault {

  // Test entity: id, name, email
  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  // Single-column table for edge cases
  case class IdOnly(id: Int)
  object IdOnly {
    implicit val schema: Schema[IdOnly] = Schema.derived
  }

  private val userTable   = Table.derived[User]
  private val idOnlyTable = Table.derived[IdOnly]

  def spec = suite("UpsertSpec")(
    suite("DO NOTHING golden strings")(
      test("Table-aware insertDoNothing renders PG and SQLite identically") {
        val frag = Upsert.insertDoNothing(userTable, User(1, "Alice", "alice@example.com"), "id")
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING""",
          frag.sql(
            SqlDialect.SQLite
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING""",
          frag.queryParams == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com")
          )
        )
      },
      test("insertDoNothing with explicit conflict column") {
        val frag = Upsert.insertDoNothing(userTable, User(2, "Bob", "bob@test.com"), "id")
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING"""
        )
      },
      test("low-level doNothingSuffix golden string") {
        val suffix = Upsert.doNothingSuffix("id")
        assertTrue(
          suffix.sql(SqlDialect.PostgreSQL) == """ ON CONFLICT ("id") DO NOTHING""",
          suffix.sql(SqlDialect.SQLite) == """ ON CONFLICT ("id") DO NOTHING""",
          suffix.queryParams.isEmpty
        )
      },
      test("low-level doNothing full insert") {
        val frag = Upsert.doNothing(
          "user",
          IndexedSeq("id", "name", "email"),
          IndexedSeq(DbValue.DbInt(1), DbValue.DbString("A"), DbValue.DbString("a@b")),
          "id"
        )
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING""",
          frag.sql(
            SqlDialect.SQLite
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING"""
        )
      },
      test("buildDoNothingSuffix alias works") {
        val s = Upsert.buildDoNothingSuffix("id")
        assertTrue(s.sql(SqlDialect.PostgreSQL) == """ ON CONFLICT ("id") DO NOTHING""")
      }
    ),
    suite("DO UPDATE golden strings")(
      test("single assignment column") {
        val frag = Upsert.insertDoUpdate(userTable, User(1, "Alice", "alice@example.com"), "id", Seq("email"))
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "email" = ?""",
          frag.sql(
            SqlDialect.SQLite
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "email" = ?""",
          // base params + assignment forwarded
          frag.queryParams == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com"),
            DbValue.DbString("alice@example.com")
          )
        )
      },
      test("multi-col assignment order preserved and PG/SQLite identical") {
        val frag = Upsert.insertDoUpdate(userTable, User(1, "Alice", "alice@example.com"), "id", Seq("name", "email"))
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?""",
          frag.sql(
            SqlDialect.SQLite
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?""",
          frag.queryParams == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com"),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com")
          )
        )
      },
      test("default update columns is all except conflict") {
        val frag = Upsert.insertDoUpdate(userTable, User(1, "Alice", "alice@example.com"), "id")
        // should be name,email in table order
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?"""
        )
      },
      test("insertDoUpdate with explicit conflict and default update set") {
        val frag = Upsert.insertDoUpdate(userTable, User(1, "Alice", "alice@example.com"), "id")
        assertTrue(
          frag.sql(SqlDialect.PostgreSQL).contains("""ON CONFLICT ("id") DO UPDATE SET"""),
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?"""
        )
      },
      test("low-level doUpdateSuffix single and multi") {
        val single = Upsert.doUpdateSuffix("id", IndexedSeq("email" -> DbValue.DbString("a@b")))
        val multi  =
          Upsert.doUpdateSuffix("id", IndexedSeq("name" -> DbValue.DbString("B"), "email" -> DbValue.DbString("b@b")))
        assertTrue(
          single.sql(SqlDialect.PostgreSQL) == """ ON CONFLICT ("id") DO UPDATE SET "email" = ?""",
          single.sql(SqlDialect.SQLite) == """ ON CONFLICT ("id") DO UPDATE SET "email" = ?""",
          single.queryParams == IndexedSeq(DbValue.DbString("a@b")),
          multi.sql(SqlDialect.PostgreSQL) == """ ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?""",
          multi.queryParams == IndexedSeq(DbValue.DbString("B"), DbValue.DbString("b@b"))
        )
      },
      test("buildDoUpdateSuffix alias works") {
        val s = Upsert.buildDoUpdateSuffix("id", IndexedSeq("email" -> DbValue.DbString("x")))
        assertTrue(s.sql(SqlDialect.PostgreSQL) == """ ON CONFLICT ("id") DO UPDATE SET "email" = ?""")
      },
      test("param forwarding: base values + assignment values appended in order") {
        // Use low-level doUpdate with explicit assignments to verify param order
        val values      = IndexedSeq(DbValue.DbInt(5), DbValue.DbString("N"), DbValue.DbString("e@x"))
        val assignments = IndexedSeq("email" -> DbValue.DbString("e@x"))
        val frag        = Upsert.doUpdate("user", IndexedSeq("id", "name", "email"), values, "id", assignments)
        assertTrue(
          frag.queryParams == values ++ assignments.map(_._2),
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "email" = ?"""
        )
      }
    ),
    suite("validation")(
      test("invalid conflict identifier throws") {
        val result = scala.util.Try(Upsert.doNothingSuffix("id; DROP TABLE user"))
        assertTrue(result.isFailure, result.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("invalid assignment identifier throws") {
        val result = scala.util.Try(Upsert.doUpdateSuffix("id", IndexedSeq("bad-col" -> DbValue.DbString("x"))))
        assertTrue(result.isFailure, result.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("invalid table name throws") {
        val result = scala.util.Try(
          Upsert.doNothing("bad-table!", IndexedSeq("id"), IndexedSeq(DbValue.DbInt(1)), "id")
        )
        assertTrue(result.isFailure)
      },
      test("unknown assignment column throws") {
        val result = scala.util.Try(
          Upsert.insertDoUpdate(userTable, User(1, "A", "a@b"), "id", Seq("unknown_col"))
        )
        assertTrue(result.isFailure, result.failed.get.getMessage.contains("not found"))
      },
      test("unknown conflict column throws") {
        val result = scala.util.Try(
          Upsert.insertDoNothing(userTable, User(1, "A", "a@b"), "unknown")
        )
        assertTrue(result.isFailure)
      },
      test("empty assignments throws") {
        val result1 = scala.util.Try(Upsert.doUpdateSuffix("id", IndexedSeq.empty))
        val result2 = scala.util.Try(Upsert.insertDoUpdate(userTable, User(1, "A", "a@b"), "id", Seq.empty))
        assertTrue(result1.isFailure, result2.isFailure)
      },
      test("single-col table doUpdate throws (no assignment)") {
        val result = scala.util.Try(Upsert.insertDoUpdate(idOnlyTable, IdOnly(1), "id"))
        assertTrue(result.isFailure)
      },
      test("assignment containing conflict column throws") {
        val result = scala.util.Try(Upsert.insertDoUpdate(userTable, User(1, "A", "a@b"), "id", Seq("id", "email")))
        assertTrue(result.isFailure, result.failed.get.getMessage.contains("conflict"))
      }
    )
  )
}
