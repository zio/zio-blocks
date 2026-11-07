//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Helpdesk.scala SearchConditions.scala
// Run from this folder: scala-cli run Step4ConditionExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.Step4ConditionExample"

package buildaqueryfromoptionalsearchfilters

/**
 * How to Build a Query from Optional Search Filters — Turn a Filled-In Field
 * into a Condition
 *
 * Turns the customer field into an optional condition and prints its SQL and
 * parameters. The shared functions come from sections 3 and 4.
 */
object Step4ConditionExample {
  def main(args: Array[String]): Unit = {
    println("customer Acme:")
    customerIs(TicketSearch(customer = Some("Acme"))).foreach(describe)
    println(s"customer blank: ${customerIs(TicketSearch(customer = Some("  ")))}")
    println(s"customer missing: ${customerIs(TicketSearch())}")
  }
}
