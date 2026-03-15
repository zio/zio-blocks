package zio.blocks.sql

import zio.test._
import zio.blocks.schema._
import java.sql.DriverManager

object RepoIntegrationSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  private val userTable = Table.derived[User](SqlDialect.SQLite)

  private given DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[Int]  = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive

  private val intCodec: DbCodec[Int] = summon[DbCodec[Int]]

  private val userRepo = Repo(userTable, "id", intCodec, (_: User).id)

  private def withFreshDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
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
    }
    f(tx)
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
    }
  )
}
