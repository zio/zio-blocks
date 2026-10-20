package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

/**
 * Bring a Java `CompletionStage` Into `Async`
 *
 * Demonstrates `Async.fromCompletionStage`, which removes the
 * `CompletionException` wrapper so a failed stage arrives as the exception the
 * producer threw.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step3CompletionStageExample"
 * }}}
 */
object Step3CompletionStageExample {
  def main(args: Array[String]): Unit = {
    val days: Int = Async.fromCompletionStage(ShippingQuotes.estimateDays("Zionomicon")).block
    println(s"Zionomicon ships in $days days")

    println(Async.fromCompletionStage(ShippingQuotes.estimateDays("")).either.block)
  }
}
