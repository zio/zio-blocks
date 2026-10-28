//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep dev.zio::zio:2.1.27
//> using dep dev.zio::zio-streams:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file BlockingPull.scala
//> using file ZioStreams.scala
//> using file ZioResources.scala
//> using file Opened.scala
// Run from this folder:  scala-cli run Step5StreamsZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step5StreamsZio"

package effectintegration

import zio._
import zio.stream.ZStream
import zio.blocks.streams.Stream

object Step5StreamsZio extends ZIOAppDefault {
  val placed  = """{"orderId":1,"status":"placed"}"""
  val paid    = """{"orderId":1,"status":"paid"}"""
  val shipped = """{"orderId":1,"status":"shipped"}"""

  val run =
    ZIO.runtime[Any].flatMap { implicit runtime =>
      for {
        events <- ZIO.attemptBlocking(OrderEvents.write(placed, paid, shipped))
        _      <- Console.printLine("-- whole file")
        all    <- ZioStreams.toZStream(OrderEvents.fromFile(events)).runCollect
        _      <- Console.printLine(s"events: ${all.map(_.status)}")
        _      <- Console.printLine("-- first event only")
        first  <- ZioStreams.toZStream(OrderEvents.fromFile(events)).take(1).runCollect
        _      <- Console.printLine(s"events: ${first.map(_.status)}")
        _      <- Console.printLine("-- a malformed line")
        broken <- ZIO.attemptBlocking(OrderEvents.write(placed, "not json", shipped))
        failed <- ZioStreams.toZStream(OrderEvents.fromFile(broken)).runCollect.either
        _      <- Console.printLine(s"result: ${failed.left.map(_.getMessage)}")
        _      <- Console.printLine("-- interruption")
        endless = Stream.fromIterator(Iterator.from(1)).ensuring(println("endless stream closed"))
        fiber  <- ZioStreams.toZStream(endless).runDrain.fork
        _      <- ZIO.sleep(100.millis)
        _      <- fiber.interrupt
        _      <- Console.printLine("-- back-pressure, ZIO Blocks -> ZStream")
        counted = Stream.fromIterable(1 to 1000).map { n => println(s"zio blocks produced $n"); n }
        taken  <- ZioStreams.toZStream(counted, chunkSize = 2).take(3).runCollect
        _      <- Console.printLine(s"zstream took: $taken")
        _      <- Console.printLine("-- back-pressure, ZStream -> ZIO Blocks")
        ticks   = ZStream
                  .iterate(1)(_ + 1)
                  .tap(n => Console.printLine(s"zstream produced $n"))
                  .ensuring(Console.printLine("zstream finalized").orDie)
        back <- ZIO.attemptBlocking(ZioStreams.fromZStream(ticks).take(3).runCollect)
        _    <- Console.printLine(s"zio blocks took: $back")
      } yield ()
    }
}
