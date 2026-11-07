package sendserversentevents

import Bookshop._

/**
 * How to Send Server-Sent Events with http-model — Returning a Stream of Events
 *
 * Turns the six arrivals into a `text/event-stream` `Response` and reads it
 * back in the same program, without a server: the status, the headers, and the
 * body text.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * sendserversentevents.Step2EventResponseExample"
 */
object Step2EventResponseExample {
  def main(args: Array[String]): Unit = {
    val response = eventResponse(paced(arrivalsAfter(0).take(2), intervalMillis = 0))
    println(s"status: ${response.status.code}")
    response.headers.toList.foreach { case (name, value) => println(s"$name: $value") }
    println(s"known body length: ${response.body.length}")
    print(response.body.asString())
  }
}
