package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

import java.util.concurrent.CompletableFuture
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Hand an `Async` Back to Future-Based Code
 *
 * Demonstrates `Async#toFuture` for Scala callers and
 * `Async#toCompletableFuture` for Java callers.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step2ToFutureExample"
 * }}}
 */
object Step2ToFutureExample {
  def main(args: Array[String]): Unit = {
    val forScala: Future[String] =
      Async
        .fromFuture(PriceService.lookup("Programming in Scala"))
        .map(p => s"Programming in Scala costs $p")
        .toFuture

    println(Await.result(forScala, 5.seconds))

    val forJava: CompletableFuture[BigDecimal] =
      Async.fromFuture(PriceService.lookup("Functional Programming in Scala")).toCompletableFuture

    println(forJava.get())
  }
}
