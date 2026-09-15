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
import zio.blocks.sql.{SqlDialect, Table}
import zio.test.*

object RelSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User { given Schema[User] = Schema.derived }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { given Schema[Repo] = Schema.derived }

  case class Employee(id: Int, name: String, managerId: Option[Int])
  object Employee { given Schema[Employee] = Schema.derived }

  val userTable     = Table.derived[User]
  val repoTable     = Table.derived[Repo]
  val employeeTable = Table.derived[Employee]

  def spec = suite("RelSpec")(
    suite("string overloads")(
      test("manyToOne via string constructs") {
        val rel = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
        assertTrue(rel.fkColumn == "owner_id", rel.pkColumn == "id")
      },
      test("oneToMany via string constructs") {
        val rel = Rel.oneToMany(repoTable, "owner_id", userTable, "id")
        assertTrue(rel.fkColumn == "owner_id", rel.pkColumn == "id")
      },
      test("direct case class apply constructs") {
        val rel = Rel(repoTable, "owner_id", userTable, "id")
        assertTrue(rel.fkColumn == "owner_id")
      }
    ),
    suite("selector overloads")(
      test("manyToOne via selectors maps field -> snake_case column") {
        val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        assertTrue(rel.fkColumn == "owner_id", rel.pkColumn == "id")
      },
      test("oneToMany via selectors maps field -> snake_case column") {
        val rel = Rel.oneToMany(repoTable, _.ownerId, userTable, _.id)
        assertTrue(rel.fkColumn == "owner_id", rel.pkColumn == "id")
      },
      test("selector manyToOne with different table pair") {
        // Repo.ownerId -> User.id via lambda syntax x => x.ownerId
        val rel = Rel.manyToOne(repoTable, (r: Repo) => r.ownerId, userTable, (u: User) => u.id)
        assertTrue(rel.fkColumn == "owner_id", rel.pkColumn == "id")
      },
      test("self-join selector") {
        val rel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)
        assertTrue(rel.fkColumn == "manager_id", rel.pkColumn == "id")
      }
    ),
    suite("runtime validation backstop")(
      test("invalid fk via string throws IllegalArgumentException naming table.column") {
        val ex = try {
          Rel(repoTable, "bad_column", userTable, "id")
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
        }
        assertTrue(ex.isDefined, ex.get.contains("bad_column"), ex.get.contains("repo"))
      },
      test("invalid pk via string throws IllegalArgumentException naming table.column") {
        val ex = try {
          Rel(repoTable, "owner_id", userTable, "bad_pk")
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
        }
        assertTrue(ex.isDefined, ex.get.contains("bad_pk"), ex.get.contains("user"))
      },
      test("manyToOne string overload invalid fk throws") {
        val ex = try {
          Rel.manyToOne(repoTable, "not_exist", userTable, "id")
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
        }
        assertTrue(ex.isDefined, ex.get.contains("not_exist"))
      },
      test("oneToMany string overload invalid pk throws") {
        val ex = try {
          Rel.oneToMany(repoTable, "owner_id", userTable, "nope")
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
        }
        assertTrue(ex.isDefined, ex.get.contains("nope"))
      }
    ),
    suite("SqlQuery join aliases")(
      test("SqlQuery.join delegates to innerJoin with same SQL") {
        val relString = Rel(repoTable, "owner_id", userTable, "id")
        val relSel    = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        val q1        = SqlQuery.from(userTable).join(relString)
        val q2        = SqlQuery.from(userTable).innerJoin(relSel)
        val sql1      = q1.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val sql2      = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp       =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        assertTrue(sql1 == sql2, sql1 == exp)
      },
      test("SqlQuery.joinLeft delegates to leftJoin with same SQL") {
        val rel  = Rel(repoTable, "owner_id", userTable, "id")
        val q1   = SqlQuery.from(userTable).joinLeft(rel)
        val q2   = SqlQuery.from(userTable).leftJoin(rel)
        val sql1 = q1.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val sql2 = q2.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp  =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 LEFT JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        assertTrue(sql1 == sql2, sql1 == exp)
      },
      test("join via selector-built Rel renders correct ON clause with aliases") {
        val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        val q   = SqlQuery.from(userTable).join(rel)
        val sql = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\""
        assertTrue(sql == exp)
      },
      test("join and joinLeft chain preserves deterministic aliases t0,t1,t2") {
        val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
        // need Tag table for chain
        case class Tag(id: Int, repoId: Int, label: String)
        object Tag { given Schema[Tag] = Schema.derived }
        val tagTable   = Table.derived[Tag]
        val repoTagRel = Rel.manyToOne(tagTable, _.repoId, repoTable, _.id)
        val q          = SqlQuery.from(userTable).join(userRepoRel).joinLeft(repoTagRel)
        val sql        = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val exp        =
          "SELECT t0.\"id\", t0.\"name\", t1.\"id\", t1.\"owner_id\", t1.\"name\", t2.\"id\", t2.\"repo_id\", t2.\"label\" FROM \"user\" AS t0 INNER JOIN \"repo\" AS t1 ON t1.\"owner_id\" = t0.\"id\" LEFT JOIN \"tag\" AS t2 ON t2.\"repo_id\" = t1.\"id\""
        assertTrue(sql == exp)
      }
    ),
    suite("compile-time typo rejection")(
      test("typo'd selector fails at compile time with named-field error") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.Rel
            import zio.blocks.sql.Table
            import zio.blocks.schema.Schema
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            case class Repo(id: Int, ownerId: Int, name: String)
            object Repo { given Schema[Repo] = Schema.derived }
            val userTable = Table.derived[User]
            val repoTable = Table.derived[Repo]
            val bad = Rel.manyToOne(repoTable, (r: Repo) => r.typoField, userTable, (u: User) => u.id)
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("unknown field 'typoField'")) ||
            errors.exists(_.message.contains("typoField"))
        )
      },
      test("valid selector typeChecks with no errors") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
            import zio.blocks.sql.query.Rel
            import zio.blocks.sql.Table
            import zio.blocks.schema.Schema
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            case class Repo(id: Int, ownerId: Int, name: String)
            object Repo { given Schema[Repo] = Schema.derived }
            val userTable = Table.derived[User]
            val repoTable = Table.derived[Repo]
            val ok: Rel[Repo, User] = Rel.manyToOne(repoTable, (r: Repo) => r.ownerId, userTable, (u: User) => u.id)
            ok.toString
          }"""
          )
        )
      },
      test("typo on pk side also fails compilation") {
        val errors = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.Rel
            import zio.blocks.sql.Table
            import zio.blocks.schema.Schema
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            case class Repo(id: Int, ownerId: Int, name: String)
            object Repo { given Schema[Repo] = Schema.derived }
            val userTable = Table.derived[User]
            val repoTable = Table.derived[Repo]
            val bad = Rel.manyToOne(repoTable, (r: Repo) => r.ownerId, userTable, (u: User) => u.nope)
          }"""
        )
        assertTrue(errors.nonEmpty, errors.exists(_.message.contains("nope")))
      }
    )
  )
}
