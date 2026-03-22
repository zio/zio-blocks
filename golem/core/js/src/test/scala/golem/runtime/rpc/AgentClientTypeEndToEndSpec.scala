package golem.runtime.rpc

import golem.host.js._
import golem.BaseAgent
import golem.runtime.agenttype.AgentMethod
import golem.runtime.annotations.{DurabilityMode, agentDefinition}
import golem.runtime.autowire.AgentImplementation
import golem.runtime.rpc.RpcValueCodec
import zio._
import zio.test._

import scala.annotation.unused
import scala.concurrent.Future
import scala.scalajs.js

object AgentClientTypeEndToEndSpec extends ZIOSpecDefault {

  @agentDefinition("E2eClientAsync", mode = DurabilityMode.Durable)
  trait AsyncEchoAgent extends BaseAgent[Unit] {
    def echo(in: String): Future[String]
  }

  def spec = suite("AgentClientTypeEndToEndSpec")(
    test("client type loopback via AgentClientRuntime.resolve (Future-returning method)") {
      ZIO.fromFuture { implicit ec =>
        val impl = new AsyncEchoAgent {
          override def echo(in: String): Future[String] =
            Future.successful(s"hello $in")
        }

        AgentImplementation.register[AsyncEchoAgent]("e2e-client-async")(impl)

        val agentType = golem.runtime.macros.AgentClientMacro.agentType[AsyncEchoAgent]

        val rpc = new RpcInvoker {
          override def invokeAndAwait(functionName: String, input: JsDataValue): Either[String, JsDataValue] =
            if (functionName != "E2eClientAsync.{echo}") Left(s"unexpected method: $functionName")
            else {
              import golem.GolemSchema._
              val witValue = RpcValueCodec.encodeValue("hello world")
              witValue.map { wv =>
                JsDataValue.tuple(js.Array(JsElementValue.componentModel(wv)))
              }
            }

          override def invoke(functionName: String, input: JsDataValue): Either[String, Unit] =
            Left("not used")

          override def scheduleInvocation(
            @unused datetime: golem.Datetime,
            @unused functionName: String,
            @unused input: JsDataValue
          ): Either[String, Unit] =
            Left("not used")
        }

        val resolvedAgent =
          AgentClientRuntime.ResolvedAgent(
            agentType.asInstanceOf[golem.runtime.agenttype.AgentType[AsyncEchoAgent, Any]],
            RemoteAgentClient("e2e-client-async", "fake-id", null, rpc)
          )

        val echo = agentType.methods.collectFirst { case m: AgentMethod[AsyncEchoAgent, String, String] @unchecked =>
          m
        }.get

        resolvedAgent.call(echo, "world")
      }.map(out => assertTrue(out == "hello world"))
    }
  )
}
