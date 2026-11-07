//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala
// Run from this folder: scala-cli run Step3TicketTableExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step3TicketTableExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — The Ticket Table and the
 * Search Form
 *
 * Creates the ticket table in a SQLite file, adds eight tickets and shows the
 * empty search form.
 */
object Step3TicketTableExample {
  def main(args: Array[String]): Unit = {
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    seedTickets(transactor)

    println(s"the empty form: ${TicketSearch()}")
    transactor.connect {
      println(s"tickets in the table: ${tickets.count}")
    }

    Files.deleteIfExists(dbFile)
  }
}
