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

/**
 * Canonical User/Repo/Star example for task-4 compile-time dump verification.
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

  inline def joins: SqlQuery[User] = SqlQuery
    .from(userTable)
    .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
    .join(starTable, leftColumn = "id", rightColumn = "repo_id")
    .where(userTable, "name", DbValue.DbString("alice"))
    .where(repoTable, "name", DbValue.DbString("my-repo"))

  // Wire dump for inline query value — direct inlining ensures macro sees the IR
  Dump.dump(
    SqlQuery
      .from(userTable)
      .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
      .join(starTable, leftColumn = "id", rightColumn = "repo_id")
      .where(userTable, "name", DbValue.DbString("alice"))
      .where(repoTable, "name", DbValue.DbString("my-repo"))
  )

  // Also keep the named inline def variant for name-derived file coverage
  Dump.dump(joins)

  // Hook dumpTable
  Dump.dumpTable(userTable)
  Dump.dumpTable(repoTable)
  Dump.dumpTable(starTable)

  // sqlChecked literal — owner-derived name (requires import zio.blocks.sql.*)
  // val joinsChecked: Frag =
  //   StringContext("SELECT * FROM user INNER JOIN repo ON user.id = repo.owner_id")
  //     .sqlChecked(userTable, repoTable)()

  val pg: SqlDialect     = SqlDialect.PostgreSQL
  val sqlite: SqlDialect = SqlDialect.SQLite

  object QueryLayerExample {
    import zio.blocks.sql.query.{SqlQuery => Qry}

    val userTable2: Table[User] = Table.derived[User]
    val repoTable2: Table[Repo] = Table.derived[Repo]

    inline def joinsQ: Qry[User] = {
      val base = Qry.from(userTable2)
      val rel  = zio.blocks.sql.query.Rel.manyToOne(userTable2, "id", repoTable2, "id")
      base.innerJoin(rel)
    }

    Dump.dumpQuery(joinsQ)
  }
}
