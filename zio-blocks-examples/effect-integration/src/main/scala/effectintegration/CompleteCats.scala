//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using dep org.typelevel::cats-effect:3.7.1
//> using dep co.fs2::fs2-core:3.14.0
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file OrderArchive.scala
//> using file PaymentGateway.scala
//> using file Cancellable.scala
//> using file Opened.scala
//> using file CatsResources.scala
//> using file CatsAsync.scala
//> using file BlockingPull.scala
//> using file CatsStreams.scala
// Run from this folder:  scala-cli run CompleteCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.CompleteCats"

package effectintegration

import java.nio.file.Files
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import zio.blocks.schema._
import zio.blocks.sql._

object CompleteCats extends IOApp.Simple {
  val gateway = new PaymentGateway(latencyMillis = 50)

  val requests = List(
    """{"id":1,"customer":"Ada","totalCents":4200}""",
    """{"id":2,"customer":"Grace","totalCents":1999}""",
    """{"id":3,"customer":"Linus"}"""
  )

  def placeOrder(transactor: JdbcTransactor, archive: OrderArchive)(body: String): IO[String] =
    for {
      order <- IO.fromEither(body.fromJson[Order])
      auth  <- CatsAsync
                .fromAsync(gateway.authorize(order))
                .timeoutTo(1.second, IO.raiseError(new TimeoutException("payment timed out")))
      _ <- IO.blocking(transactor.transact(OrdersDb.insert(order)))
      _ <- IO.blocking(archive.append(order))
    } yield s"order ${order.id} placed ($auth)"

  val resources: Resource[IO, (JdbcTransactor, OrderArchive)] =
    for {
      transactor <- Resource.eval(IO.blocking(OrdersDb.open()))
      path       <- Resource.eval(IO.blocking(Files.createTempFile("archive", ".jsonl")))
      archive    <- CatsResources.fromResource(OrderArchive.resource(path))
    } yield (transactor, archive)

  val run =
    resources.use { case (transactor, archive) =>
      for {
        _ <- requests.traverse_ { body =>
               placeOrder(transactor, archive)(body).attempt.flatMap {
                 case Left(error)   => IO.println(s"rejected: ${error.getMessage}")
                 case Right(placed) => IO.println(placed)
               }
             }
        revenue <- IO.blocking(transactor.connect(OrdersDb.totals().runFold(0L)(_ + _))).rethrow
        _       <- IO.println(s"revenue: $revenue cents")
        events  <- IO.blocking(
                    OrderEvents.write(
                      """{"orderId":1,"status":"shipped"}""",
                      """{"orderId":2,"status":"paid"}""",
                      """{"orderId":2,"status":"shipped"}"""
                    )
                  )
        _ <- CatsStreams
               .toFs2(OrderEvents.fromFile(events))
               .filter(_.status == "shipped")
               .evalMap(event => IO.println(s"shipped: order ${event.orderId}"))
               .compile
               .drain
      } yield ()
    }
}
