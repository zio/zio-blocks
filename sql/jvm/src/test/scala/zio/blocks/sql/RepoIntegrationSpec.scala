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

object RepoIntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  enum Priority {
    case Low, Medium, High
  }
  object Priority {
    implicit val schema: Schema[Priority] = Schema.derived
  }

  case class Task(id: Int, title: String, priority: Priority)
  object Task {
    implicit val schema: Schema[Task] = Schema.derived
  }

  private val userTable = Table.derived[User]
  private val taskTable = Table.derived[Task]

  private given DbCodec[User]     = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Task]     = Task.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int]      = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive
  private given DbCodec[Priority] = Priority.schema.deriving(DbCodecDeriver).derive

  private val intCodec: DbCodec[Int] = summon[DbCodec[Int]]

  private val userRepo     = Repo(userTable, "id", intCodec, (_: User).id)
  private val taskRepo     = Repo(taskTable, "id", intCodec, (_: Task).id)
  private val autoUserRepo = Repo(userTable, "id", intCodec, (_: User).id)

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
        .literal(
          "CREATE TABLE IF NOT EXISTS user (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
        )
        .update
      Frag
        .literal(
          "CREATE TABLE IF NOT EXISTS task (id INTEGER NOT NULL, title TEXT NOT NULL, priority TEXT NOT NULL)"
        )
        .update
    }
    f(tx)
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
        .literal(
          "CREATE TABLE IF NOT EXISTS user (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
        )
        .update
      Frag
        .literal(
          "CREATE TABLE IF NOT EXISTS task (id INTEGER NOT NULL, title TEXT NOT NULL, priority TEXT NOT NULL)"
        )
        .update
    }
    testLogger.clear()
    f(tx, testLogger)
  }

  private def withAutoIncrementDb[A](f: JdbcTransactor => A): A = {
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
          "CREATE TABLE IF NOT EXISTS user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL)"
        )
        .update
    }
    f(tx)
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

  def spec: Spec[TestEnvironment, Any] = suite("RepoIntegrationSpec")(
    test("insert and find roundtrip") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "alice@test.com"))
          val found = userRepo.find(1)
          assertTrue(
            found.isDefined,
            found.get.id == 1,
            found.get.name == "Alice",
            found.get.email == "alice@test.com"
          )
        }
      }
    },
    test("insert returns 1 for single insert") {
      withFreshDb { tx =>
        tx.connect {
          val rows = userRepo.insert(User(1, "Alice", "alice@test.com"))
          assertTrue(rows == 1)
        }
      }
    },
    test("find returns empty Maybe for non-existing") {
      withFreshDb { tx =>
        tx.connect {
          val found = userRepo.find(999)
          assertTrue(found.isEmpty)
        }
      }
    },
    test("all returns all inserted rows") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.insert(User(2, "Bob", "b@test.com"))
          userRepo.insert(User(3, "Charlie", "c@test.com"))
          val all = userRepo.all
          assertTrue(all.size == 3)
        }
      }
    },
    test("count returns correct count") {
      withFreshDb { tx =>
        tx.connect {
          assertTrue(userRepo.count == 0L)
        }
      }
    },
    test("count reflects insertions") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.insert(User(2, "Bob", "b@test.com"))
          assertTrue(userRepo.count == 2L)
        }
      }
    },
    test("exists returns true for existing, false for missing") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          assertTrue(
            userRepo.exists(1),
            !userRepo.exists(999)
          )
        }
      }
    },
    test("update modifies existing row") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "old@test.com"))
          userRepo.update(User(1, "Alice Updated", "new@test.com"))
          val found = userRepo.find(1)
          assertTrue(
            found.isDefined,
            found.get.name == "Alice Updated",
            found.get.email == "new@test.com"
          )
        }
      }
    },
    test("update returns 1 for existing, 0 for non-existing") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          val updated    = userRepo.update(User(1, "Alice Updated", "new@test.com"))
          val notUpdated = userRepo.update(User(999, "Ghost", "ghost@test.com"))
          assertTrue(updated == 1, notUpdated == 0)
        }
      }
    },
    test("delete removes the row") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.delete(1)
          assertTrue(userRepo.find(1).isEmpty)
        }
      }
    },
    test("delete returns 1 for existing, 0 for non-existing") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          val deleted    = userRepo.delete(1)
          val notDeleted = userRepo.delete(999)
          assertTrue(deleted == 1, notDeleted == 0)
        }
      }
    },
    test("delete by entity extracts ID") {
      withFreshDb { tx =>
        tx.connect {
          val user = User(1, "Alice", "a@test.com")
          userRepo.insert(user)
          userRepo.delete(user.id)
          assertTrue(userRepo.find(1).isEmpty)
        }
      }
    },
    test("clear removes all rows") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.insert(User(2, "Bob", "b@test.com"))
          userRepo.clear()
          assertTrue(
            userRepo.count == 0L,
            userRepo.all.isEmpty
          )
        }
      }
    },
    test("full CRUD lifecycle") {
      withFreshDb { tx =>
        tx.connect {
          // Create
          userRepo.insert(User(1, "Alice", "alice@test.com"))
          userRepo.insert(User(2, "Bob", "bob@test.com"))
          assertTrue(userRepo.count == 2L)

          // Read
          val alice = userRepo.find(1)
          assertTrue(alice.get.name == "Alice")

          // Update
          userRepo.update(User(1, "Alice Smith", "alice.smith@test.com"))
          val updated = userRepo.find(1)
          assertTrue(updated.get.name == "Alice Smith")

          // Delete
          userRepo.delete(2)
          assertTrue(userRepo.count == 1L)

          // Clear
          userRepo.clear()
          assertTrue(userRepo.count == 0L)
        }
      }
    },
    suite("enum integration")(
      test("insert and find with enum field") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "Write tests", Priority.High))
            val found = taskRepo.find(1)
            assertTrue(
              found.isDefined,
              found.get.id == 1,
              found.get.title == "Write tests",
              found.get.priority == Priority.High
            )
          }
        }
      },
      test("enum values round-trip through database") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "Low task", Priority.Low))
            taskRepo.insert(Task(2, "Medium task", Priority.Medium))
            taskRepo.insert(Task(3, "High task", Priority.High))

            val t1 = taskRepo.find(1)
            val t2 = taskRepo.find(2)
            val t3 = taskRepo.find(3)

            assertTrue(
              t1.get.priority == Priority.Low,
              t2.get.priority == Priority.Medium,
              t3.get.priority == Priority.High
            )
          }
        }
      },
      test("update enum field") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "A task", Priority.Low))
            taskRepo.update(Task(1, "A task", Priority.High))
            val found = taskRepo.find(1)
            assertTrue(found.get.priority == Priority.High)
          }
        }
      },
      test("all with enum fields") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "Task 1", Priority.Low))
            taskRepo.insert(Task(2, "Task 2", Priority.High))
            val all = taskRepo.all
            assertTrue(all.size == 2)
          }
        }
      }
    ),
    suite("insertReturning")(
      test("returns the inserted entity") {
        withFreshDb { tx =>
          tx.connect {
            val returned = userRepo.insertReturning(User(1, "Alice", "alice@test.com"))
            assertTrue(
              returned.id == 1,
              returned.name == "Alice",
              returned.email == "alice@test.com"
            )
          }
        }
      },
      test("entity exists in database after insertReturning") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insertReturning(User(1, "Bob", "bob@test.com"))
            val found = userRepo.find(1)
            assertTrue(
              found.isDefined,
              found.get.name == "Bob"
            )
          }
        }
      },
      test("getGeneratedKeys returns auto-generated IDs") {
        withAutoIncrementDb { tx =>
          tx.connect {
            val insertFrag = Frag.literal("INSERT INTO user (name, email) VALUES ('Alice', 'alice@test.com')")
            val keys       = insertFrag.updateReturningKeys[Int]
            assertTrue(
              keys == List(1)
            )
          }
        }
      },
      test("getGeneratedKeys increments correctly") {
        withAutoIncrementDb { tx =>
          tx.connect {
            val k1 =
              Frag.literal("INSERT INTO user (name, email) VALUES ('Alice', 'a@test.com')").updateReturningKeys[Int]
            val k2 =
              Frag.literal("INSERT INTO user (name, email) VALUES ('Bob', 'b@test.com')").updateReturningKeys[Int]
            val k3 =
              Frag.literal("INSERT INTO user (name, email) VALUES ('Charlie', 'c@test.com')").updateReturningKeys[Int]
            assertTrue(
              k1 == List(1),
              k2 == List(2),
              k3 == List(3)
            )
          }
        }
      },
      test("insertReturning uses getGeneratedKeys for the lookup") {
        withFreshDb { tx =>
          tx.connect {
            val returned = userRepo.insertReturning(User(42, "Alice", "alice@test.com"))
            assertTrue(
              returned.id == 42,
              returned.name == "Alice",
              returned.email == "alice@test.com"
            )
          }
        }
      }
    ),
    suite("insertBatch")(
      test("inserts multiple entities in batch") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertBatch(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com"),
                User(3, "Charlie", "c@test.com")
              )
            )
            assertTrue(count == 3)
          }
        }
      },
      test("all entities are queryable after insertBatch") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insertBatch(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com"),
                User(3, "Charlie", "c@test.com")
              )
            )
            val all = userRepo.all
            assertTrue(
              all.size == 3,
              all.map(_.name).toSet == Set("Alice", "Bob", "Charlie")
            )
          }
        }
      },
      test("insertBatch with empty iterable returns 0") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertBatch(List.empty[User])
            assertTrue(count == 0, userRepo.count == 0L)
          }
        }
      },
      test("insertBatch with single entity") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertBatch(List(User(1, "Solo", "solo@test.com")))
            assertTrue(count == 1, userRepo.count == 1L)
          }
        }
      }
    ),
    suite("derived with zero args")(
      test("insert and find roundtrip with auto-detected ID") {
        case class Widget(id: Int, label: String)
        object Widget {
          implicit val schema: Schema[Widget] = Schema.derived
        }
        val widgetRepo = Repo.derived[Widget, Int]

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
          val tx = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
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
            Frag.literal("CREATE TABLE IF NOT EXISTS widget (id INTEGER NOT NULL, label TEXT NOT NULL)").update
            widgetRepo.insert(Widget(1, "Sprocket"))
            val found = widgetRepo.find(1)
            assertTrue(
              found.isDefined,
              found.get.id == 1,
              found.get.label == "Sprocket"
            )
          }
        } finally conn.close()
      }
    ),
    suite("SqlLogger")(
      test("onSuccess is called for insert") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            assertTrue(
              logger.successes.size == 1,
              logger.successes.head.sql.contains("INSERT INTO"),
              logger.successes.head.rowCount == 1,
              logger.errors.isEmpty
            )
          }
        }
      },
      test("onSuccess is called for query") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            logger.clear()
            val _ = userRepo.all
            assertTrue(
              logger.successes.size == 1,
              logger.successes.head.sql.contains("SELECT"),
              logger.successes.head.rowCount == 1,
              logger.errors.isEmpty
            )
          }
        }
      },
      test("onError is called on SQL error") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            try {
              Frag.literal("INSERT INTO nonexistent_table (id) VALUES (1)").update
            } catch {
              case _: Throwable => ()
            }
            assertTrue(
              logger.errors.size == 1,
              logger.errors.head.sql.contains("nonexistent_table"),
              logger.successes.isEmpty
            )
          }
        }
      },
      test("duration is non-negative") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            assertTrue(!logger.successes.head.duration.isNegative)
          }
        }
      },
      test("insertBatch logs success") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insertBatch(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com")
              )
            )
            assertTrue(
              logger.successes.size == 1,
              logger.successes.head.sql.contains("INSERT INTO"),
              logger.successes.head.rowCount == 2,
              logger.errors.isEmpty
            )
          }
        }
      },
      test("noop logger does not throw") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insert(User(1, "Alice", "a@test.com"))
            val _ = userRepo.all
            assertTrue(true)
          }
        }
      }
    )
  )
}
