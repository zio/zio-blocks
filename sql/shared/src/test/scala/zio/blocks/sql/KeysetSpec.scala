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

object KeysetSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  val userTable: Table[User] = Table.derived[User]

  def spec: Spec[TestEnvironment, Any] = suite("KeysetSpec")(
    suite("Frag.keysetAfter")(
      test("renders WHERE col > ? ORDER BY col ASC LIMIT n (table-validated)") {
        val frag = Frag.keysetAfter(userTable, "id", DbValue.DbInt(42), 20)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == " WHERE id > ? ORDER BY id ASC LIMIT 20",
          frag.sql(SqlDialect.PostgreSQL) == " WHERE id > ? ORDER BY id ASC LIMIT 20",
          frag.queryParams == IndexedSeq(DbValue.DbInt(42))
        )
      },
      test("renders without table (identifier only)") {
        val frag = Frag.keysetAfter("created_at", DbValue.DbLong(1000L), 10)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == " WHERE created_at > ? ORDER BY created_at ASC LIMIT 10",
          frag.queryParams == IndexedSeq(DbValue.DbLong(1000L))
        )
      },
      test("composes with base SELECT") {
        val base = Frag.literal("SELECT id, name, email FROM user")
        val page = base ++ Frag.keysetAfter(userTable, "id", DbValue.DbInt(5), 10)
        assertTrue(
          page.sql(SqlDialect.SQLite) == "SELECT id, name, email FROM user WHERE id > ? ORDER BY id ASC LIMIT 10",
          page.queryParams == IndexedSeq(DbValue.DbInt(5))
        )
      },
      test("rejects unknown column (table-validated)") {
        val result = scala.util.Try(Frag.keysetAfter(userTable, "unknown_col", DbValue.DbInt(1), 10))
        assertTrue(
          result.isFailure,
          result.failed.get.isInstanceOf[IllegalArgumentException],
          result.failed.get.getMessage.contains("not found in table")
        )
      },
      test("rejects invalid identifier (table-validated)") {
        val result = scala.util.Try(Frag.keysetAfter(userTable, "bad-col!", DbValue.DbInt(1), 10))
        assertTrue(result.isFailure && result.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("rejects invalid identifier (no table)") {
        val result = scala.util.Try(Frag.keysetAfter("bad col", DbValue.DbInt(1), 10))
        assertTrue(result.isFailure && result.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("rejects limit <= 0 (table overload)") {
        val r1 = scala.util.Try(Frag.keysetAfter(userTable, "id", DbValue.DbInt(1), 0))
        val r2 = scala.util.Try(Frag.keysetAfter(userTable, "id", DbValue.DbInt(1), -5))
        assertTrue(
          r1.isFailure && r1.failed.get.isInstanceOf[IllegalArgumentException],
          r2.isFailure && r2.failed.get.isInstanceOf[IllegalArgumentException]
        )
      },
      test("rejects limit <= 0 (no-table overload)") {
        val r1 = scala.util.Try(Frag.keysetAfter("id", DbValue.DbInt(1), 0))
        assertTrue(r1.isFailure && r1.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("uses > not >= (no duplicate cursor row)") {
        val frag = Frag.keysetAfter(userTable, "id", DbValue.DbInt(99), 5)
        assertTrue(frag.sql(SqlDialect.SQLite).contains("id > ?"), !frag.sql(SqlDialect.SQLite).contains("id >= ?"))
      }
    )
  )
}
