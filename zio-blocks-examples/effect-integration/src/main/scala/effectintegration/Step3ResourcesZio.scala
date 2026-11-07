//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep dev.zio::zio:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file OrderArchive.scala
//> using file Opened.scala
//> using file ZioResources.scala
// Run from this folder:  scala-cli run Step3ResourcesZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step3ResourcesZio"

package effectintegration

import java.nio.file.Files
import zio._
import zio.blocks.scope.{Scope => BlocksScope}

object Step3ResourcesZio extends ZIOAppDefault {
  val order = Order(1, "Ada", 4200)

  val run =
    ZIO.runtime[Any].flatMap { implicit runtime =>
      for {
        path   <- ZIO.attemptBlocking(Files.createTempFile("archive", ".jsonl"))
        archive = ZioResources.fromResource(OrderArchive.resource(path))
        _      <- Console.printLine("-- success, through a ZLayer")
        _      <- ZIO
               .serviceWithZIO[OrderArchive](a => ZIO.attemptBlocking(a.append(order)))
               .provide(ZLayer.scoped(archive))
        _         <- Console.printLine("-- failure")
        failed    <- ZIO.scoped(archive *> ZIO.fail(new Exception("payment declined"))).either
        _         <- Console.printLine(s"result: ${failed.left.map(_.getMessage)}")
        _         <- Console.printLine("-- interruption")
        fiber     <- ZIO.scoped(archive *> ZIO.never).fork
        _         <- ZIO.sleep(100.millis)
        _         <- fiber.interrupt
        _         <- Console.printLine("-- a ZIO-managed archive used from ZIO Blocks code")
        zioManaged = ZioResources.toResource(ZIO.fromAutoCloseable(ZIO.attempt(new OrderArchive(path))))
        _         <- ZIO.attemptBlocking {
               BlocksScope.global.scoped { scope =>
                 import scope._
                 val a = allocate(zioManaged)
                 $(a)(_.append(order))
               }
             }
        _ <- ZIO.attemptBlocking(Files.delete(path))
      } yield ()
    }
}
