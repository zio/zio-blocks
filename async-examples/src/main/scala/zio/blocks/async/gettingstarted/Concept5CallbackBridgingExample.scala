//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-async:0.0.56
// Run from this folder:     scala-cli run Concept5CallbackBridgingExample.scala
// Or from async-examples:   sbt "runMain zio.blocks.async.gettingstarted.Concept5CallbackBridgingExample"

package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Getting Started with Async — Callback Bridging: `Async.promise` and
 * `Completer`
 *
 * The Scala 3 version of the section's code: `summon[Completer[String]]`
 * retrieves the completer, which a background thread completes after 10 ms.
 */
object Concept5CallbackBridgingExample {
  def main(args: Array[String]): Unit = {
    val result: String = Async
      .promise[String] {
        val c = summon[Completer[String]]
        new Thread {
          override def run(): Unit = {
            Thread.sleep(10)
            c.succeed("hello from callback")
          }
        }.start()
      }
      .block
    println(s"Promise resolved: $result")
  }
}
