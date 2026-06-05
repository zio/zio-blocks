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
import zio.blocks.schema._

object RepoSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("RepoSpec")(
    suite("buildInsertFrag")(
      test("builds correct INSERT Frag for 3 values") {
        val values = IndexedSeq(
          DbValue.DbInt(1): DbValue,
          DbValue.DbString("Alice"): DbValue,
          DbValue.DbString("alice@example.com"): DbValue
        )
        val frag = Repo.buildInsertFrag("user", "id, name, email", values)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO user (id, name, email) VALUES (?, ?, ?)",
          frag.sql(SqlDialect.PostgreSQL) == "INSERT INTO user (id, name, email) VALUES (?, ?, ?)",
          frag.queryParams == values
        )
      },
      test("builds correct INSERT Frag for 1 value") {
        val values = IndexedSeq(DbValue.DbInt(42): DbValue)
        val frag   = Repo.buildInsertFrag("t", "id", values)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO t (id) VALUES (?)",
          frag.queryParams == values
        )
      },
      test("builds INSERT Frag with no values") {
        val frag = Repo.buildInsertFrag("t", "", IndexedSeq.empty)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO t DEFAULT VALUES",
          frag.queryParams.isEmpty
        )
      }
    ),
    suite("buildUpdateFrag")(
      test("builds correct UPDATE Frag for 2 columns + 1 id") {
        val columns      = IndexedSeq("name", "email")
        val entityValues = IndexedSeq(DbValue.DbString("Bob"): DbValue, DbValue.DbString("bob@test.com"): DbValue)
        val idValues     = IndexedSeq(DbValue.DbInt(1): DbValue)
        val frag         = Repo.buildUpdateFrag("user", columns, entityValues, "id", idValues)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "UPDATE user SET name = ?, email = ? WHERE id = ?",
          frag.sql(SqlDialect.PostgreSQL) == "UPDATE user SET name = ?, email = ? WHERE id = ?",
          frag.queryParams == entityValues ++ idValues
        )
      },
      test("builds correct UPDATE Frag for 1 column + 1 id") {
        val columns      = IndexedSeq("name")
        val entityValues = IndexedSeq(DbValue.DbString("Alice"): DbValue)
        val idValues     = IndexedSeq(DbValue.DbInt(5): DbValue)
        val frag         = Repo.buildUpdateFrag("t", columns, entityValues, "id", idValues)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "UPDATE t SET name = ? WHERE id = ?",
          frag.queryParams == entityValues ++ idValues
        )
      }
    ),
    suite("Repo construction")(
      test("exposes table metadata") {
        case class Item(id: Int, name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }
        val table                 = Table.derived[Item]
        val idCodec: DbCodec[Int] = new DbCodec[Int] {
          val columns: IndexedSeq[String]                                              = IndexedSeq("value")
          def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Int =
            reader.getInt(columnLabels.head)
          override def readValue(reader: DbResultReader, startIndex: Int): Int     = reader.getInt(startIndex)
          def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit =
            writer.setInt(startIndex, value)
          def toDbValues(value: Int): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbInt(value))
        }
        val repo = Repo(table, "id", idCodec, (_: Item).id)
        assertTrue(
          repo.table.name == "item",
          repo.idColumn == "id",
          repo.table.columns == IndexedSeq("id", "name")
        )
      },
      test("rejects invalid explicit id column names") {
        case class Item(id: Int, name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }
        val table = Table.derived[Item]
        val error = try {
          Repo(table, "id; DROP TABLE item", DbCodec.intCodec, (_: Item).id)
          throw new AssertionError("Expected IllegalArgumentException")
        } catch {
          case e: IllegalArgumentException => e
        }
        assertTrue(error.getMessage.contains("Invalid SQL column identifier"))
      }
    ),
    suite("derived with zero args")(
      test("auto-detects unique Int ID field") {
        case class Item(id: Int, name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }
        val repo = Repo.derived[Item, Int]
        assertTrue(
          repo.idColumn == "id",
          repo.table.name == "item",
          repo.getId(Item(42, "widget")) == 42
        )
      },
      test("auto-detects unique Long ID field") {
        case class Article(title: String, articleId: Long)
        object Article {
          implicit val schema: Schema[Article] = Schema.derived
        }
        val repo = Repo.derived[Article, Long]
        assertTrue(
          repo.idColumn == "article_id",
          repo.getId(Article("hello", 99L)) == 99L
        )
      },
      test("uses renamed field for auto-detected ID column") {
        case class RenamedId(@Modifier.rename("widget_id") id: Int, name: String)
        object RenamedId {
          implicit val schema: Schema[RenamedId] = Schema.derived
        }
        val repo = Repo.derived[RenamedId, Int]
        assertTrue(
          repo.idColumn == "widget_id",
          repo.getId(RenamedId(7, "gadget")) == 7
        )
      },
      test("fails for ambiguous ID type") {
        case class TwoInts(id: Int, otherId: Int, name: String)
        object TwoInts {
          implicit val schema: Schema[TwoInts] = Schema.derived
        }
        val result = scala.util.Try(Repo.derived[TwoInts, Int])
        assertTrue(result.isFailure)
      },
      test("fails for missing ID type") {
        case class NoLong(id: Int, name: String)
        object NoLong {
          implicit val schema: Schema[NoLong] = Schema.derived
        }
        val result = scala.util.Try(Repo.derived[NoLong, Long])
        assertTrue(result.isFailure)
      },
      test("update is a no-op when table only contains the ID column") {
        case class IdOnly(id: Int)
        object IdOnly {
          implicit val schema: Schema[IdOnly] = Schema.derived
        }

        val repo = Repo.derived[IdOnly, Int]

        val unusedConnection = new DbConnection {
          def prepareStatement(sql: String): DbPreparedStatement =
            throw new AssertionError(s"prepareStatement should not be called: $sql")
          def prepareStatementReturningKeys(sql: String): DbPreparedStatement =
            throw new AssertionError(s"prepareStatementReturningKeys should not be called: $sql")
          def close(): Unit                            = ()
          def isClosed: Boolean                        = false
          def setAutoCommit(autoCommit: Boolean): Unit = ()
          def getAutoCommit: Boolean                   = true
          def commit(): Unit                           = ()
          def rollback(): Unit                         = ()
        }

        given DbCon = new DbCon {
          val connection: DbConnection = unusedConnection
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }

        assertTrue(repo.update(IdOnly(1)) == 0)
      }
    )
  )
}
