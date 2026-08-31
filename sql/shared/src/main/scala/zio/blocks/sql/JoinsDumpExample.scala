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
import zio.blocks.sql.query.{Rel, SqlQuery => Qry, col, lit}
import zio.blocks.sql.query.*

/**
 * Canonical User/Repo/Star example for compile-time dump verification.
 *
 * After Task 7 the sole public query builder is
 * `zio.blocks.sql.query.SqlQuery`; this example uses the typed relational IR
 * with `Rel` and typed `col`/`lit` predicates. `Dump.dump` and `Dump.dumpQuery`
 * both consume that IR.
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

  inline def joins: Qry[User] = Qry
    .from(userTable)
    .innerJoin(Rel.manyToOne(repoTable, "owner_id", userTable, "id"))
    .innerJoin(Rel.manyToOne(starTable, "repo_id", repoTable, "id"))
    .where(col[User](_.name) === lit("alice"))
    .where(col[Repo](_.name) === lit("my-repo"))

  // Wire dump for inline query value — direct inlining ensures macro sees the IR
  Dump.dump(
    Qry
      .from(userTable)
      .innerJoin(Rel.manyToOne(repoTable, "owner_id", userTable, "id"))
      .innerJoin(Rel.manyToOne(starTable, "repo_id", repoTable, "id"))
      .where(col[User](_.name) === lit("alice"))
      .where(col[Repo](_.name) === lit("my-repo"))
  )

  // Also keep the named inline def variant for name-derived file coverage
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

    inline def joinsQ: Qry[User] = {
      val base = Qry.from(userTable2)
      val rel  = Rel.manyToOne(repoTable2, "owner_id", userTable2, "id")
      base.innerJoin(rel)
    }

    Dump.dumpQuery(joinsQ)
    Dump.dump(joinsQ)
  }
}
