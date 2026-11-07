package writejoinsandinspectgeneratedsql

import zio.blocks.sql.query.*
import zio.blocks.sql.*

/**
 * How to Write Joins and Inspect the Generated SQL: Complete Example
 *
 * Builds the library, runs the many-to-one join, the one-to-many and left
 * joins, the filtered query, and prints the generated SQL for both dialects.
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.CompleteExample"
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

    // Many-to-one: each book with its first author
    val booksWithAuthors = SqlQuery.from(books).join(bookAuthor)

    transactor.connect {
      booksWithAuthors.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, author) =>
        println(s"${book.title} (${book.year}) by ${author.name}")
      }
    }

    // One-to-many: each author's books, grouped in Scala
    val authorsAndBooks = SqlQuery.from(authors).join(authorBooks)

    transactor.connect {
      val rows = authorsAndBooks.toFrag(SqlDialect.SQLite).query[(Author, Book)]
      rows.groupMap(_._1)(_._2).toList.sortBy(_._1.name).foreach { case (author, written) =>
        println(s"${author.name}: ${written.sortBy(_.year).map(_.title).mkString(", ")}")
      }
    }

    // Left join: keep authors without books, decoding the optional side as BookColumns
    val everyAuthor = SqlQuery.from(authors).leftJoin(authorBooks)

    transactor.connect {
      val rows = everyAuthor.toFrag(SqlDialect.SQLite).query[(Author, BookColumns)]
      rows.groupMap(_._1)(_._2.toBook).toList.sortBy(_._1.name).foreach { case (author, written) =>
        val titles = written.flatten.map(_.title)
        println(s"${author.name}: ${if (titles.isEmpty) "no books yet" else titles.mkString(", ")}")
      }
    }

    // Filter and sort
    val publishedAfter = 2010

    val recentBooks = SqlQuery
      .from(books)
      .join(bookAuthor)
      .where(sql"""t0."year" > $publishedAfter""")
      .orderBy("year", SortOrder.Desc)

    transactor.connect {
      recentBooks.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, author) =>
        println(s"${book.year}: ${book.title} by ${author.name}")
      }
    }

    // The generated SQL
    println(recentBooks.explain(SqlDialect.SQLite))
    println(recentBooks.explain(SqlDialect.PostgreSQL))
    println(everyAuthor.sql(SqlDialect.SQLite))
  }
}
