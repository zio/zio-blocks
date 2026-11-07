//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
// Run from this folder:  scala-cli run Step1PureZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step1PureZio"

package effectintegration

import zio._
import zio.blocks.schema._

object Step1PureZio extends ZIOAppDefault {
  def parseOrder(body: String): IO[SchemaError, Order] = ZIO.fromEither(body.fromJson[Order])

  def render(order: Order): UIO[String] = ZIO.succeed(order.toJsonString)

  val run =
    for {
      order  <- parseOrder("""{"id":1,"customer":"Ada","totalCents":4200}""")
      _      <- Console.printLine(s"parsed: $order")
      json   <- render(order.copy(totalCents = 4700))
      _      <- Console.printLine(s"rendered: $json")
      result <- parseOrder("""{"id":2,"customer":"Grace"}""").either
      _      <- Console.printLine(s"missing field: ${result.left.map(_.getMessage)}")
    } yield ()
}
