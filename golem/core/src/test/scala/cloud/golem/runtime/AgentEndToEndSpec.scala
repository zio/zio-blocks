package cloud.golem.runtime

import cloud.golem.runtime.autowire.{AgentImplementation, HostPayload, MethodBinding}
import cloud.golem.runtime.Sum
import cloud.golem.runtime.util.FutureInterop
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class AgentEndToEndSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  trait EchoAgent {
    def echo(in: String): String
    def add(in: Sum): Int
  }

  private def liftEither[A](e: Either[String, A]): Future[A] =
    e.fold(err => Future.failed(js.JavaScriptException(err)), Future.successful)

  private def binding(
    name: String,
    defn: cloud.golem.runtime.autowire.AgentDefinition[EchoAgent]
  ): MethodBinding[EchoAgent] =
    defn.methodMetadata.find(_.metadata.name == name).getOrElse(sys.error(s"binding not found: $name"))

  test("echo roundtrips through binding encode/decode") {
    val impl = new EchoAgent {
      override def echo(in: String): String = s"hello $in"
      override def add(in: Sum): Int        = in.a + in.b
    }
    val defn     = AgentImplementation.register[EchoAgent]("e2e-echo")(impl)
    val instance = impl

    val b = binding("echo", defn)

    for {
      payload <- liftEither(HostPayload.encode[String]("world"))
      raw     <- FutureInterop.fromPromise(b.invoke(instance, payload))
      decoded <- liftEither(HostPayload.decode[String](raw))
    } yield assert(decoded == "hello world")
  }

  test("case class payload roundtrips through binding encode/decode") {
    val impl = new EchoAgent {
      override def echo(in: String): String = in
      override def add(in: Sum): Int        = in.a + in.b
    }
    val defn     = AgentImplementation.register[EchoAgent]("e2e-echo-2")(impl)
    val instance = impl

    val b = binding("add", defn)

    for {
      payload <- liftEither(HostPayload.encode[Sum](Sum(2, 3)))
      raw     <- FutureInterop.fromPromise(b.invoke(instance, payload))
      decoded <- liftEither(HostPayload.decode[Int](raw))
    } yield assert(decoded == 5)
  }

}
