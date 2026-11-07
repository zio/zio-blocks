//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-data-migration:0.0.56
//> using file Customers.scala
// Run from this folder:    scala-cli run Step1DescribeRowsExample.scala
// Or from sql-examples:    sbt "runMain migratetablerowsonline.Step1DescribeRowsExample"

package migratetablerowsonline

/**
 * How to Migrate Table Rows to a New Version Online — Describe the Old and New
 * Rows
 *
 * Applies the name-splitting migration to two customers.
 */
object Step1DescribeRowsExample {
  def main(args: Array[String]): Unit = {
    println(splitName(CustomerV1(1, "Ada Lovelace", "ada@example.com")))
    println(splitName(CustomerV1(2, "Grace Brewster Hopper", "grace@example.com")))
  }
}
