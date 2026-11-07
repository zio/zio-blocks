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
import zio.blocks.sql.query.{Rel, SqlQuery => Qry}

/**
 * A test fixture for [[Dump]]: three tables and a two-join query that
 * `Dump.dumpQuery` and `Dump.dumpTable` write out when the library itself is
 * compiled with `-Dzib.sql.dumpDir`. It ships in the published jar by mistake,
 * a known bug in 0.0.56; it is not part of the API.
 */
object JoinsDumpExample {

  /** Fixture row type. */
  case class User(id: Int, name: String)

  /** Fixture schema. */
  object User { implicit val schema: Schema[User] = Schema.derived }

  /** Fixture row type. */
  case class Repo(id: Int, ownerId: Int, name: String)

  /** Fixture schema. */
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }

  /** Fixture row type. */
  case class Star(userId: Int, repoId: Int)

  /** Fixture schema. */
  object Star { implicit val schema: Schema[Star] = Schema.derived }

  /** Fixture table. */
  val userTable: Table[User] = Table.derived[User]

  /** Fixture table. */
  val repoTable: Table[Repo] = Table.derived[Repo]

  /** Fixture table. */
  val starTable: Table[Star] = Table.derived[Star]

  // Rels stay inline in each dumped chain below so the dump macro can peel
  // the join tree; keep them as the canonical two-join shape.
  /** The fixture query: users joined to their repos and stars. */
  inline def joins: Qry[User] = Qry
    .from(userTable)
    .innerJoin(Rel(repoTable, "owner_id", userTable, "id"))
    .innerJoin(Rel(starTable, "repo_id", repoTable, "id"))
    .filter(Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice"))))
    .filter(Frag(IndexedSeq("t1.\"name\" = ", ""), IndexedSeq(DbValue.DbString("my-repo"))))

  // Wire dump for inline query value — direct inlining ensures macro sees the IR
  Dump.dumpQuery(
    Qry
      .from(userTable)
      .innerJoin(Rel(repoTable, "owner_id", userTable, "id"))
      .innerJoin(Rel(starTable, "repo_id", repoTable, "id"))
      .filter(Frag(IndexedSeq("""t0."name" = """, ""), IndexedSeq(DbValue.DbString("alice"))))
      .filter(Frag(IndexedSeq("""t1."name" = """, ""), IndexedSeq(DbValue.DbString("my-repo"))))
  )

  // Also keep the named inline def variant for name-derived file coverage
  Dump.dumpQuery(joins)

  // Hook dumpTable
  Dump.dumpTable(userTable)
  Dump.dumpTable(repoTable)
  Dump.dumpTable(starTable)

  // sqlChecked literal — owner-derived name (requires import zio.blocks.sql.*)
  // val joinsChecked: Frag =
  //   StringContext("SELECT * FROM user INNER JOIN repo ON user.id = repo.owner_id")
  //     .sqlChecked(userTable, repoTable)()

  /** Fixture dialect. */
  val pg: SqlDialect = SqlDialect.PostgreSQL

  /** Fixture dialect. */
  val sqlite: SqlDialect = SqlDialect.SQLite
}
