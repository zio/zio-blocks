package testdatabasecode

import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — the code under test
 *
 * A bookshop repository: find a book by its title, add copies to its stock and
 * add several books at once. The tests in `src/test/scala/testdatabasecode`
 * run it against a fresh SQLite database.
 */
case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String], stock: Int)

object Book {
  given Schema[Book] = Schema.derived
}

object Bookshop {

  val books: Repo[Book, Long] = Repo.derived[Book, Long]

  def findByTitle(title: String)(using DbCon): Maybe[Book] =
    sql"SELECT id, title, author, year, isbn, stock FROM book WHERE title = $title".queryOne[Book]

  def restock(id: Long, copies: Int)(using DbCon): Int =
    sql"UPDATE book SET stock = stock + $copies WHERE id = $id".update

  def addAll(newBooks: Seq[Book])(using DbCon): Int =
    newBooks.map(books.insert).sum
}
