//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using file Model.scala

package readandwritejsonlines

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters._

import zio.blocks.streams._

/**
 * How to Read and Write JSON Lines (NDJSON) with Streams — Read Lines From a
 * File
 *
 * Opens the sample sensor log as a stream of lines and prints each one. The
 * file is opened when the stream runs and closed after its last line.
 *
 * Run with: cd zio-blocks-examples && sbt "jsonLines/runMain
 * readandwritejsonlines.Step1ReadLinesExample"
 */
object Step1ReadLinesExample {

  def readLines(path: Path): Stream[Nothing, String] =
    Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
      Stream.fromIterator(reader.lines().iterator().asScala)
    )

  def main(args: Array[String]): Unit = {
    val dir  = Files.createTempDirectory("json-lines")
    val file = Sample.writeTo(dir)

    readLines(file).runForeach(line => println(line))

    Files.delete(file)
    Files.delete(dir)
  }
}
