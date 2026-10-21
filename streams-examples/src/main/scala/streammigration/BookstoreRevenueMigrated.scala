package streammigration

import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import zio.blocks.streams.*

/**
 * The bookstore order pipeline from the "Coming from fs2, ZIO Streams or Ox"
 * guide, fully migrated to ZIO Blocks Streams. The sales data is fictional.
 *
 * The program runs from `main`, not from the body of an `object ... extends
 * App`, so blocking terminals never run inside a class initializer.
 *
 * Run it from the `streams-examples` directory:
 * {{{
 * sbt "runMain streammigration.BookstoreRevenueMigrated"
 * }}}
 */
object BookstoreRevenueMigrated {

  final case class OrderEvent(orderId: Long, title: String, copies: Int)

  // Fictional order log: one CSV line per order, and one malformed line
  private val orderLines: List[String] = List(
    "1,Zionomicon,2",
    "2,Programming in Scala,1",
    "3,Functional Programming in Scala,3",
    "not-an-order",
    "4,Zionomicon,1",
    "5,Programming in Scala,4"
  )

  private val listPrices: Map[String, BigDecimal] = Map(
    "Zionomicon"                      -> BigDecimal("49.99"),
    "Programming in Scala"            -> BigDecimal("59.95"),
    "Functional Programming in Scala" -> BigDecimal("44.99")
  )

  def parseOrder(line: String): Either[String, OrderEvent] =
    line.split(',') match {
      case Array(id, title, copies) =>
        (id.toLongOption, copies.toIntOption) match {
          case (Some(i), Some(c)) => Right(OrderEvent(i, title, c))
          case _                  => Left(s"malformed order: $line")
        }
      case _ => Left(s"malformed order: $line")
    }

  // A slow lookup, standing in for a call to a pricing service
  def priceOf(title: String): BigDecimal = {
    Thread.sleep(20)
    listPrices(title)
  }

  def addRevenue(totals: Map[String, BigDecimal], sale: (String, BigDecimal)): Map[String, BigDecimal] =
    totals.updated(sale._1, totals.getOrElse(sale._1, BigDecimal(0)) + sale._2)

  // Opens the log on every run and closes it when the run ends
  def orderLog(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(
      Files.newBufferedReader(path),
      (reader: BufferedReader) => { reader.close(); println("order log closed") }
    )(reader => Stream.fromIterator(reader.lines().iterator().asScala))

  def main(args: Array[String]): Unit = {
    val path = Files.write(Files.createTempFile("orders", ".csv"), orderLines.asJava)
    try {
      val parsed: Stream[Nothing, Either[String, OrderEvent]] = orderLog(path).map(parseOrder)

      // First run: report the malformed lines
      parsed.collect { case Left(error) => error }
        .runForeach(error => println(s"skipped: $error"))

      // Second run: the log is opened again, and orders are priced in parallel.
      // mapPar emits in completion order; the fold does not depend on it.
      val revenue: Either[Nothing, Map[String, BigDecimal]] =
        parsed.collect { case Right(order) => order }
          .mapPar(4)(order => order.title -> priceOf(order.title) * order.copies)
          .runFold(Map.empty[String, BigDecimal])(addRevenue)

      revenue.foreach { totals =>
        totals.toList.sortBy(_._1).foreach { case (title, total) => println(s"$title: $$$total") }
      }
    } finally {
      Files.deleteIfExists(path)
      ()
    }
  }
}
