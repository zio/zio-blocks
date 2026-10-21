package writejoinsandinspectgeneratedsql

import zio.blocks.sql.*
import zio.blocks.sql.query.*

/**
 * How to Write Joins and Inspect the Generated SQL: See the SQL Before It Runs
 *
 * Prints explain for SQLite and PostgreSQL, and the exact SQL string toFrag
 * sends to the driver.
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.Step5ExplainExample"
 */
object Step5ExplainExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

    val publishedAfter = 2010

    val recentBooks = SqlQuery
      .from(books)
      .join(bookAuthor)
      .where(sql"""t0."year" > $publishedAfter""")
      .orderBy("year", SortOrder.Desc)

    println(recentBooks.explain(SqlDialect.SQLite))
    println(recentBooks.explain(SqlDialect.PostgreSQL))

    val everyAuthor = SqlQuery.from(authors).leftJoin(authorBooks)

    println(everyAuthor.sql(SqlDialect.SQLite))
  }
}
