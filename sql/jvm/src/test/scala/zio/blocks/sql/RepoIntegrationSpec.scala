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

  private val userTable = Table.derived[User](SqlDialect.SQLite)
  private val taskTable = Table.derived[Task](SqlDialect.SQLite)

  private given DbCodec[User]     = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Task]     = Task.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int]      = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive
  private given DbCodec[Priority] = Priority.schema.deriving(DbCodecDeriver).derive

  private val intCodec: DbCodec[Int] = summon[DbCodec[Int]]

  private val userRepo = Repo(userTable, "id", intCodec, (_: User).id)
  private val taskRepo = Repo(taskTable, "id", intCodec, (_: Task).id)

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
      SqlOps.update(
        Frag.const(
          "CREATE TABLE IF NOT EXISTS user (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
        )
      )
      SqlOps.update(
        Frag.const(
          "CREATE TABLE IF NOT EXISTS task (id INTEGER NOT NULL, title TEXT NOT NULL, priority TEXT NOT NULL)"
        )
      )
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
      SqlOps.update(
        Frag.const(
          "CREATE TABLE IF NOT EXISTS user (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
        )
      )
      SqlOps.update(
        Frag.const(
          "CREATE TABLE IF NOT EXISTS task (id INTEGER NOT NULL, title TEXT NOT NULL, priority TEXT NOT NULL)"
        )
      )
    }
    testLogger.clear()
    f(tx, testLogger)
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
    test("insert and findById roundtrip") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "alice@test.com"))
          val found = userRepo.findById(1)
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
    test("findById returns None for non-existing") {
      withFreshDb { tx =>
        tx.connect {
          val found = userRepo.findById(999)
          assertTrue(found.isEmpty)
        }
      }
    },
    test("findAll returns all inserted rows") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.insert(User(2, "Bob", "b@test.com"))
          userRepo.insert(User(3, "Charlie", "c@test.com"))
          val all = userRepo.findAll
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
    test("existsById returns true for existing, false for missing") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          assertTrue(
            userRepo.existsById(1),
            !userRepo.existsById(999)
          )
        }
      }
    },
    test("update modifies existing row") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "old@test.com"))
          userRepo.update(User(1, "Alice Updated", "new@test.com"))
          val found = userRepo.findById(1)
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
    test("deleteById removes the row") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.deleteById(1)
          assertTrue(userRepo.findById(1).isEmpty)
        }
      }
    },
    test("deleteById returns 1 for existing, 0 for non-existing") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          val deleted    = userRepo.deleteById(1)
          val notDeleted = userRepo.deleteById(999)
          assertTrue(deleted == 1, notDeleted == 0)
        }
      }
    },
    test("delete removes by entity") {
      withFreshDb { tx =>
        tx.connect {
          val user = User(1, "Alice", "a@test.com")
          userRepo.insert(user)
          userRepo.delete(user)
          assertTrue(userRepo.findById(1).isEmpty)
        }
      }
    },
    test("truncate removes all rows") {
      withFreshDb { tx =>
        tx.connect {
          userRepo.insert(User(1, "Alice", "a@test.com"))
          userRepo.insert(User(2, "Bob", "b@test.com"))
          userRepo.truncate()
          assertTrue(
            userRepo.count == 0L,
            userRepo.findAll.isEmpty
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
          val alice = userRepo.findById(1)
          assertTrue(alice.get.name == "Alice")

          // Update
          userRepo.update(User(1, "Alice Smith", "alice.smith@test.com"))
          val updated = userRepo.findById(1)
          assertTrue(updated.get.name == "Alice Smith")

          // Delete
          userRepo.deleteById(2)
          assertTrue(userRepo.count == 1L)

          // Truncate
          userRepo.truncate()
          assertTrue(userRepo.count == 0L)
        }
      }
    },
    suite("enum integration")(
      test("insert and findById with enum field") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "Write tests", Priority.High))
            val found = taskRepo.findById(1)
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

            val t1 = taskRepo.findById(1)
            val t2 = taskRepo.findById(2)
            val t3 = taskRepo.findById(3)

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
            val found = taskRepo.findById(1)
            assertTrue(found.get.priority == Priority.High)
          }
        }
      },
      test("findAll with enum fields") {
        withFreshDb { tx =>
          tx.connect {
            taskRepo.insert(Task(1, "Task 1", Priority.Low))
            taskRepo.insert(Task(2, "Task 2", Priority.High))
            val all = taskRepo.findAll
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
            val found = userRepo.findById(1)
            assertTrue(
              found.isDefined,
              found.get.name == "Bob"
            )
          }
        }
      }
    ),
    suite("insertAll")(
      test("inserts multiple entities in batch") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertAll(
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
      test("all entities are queryable after insertAll") {
        withFreshDb { tx =>
          tx.connect {
            userRepo.insertAll(
              List(
                User(1, "Alice", "a@test.com"),
                User(2, "Bob", "b@test.com"),
                User(3, "Charlie", "c@test.com")
              )
            )
            val all = userRepo.findAll
            assertTrue(
              all.size == 3,
              all.map(_.name).toSet == Set("Alice", "Bob", "Charlie")
            )
          }
        }
      },
      test("insertAll with empty iterable returns 0") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertAll(List.empty[User])
            assertTrue(count == 0, userRepo.count == 0L)
          }
        }
      },
      test("insertAll with single entity") {
        withFreshDb { tx =>
          tx.connect {
            val count = userRepo.insertAll(List(User(1, "Solo", "solo@test.com")))
            assertTrue(count == 1, userRepo.count == 1L)
          }
        }
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
            val _ = userRepo.findAll
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
              SqlOps.update(Frag.const("INSERT INTO nonexistent_table (id) VALUES (1)"))
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
      test("insertAll logs success") {
        withFreshDbAndLogger { (tx, logger) =>
          tx.connect {
            userRepo.insertAll(
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
            val _ = userRepo.findAll
            assertTrue(true)
          }
        }
      }
    )
  )
}
