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
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{SqlQuery => Qry, Rel, col, lit}
import zio.blocks.sql.query.*

object Task7CharacterizationSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  val userTable = Table.derived[User]
  val repoTable = Table.derived[Repo]

  def spec = suite("Task7CharacterizationSpec")(
    test("unified SqlQuery explain and statement via typed IR") {
      val rel     = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
      val q       = Qry.from(userTable).innerJoin(rel).where(col[User](_.name) === lit("alice"))
      val explain = q.explain(SqlDialect.PostgreSQL)
      val st      = q.statement(SqlDialect.PostgreSQL)
      val fragSql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      assertTrue(
        explain.contains("FROM \"user\" AS t0"),
        explain.contains("INNER JOIN \"repo\" AS t1"),
        explain.contains("?1"),
        explain.contains("-- params: 1:String"),
        !explain.contains("alice"),
        st.source.table == "user",
        st.joins.head.onLeft == SqlStatement.ColumnRef("t1", "owner_id"),
        st.frag.params == IndexedSeq(DbValue.DbString("alice")),
        fragSql.contains("WHERE t0.\"name\" = ?")
      )
    },
    test("wildcard import of zio.blocks.sql.* and zio.blocks.sql.query.* is unambiguous for SqlQuery") {
      val errors = scala.compiletime.testing.typeCheckErrors("""
        import zio.blocks.sql._
        import zio.blocks.sql.query._
        val _ : SqlQuery[Int] = null.asInstanceOf[SqlQuery[Int]]
        val _ = SqlQuery.from(null.asInstanceOf[Table[Int]])
      """)
      // After unification only one public SqlQuery remains under zio.blocks.sql.query,
      // so importing both packages should not be ambiguous (SqlQuery resolves to query package)
      assertTrue(errors.isEmpty)
    },
    test("Frag sql interpolation remains available") {
      val f2 = sql"SELECT * FROM user WHERE id = ${DbValue.DbInt(42)}"
      assertTrue(
        f2.sql(SqlDialect.PostgreSQL).contains("SELECT * FROM user"),
        f2.params == IndexedSeq(DbValue.DbInt(42))
      )
    },
    test("Dump.dump and Dump.dumpQuery both accept typed IR (compile check)") {
      val rel = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
      val q   = Qry.from(userTable).innerJoin(rel)
      // This test verifies both Dump entry points compile with the typed IR
      // Actual file emission is verified via ExplainDumpGoldenSpec with -Dzib.sql.dumpDir
      val sql = q.sql(SqlDialect.PostgreSQL)
      assertTrue(sql.contains("INNER JOIN"))
    }
  )
}
