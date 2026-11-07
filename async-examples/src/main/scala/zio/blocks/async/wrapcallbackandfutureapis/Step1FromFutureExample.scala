package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Bring a Scala `Future` Into `Async`
 *
 * Demonstrates `Async.fromFuture` on a successful and a failed `Future`,
 * observing the failure with `Async#either` and recovering with
 * `Async#catchAll`.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step1FromFutureExample"
 * }}}
 */
object Step1FromFutureExample {
  def main(args: Array[String]): Unit = {
    val price: BigDecimal = Async.fromFuture(PriceService.lookup("Zionomicon")).block
    println(s"Zionomicon costs $price")

    println(Async.fromFuture(PriceService.lookup("Scala with Cats")).either.block)

    val fallbackPrice: BigDecimal =
      Async
        .fromFuture(PriceService.lookup("Scala with Cats"))
        .catchAll(_ => Async.succeed(BigDecimal(0)))
        .block
    println(s"Fallback price: $fallbackPrice")
  }
}
