package zio.blocks.sql

import zio.test.*
import zio.blocks.schema.*
import java.sql.DriverManager

object TransactorSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  private val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  private given DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

  private given DbCodec[Int] = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive

  private given DbCodec[String] = implicitly[Schema[String]].deriving(DbCodecDeriver).derive

  private def sharedConnTransactor(): (JdbcTransactor, java.sql.Connection) = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[A](f: DbCon ?=> A): A = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
        }
        f
      }

      override def transact[A](f: DbTx ?=> A): A = {
        val dbConn = new JdbcConnection(conn)
        conn.setAutoCommit(false)
        try {
          given tx: DbTx = new DbTx {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = SqlDialect.SQLite
          }
          val result = f
          conn.commit()
          result
        } catch {
          case e: Throwable =>
            conn.rollback()
            throw e
        } finally conn.setAutoCommit(true)
      }
    }
    (tx, conn)
  }

  def spec: Spec[TestEnvironment, Any] = suite("TransactorSpec")(
    test("connect executes queries") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS test_connect (id INTEGER NOT NULL)"))
        SqlOps.update(
          sql"INSERT INTO test_connect (id) VALUES (${DbValue.DbInt(1)})"
        )
        val ids = SqlOps.query[Int](sql"SELECT id FROM test_connect")
        assertTrue(ids == List(1))
      }
    },
    test("INSERT and SELECT roundtrip") {
      transactor.connect {
        SqlOps.update(
          Frag.const(
            "CREATE TABLE IF NOT EXISTS users (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
          )
        )
        SqlOps.update(
          sql"INSERT INTO users (id, name, email) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("Alice")}, ${DbValue.DbString("alice@example.com")})"
        )
        SqlOps.update(
          sql"INSERT INTO users (id, name, email) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("Bob")}, ${DbValue.DbString("bob@example.com")})"
        )
        val users = SqlOps.query[User](sql"SELECT id, name, email FROM users ORDER BY id")
        assertTrue(
          users.length == 2,
          users.head.id == 1,
          users.head.name == "Alice",
          users.head.email == "alice@example.com",
          users(1).name == "Bob"
        )
      }
    },
    test("queryOne returns first result") {
      transactor.connect {
        SqlOps.update(
          Frag.const(
            "CREATE TABLE IF NOT EXISTS query_one_test (id INTEGER NOT NULL, val TEXT NOT NULL)"
          )
        )
        SqlOps.update(
          sql"INSERT INTO query_one_test (id, val) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("first")})"
        )
        SqlOps.update(
          sql"INSERT INTO query_one_test (id, val) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("second")})"
        )
        val result = SqlOps.queryOne[String](
          sql"SELECT val FROM query_one_test WHERE id = ${DbValue.DbInt(1)}"
        )
        assertTrue(result == Some("first"))
      }
    },
    test("empty result returns empty List") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS empty_test (id INTEGER NOT NULL)"))
        val result = SqlOps.query[Int](sql"SELECT id FROM empty_test")
        assertTrue(result.isEmpty)
      }
    },
    test("queryOne on empty result returns None") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS empty_one_test (id INTEGER NOT NULL)"))
        val result = SqlOps.queryOne[Int](sql"SELECT id FROM empty_one_test")
        assertTrue(result.isEmpty)
      }
    },
    test("transaction commits on success") {
      val (tx, conn) = sharedConnTransactor()
      try {
        tx.connect {
          SqlOps.update(Frag.const("CREATE TABLE tx_commit (id INTEGER NOT NULL, name TEXT NOT NULL)"))
        }
        tx.transact {
          SqlOps.update(
            sql"INSERT INTO tx_commit (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("committed")})"
          )
        }
        tx.connect {
          val rows = SqlOps.query[String](sql"SELECT name FROM tx_commit")
          assertTrue(rows == List("committed"))
        }
      } finally conn.close()
    },
    test("transaction rolls back on exception") {
      val (tx, conn) = sharedConnTransactor()
      try {
        tx.connect {
          SqlOps.update(Frag.const("CREATE TABLE tx_rollback (id INTEGER NOT NULL, name TEXT NOT NULL)"))
          SqlOps.update(
            sql"INSERT INTO tx_rollback (id, name) VALUES (${DbValue.DbInt(0)}, ${DbValue.DbString("before")})"
          )
        }
        try {
          tx.transact {
            SqlOps.update(
              sql"INSERT INTO tx_rollback (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("should_rollback")})"
            )
            throw new RuntimeException("forced error")
          }
        } catch {
          case _: RuntimeException => ()
        }
        tx.connect {
          val rows = SqlOps.query[String](sql"SELECT name FROM tx_rollback WHERE id = ${DbValue.DbInt(1)}")
          assertTrue(rows.isEmpty)
        }
      } finally conn.close()
    },
    test("update returns affected row count") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS count_test (id INTEGER NOT NULL)"))
        SqlOps.update(
          sql"INSERT INTO count_test (id) VALUES (${DbValue.DbInt(1)})"
        )
        SqlOps.update(
          sql"INSERT INTO count_test (id) VALUES (${DbValue.DbInt(2)})"
        )
        val deleted = SqlOps.update(Frag.const("DELETE FROM count_test"))
        assertTrue(deleted == 2)
      }
    }
  )
}
