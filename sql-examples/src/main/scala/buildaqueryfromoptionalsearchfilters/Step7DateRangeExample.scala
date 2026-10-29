//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step7DateRangeExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step7DateRangeExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import java.time.LocalDate
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Filter a Date Range
 *
 * Asks for the tickets opened in the first half of October, and then for those
 * opened from October 20 on. The shared functions come from sections 3 to 7;
 * the SQLite table is set up as in section 3.
 */
object Step7DateRangeExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    transactor.connect {
      val firstHalf =
        TicketSearch(openedFrom = Some(LocalDate.of(2026, 10, 1)), openedTo = Some(LocalDate.of(2026, 10, 15)))
      val fromOct20 = TicketSearch(openedFrom = Some(LocalDate.of(2026, 10, 20)))

      print("first half of October: ")
      println(
        (Frag.literal("SELECT id FROM ticket") ++ whereAnd(openedOnOrAfter(firstHalf), openedOnOrBefore(firstHalf)))
          .query[Long]
      )
      print("from October 20: ")
      println(
        (Frag.literal("SELECT id FROM ticket") ++ whereAnd(openedOnOrAfter(fromOct20), openedOnOrBefore(fromOct20)))
          .query[Long]
      )
    }

    Files.deleteIfExists(dbFile)
  }
}
