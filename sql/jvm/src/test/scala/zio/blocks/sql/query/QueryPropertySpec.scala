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

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.{DbValue, Frag, SqlDialect, Table}
import zio.test.*

object QueryPropertySpec extends ZIOSpecDefault {

  @Modifier.config("sql.table_name", "users")
  case class User(id: Int, name: String, age: Int, salary: Double)
  object User { given Schema[User] = Schema.derived }

  @Modifier.config("sql.table_name", "repos")
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  val userTable   = Table.derived[User]
  val repoTable   = Table.derived[Repo]
  val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)

  private def buildPlain(seed: Int): SqlQuery[?, ?] = {
    val base = SqlQuery.from(userTable)
    val q2   = (seed % 8) match {
      case 0 => base
      case 1 => base.where(base.col[User](_.age) > lit(seed % 100))
      case 2 => base.where(base.col[User](_.name).like("a%"))
      case 3 =>
        val vals = Seq(seed % 5, (seed * 7) % 11, (seed * 13) % 17).distinct.take(2)
        base.where(base.col[User](_.id).in(vals))
      case 4 =>
        base.where((base.col[User](_.age) > lit(10)) && (base.col[User](_.name) === lit("x")))
      case 5 =>
        base.where((base.col[User](_.age) < lit(50)) || (base.col[User](_.id) === lit(seed % 1000)))
      case 6 =>
        base.where(!(base.col[User](_.age) > lit(20)))
      case 7 =>
        base.where(base.col[User](_.id).in(Seq.empty[Int]))
    }
    // additional AND/OR chaining for coverage (lineage keeps q2.Scope = base.Scope)
    val q3 = if (seed % 11 == 0) q2.where(base.col[User](_.id) > lit(0)) else q2
    // groupBy / having (valid combos)
    val q4 = if (seed % 5 == 0) {
      val grouped = if (seed % 2 == 0) q3.groupBy(base.col[User](_.name)) else q3.groupBy(base.col[User](_.age))
      (seed % 4) match {
        case 0 => grouped.having(grouped.count(grouped.col[User](_.id)) > lit((seed % 3).toLong))
        case 1 => grouped.having(grouped.sum(grouped.col[User](_.salary)) > lit(100.0))
        case 2 => grouped.having(grouped.avg(grouped.col[User](_.salary)) > lit(50.0))
        case 3 => grouped.having(countStar > lit(1L))
      }
    } else q3
    val q5 = if (seed % 4 == 0) q4.limit(seed % 10 + 1) else q4
    val q6 = if (seed % 6 == 0) q5.offset(seed % 5) else q5
    q6
  }

  private def buildJoinedInner(seed: Int): SqlQuery[?, ?] = {
    val base = SqlQuery.from(userTable).innerJoin(userRepoRel)
    val q2   = (seed % 8) match {
      case 0 => base
      case 1 => base.where(base.col[User](_.age) > lit(seed % 100))
      case 2 => base.where(base.col[User](_.name).like("a%"))
      case 3 =>
        val vals = Seq(seed % 5, (seed * 7) % 11, (seed * 13) % 17).distinct.take(2)
        base.where(base.col[User](_.id).in(vals))
      case 4 =>
        base.where((base.col[User](_.age) > lit(10)) && (base.col[User](_.name) === lit("x")))
      case 5 =>
        base.where((base.col[User](_.age) < lit(50)) || (base.col[User](_.id) === lit(seed % 1000)))
      case 6 =>
        base.where(!(base.col[User](_.age) > lit(20)))
      case 7 =>
        base.where(base.col[User](_.id).in(Seq.empty[Int]))
    }
    // joined repo predicate
    val q3 = if (seed % 7 == 0) q2.where(base.col[Repo](_.name) === lit("prop")) else q2
    val q4 = if (seed % 4 == 0) q3.limit(seed % 10 + 1) else q3
    val q5 = if (seed % 6 == 0) q4.offset(seed % 5) else q4
    q5
  }

  private def buildJoinedLeft(seed: Int): SqlQuery[?, ?] = {
    val base = SqlQuery.from(userTable).leftJoin(userRepoRel)
    val q2   = (seed % 8) match {
      case 0 => base
      case 1 => base.where(base.col[User](_.age) > lit(seed % 100))
      case 2 => base.where(base.col[User](_.name).like("a%"))
      case 3 =>
        val vals = Seq(seed % 5, (seed * 7) % 11, (seed * 13) % 17).distinct.take(2)
        base.where(base.col[User](_.id).in(vals))
      case 4 =>
        base.where((base.col[User](_.age) > lit(10)) && (base.col[User](_.name) === lit("x")))
      case 5 =>
        base.where((base.col[User](_.age) < lit(50)) || (base.col[User](_.id) === lit(seed % 1000)))
      case 6 =>
        base.where(!(base.col[User](_.age) > lit(20)))
      case 7 =>
        base.where(base.col[User](_.id).in(Seq.empty[Int]))
    }
    // joined repo predicate
    val q3 = if (seed % 7 == 0) q2.where(base.col[Repo](_.name) === lit("prop")) else q2
    val q4 = if (seed % 4 == 0) q3.limit(seed % 10 + 1) else q3
    val q5 = if (seed % 6 == 0) q4.offset(seed % 5) else q4
    q5
  }

  private def buildQuery(seed: Int): SqlQuery[?, ?] =
    (seed % 3) match {
      case 1 => buildJoinedInner(seed)
      case 2 => buildJoinedLeft(seed)
      case _ => buildPlain(seed)
    }

  def spec = suite("QueryPropertySpec")(
    test("deterministic 500 query shapes placeholder count equals params for both dialects") {
      val total    = 500
      var failures = List.empty[String]
      var idx      = 0
      while (idx < total) {
        val q       = buildQuery(idx)
        val fragPg  = q.toFrag(SqlDialect.PostgreSQL)
        val fragLt  = q.toFrag(SqlDialect.SQLite)
        val pgSql   = fragPg.sql(SqlDialect.PostgreSQL)
        val ltSql   = fragLt.sql(SqlDialect.SQLite)
        val pgPlace = pgSql.count(_ == '?')
        val ltPlace = ltSql.count(_ == '?')
        if (pgPlace != fragPg.params.size)
          failures ::= s"pg seed $idx: placeholder $pgPlace != params ${fragPg.params.size} sql=$pgSql"
        if (ltPlace != fragLt.params.size)
          failures ::= s"sqlite seed $idx: placeholder $ltPlace != params ${fragLt.params.size} sql=$ltSql"
        if (fragPg.params.size != fragLt.params.size)
          failures ::= s"seed $idx: pg params ${fragPg.params.size} != sqlite params ${fragLt.params.size}"
        idx += 1
      }
      assertTrue(failures.isEmpty, total == 500)
    },
    test("property oracle detects mismatch when perturbed count is compared") {
      val q         = SqlQuery.from(userTable)
      val q2        = q.where(q.col[User](_.id) === lit(1)).where(q.col[User](_.name) === lit("a"))
      val frag      = q2.toFrag(SqlDialect.PostgreSQL)
      val sql       = frag.sql(SqlDialect.PostgreSQL)
      val correct   = sql.count(_ == '?')
      val perturbed = correct + 1
      assertTrue(
        correct == frag.params.size,
        perturbed != frag.params.size,
        correct == 2,
        perturbed == 3
      )
    },
    test("negative control: empty IN yields 1=0 with zero params and zero placeholders") {
      val q    = SqlQuery.from(userTable)
      val q2   = q.where(q.col[User](_.id).in(Seq.empty[Int]))
      val frag = q2.toFrag(SqlDialect.PostgreSQL)
      val sql  = frag.sql(SqlDialect.PostgreSQL)
      assertTrue(
        sql == "SELECT t0.\"id\", t0.\"name\", t0.\"age\", t0.\"salary\" FROM \"users\" AS t0 WHERE 1=0",
        frag.params.isEmpty,
        sql.count(_ == '?') == 0
      )
    }
  )
}
