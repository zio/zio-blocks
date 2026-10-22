package writejoinsandinspectgeneratedsql

import zio.blocks.sql.query.*
import zio.blocks.sql.*

/**
 * How to Write Joins and Inspect the Generated SQL: Join Each Book to Its First
 * Author
 *
 * Joins each book to its first author with Rel.manyToOne and decodes rows as
 * (Book, Author) tuples.
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.Step1ManyToOneExample"
 */
object Step1ManyToOneExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

    val booksWithAuthors = SqlQuery.from(books).join(bookAuthor)

    transactor.connect {
      booksWithAuthors.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, author) =>
        println(s"${book.title} (${book.year}) by ${author.name}")
      }
    }
  }
}
