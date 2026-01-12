package golem.examples.minimal

import golem.runtime.annotations.agentImplementation
import golem.runtime.rpc.{AgentClient, AgentClientRuntime}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

@agentImplementation()
final class WorkerImpl(private val shardName: String, private val shardIndex: Int) extends Worker {
  override def reverse(input: String): Future[String] =
    Future.successful(s"$shardName:$shardIndex:" + input.reverse)

  override def handle(payload: TypedPayload): Future[TypedReply] =
    Future.successful(
      TypedReply(
        shardName = shardName,
        shardIndex = shardIndex,
        reversed = payload.name.reverse,
        payload = payload
      )
    )
}

@agentImplementation()
final class CoordinatorImpl(id: String) extends Coordinator {
  override def route(shardName: String, shardIndex: Int, input: String): Future[String] =
    AgentClientRuntime
      .resolve[Worker, (String, Int)](
        AgentClient.planWithCtor[Worker, (String, Int)],
        (shardName, shardIndex)
      ) match {
      case Left(err) =>
        Future.failed(new RuntimeException(err))
      case Right(resolved) =>
        resolved
          .callByName[String, String]("reverse", input)
          .recover { case t: Throwable =>
            s"agent-to-agent failed: ${t.toString}"
          }
    }

  override def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply] =
    AgentClientRuntime
      .resolve[Worker, (String, Int)](
        AgentClient.planWithCtor[Worker, (String, Int)],
        (shardName, shardIndex)
      ) match {
      case Left(err) =>
        Future.failed(new RuntimeException(err))
      case Right(resolved) =>
        resolved
          .callByName[TypedPayload, TypedReply]("handle", payload)
          .recover { case t: Throwable =>
            TypedReply(
              shardName = shardName,
              shardIndex = shardIndex,
              reversed = s"agent-to-agent failed: ${t.toString}",
              payload = payload
            )
          }
    }
}
