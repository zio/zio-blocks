package sendserversentevents

import zio.http._

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Rendering One Event
 *
 * Builds `ServerSentEvent` values and prints the exact text each one puts on
 * the wire, with every line break shown as `\n`, including a two-line payload,
 * then shows that an `id` containing a line break is rejected.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step1RenderEventExample"
 */
object Step1RenderEventExample {
  def show(event: ServerSentEvent[String]): Unit =
    println(event.render.replace("\n", "\\n"))

  def main(args: Array[String]): Unit = {
    show(ServerSentEvent("Zionomicon"))
    show(ServerSentEvent("Zionomicon", "arrival").id("1").retry(2000))
    show(arrival(1, books.head))
    try ServerSentEvent("Zionomicon").id("1\n2")
    catch { case error: IllegalArgumentException => println(s"rejected: ${error.getMessage}") }
  }
}
