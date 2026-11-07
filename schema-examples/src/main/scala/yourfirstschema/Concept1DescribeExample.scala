package yourfirstschema

import zio.blocks.schema._

/**
 * Your First Schema: Round-Trip a Type Through JSON — Concept 1: Describe a
 * Type with a Schema
 *
 * Derives a Schema for a flat Book case class and prints the record the
 * compiler derived.
 *
 * Run with: cd schema-examples && sbt "runMain
 * yourfirstschema.Concept1DescribeExample"
 */
object Concept1DescribeExample extends App {

  case class Book(title: String, year: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  println(Book.schema)
}
