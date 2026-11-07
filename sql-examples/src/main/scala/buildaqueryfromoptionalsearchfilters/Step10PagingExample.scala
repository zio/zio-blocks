//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step10PagingExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step10PagingExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Return One Page of
 * Results
 *
 * Counts all tickets and asks for the second page of three, newest first. The
 * shared functions come from sections 3 to 10; the SQLite table is set up as in
 * section 3.
 */
object Step10PagingExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    transactor.connect {
      val secondPage = TicketSearch(sortBy = SortBy.Newest, page = 2, pageSize = 3)
      val where      = whereAnd()
      print("matching tickets: ")
      println((sql"SELECT COUNT(*) FROM ticket" ++ where).queryOne[Long])
      print("second page of three, newest first: ")
      println(
        (Frag.literal("SELECT id FROM ticket") ++ where ++ orderBy(secondPage.sortBy) ++ pageOf(secondPage)).query[Long]
      )
    }

    Files.deleteIfExists(dbFile)
  }
}
