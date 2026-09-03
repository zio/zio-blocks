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

  /**
   * Records every `setX` call by 1-based index for batch equivalence checks.
   */
  private final class RecordingWriter extends DbParamWriter {
    private val slots                                                = scala.collection.mutable.Map.empty[Int, DbValue]
    private def record(index: Int, value: DbValue): Unit             = slots(index) = value
    def setInt(index: Int, value: Int): Unit                         = record(index, DbValue.DbInt(value))
    def setLong(index: Int, value: Long): Unit                       = record(index, DbValue.DbLong(value))
    def setDouble(index: Int, value: Double): Unit                   = record(index, DbValue.DbDouble(value))
    def setFloat(index: Int, value: Float): Unit                     = record(index, DbValue.DbFloat(value))
    def setBoolean(index: Int, value: Boolean): Unit                 = record(index, DbValue.DbBoolean(value))
    def setString(index: Int, value: String): Unit                   = record(index, DbValue.DbString(value))
    def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit =
      record(index, DbValue.DbBigDecimal(scala.BigDecimal(value)))
    def setBytes(index: Int, value: Array[Byte]): Unit                     = record(index, DbValue.DbBytes(value))
    def setShort(index: Int, value: Short): Unit                           = record(index, DbValue.DbShort(value))
    def setByte(index: Int, value: Byte): Unit                             = record(index, DbValue.DbByte(value))
    def setLocalDate(index: Int, value: java.time.LocalDate): Unit         = record(index, DbValue.DbLocalDate(value))
    def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit =
      record(index, DbValue.DbLocalDateTime(value))
    def setLocalTime(index: Int, value: java.time.LocalTime): Unit                 = record(index, DbValue.DbLocalTime(value))
    def setInstant(index: Int, value: java.time.Instant): Unit                     = record(index, DbValue.DbInstant(value))
    def setDuration(index: Int, value: java.time.Duration): Unit                   = record(index, DbValue.DbDuration(value))
    def setUUID(index: Int, value: java.util.UUID): Unit                           = record(index, DbValue.DbUUID(value))
    def setNull(index: Int, sqlType: Int): Unit                                    = record(index, DbValue.DbNull)
    def setArray(index: Int, elementType: String, elements: IndexedSeq[Any]): Unit =
      record(index, DbValue.DbArray(elementType, elements))
    def snapshot: IndexedSeq[DbValue] =
      slots.toIndexedSeq.sortBy(_._1).map(_._2)
    def clear(): Unit = slots.clear()
  }

  /** Captures prepared SQL and per-row param snapshots for batch tests. */
  private final class RecordingConnection(
    preparedSql: scala.collection.mutable.ListBuffer[String],
    rowValues: scala.collection.mutable.ListBuffer[IndexedSeq[DbValue]]
  ) extends DbConnection {
    private val writer    = new RecordingWriter
    private val statement = new DbPreparedStatement {
      def executeQuery(): DbResultSet               = throw new AssertionError("no queries expected")
      def executeUpdate(): Int                      = throw new AssertionError("no updates expected")
      def executeUpdateReturningKeys(): DbResultSet = throw new AssertionError("no keys expected")
      def close(): Unit                             = ()
      def paramWriter: DbParamWriter                = writer
      def addBatch(): Unit                          = {
        rowValues += writer.snapshot
        writer.clear()
      }
      def executeBatch(): Array[Int] = Array.fill(rowValues.size)(1)
    }
    def prepareStatement(sql: String): DbPreparedStatement = {
      preparedSql += sql
      writer.clear()
      statement
    }
    def prepareStatementReturningKeys(sql: String): DbPreparedStatement =
      throw new AssertionError(s"prepareStatementReturningKeys should not be called: $sql")
    def close(): Unit                            = ()
    def isClosed: Boolean                        = false
    def setAutoCommit(autoCommit: Boolean): Unit = ()
    def getAutoCommit: Boolean                   = true
    def commit(): Unit                           = ()
    def rollback(): Unit                         = ()
    override def savepoint(name: String): Unit   = ()
    override def release(name: String): Unit     = ()
    override def rollbackTo(name: String): Unit  = ()
  }

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
        case class TwoInts(intA: Int, intB: Int, name: String)
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
      test("selects @Modifier.id-annotated field among multiple same-type fields") {
        case class MarkedId(@Modifier.id a: Int, b: Int, name: String)
        object MarkedId {
          implicit val schema: Schema[MarkedId] = Schema.derived
        }
        val repo = Repo.derived[MarkedId, Int]
        assertTrue(
          repo.idColumn == "a",
          repo.getId(MarkedId(42, 7, "test")) == 42
        )
      },
      test("fails for multiple @Modifier.id-annotated fields of same type") {
        case class TwoMarkedIds(@Modifier.id a: Int, @Modifier.id b: Int, name: String)
        object TwoMarkedIds {
          implicit val schema: Schema[TwoMarkedIds] = Schema.derived
        }
        val result = scala.util.Try(Repo.derived[TwoMarkedIds, Int])
        assertTrue(result.isFailure)
      },
      test("selects 'id' field by name among multiple same-type fields") {
        case class LiteralId(id: Int, otherId: Int, name: String)
        object LiteralId {
          implicit val schema: Schema[LiteralId] = Schema.derived
        }
        val repo = Repo.derived[LiteralId, Int]
        assertTrue(
          repo.idColumn == "id",
          repo.getId(LiteralId(42, 7, "test")) == 42
        )
      },
      test("selects '<entity>Id' field by convention among multiple same-type fields") {
        case class Widget(a: Int, b: Int, name: String, widgetId: Int)
        object Widget {
          implicit val schema: Schema[Widget] = Schema.derived
        }
        val repo = Repo.derived[Widget, Int]
        assertTrue(
          repo.idColumn == "widget_id",
          repo.getId(Widget(1, 2, "gadget", 99)) == 99
        )
      },
      test("subclasses inherit derived CRUD metadata") {
        case class User(userId: Int, name: String)
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }

        final class UserRepo extends Repo[User, Int]

        val repo = new UserRepo
        assertTrue(
          repo.idColumn == "user_id",
          repo.getId(User(42, "Ada")) == 42
        )
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
          override def savepoint(name: String): Unit   = ()
          override def release(name: String): Unit     = ()
          override def rollbackTo(name: String): Unit  = ()
        }

        given DbCon = new DbCon {
          val connection: DbConnection = unusedConnection
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }

        assertTrue(repo.update(IdOnly(1)) == 0)
      }
    ),
    suite("insertOrUpdateBatch uses one SQL shape")(
      test("batch prepares a single statement and writes insert ++ assignment params per row") {
        case class User(id: Int, name: String)
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }
        val repo        = Repo.derived[User, Int]
        val preparedSql = scala.collection.mutable.ListBuffer.empty[String]
        val rowValues   = scala.collection.mutable.ListBuffer.empty[IndexedSeq[DbValue]]
        given DbCon     = new DbCon {
          val connection: DbConnection = new RecordingConnection(preparedSql, rowValues)
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        val total = repo.insertOrUpdateBatch(List(User(1, "Alice"), User(2, "Bob")))
        assertTrue(
          total == 2,
          preparedSql.size == 1,
          preparedSql.head ==
            """INSERT INTO user (id, name) VALUES (?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?""",
          rowValues.size == 2,
          rowValues(0) == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Alice"), DbValue.DbString("Alice")),
          rowValues(1) == IndexedSeq(DbValue.DbInt(2), DbValue.DbString("Bob"), DbValue.DbString("Bob"))
        )
      },
      test("batch params match Upsert.insertDoUpdate params for the same entity") {
        case class User(id: Int, name: String)
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }
        val repo        = Repo.derived[User, Int]
        val preparedSql = scala.collection.mutable.ListBuffer.empty[String]
        val rowValues   = scala.collection.mutable.ListBuffer.empty[IndexedSeq[DbValue]]
        given DbCon     = new DbCon {
          val connection: DbConnection = new RecordingConnection(preparedSql, rowValues)
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        repo.insertOrUpdateBatch(List(User(9, "Zed")))
        val expected = Upsert.insertDoUpdate(repo.table, User(9, "Zed"), "id")
        assertTrue(
          preparedSql.head == expected.sql(SqlDialect.SQLite),
          rowValues.head == expected.queryParams
        )
      }
    ),
    suite("inListParts")(
      test("builds one placeholder group per element") {
        assertTrue(
          Repo.inListParts("SELECT a FROM t WHERE (id) IN (", 1) == IndexedSeq("SELECT a FROM t WHERE (id) IN (", ")"),
          Repo.inListParts("SELECT a FROM t WHERE (id) IN (", 3) ==
            IndexedSeq("SELECT a FROM t WHERE (id) IN (", ", ", ", ", ")")
        )
      }
    )
  )
}
