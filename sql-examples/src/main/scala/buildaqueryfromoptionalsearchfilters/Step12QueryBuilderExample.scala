//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step12QueryBuilderExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step12QueryBuilderExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.query.{SortOrder, SqlQuery}
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — The Same Search with the
 * Query Builder
 *
 * Builds the support agent's search with `SqlQuery`, prints its SQL and runs
 * it. The shared functions come from sections 3 to 11; the SQLite table is set
 * up as in section 3.
 */
object Step12QueryBuilderExample {
  def searchWithBuilder(search: TicketSearch): SqlQuery[Ticket] = {
    val filtered = conditions(search).flatten.foldLeft(SqlQuery.from(ticketTable)) { (query, condition) =>
      query.where(sql"($condition)")
    }
    val sorted = search.sortBy match {
      case SortBy.Newest     => filtered.orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
      case SortBy.Oldest     => filtered.orderBy("opened_on").orderBy("id")
      case SortBy.MostUrgent =>
        filtered.orderBy("priority").orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
    }
    sorted.limit(pageSizeOf(search)).offset(offsetOf(search))
  }

  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    val builderQuery = searchWithBuilder(loginProblems)

    println(builderQuery.explain(SqlDialect.SQLite))

    println("builder results:")
    transactor.connect {
      builderQuery.toFrag(SqlDialect.SQLite).query[Ticket].foreach(println)
    }

    Files.deleteIfExists(dbFile)
  }
}
