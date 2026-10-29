//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step5JoinWithAndExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step5JoinWithAndExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Join the Conditions with
 * AND
 *
 * Puts the WHERE clause after the SELECT, with no condition and with a
 * customer, and runs the second query. The shared functions come from sections
 * 3 to 5; the SQLite table is set up as in section 3.
 */
object Step5JoinWithAndExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    println("no conditions:")
    describe(selectTickets ++ whereAnd())
    println("customer Acme:")
    describe(selectTickets ++ whereAnd(customerIs(TicketSearch(customer = Some("Acme")))))

    println("Acme's tickets:")
    transactor.connect {
      (selectTickets ++ whereAnd(customerIs(TicketSearch(customer = Some("Acme"))))).query[Ticket].foreach(println)
    }

    Files.deleteIfExists(dbFile)
  }
}
