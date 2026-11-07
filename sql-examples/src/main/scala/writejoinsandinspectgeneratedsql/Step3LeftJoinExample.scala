package writejoinsandinspectgeneratedsql

import zio.blocks.sql.query.*
import zio.blocks.sql.*

/**
 * How to Write Joins and Inspect the Generated SQL: Keep Authors Who Have No
 * Books
 *
 * Left join: shows the bogus Book decoded from NULL columns, and the
 * BookColumns fix that yields Option[Book].
 *
 * Run with: cd sql-examples && sbt "runMain
 * writejoinsandinspectgeneratedsql.Step3LeftJoinExample"
 */
object Step3LeftJoinExample {
  def main(args: Array[String]): Unit = {
    val transactor = Library.open()
    import Library.*

    println(SqlQuery.from(authors).leftJoin(authorBooks) == SqlQuery.from(authors).joinLeft(authorBooks))
    println(SqlQuery.from(authors).leftJoin(authorBooks) == SqlQuery.from(authors).join(authorBooks, JoinKind.Left))

    val everyAuthor = SqlQuery.from(authors).leftJoin(authorBooks)

    transactor.connect {
      everyAuthor.toFrag(SqlDialect.SQLite).query[(Author, Book)].foreach(println)
    }

    transactor.connect {
      val rows = everyAuthor.toFrag(SqlDialect.SQLite).query[(Author, BookColumns)]
      rows.groupMap(_._1)(_._2.toBook).toList.sortBy(_._1.name).foreach { case (author, written) =>
        val titles = written.flatten.map(_.title)
        println(s"${author.name}: ${if (titles.isEmpty) "no books yet" else titles.mkString(", ")}")
      }
    }
  }
}
