//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep org.typelevel::cats-effect:3.7.1
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file OrderArchive.scala
//> using file Opened.scala
//> using file CatsResources.scala
// Run from this folder:  scala-cli run Step3ResourcesCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step3ResourcesCats"

package effectintegration

import java.nio.file.Files
import scala.concurrent.duration._
import cats.effect.{IO, IOApp, Resource}
import cats.effect.std.Dispatcher
import zio.blocks.scope.Scope

object Step3ResourcesCats extends IOApp.Simple {
  val order = Order(1, "Ada", 4200)

  val run =
    Dispatcher.parallel[IO].use { implicit dispatcher =>
      for {
        path       <- IO.blocking(Files.createTempFile("archive", ".jsonl"))
        archive     = CatsResources.fromResource(OrderArchive.resource(path))
        _          <- IO.println("-- success")
        _          <- archive.use(a => IO.blocking(a.append(order)))
        _          <- IO.println("-- failure")
        failed     <- archive.use(_ => IO.raiseError[Unit](new Exception("payment declined"))).attempt
        _          <- IO.println(s"result: ${failed.left.map(_.getMessage)}")
        _          <- IO.println("-- cancellation")
        fiber      <- archive.use(_ => IO.never[Unit]).start
        _          <- IO.sleep(100.millis)
        _          <- fiber.cancel
        _          <- IO.println("-- a cats-effect-managed archive used from ZIO Blocks code")
        catsManaged = CatsResources.toResource(Resource.fromAutoCloseable(IO.blocking(new OrderArchive(path))))
        _          <- IO.blocking {
               Scope.global.scoped { scope =>
                 import scope._
                 val a = allocate(catsManaged)
                 $(a)(_.append(order))
               }
             }
        _ <- IO.blocking(Files.delete(path))
      } yield ()
    }
}
