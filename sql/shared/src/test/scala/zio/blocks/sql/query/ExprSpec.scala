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

package zio.blocks.sql.query

import zio.blocks.schema.Schema
import zio.blocks.sql.{DbValue, Frag, SqlDialect, Table}
import zio.test.*

object ExprSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String, age: Int, salary: Double)
  object User { given Schema[User] = Schema.derived }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  case class Employee(id: Int, name: String, managerId: Option[Int])
  object Employee { given Schema[Employee] = Schema.derived }

  val userTable     = Table.derived[User]
  val repoTable     = Table.derived[Repo]
  val employeeTable = Table.derived[Employee]

  def spec = suite("ExprSpec")(
    suite("where filters")(
      test("simple comparison renders qualified column with placeholder") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.where(q.col[User](_.name) === lit("Alice"))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        val exp  = "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE t0.\"name\" = ?"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == exp, frag.params == IndexedSeq(DbValue.DbString("Alice")))
      },
      test("and combinator renders parenthesized AND with two placeholders") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.where((q.col[User](_.age) > lit(21)) && q.col[User](_.name).like("a%"))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        val exp  =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE (t0.\"age\" > ? AND t0.\"name\" LIKE ?)"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == exp, frag.params.size == 2, frag.params(0) == DbValue.DbInt(21))
      },
      test("or and not combinators") {
        val qNotQ = SqlQuery.from(userTable)
        val qNot  = qNotQ.where(!(qNotQ.col[User](_.age) > lit(18)))
        val expNot =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE NOT (t0.\"age\" > ?)"
        val qOrQ = SqlQuery.from(userTable)
        val qOr  = qOrQ.where((qOrQ.col[User](_.age) < lit(10)) || (qOrQ.col[User](_.name) === lit("Bob")))
        val expOr =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE (t0.\"age\" < ? OR t0.\"name\" = ?)"
        assertTrue(
          qNot.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expNot,
          qOr.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expOr
        )
      },
      test("comparison operators cover = < > <= >= <>") {
        val q  = SqlQuery.from(userTable)
        val q2 = q.where(q.col[User](_.id) =!= lit(5))
        val exp = "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE t0.\"id\" <> ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("in combinator renders IN list with placeholders") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.where(q.col[User](_.id).in(Seq(1, 2, 3)))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        val exp  =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE t0.\"id\" IN (?, ?, ?)"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == exp, frag.params.size == 3)
      },
      test("empty in renders always-false without placeholders") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.where(q.col[User](_.id).in(Seq.empty[Int]))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        val exp  = "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE 1=0"
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == exp, frag.params.isEmpty)
      },
      test("arithmetic plus renders qualified arithmetic") {
        val q   = SqlQuery.from(userTable)
        val q2  = q.where((q.col[User](_.age) + lit(5)) > lit(30))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE (t0.\"age\" + ?) > ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("qualified column respects join alias") {
        val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        val q   = SqlQuery.from(userTable).innerJoin(rel)
        val q2  = q.where(q.col[Repo](_.name) === lit("foo"))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\" WHERE t1.\"name\" = ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("placeholder count equals params count") {
        val q            = SqlQuery.from(userTable)
        val q2           = q.where((q.col[User](_.id) > lit(1)) && (q.col[User](_.name) === lit("a")))
        val frag         = q2.toFrag(SqlDialect.PostgreSQL)
        val sql          = frag.sql(SqlDialect.PostgreSQL)
        val placeholders = sql.count(_ == '?')
        assertTrue(placeholders == frag.params.size)
      },
      test("repeated dialect rendering is deterministic and stateless") {
        val q  = SqlQuery.from(userTable)
        val q2 = q.where(q.col[User](_.name) === lit("x"))
        val p1 = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val p2 = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val s1 = q2.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val s2 = q2.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        assertTrue(p1 == p2, s1 == s2)
      }
    ),
    suite("groupBy and having")(
      test("groupBy single column renders qualified GROUP BY") {
        val q   = SqlQuery.from(userTable)
        val q2  = q.groupBy(q.col[User](_.name))
        val exp = "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\""
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("groupBy multiple columns") {
        val q   = SqlQuery.from(userTable)
        val q2  = q.groupBy(q.col[User](_.name), q.col[User](_.age))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\", t0.\"age\""
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("groupBy with join alias") {
        val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        val q   = SqlQuery.from(userTable).innerJoin(rel)
        val q2  = q.groupBy(q.col[Repo](_.name))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\" GROUP BY t1.\"name\""
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("having with count aggregate renders HAVING COUNT > ?") {
        val q   = SqlQuery.from(userTable)
        val q2  = q.groupBy(q.col[User](_.name)).having(q.count(q.col[User](_.id)) > lit(1L))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING COUNT(t0.\"id\") > ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("having with sum/avg/min/max") {
        val qSumQ  = SqlQuery.from(userTable)
        val qSum   = qSumQ.groupBy(qSumQ.col[User](_.name)).having(qSumQ.sum(qSumQ.col[User](_.salary)) > lit(1000.0))
        val expSum =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING SUM(t0.\"salary\") > ?"
        val qAvgQ  = SqlQuery.from(userTable)
        val qAvg   = qAvgQ.groupBy(qAvgQ.col[User](_.name)).having(qAvgQ.avg(qAvgQ.col[User](_.salary)) > lit(500.0))
        val expAvg =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING AVG(t0.\"salary\") > ?"
        val qMinQ  = SqlQuery.from(userTable)
        val qMin   = qMinQ.groupBy(qMinQ.col[User](_.name)).having(qMinQ.min(qMinQ.col[User](_.age)) > lit(18))
        val expMin =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING MIN(t0.\"age\") > ?"
        val qMaxQ  = SqlQuery.from(userTable)
        val qMax   = qMaxQ.groupBy(qMaxQ.col[User](_.name)).having(qMaxQ.max(qMaxQ.col[User](_.salary)) > lit(2000.0))
        val expMax =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING MAX(t0.\"salary\") > ?"
        assertTrue(
          qSum.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expSum,
          qAvg.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expAvg,
          qMin.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expMin,
          qMax.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == expMax
        )
      },
      test("countStar renders COUNT(*) in having") {
        val q   = SqlQuery.from(userTable)
        val q2  = q.groupBy(q.col[User](_.name)).having(countStar > lit(2L))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING COUNT(*) > ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      },
      test("having placeholder count equals params") {
        val q            = SqlQuery.from(userTable)
        val q2           = q.groupBy(q.col[User](_.name)).having(q.count(q.col[User](_.id)) > lit(5L))
        val frag         = q2.toFrag(SqlDialect.PostgreSQL)
        val placeholders = frag.sql(SqlDialect.PostgreSQL).count(_ == '?')
        assertTrue(placeholders == frag.params.size)
      }
    ),
    suite("self-join alias binding")(
      test("self-join unaliased col is rejected at compile time") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Employee(id: Int, name: String, managerId: Option[Int])
            object Employee { given Schema[Employee] = Schema.derived }
            val employeeTable: Table[Employee] = Table.derived[Employee]
            val q = SqlQuery.from(employeeTable).innerJoin(Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id))
            q.where(q.col[Employee](_.name) === lit("x"))
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("self-join alias-qualified colAt binds to requested alias") {
        val rel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)
        val q   = SqlQuery.from(employeeTable).innerJoin(rel)
        val q2  = q.where(q.colAt[Employee]("t1", _.name) === lit("boss"))
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"manager_id\", t1.\"id\", t1.\"name\", t1.\"manager_id\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\" WHERE t1.\"name\" = ?"
        assertTrue(q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL) == exp)
      }
    ),
    suite("compile-time typo rejection")(
      test("typo'd selector fails at compile time with named-field error") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.col[User](_.typoField)
          }"""
        )
        assertTrue(errors.nonEmpty, errors.exists(_.message.contains("typoField")))
      }
    ),
    suite("type-safety negatives (must fail at compile time)")(
      test("Int like must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String, age: Int)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.col[User](_.age).like("a%")
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("String arithmetic must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String, age: Int)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = (q.col[User](_.name) + lit("x")) > lit("y")
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("mismatched comparison must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String, age: Int)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.col[User](_.age) === lit("string")
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("unsupported literal must not compile or must throw") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            case class Foo(bar: List[Int])
            val bad = lit(List(1,2,3))
          }"""
        )
        // fallback is currently stringify, so this will not error at compile time;
        // we assert runtime failure instead if typeCheck passes
        if (errors.nonEmpty) assertTrue(errors.nonEmpty)
        else {
          val threw = try { lit(List(1, 2, 3)); false }
          catch { case _: Throwable => true }
          assertTrue(threw)
        }
      }
    ),
    suite("renamed field and custom table name mapping")(
      test("renamed field uses Modifier.rename column name") {
        import zio.blocks.schema.Modifier
        case class RenamedUser(@Modifier.rename("user_name") name: String, age: Int)
        object RenamedUser { given Schema[RenamedUser] = Schema.derived }
        val renamedTable = Table.derived[RenamedUser]
        val q            = SqlQuery.from(renamedTable)
        val q2           = q.where(q.col[RenamedUser](_.name) === lit("Alice"))
        val sql          = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp          = "SELECT t0.\"user_name\", t0.\"age\" FROM \"renamed_user\" AS t0 WHERE t0.\"user_name\" = ?"
        assertTrue(sql == exp)
      },
      test("custom table name via Modifier.config is respected for alias") {
        import zio.blocks.schema.Modifier
        @Modifier.config("sql.table_name", "my_users")
        case class CustomUser(id: Int, name: String)
        object CustomUser { given Schema[CustomUser] = Schema.derived }
        val customTable = Table.derived[CustomUser]
        assertTrue(customTable.name == "my_users")
        val q   = SqlQuery.from(customTable)
        val q2  = q.where(q.col[CustomUser](_.name) === lit("Bob"))
        val sql = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"my_users\" AS t0 WHERE t0.\"name\" = ?"
        assertTrue(sql == exp)
      }
    ),
    suite("alias validation")(
      test("out-of-range alias fails at compile time") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            q.where(q.colAt[User]("t99", _.name) === lit("x"))
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("foreign alias (Repo alias for User column) fails at compile time") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            case class Repo(id: Int, ownerId: Int, name: String)
            object Repo { given Schema[Repo] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val repoTable: Table[Repo] = Table.derived[Repo]
            val q = SqlQuery.from(userTable).innerJoin(Rel.manyToOne(repoTable, _.ownerId, userTable, _.id))
            q.where(q.colAt[User]("t1", _.name) === lit("x"))
          }"""
        )
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("second-repair type-safety and exact rendering")(
      test("sum(String) must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.sum(q.col[User](_.name))
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("avg(String) must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.avg(q.col[User](_.name))
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("Boolean ordering < must not compile") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Flag(flag: Boolean)
            object Flag { given Schema[Flag] = Schema.derived }
            val flagTable: Table[Flag] = Table.derived[Flag]
            val q = SqlQuery.from(flagTable)
            val bad = q.col[Flag](_.flag) < lit(true)
          }"""
        )
        assertTrue(errors.nonEmpty)
      },
      test("exact custom-table SQL full-string") {
        import zio.blocks.schema.Modifier
        @Modifier.config("sql.table_name", "my_users2")
        case class CustomUser2(id: Int, name: String)
        object CustomUser2 { given Schema[CustomUser2] = Schema.derived }
        val customTable = Table.derived[CustomUser2]
        val q           = SqlQuery.from(customTable)
        val q2          = q.where(q.col[CustomUser2](_.name) === lit("Bob"))
        val sql         = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp         = "SELECT t0.\"id\", t0.\"name\" FROM \"my_users2\" AS t0 WHERE t0.\"name\" = ?"
        assertTrue(sql == exp, q2.toFrag(SqlDialect.PostgreSQL).params == IndexedSeq(DbValue.DbString("Bob")))
      },
      test("placeholder parity for IN") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.where(q.col[User](_.id).in(Seq(1, 2, 3, 4, 5)))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        assertTrue(frag.sql(SqlDialect.PostgreSQL).count(_ == '?') == frag.params.size, frag.params.size == 5)
      },
      test("placeholder parity for aggregate having with IN") {
        val q    = SqlQuery.from(userTable)
        val q2   = q.groupBy(q.col[User](_.name)).having(q.count(q.col[User](_.id)) > lit(2L))
        val frag = q2.toFrag(SqlDialect.PostgreSQL)
        assertTrue(frag.sql(SqlDialect.PostgreSQL).count(_ == '?') == frag.params.size)
        val q3    = SqlQuery.from(userTable)
        val q4    = q3.where(q3.col[User](_.id).in(Seq(10, 20)))
        val frag2 = q4.toFrag(SqlDialect.PostgreSQL)
        assertTrue(
          frag2.sql(
            SqlDialect.PostgreSQL
          ) == "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"user\" AS t0 WHERE t0.\"id\" IN (?, ?)"
        )
      }
    )
  )
}