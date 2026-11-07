//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step6LikeSearchExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step6LikeSearchExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Match Part of the Subject
 * with LIKE
 *
 * Searches for `50%` once with the raw pattern and once with the escaped
 * pattern. The shared functions come from sections 3 to 6; the SQLite table is
 * set up as in section 3.
 */
object Step6LikeSearchExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 3: table created, eight tickets inserted
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    seedTickets(transactor)

    transactor.connect {
      print("raw pattern matches: ")
      println(sql"SELECT subject FROM ticket WHERE subject LIKE ${"%50%%"}".query[String])
      print("escaped pattern matches: ")
      println(
        (Frag.literal("SELECT subject FROM ticket") ++ whereAnd(subjectContains(TicketSearch(text = Some("50%")))))
          .query[String]
      )
    }

    Files.deleteIfExists(dbFile)
  }
}
