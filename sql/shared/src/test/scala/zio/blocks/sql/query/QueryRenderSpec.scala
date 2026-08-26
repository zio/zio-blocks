/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package zio.blocks.sql.query

import zio.blocks.schema.Schema
import zio.blocks.sql.{DbValue, Frag, SqlDialect, Table}
import zio.test.*

object QueryRenderSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  case class Tag(id: Int, repoId: Int, label: String)
  object Tag { given Schema[Tag] = Schema.derived }

  case class Employee(id: Int, name: String, managerId: Option[Int])
  object Employee { given Schema[Employee] = Schema.derived }

  val userTable     = Table.derived[User]
  val repoTable     = Table.derived[Repo]
  val tagTable      = Table.derived[Tag]
  val employeeTable = Table.derived[Employee]

  val userRepoRel     = Rel(repoTable, "owner_id", userTable, "id")
  val repoTagRel      = Rel(tagTable, "repo_id", repoTable, "id")
  val employeeSelfRel = Rel(employeeTable, "manager_id", employeeTable, "id")

  def spec = suite("QueryRenderSpec")(
    suite("single-source select")(
      test("renders qualified SELECT FROM for single table") {
        val q   = SqlQuery.from(userTable)
        val pg  = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val lt  = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0"
        assertTrue(pg == exp, lt == exp)
      },
      test("single-source with different table") {
        val q   = SqlQuery.from(repoTable)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"owner_id\", t0.\"name\" FROM \"repo\" AS t0"
        assertTrue(sql == exp)
      }
    ),
    suite("inner and left join chains")(
      test("inner join emits INNER JOIN with alias-qualified ON") {
        val q   = SqlQuery.from(userTable).innerJoin(userRepoRel)
        val pg  = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        assertTrue(pg == exp)
      },
      test("inner + left join chain uses t0,t1,t2 deterministic") {
        val q = SqlQuery
          .from(userTable)
          .innerJoin(userRepoRel)
          .leftJoin(repoTagRel)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\", t2.\"id\", t2.\"repo_id\", t2.\"label\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\" LEFT JOIN \"tag\" AS t2 ON t2.\"repo_id\" = t1.\"id\""
        assertTrue(sql == exp)
      },
      test("left join kind renders LEFT JOIN") {
        val q   = SqlQuery.from(userTable).leftJoin(userRepoRel)
        val sql = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        assertTrue(sql.contains("LEFT JOIN"), !sql.contains("INNER JOIN"))
      }
    ),
    suite("self-join")(
      test("self-join same table gets distinct aliases t0,t1") {
        val q   = SqlQuery.from(employeeTable).innerJoin(employeeSelfRel)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t0.\"manager_id\", t1.\"id\", t1.\"name\", t1.\"manager_id\" FROM \"employee\" AS t0 INNER JOIN \"employee\" AS t1 ON t0.\"manager_id\" = t1.\"id\""
        assertTrue(sql == exp)
      },
      test("self-join preserves distinct aliases on second self-join") {
        val q   = SqlQuery.from(employeeTable).innerJoin(employeeSelfRel).innerJoin(employeeSelfRel)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        assertTrue(sql.contains("t1.\"id\""), sql.contains("t2.\"id\""), sql.contains("t0.\"manager_id\""))
      }
    ),
    suite("order/limit/offset")(
      test("order by single column") {
        val q   = SqlQuery.from(userTable).orderBy("name", SortOrder.Asc)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 ORDER BY t0.\"name\" ASC"
        assertTrue(sql == exp)
      },
      test("order by multiple columns with mixed directions") {
        val q = SqlQuery
          .from(userTable)
          .orderBy("name", SortOrder.Asc)
          .orderBy("id", SortOrder.Desc)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 ORDER BY t0.\"name\" ASC, t0.\"id\" DESC"
        assertTrue(sql == exp)
      },
      test("limit only") {
        val q   = SqlQuery.from(userTable).limit(10)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 LIMIT 10"
        assertTrue(sql == exp)
      },
      test("limit + offset") {
        val q   = SqlQuery.from(userTable).limit(10).offset(5)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 LIMIT 10 OFFSET 5"
        assertTrue(sql == exp)
      },
      test("order + limit + offset combo") {
        val q = SqlQuery
          .from(userTable)
          .orderBy("name", SortOrder.Asc)
          .limit(20)
          .offset(10)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 ORDER BY t0.\"name\" ASC LIMIT 20 OFFSET 10"
        assertTrue(sql == exp)
      }
    ),
    suite("filters and grouping")(
      test("filter frag composed via Frag and renders with placeholder") {
        val filterFrag = Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("Alice")))
        val q          = SqlQuery.from(userTable).filter(filterFrag)
        val frag       = q.toFrag(SqlDialect.PostgreSQL)
        assertTrue(
          frag.sql(SqlDialect.PostgreSQL) == "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 WHERE t0.\"name\" = ?",
          frag.params == IndexedSeq(DbValue.DbString("Alice"))
        )
      },
      test("multiple filters combined with AND") {
        val f1 = Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("Alice")))
        val f2 = Frag(IndexedSeq("t0.\"id\" > ", ""), IndexedSeq(DbValue.DbInt(10)))
        val q  = SqlQuery
          .from(userTable)
          .filter(f1)
          .filter(f2)
        val frag = q.toFrag(SqlDialect.PostgreSQL)
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 WHERE t0.\"name\" = ? AND t0.\"id\" > ?",
          frag.params.size == 2
        )
      },
      test("groupBy renders qualified columns") {
        val q   = SqlQuery.from(userTable).groupBy("name")
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp = "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 GROUP BY t0.\"name\""
        assertTrue(sql == exp)
      },
      test("groupBy + having with frag") {
        val havingFrag = Frag(IndexedSeq("COUNT(*) > ", ""), IndexedSeq(DbValue.DbInt(1)))
        val q          = SqlQuery
          .from(userTable)
          .groupBy("name")
          .having(havingFrag)
        val frag = q.toFrag(SqlDialect.PostgreSQL)
        assertTrue(
          frag.sql(
            SqlDialect.PostgreSQL
          ) == "SELECT t0.\"id\", t0.\"name\" FROM \"user\" AS t0 GROUP BY t0.\"name\" HAVING COUNT(*) > ?",
          frag.params == IndexedSeq(DbValue.DbInt(1))
        )
      },
      test("placeholder count equals params count via dialect") {
        val filterFrag = Frag(
          IndexedSeq("t0.\"id\" = ", " AND t0.\"name\" = ", ""),
          IndexedSeq(DbValue.DbInt(1), DbValue.DbString("a"))
        )
        val q            = SqlQuery.from(userTable).filter(filterFrag)
        val frag         = q.toFrag(SqlDialect.PostgreSQL)
        val sql          = frag.sql(SqlDialect.PostgreSQL)
        val placeholders = sql.count(_ == '?')
        assertTrue(placeholders == frag.params.size)
      }
    ),
    suite("Frag composition invariants")(
      test("renderer uses Frag.++ and.SqlIdentifier — every column is alias qualified") {
        val filterFrag = Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("x")))
        val q          = SqlQuery
          .from(userTable)
          .innerJoin(userRepoRel)
          .filter(filterFrag)
          .orderBy("id", SortOrder.Desc)
          .limit(5)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("t0.\"id\""),
          sql.contains("t0.\"name\""),
          sql.contains("t1.\"id\""),
          sql.contains("t1.\"owner_id\""),
          !sql.contains(" FROM user "),
          sql.contains("FROM \"user\" AS t0"),
          sql.contains("JOIN \"repo\" AS t1 ON")
        )
      }
    )
  )
}
