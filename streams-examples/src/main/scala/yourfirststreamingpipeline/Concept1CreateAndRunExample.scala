package yourfirststreamingpipeline

import zio.blocks.streams._

/**
 * Your First Streaming Pipeline: Turn a Sales Log into a Report — Concept 1:
 * Create and Run a Stream
 *
 * Creates a stream of book titles and runs it with runCollect.
 *
 * Run with: cd streams-examples && sbt "runMain
 * yourfirststreamingpipeline.Concept1CreateAndRunExample"
 */
object Concept1CreateAndRunExample {
  def main(args: Array[String]): Unit = {
    val titles = Stream("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

    println("Titles stream described; nothing has run yet")
    println(titles.runCollect)
  }
}
