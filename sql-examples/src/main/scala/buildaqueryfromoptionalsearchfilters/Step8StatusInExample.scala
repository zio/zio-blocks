//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step8StatusInExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step8StatusInExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Match Any of Several
 * Statuses with IN
 *
 * Ticks the `open` and `pending` boxes, prints the condition and runs it. The
 * shared functions come from sections 3 to 8; the SQLite table is set up as in
 * section 3.
 */
object Step8StatusInExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    println("a List interpolated as one value:")
    describe(sql"status IN (${List("open", "pending")})")

    transactor.connect {
      print("matching ids: ")
      println(
        (Frag.literal("SELECT id FROM ticket") ++ whereAnd(Some(sql"status IN (${List("open", "pending")})")))
          .query[Long]
      )
    }

    val openOrPending = TicketSearch(statuses = Set("pending", "open"))

    println("open or pending:")
    statusIn(openOrPending).foreach(describe)

    transactor.connect {
      print("matching ids: ")
      println((Frag.literal("SELECT id FROM ticket") ++ whereAnd(statusIn(openOrPending))).query[Long])
    }

    Files.deleteIfExists(dbFile)
  }
}
