package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

/**
 * How to Use the sql Module from a ZIO Application — the shared bookstore model
 *
 * The `Book` model, its `Repo`, the books on the shelf, the hand-written
 * `CREATE TABLE` statement and a stock lookup. The examples in this package
 * import them.
 */
case class Book(id: Long, title: String, year: Int, stock: Int)

object Book {
  given Schema[Book] = Schema.derived
}

val books: Repo[Book, Long] = Repo.derived[Book, Long]

val shelf = List(
  Book(1L, "Zionomicon", 2024, 5),
  Book(2L, "Programming in Scala", 2008, 3),
  Book(3L, "Functional Programming in Scala", 2014, 4)
)

def createBookTable()(using DbCon): Unit =
  sql"""CREATE TABLE book (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    year INTEGER NOT NULL,
    stock INTEGER NOT NULL
  )""".update

def stockOf(bookId: Long): ZIO[TransactorZIO, Throwable, Int] =
  ZIO.serviceWithZIO[TransactorZIO](
    _.connect(sql"SELECT stock FROM book WHERE id = $bookId".queryOne[Int].getOrElse(0))
  )
