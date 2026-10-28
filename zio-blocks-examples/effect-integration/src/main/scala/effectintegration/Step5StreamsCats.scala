//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep org.typelevel::cats-effect:3.7.1
//> using dep co.fs2::fs2-core:3.14.0
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file BlockingPull.scala
//> using file CatsStreams.scala
//> using file CatsResources.scala
//> using file Opened.scala
// Run from this folder:  scala-cli run Step5StreamsCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step5StreamsCats"

package effectintegration

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
import cats.effect.std.Dispatcher
import zio.blocks.streams.Stream

object Step5StreamsCats extends IOApp.Simple {
  val placed  = """{"orderId":1,"status":"placed"}"""
  val paid    = """{"orderId":1,"status":"paid"}"""
  val shipped = """{"orderId":1,"status":"shipped"}"""

  val run =
    Dispatcher.parallel[IO].use { implicit dispatcher =>
      for {
        events <- IO.blocking(OrderEvents.write(placed, paid, shipped))
        _      <- IO.println("-- whole file")
        all    <- CatsStreams.toFs2(OrderEvents.fromFile(events)).compile.toList
        _      <- IO.println(s"events: ${all.map(_.status)}")
        _      <- IO.println("-- first event only")
        first  <- CatsStreams.toFs2(OrderEvents.fromFile(events)).take(1).compile.toList
        _      <- IO.println(s"events: ${first.map(_.status)}")
        _      <- IO.println("-- a malformed line")
        broken <- IO.blocking(OrderEvents.write(placed, "not json", shipped))
        failed <- CatsStreams.toFs2(OrderEvents.fromFile(broken)).compile.toList.attempt
        _      <- IO.println(s"result: ${failed.left.map(_.getMessage)}")
        _      <- IO.println("-- cancellation")
        endless = Stream.fromIterator(Iterator.from(1)).ensuring(println("endless stream closed"))
        fiber  <- CatsStreams.toFs2(endless).compile.drain.start
        _      <- IO.sleep(100.millis)
        _      <- fiber.cancel
        _      <- IO.println("-- back-pressure, ZIO Blocks -> fs2")
        counted = Stream.fromIterable(1 to 1000).map { n => println(s"zio blocks produced $n"); n }
        taken  <- CatsStreams.toFs2(counted, chunkSize = 2).take(3).compile.toList
        _      <- IO.println(s"fs2 took: $taken")
        _      <- IO.println("-- back-pressure, fs2 -> ZIO Blocks")
        ticks   = fs2.Stream
                  .iterate(1)(_ + 1)
                  .covary[IO]
                  .evalTap(n => IO.println(s"fs2 produced $n"))
                  .onFinalize(IO.println("fs2 finalized"))
        back <- IO.blocking(CatsStreams.fromFs2(ticks, buffer = 2).take(3).runCollect)
        _    <- IO.println(s"zio blocks took: $back")
      } yield ()
    }
}
