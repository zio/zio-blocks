package golem.runtime.rpc

import golem.runtime.agenttype.AgentType
import zio._
import zio.test._

import golem.host.js._

import scala.concurrent.Future
import scala.scalajs.js

object AgentClientBindListDoubleSpec extends ZIOSpecDefault {

  def spec = suite("AgentClientBindListDoubleSpec")(
    test("AgentClient.bind supports Unit method with List[Double] param (no missing JS function)") {
      ZIO.fromFuture { implicit ec =>
        val agentType =
          AgentClient.agentType[BindListDoubleWorkflow].asInstanceOf[AgentType[BindListDoubleWorkflow, Unit]]

        final class RecordingInvoker extends RpcInvoker {
          var triggered: Boolean = false

          override def invokeAndAwait(functionName: String, input: JsDataValue): Either[String, JsDataValue] =
            Left("not used")

          override def invoke(functionName: String, input: JsDataValue): Either[String, Unit] = {
            triggered = true
            Right(())
          }

          override def scheduleInvocation(
            datetime: golem.Datetime,
            functionName: String,
            input: JsDataValue
          ): Either[String, Unit] =
            Left("not used")
        }

        val invoker = new RecordingInvoker
        val remote  = RemoteAgentClient(agentType.typeName, "agent-1", null, invoker)

        val resolved =
          AgentClientRuntime.ResolvedAgent(
            agentType.asInstanceOf[AgentType[BindListDoubleWorkflow, Any]],
            remote
          )

        val client = AgentClient.bind[BindListDoubleWorkflow](resolved)

        client.finished(List(1.0, 2.0))

        Future.successful(invoker.triggered)
      }.map(triggered => assertTrue(triggered))
    }
  )
}
