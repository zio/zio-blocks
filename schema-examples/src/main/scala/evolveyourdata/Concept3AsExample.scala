package evolveyourdata

import zio.blocks.schema._

/**
 * Evolve Your Data: Convert Both Ways with As
 *
 * Derives an `As[BookV1, BookV2]`, converts in both directions and checks the
 * round trip.
 *
 * Run with: cd schema-examples && sbt "runMain evolveyourdata.Concept3AsExample"
 */
object Concept3AsExample extends App {

  case class BookV1(title: String, author: String, year: Int, edition: Int)

  case class BookV2(title: String, author: String, year: Int, edition: Int, isbn: Option[String])

  val v1AndV2: As[BookV1, BookV2] = As.derived[BookV1, BookV2]

  val v2ToV1: As[BookV2, BookV1] = v1AndV2.reverse

  val programmingInScalaV1 = BookV1("Programming in Scala", "Martin Odersky", 2008, 1)

  println(v1AndV2.into(programmingInScalaV1))

  val programmingInScalaV2 = BookV2("Programming in Scala", "Martin Odersky", 2008, 1, Some("978-0981531601"))

  println(v2ToV1.into(programmingInScalaV2))

  println(v1AndV2.into(programmingInScalaV1).flatMap(v1AndV2.from))
}
