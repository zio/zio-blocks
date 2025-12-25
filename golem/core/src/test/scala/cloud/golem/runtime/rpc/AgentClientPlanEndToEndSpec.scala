package cloud.golem.runtime.rpc

import cloud.golem.runtime.autowire.{AgentImplementation, WitValueBuilder}
import cloud.golem.runtime.plan.ClientMethodPlan
import cloud.golem.data.{DataType, DataValue}
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class AgentClientPlanEndToEndSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  trait AsyncEchoAgent {
    def echo(in: String): Future[String]
  }

  test("client plan loopback via AgentClientRuntime.resolve (Future-returning method)") {
    val impl = new AsyncEchoAgent {
      override def echo(in: String): Future[String] =
        Future.successful(s"hello $in")
    }

    AgentImplementation.register[AsyncEchoAgent]("e2e-client-async")(impl)

    val plan = cloud.golem.runtime.macros.AgentClientMacro.plan[AsyncEchoAgent]

    val rpc = new RpcInvoker {
      override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] =
        if (functionName != "echo") Left("unexpected method")
        else {
          val res = "hello world"
          WitValueBuilder.build(
            DataType.TupleType(List(DataType.StringType)),
            DataValue.TupleValue(List(DataValue.StringValue(res)))
          )
        }
      override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] = Left("not used")
      override def scheduleInvocation(
        datetime: js.Dynamic,
        functionName: String,
        params: js.Array[js.Dynamic]
      ): Either[String, Unit] =
        Left("not used")
    }

    val resolvedAgent =
      AgentClientRuntime.ResolvedAgent(plan, RemoteAgentClient("e2e-client-async", "fake-id", null, rpc))
    val echo = plan.methods.collectFirst { case m: ClientMethodPlan.Aux[AsyncEchoAgent, String, String] @unchecked =>
      m
    }.get

    resolvedAgent.call(echo, "world").map(out => assert(out == "hello world"))
  }
}
