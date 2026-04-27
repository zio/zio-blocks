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

import zio.test.*

object FragSpec extends ZIOSpecDefault {
  def spec = suite("FragSpec")(
    suite("sql interpolator")(
      test("basic interpolation without params") {
        val frag = sql"SELECT 1"
        assertTrue(
          frag.parts == IndexedSeq("SELECT 1"),
          frag.params.isEmpty
        )
      },
      test("single param") {
        val frag = sql"SELECT * FROM t WHERE id = ${DbValue.DbInt(42)}"
        assertTrue(
          frag.parts == IndexedSeq("SELECT * FROM t WHERE id = ", ""),
          frag.params == IndexedSeq(DbValue.DbInt(42))
        )
      },
      test("multiple params") {
        val v1   = DbValue.DbString("Alice")
        val v2   = DbValue.DbInt(30)
        val frag = sql"INSERT INTO t (name, age) VALUES ($v1, $v2)"
        assertTrue(
          frag.parts == IndexedSeq("INSERT INTO t (name, age) VALUES (", ", ", ")"),
          frag.params == IndexedSeq(v1, v2)
        )
      },
      test("values are never in the SQL string") {
        val name     = DbValue.DbString("Robert'); DROP TABLE students;--")
        val frag     = sql"SELECT * FROM users WHERE name = $name"
        val rendered = frag.sql(SqlDialect.PostgreSQL)
        assertTrue(
          !rendered.contains("Robert"),
          !rendered.contains("DROP"),
          rendered == "SELECT * FROM users WHERE name = $1"
        )
      }
    ),
    suite("Frag.sql rendering")(
      test("PostgreSQL uses numbered placeholders") {
        val frag = sql"SELECT * FROM t WHERE a = ${DbValue.DbInt(1)} AND b = ${DbValue.DbString("x")}"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == "SELECT * FROM t WHERE a = $1 AND b = $2")
      },
      test("SQLite uses ? placeholders") {
        val frag = sql"SELECT * FROM t WHERE a = ${DbValue.DbInt(1)} AND b = ${DbValue.DbString("x")}"
        assertTrue(frag.sql(SqlDialect.SQLite) == "SELECT * FROM t WHERE a = ? AND b = ?")
      },
      test("no params renders plain SQL") {
        val frag = sql"SELECT 1"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == "SELECT 1")
      }
    ),
    suite("Frag composition")(
      test("concatenation merges adjacent parts") {
        val f1     = sql"SELECT * FROM t"
        val f2     = sql" WHERE id = ${DbValue.DbInt(1)}"
        val merged = f1 ++ f2
        assertTrue(
          merged.parts == IndexedSeq("SELECT * FROM t WHERE id = ", ""),
          merged.params == IndexedSeq(DbValue.DbInt(1))
        )
      },
      test("concatenation with params on both sides") {
        val f1     = sql"a = ${DbValue.DbInt(1)} AND "
        val f2     = sql"b = ${DbValue.DbString("x")}"
        val merged = f1 ++ f2
        assertTrue(
          merged.sql(SqlDialect.PostgreSQL) == "a = $1 AND b = $2",
          merged.params == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("x"))
        )
      },
      test("Frag.empty is identity for ++") {
        val frag = sql"SELECT 1"
        assertTrue(
          (frag ++ Frag.empty).sql(SqlDialect.PostgreSQL) == "SELECT 1",
          (Frag.empty ++ frag).sql(SqlDialect.PostgreSQL) == "SELECT 1"
        )
      }
    ),
    suite("Frag.const and Frag.empty")(
      test("Frag.const creates parameterless fragment") {
        val frag = Frag.const("ORDER BY id")
        assertTrue(
          frag.parts == IndexedSeq("ORDER BY id"),
          frag.params.isEmpty,
          frag.sql(SqlDialect.PostgreSQL) == "ORDER BY id"
        )
      },
      test("Frag.empty is empty") {
        assertTrue(Frag.empty.isEmpty)
      },
      test("Frag with params is not empty") {
        val frag = sql"SELECT ${DbValue.DbInt(1)}"
        assertTrue(!frag.isEmpty)
      }
    ),
    suite("queryParams")(
      test("returns all params in order") {
        val frag = sql"INSERT INTO t VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")}, ${DbValue.DbBoolean(true)})"
        assertTrue(
          frag.queryParams == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("a"),
            DbValue.DbBoolean(true)
          )
        )
      }
    )
  )
}
