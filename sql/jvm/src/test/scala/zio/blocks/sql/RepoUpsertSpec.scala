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
import java.sql.DriverManager
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object RepoUpsertSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class OnlyId(id: Int)
  object OnlyId {
    implicit val schema: Schema[OnlyId] = Schema.derived
  }

  private val userTable   = Table.derived[User]
  private val onlyIdTable = Table.derived[OnlyId]

  private given DbCodec[User]   = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[OnlyId] = OnlyId.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int]    = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive

  private val intCodec: DbCodec[Int] = summon[DbCodec[Int]]

  private val userRepo   = Repo(userTable, "id", intCodec, (_: User).id)
  private val onlyIdRepo = Repo(onlyIdTable, "id", intCodec, (_: OnlyId).id)

  // Unique email table for constraint violation test
  case class UserUnique(id: Int, name: String, email: String)
  object UserUnique {
    implicit val schema: Schema[UserUnique] = Schema.derived
  }
  private val userUniqueTable       = Table.derived[UserUnique]
  private given DbCodec[UserUnique] = UserUnique.schema.deriving(DbCodecDeriver).derive
  private val userUniqueRepo        = Repo(userUniqueTable, "id", intCodec, (_: UserUnique).id)

  private def withFreshDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        f
      }
    }
    tx.connect {
      Frag
        .literal("CREATE TABLE IF NOT EXISTS user (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL)")
        .update
    }
    try f(tx)
    finally conn.close()
  }

  private def withFreshDbAndLogger[A](f: (JdbcTransactor, CapturingLogger) => A): A = {
    val conn       = DriverManager.getConnection("jdbc:sqlite::memory:")
    val testLogger = new CapturingLogger
    val tx         = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = testLogger
        }
        f
      }
    }
    tx.connect {
      Frag
        .literal("CREATE TABLE IF NOT EXISTS user (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL)")
        .update
    }
    testLogger.clear()
    try f(tx, testLogger)
    finally conn.close()
  }

  private def withUniqueDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        f
      }
    }
    tx.connect {
      Frag
        .literal(
          "CREATE TABLE IF NOT EXISTS user_unique (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE)"
        )
        .update
    }
    try f(tx)
    finally conn.close()
  }

  private def withOnlyIdDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        f
      }
    }
    tx.connect {
      Frag.literal("CREATE TABLE IF NOT EXISTS only_id (id INTEGER PRIMARY KEY)").update
    }
    try f(tx)
    finally conn.close()
  }

  private class CapturingLogger extends SqlLogger {
    val successes: ArrayBuffer[SqlLogger.SuccessEvent] = ArrayBuffer.empty
    val errors: ArrayBuffer[SqlLogger.ErrorEvent]      = ArrayBuffer.empty

    def onSuccess(event: SqlLogger.SuccessEvent): Unit = successes += event
    def onError(event: SqlLogger.ErrorEvent): Unit     = errors += event

    def clear(): Unit = {
      successes.clear()
      errors.clear()
    }
  }

  def spec: Spec[TestEnvironment, Any] = suite("RepoUpsertSpec")(
    suite("insertOrUpdate single")(
      test("insert new via upsert inserts 1 row") {
        withFreshDb { tx =>
          tx.connect {
            val c = userRepo.insertOrUpdate(User(1, "Alice", "alice@test.com"))
            assertTrue(c == 1, userRepo.count == 1L, userRepo.find(1).get.name == "Alice")
          }
        }
      },
      test("update existing via upsert overwrites non-id columns") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "alice@test.com"))
            val c     = userRepo.insertOrUpdate(User(1, "Alice Updated", "new@test.com"))
            val found = userRepo.find(1)
            assertTrue(
              c == 1,
              found.isDefined,
              found.get.name == "Alice Updated",
              found.get.email == "new@test.com",
              userRepo.count == 1L
            )
          }
        }
      },
      test("double upsert single row retains second payload") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insertOrUpdate(User(1, "First", "first@test.com"))
            userRepo.insertOrUpdate(User(1, "Second", "second@test.com"))
            val found = userRepo.find(1)
            assertTrue(
              userRepo.count == 1L,
              userRepo.all.size == 1,
              found.isDefined,
              found.get.name == "Second",
              found.get.email == "second@test.com"
            )
          }
        }
      },
      test("insertOrUpdate new vs update both return 1") {
        withFreshDb { tx =>
          tx.connect {
            val c1 = userRepo.insertOrUpdate(User(10, "A", "a@test.com"))
            val c2 = userRepo.insertOrUpdate(User(10, "B", "b@test.com"))
            val c3 = userRepo.insertOrUpdate(User(11, "C", "c@test.com"))
            assertTrue(c1 == 1, c2 == 1, c3 == 1, userRepo.count == 2L)
          }
        }
      },
      test("upsert does not affect other rows") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            userRepo.insert(User(2, "Bob", "b@test.com"))
            userRepo.insertOrUpdate(User(1, "Alice2", "a2@test.com"))
            val b = userRepo.find(2)
            assertTrue(b.isDefined, b.get.name == "Bob", b.get.email == "b@test.com", userRepo.count == 2L)
          }
        }
      },
      test("golden SQL for Repo wrapper matches Upsert builder") {
        val frag = Upsert.insertDoUpdate(userTable, User(1, "Alice", "alice@example.com"), "id")
        assertTrue(
          frag.sql(
            SqlDialect.SQLite
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?""",
          frag.sql(
            SqlDialect.PostgreSQL
          ) == """INSERT INTO user (id, name, email) VALUES (?, ?, ?) ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?""",
          frag.queryParams == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com"),
            DbValue.DbString("Alice"),
            DbValue.DbString("alice@example.com")
          )
        )
      }
    ),
    suite("insertOrUpdateBatch")(
      test("inserts multiple new rows via batch") {
        withFreshDb { tx =>
          tx.connect {
            val c = userRepo.insertOrUpdateBatch(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com"),
                User(3, "Charlie", "c@test.com")
              )
            )
            assertTrue(c == 3, userRepo.count == 3L, userRepo.all.size == 3)
          }
        }
      },
      test("mixed batch with new and existing ids") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            userRepo.insert(User(2, "Bob", "b@test.com"))
            val c = userRepo.insertOrUpdateBatch(
              List(
                User(1, "Alice Updated", "a2@test.com"),
                User(3, "Charlie", "c@test.com"),
                User(2, "Bob Updated", "b2@test.com")
              )
            )
            val a1 = userRepo.find(1)
            val a2 = userRepo.find(2)
            val a3 = userRepo.find(3)
            assertTrue(
              c == 3,
              userRepo.count == 3L,
              a1.get.name == "Alice Updated",
              a1.get.email == "a2@test.com",
              a2.get.name == "Bob Updated",
              a3.isDefined
            )
          }
        }
      },
      test("batch counts correct") {
        withFreshDb { tx =>
          tx.connect {
            val c1 = userRepo.insertOrUpdateBatch(List(User(1, "A", "a@test.com"), User(2, "B", "b@test.com")))
            val c2 = userRepo.insertOrUpdateBatch(List(User(1, "A2", "a2@test.com"), User(3, "C", "c@test.com")))
            assertTrue(c1 == 2, c2 == 2, userRepo.count == 3L)
          }
        }
      },
      test("empty batch returns 0 without touching db") {
        withFreshDb { tx =>
          tx.connect {
            val c = userRepo.insertOrUpdateBatch(List.empty[User])
            assertTrue(c == 0, userRepo.count == 0L)
          }
        }
      },
      test("single entity batch") {
        withFreshDb { tx =>
          tx.connect {
            val c = userRepo.insertOrUpdateBatch(List(User(99, "Solo", "solo@test.com")))
            assertTrue(c == 1, userRepo.count == 1L, userRepo.find(99).isDefined)
          }
        }
      },
      test("batch with all existing ids updates each") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "A", "a@test.com"))
            userRepo.insert(User(2, "B", "b@test.com"))
            val c = userRepo.insertOrUpdateBatch(
              List(
                User(1, "A2", "a2@test.com"),
                User(2, "B2", "b2@test.com")
              )
            )
            assertTrue(
              c == 2,
              userRepo.find(1).get.name == "A2",
              userRepo.find(2).get.name == "B2",
              userRepo.count == 2L
            )
          }
        }
      },
      test("batch upsert yields single row per id after double batch") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insertOrUpdateBatch(List(User(1, "A", "a@test.com")))
            userRepo.insertOrUpdateBatch(List(User(1, "B", "b@test.com")))
            assertTrue(userRepo.count == 1L, userRepo.find(1).get.name == "B")
          }
        }
      }
    ),
    suite("constraint violation propagation")(
      test("single upsert violating UNIQUE propagates exception") {
        withUniqueDb { tx =>
          tx.connect {
            userUniqueRepo.insert(UserUnique(1, "Alice", "shared@test.com"))
            val result = Try(userUniqueRepo.insertOrUpdate(UserUnique(2, "Bob", "shared@test.com")))
            assertTrue(result.isFailure)
          }
        }
      },
      test("batch violating UNIQUE propagates exception") {
        withUniqueDb { tx =>
          tx.connect {
            userUniqueRepo.insert(UserUnique(1, "Alice", "a@test.com"))
            val result = Try(
              userUniqueRepo.insertOrUpdateBatch(
                List(
                  UserUnique(2, "Bob", "b@test.com"),
                  UserUnique(3, "Charlie", "a@test.com")
                )
              )
            )
            assertTrue(result.isFailure)
          }
        }
      }
    ),
    suite("single-col table edge")(
      test("insertOrUpdate on single-col table throws IllegalArgumentException") {
        withOnlyIdDb { tx =>
          tx.connect {
            val result = Try(onlyIdRepo.insertOrUpdate(OnlyId(1)))
            assertTrue(result.isFailure, result.failed.get.isInstanceOf[IllegalArgumentException])
          }
        }
      },
      test("insertOrUpdateBatch on single-col table throws") {
        withOnlyIdDb { tx =>
          tx.connect {
            val result = Try(onlyIdRepo.insertOrUpdateBatch(List(OnlyId(1), OnlyId(2))))
            assertTrue(result.isFailure, result.failed.get.isInstanceOf[IllegalArgumentException])
          }
        }
      },
      test("empty batch on single-col table returns 0 without throwing") {
        withOnlyIdDb { tx =>
          tx.connect {
            val c = onlyIdRepo.insertOrUpdateBatch(List.empty[OnlyId])
            assertTrue(c == 0)
          }
        }
      }
    ),
    suite("logging")(
      test("insertOrUpdate logs success with ON CONFLICT sql") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insertOrUpdate(User(1, "Alice", "a@test.com"))
            assertTrue(
              logger.successes.size == 1,
              logger.successes.head.sql.contains("ON CONFLICT"),
              logger.successes.head.sql.contains("DO UPDATE SET"),
              logger.errors.isEmpty
            )
          }
        }
      },
      test("insertOrUpdateBatch logs success with ON CONFLICT sql and total count") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insertOrUpdateBatch(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com")
              )
            )
            assertTrue(
              logger.successes.size == 1,
              logger.successes.head.sql.contains("ON CONFLICT"),
              logger.successes.head.sql.contains("DO UPDATE SET"),
              logger.successes.head.rowCount == 2,
              logger.errors.isEmpty
            )
          }
        }
      },
      test("batch logs empty params like insertBatch") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insertOrUpdateBatch(List(User(1, "A", "a@test.com")))
            assertTrue(logger.successes.head.params.isEmpty)
          }
        }
      }
    )
  )
}
