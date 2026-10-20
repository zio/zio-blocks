package zio.blocks.async.wrapcallbackandfutureapis

import zio.blocks.async._

/**
 * Wrap a Callback That Reports Errors
 *
 * Demonstrates `Async.promise` with a `Completer`: `Completer#succeed` on the
 * success branch and `Completer#fail` on the error branch. The body is a Scala
 * 3 context function (`c ?=>`); on Scala 2 write `c =>`.
 *
 * Run with:
 * {{{
 *   cd async-examples && sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step4CallbackErrorsExample"
 * }}}
 */
object Step4CallbackErrorsExample {
  def stockOf(client: InventoryClient, title: String): Async[Int] =
    Async.promise[Int] { c ?=>
      client.stock(title) {
        case Right(level) => c.succeed(level)
        case Left(error)  => c.fail(error)
      }
      () // the Request handle is dropped here
    }

  def main(args: Array[String]): Unit = {
    val inventory = new InventoryClient(replyAfterMillis = 10)

    println(stockOf(inventory, "Zionomicon").block)

    println(stockOf(inventory, "Scala with Cats").either.block)
  }
}
