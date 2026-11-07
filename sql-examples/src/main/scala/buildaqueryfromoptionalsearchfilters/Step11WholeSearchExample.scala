//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step11WholeSearchExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step11WholeSearchExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Run the Whole Search
 *
 * Joins the SELECT, the WHERE clause, the sort order and the page, runs the
 * support agent's search with its count, and then a hostile input. The shared
 * functions come from sections 3 to 11; the SQLite table is set up as in
 * section 3.
 */
object Step11WholeSearchExample {
  def searchQuery(search: TicketSearch): Frag =
    selectTickets ++ whereAnd(conditions(search)*) ++ orderBy(search.sortBy) ++ pageOf(search)

  def countQuery(search: TicketSearch): Frag =
    sql"SELECT COUNT(*) FROM ticket" ++ whereAnd(conditions(search)*)

  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    println("support agent's search:")
    describe(searchQuery(loginProblems))

    transactor.connect {
      println(s"matching tickets: ${countQuery(loginProblems).queryOne[Long]}")
      searchQuery(loginProblems).query[Ticket].foreach(println)
    }

    val hostile = TicketSearch(text = Some("'; DROP TABLE ticket; --"))

    println("hostile input:")
    describe(searchQuery(hostile))

    transactor.connect {
      print("hostile search finds: ")
      println(searchQuery(hostile).query[Ticket])
      println(s"tickets in the table: ${tickets.count}")
    }

    Files.deleteIfExists(dbFile)
  }
}
