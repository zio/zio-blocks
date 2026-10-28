package postgrespool

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import java.sql.SQLException
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: shared setup
 *
 * The pool settings read from the environment, the health check, the Book case
 * class with its Schema, the Table and Repo for it, and the database work that
 * the examples in this package share.
 */
def bookstorePoolConfig(): HikariConfig = {
  val config = new HikariConfig()
  config.setJdbcUrl(sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore"))
  config.setUsername(sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore"))
  config.setPassword(sys.env("BOOKSTORE_DB_PASSWORD"))
  config.setPoolName("bookstore")
  config.setMaximumPoolSize(10)
  config.setConnectionTimeout(5000)
  config
}

def openBookstorePool(): HikariDataSource =
  new HikariDataSource(bookstorePoolConfig())

def isHealthy(transactor: JdbcTransactor): Boolean =
  try transactor.connect(sql"SELECT 1".queryOne[Int].contains(1))
  catch { case _: SQLException => false }

def serverVersion()(using DbCon): String =
  sql"SELECT version()".queryOne[String].getOrElse("unknown")

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

object Book {
  given Schema[Book] = Schema.derived
}

val bookTable = Table.derived[Book]
val books     = Repo.derived[Book, Long]

def stockShelf()(using DbCon): List[Book] = {
  bookTable.createTable(SqlDialect.PostgreSQL).update
  books.clear()
  books.insert(Book(1L, "Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, None))
  books.insert(Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")))
  books.insert(Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657")))
  books.all
}

def shelfReport()(using DbCon): (Long, List[Book]) =
  (books.count, books.all)
