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

import zio.blocks.schema.Schema
import zio.blocks.sql.query.{Rel, SqlQuery => Qry, lit}
import zio.blocks.sql.query.*

/**
 * Canonical User/Repo/Star example for compile-time dump verification.
 *
 * After Task 8 the sole public query builder is
 * `zio.blocks.sql.query.SqlQuery`; column expressions are query-bound
 * (`q.col[Table](_.field)`), so queries are built with a named query value
 * first and the columns/predicates are constructed from that same value.
 * `Dump.dump` and `Dump.dumpQuery` both consume the typed relational IR.
 */
object JoinsDumpExample {

  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }

  case class Star(userId: Int, repoId: Int)
  object Star { implicit val schema: Schema[Star] = Schema.derived }

  val userTable: Table[User] = Table.derived[User]
  val repoTable: Table[Repo] = Table.derived[Repo]
  val starTable: Table[Star] = Table.derived[Star]

  val userRepoRel = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
  val repoStarRel = Rel.manyToOne(starTable, "repo_id", repoTable, "id")

  // Stable top-level query values: `inline def joins` below must reference
  // stable paths so the path-dependent scope survives inlining. Types are
  // inferred so the `S` join-phantom stays concrete for the col macros.
  val joinsBase     = Qry.from(userTable).innerJoin(userRepoRel).innerJoin(repoStarRel)
  val joinsFiltered =
    joinsBase.where(joinsBase.col[User](_.name) === lit("alice"))

  val joinsRepoName: Expr[String, joinsBase.type]  = joinsFiltered.col[Repo](_.name)
  val joinsRepoPred: Expr[Boolean, joinsBase.type] = joinsRepoName === lit("my-repo")
  inline def joins: Qry[User, _]                   = joinsFiltered.where(joinsRepoPred)

  // Wire dump for the inline query value and the named inline def
  Dump.dump(joins)
  Dump.dumpQuery(joins)

  // Hook dumpTable
  Dump.dumpTable(userTable)
  Dump.dumpTable(repoTable)
  Dump.dumpTable(starTable)

  val pg: SqlDialect     = SqlDialect.PostgreSQL
  val sqlite: SqlDialect = SqlDialect.SQLite

  object QueryLayerExample {
    val userTable2: Table[User] = Table.derived[User]
    val repoTable2: Table[Repo] = Table.derived[Repo]

    inline def joinsQ: Qry[User, _] = {
      val base = Qry.from(userTable2)
      val rel  = Rel.manyToOne(repoTable2, "owner_id", userTable2, "id")
      base.innerJoin(rel)
    }

    Dump.dumpQuery(joinsQ)
    Dump.dump(joinsQ)
  }
}
