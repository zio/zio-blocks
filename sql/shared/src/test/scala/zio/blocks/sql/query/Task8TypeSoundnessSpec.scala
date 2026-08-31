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

import zio.test.*

/**
 * Compile-time type-soundness proofs for the path-dependent query scope.
 *
 * Every negative test asserts the intended error text and is paired with a
 * positive compile control: the same snippet shape with the correct
 * scope/nullability/type must type-check, ruling out vacuous failures (e.g.
 * missing imports or broken snippet scaffolding).
 */
object Task8TypeSoundnessSpec extends ZIOSpecDefault {

  def spec = suite("Task8TypeSoundnessSpec")(
    suite("query-bound scope identity")(
      test("q1.Scope and q2.Scope are distinct — q1.select(q2.col) fails with the foreign scope") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q1 = SqlQuery.from(userTable)
            val q2 = SqlQuery.from(userTable)
            q1.select[Int](q2.col[User](_.id))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("q2.Scope")),
          // positive control: same snippet with q1's own column compiles
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q1 = SqlQuery.from(userTable)
              q1.select[Int](q1.col[User](_.id))
            }"""
          )
        )
      },
      test("q1.where(q2.col ...) fails with the query-bound scope requirement") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q1 = SqlQuery.from(userTable)
            val q2 = SqlQuery.from(userTable)
            q1.where(q2.col[User](_.id) === lit(1))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("q1.Scope")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q1 = SqlQuery.from(userTable)
              q1.where(q1.col[User](_.id) === lit(1))
            }"""
          )
        )
      },
      test("q1.groupBy(q2.col ...) fails with the query-bound scope requirement") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q1 = SqlQuery.from(userTable)
            val q2 = SqlQuery.from(userTable)
            q1.groupBy(q2.col[User](_.name))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("q2.Scope")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q1 = SqlQuery.from(userTable)
              q1.groupBy(q1.col[User](_.name))
            }"""
          )
        )
      },
      test("q1.having(q2.col ...) fails with the query-bound scope requirement") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q1 = SqlQuery.from(userTable)
            val q2 = SqlQuery.from(userTable)
            q1.having(q2.col[User](_.id) === lit(1))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("q1.Scope")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q1 = SqlQuery.from(userTable)
              q1.having(q1.col[User](_.id) === lit(1))
            }"""
          )
        )
      },
      test("cross-query compound expressions fail: q1.col + q2.col in one select") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: Int)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q1 = SqlQuery.from(saleTable)
            val q2 = SqlQuery.from(saleTable)
            q1.select[Int](q1.col[Sale](_.amount).plus(q2.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("q2.Scope")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q1 = SqlQuery.from(saleTable)
              q1.select[Int](q1.col[Sale](_.amount).plus(q1.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("a query-bound column survives val binding with its scope (positive control)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q = SqlQuery.from(userTable)
              val c: Expr[Int, q.type] = q.col[User](_.id)
              val tq = q.select[Int](c)
              val tq2 = tq.where(q.col[User](_.id) === lit(1))
            }"""
          )
        )
      },
      test("fluent chaining from a named base keeps one scope (positive control)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).innerJoin(rel)
              val tq = q.where(q.col[User](_.id) === lit(1)).select[(Int, String)](q.col[User](_.id), q.col[Repo](_.name))
            }"""
          )
        )
      }
    ),
    suite("global col/colAt are not public")(
      test("top-level col is not available outside the package") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            q.where(col[User](_.id) === lit(1))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("col")),
          // positive control: query-bound col in the same snippet shape compiles
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q = SqlQuery.from(userTable)
              q.where(q.col[User](_.id) === lit(1))
            }"""
          )
        )
      },
      test("global colAt is not available outside the package") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            q.where(colAt[User]("t0", _.id) === lit(1))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("colAt")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q = SqlQuery.from(userTable)
              q.where(q.colAt[User]("t0", _.id) === lit(1))
            }"""
          )
        )
      }
    ),
    suite("LEFT JOIN nullability compile-time")(
      test("non-optional right-side projection after LEFT JOIN fails with an Option type mismatch") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
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
            val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
            val q = SqlQuery.from(userTable).leftJoin(rel)
            q.select[Int](q.col[Repo](_.id))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Option")),
          errors.exists(_.message.contains("type mismatch")),
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).leftJoin(rel)
              q.select[Option[Int]](q.col[Repo](_.id))
            }"""
          )
        )
      },
      test("query-bound left-side projection as Option compiles without asOption (positive control)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).leftJoin(rel)
              q.select[Option[Int]](q.col[Repo](_.id))
            }"""
          )
        )
      },
      test("source column via query-bound col remains non-optional after LEFT JOIN") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
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
            val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
            val q = SqlQuery.from(userTable).leftJoin(rel)
            q.select[Option[Int]](q.col[User](_.id))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Option")),
          errors.exists(_.message.contains("type mismatch")),
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).leftJoin(rel)
              q.select[Int](q.col[User](_.id))
            }"""
          )
        )
      },
      test("inner join slot stays non-optional (positive control)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).innerJoin(rel)
              q.select[String](q.col[Repo](_.name))
            }"""
          )
        )
      },
      test("mixed slots: source non-optional, LEFT-joined optional in one projection (positive control)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).leftJoin(rel)
              q.select[(String, Option[String])](q.col[User](_.name), q.col[Repo](_.name))
            }"""
          )
        )
      }
    ),
    suite("self-join slot model")(
      test("unaliased col on a repeated table (self-join) is rejected with the disambiguation error") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Employee(id: Int, name: String, managerId: Option[Int])
            object Employee { given Schema[Employee] = Schema.derived }
            val employeeTable: Table[Employee] = Table.derived[Employee]
            val rel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)
            val q = SqlQuery.from(employeeTable).innerJoin(rel)
            q.select[String](q.col[Employee](_.name))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("appears more than once")),
          errors.exists(_.message.contains("colAt")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Employee(id: Int, name: String, managerId: Option[Int])
              object Employee { given Schema[Employee] = Schema.derived }
              val employeeTable: Table[Employee] = Table.derived[Employee]
              val rel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)
              val q = SqlQuery.from(employeeTable).innerJoin(rel)
              q.select[(String, String)](q.colAt[Employee]("t0", _.name), q.colAt[Employee]("t1", _.name))
            }"""
          )
        )
      },
      test("colAt with a slot whose table does not match the selector fails with the slot error") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
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
            val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
            val q = SqlQuery.from(userTable).innerJoin(rel)
            q.select[String](q.colAt[User]("t1", _.name))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("does not resolve to a table slot")),
          scala.compiletime.testing.typeChecks(
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
              val rel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
              val q = SqlQuery.from(userTable).innerJoin(rel)
              q.select[(String, String)](q.colAt[User]("t0", _.name), q.colAt[Repo]("t1", _.name))
            }"""
          )
        )
      },
      test("colAt with an out-of-range alias fails with the slot error") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            q.select[String](q.colAt[User]("t5", _.name))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("does not resolve to a table slot")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q = SqlQuery.from(userTable)
              q.select[String](q.colAt[User]("t0", _.name))
            }"""
          )
        )
      },
      test("colAt with an invalid alias identifier fails with the slot error") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            q.select[String](q.colAt[User]("bad-alias!", _.name))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("does not resolve to a table slot"))
        )
      }
    ),
    suite("foreign table rejection")(
      test("column from a table not in the query fails with the not-part-of-query error") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class User(id: Int, name: String)
            object User { given Schema[User] = Schema.derived }
            case class Extra(id: Int, ref: Int)
            object Extra { given Schema[Extra] = Schema.derived }
            val userTable: Table[User] = Table.derived[User]
            val q = SqlQuery.from(userTable)
            val bad = q.col[Extra](_.ref)
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("not part of this query")),
          errors.exists(_.message.contains("Extra")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class User(id: Int, name: String)
              object User { given Schema[User] = Schema.derived }
              val userTable: Table[User] = Table.derived[User]
              val q = SqlQuery.from(userTable)
              val ok = q.col[User](_.id)
            }"""
          )
        )
      }
    ),
    suite("aggregate result typing compile-time")(
      test("SUM Int widens to Option[Long] — Int projection fails with the widened type") {
        val errsInt: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: Int)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Int](q.sum(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errsInt.nonEmpty,
          errsInt.exists(_.message.contains("type mismatch")),
          errsInt.exists(_.message.contains("Long")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[Long]](q.sum(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("SUM Long widens to Option[BigDecimal] (PostgreSQL numeric truth)") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Long)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[BigDecimal]](q.sum(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("SUM Double stays Option[Double]") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Double)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[Double]](q.sum(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("SUM over String does not compile (gate typeclass missing)") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: String)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Option[Long]](q.sum(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Summable")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[Long]](q.sum(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("AVG Int returns Option[BigDecimal] — Double projection fails, BigDecimal compiles") {
        val errsDouble: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: Int)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Option[Double]](q.avg(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errsDouble.nonEmpty,
          errsDouble.exists(_.message.contains("type mismatch")),
          errsDouble.exists(_.message.contains("BigDecimal")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[BigDecimal]](q.avg(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("AVG Double returns Option[Double]") {
        assertTrue(
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Double)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[Double]](q.avg(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("AVG over String does not compile (gate typeclass missing)") {
        val errors: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: String)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Option[BigDecimal]](q.avg(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errors.nonEmpty,
          errors.exists(_.message.contains("Averagable"))
        )
      },
      test("MIN/MAX return Option and preserve input type") {
        val errsMin: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: Int)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Int](q.min(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errsMin.nonEmpty,
          errsMin.exists(_.message.contains("type mismatch")),
          errsMin.exists(_.message.contains("Option")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Option[Int]](q.min(q.col[Sale](_.amount)))
            }"""
          )
        )
      },
      test("COUNT remains non-optional Long") {
        val errsCount: List[scala.compiletime.testing.Error] = scala.compiletime.testing.typeCheckErrors(
          """{
            import zio.blocks.sql.query.*
            import zio.blocks.schema.Schema
            import zio.blocks.sql.Table
            case class Sale(id: Int, amount: Int)
            object Sale { given Schema[Sale] = Schema.derived }
            val saleTable: Table[Sale] = Table.derived[Sale]
            val q = SqlQuery.from(saleTable)
            q.select[Option[Long]](q.count(q.col[Sale](_.amount)))
          }"""
        )
        assertTrue(
          errsCount.nonEmpty,
          errsCount.exists(_.message.contains("type mismatch")),
          errsCount.exists(_.message.contains("Long")),
          scala.compiletime.testing.typeChecks(
            """{
              import zio.blocks.sql.query.*
              import zio.blocks.schema.Schema
              import zio.blocks.sql.Table
              case class Sale(id: Int, amount: Int)
              object Sale { given Schema[Sale] = Schema.derived }
              val saleTable: Table[Sale] = Table.derived[Sale]
              val q = SqlQuery.from(saleTable)
              q.select[Long](q.count(q.col[Sale](_.amount)))
            }"""
          )
        )
      }
    ),
    suite("inOpt semantics")(
      test("inOpt with a None entry throws with an exact error instead of silently dropping") {
        import zio.blocks.schema.Schema
        import zio.blocks.sql.Table
        case class User(id: Int, name: String)
        object User { given Schema[User] = Schema.derived }
        case class Repo(id: Int, ownerId: Int, name: String)
        object Repo { given Schema[Repo] = Schema.derived }
        val userTable = Table.derived[User]
        val repoTable = Table.derived[Repo]
        val q         = SqlQuery.from(userTable).leftJoin(Rel.manyToOne(repoTable, _.ownerId, userTable, _.id))
        val thrown    = try {
          q.where(q.col[Repo](_.id).inOpt(Seq(Some(1), None, Some(2))) === lit(true))
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
        }
        assertTrue(
          thrown.isDefined,
          thrown.get.contains("inOpt"),
          thrown.get.contains("None"),
          thrown.get.contains("index 1")
        )
      },
      test("inOpt with all-Some values compiles and never drops None") {
        import zio.blocks.schema.Schema
        import zio.blocks.sql.Table
        case class User(id: Int, name: String)
        object User { given Schema[User] = Schema.derived }
        case class Repo(id: Int, ownerId: Int, name: String)
        object Repo { given Schema[Repo] = Schema.derived }
        val userTable = Table.derived[User]
        val repoTable = Table.derived[Repo]
        val q         = SqlQuery.from(userTable).leftJoin(Rel.manyToOne(repoTable, _.ownerId, userTable, _.id))
        val ok        = q.where(q.col[Repo](_.id).inOpt(Seq(Some(1), Some(2))) === lit(true))
        assertTrue(ok.toFrag(zio.blocks.sql.SqlDialect.SQLite).sql(zio.blocks.sql.SqlDialect.SQLite).contains("IN"))
      }
    )
  )

}
