//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step9SortByExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step9SortByExample"

package buildaqueryfromoptionalsearchfilters

import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Sort by an Allowed Column
 *
 * Turns the sort choice of a web request into a `SortBy`, with a default for
 * anything unknown, and prints the ORDER BY text of each choice. The shared
 * `orderBy` comes from section 9.
 */
object Step9SortByExample {
  def parseSortBy(param: String): SortBy =
    SortBy.values.find(_.toString == param).getOrElse(SortBy.Newest)

  def main(args: Array[String]): Unit = {
    println(s"MostUrgent parses to: ${parseSortBy("MostUrgent")}")
    println(s"an injection attempt parses to: ${parseSortBy("subject; DROP TABLE ticket")}")
    SortBy.values.foreach(sortBy => println(s"$sortBy:${orderBy(sortBy).sql(SqlDialect.SQLite)}"))
  }
}
