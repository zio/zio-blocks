package writejoinsandinspectgeneratedsql

import zio.blocks.sql.*
import zio.blocks.sql.query.*

/**
 * How to Write Joins and Inspect the Generated SQL: List Each Author's Books
 *
 * Starts from authors, joins with Rel.oneToMany and groups the rows by author
 * in Scala.
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.Step2OneToManyExample"
 */
object Step2OneToManyExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

    val authorsAndBooks = SqlQuery.from(authors).join(authorBooks)

    transactor.connect {
      val rows = authorsAndBooks.toFrag(SqlDialect.SQLite).query[(Author, Book)]
      rows.groupMap(_._1)(_._2).toList.sortBy(_._1.name).foreach { case (author, written) =>
        println(s"${author.name}: ${written.sortBy(_.year).map(_.title).mkString(", ")}")
      }
    }
  }
}
