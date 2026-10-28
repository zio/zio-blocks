//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep org.typelevel::cats-effect:3.7.1
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
// Run from this folder:  scala-cli run Step1PureCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step1PureCats"

package effectintegration

import cats.effect.{IO, IOApp}
import zio.blocks.schema._

object Step1PureCats extends IOApp.Simple {
  def parseOrder(body: String): IO[Order] = IO.fromEither(body.fromJson[Order])

  def render(order: Order): IO[String] = IO.pure(order.toJsonString)

  val run =
    for {
      order  <- parseOrder("""{"id":1,"customer":"Ada","totalCents":4200}""")
      _      <- IO.println(s"parsed: $order")
      json   <- render(order.copy(totalCents = 4700))
      _      <- IO.println(s"rendered: $json")
      result <- parseOrder("""{"id":2,"customer":"Grace"}""").attempt
      _      <- IO.println(s"missing field: ${result.left.map(_.getMessage)}")
    } yield ()
}
