package example.integrationtests

import golem.runtime.annotations.agentImplementation
import zio._
import zio.http._

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class FetchAgentImpl(@unused private val key: String) extends FetchAgent {

  override def fetchFromPort(port: Int): Future[String] = {
    val effect =
      (for {
        response <- ZIO.serviceWithZIO[Client] { client =>
                      client.url(url"http://localhost").port(port).batched.get("/test")
                    }
        body <- response.body.asString
      } yield body).provide(ZClient.default)

    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.runToFuture(effect)
    }
  }
}
