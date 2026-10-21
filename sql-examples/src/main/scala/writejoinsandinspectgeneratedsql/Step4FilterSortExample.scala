package writejoinsandinspectgeneratedsql

import zio.blocks.sql.*
import zio.blocks.sql.query.*

/**
 * How to Write Joins and Inspect the Generated SQL: Filter and Sort a Join
 *
 * Adds where conditions using table aliases and sorts with orderBy.
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.Step4FilterSortExample"
 */
object Step4FilterSortExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

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

    val author = "Martin Odersky"

    val booksByOdersky = SqlQuery
      .from(books)
      .join(bookAuthor)
      .where(sql"""t1."name" = $author""")
      .where(sql"""t0."year" < ${2020}""")

    transactor.connect {
      booksByOdersky.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, _) =>
        println(book.title)
      }
    }
  }
}
