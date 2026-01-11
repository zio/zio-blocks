package golem.examples.minimal

import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import golem.runtime.plan.ClientMethodPlan
import golem.runtime.rpc.{AgentClient, AgentClientRuntime}
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
  private val workerPlan =
    AgentClient.plan[Worker].asInstanceOf[golem.runtime.plan.AgentClientPlan[Worker, (String, Int)]]

  private def workerMethod[In, Out](name: String): Option[ClientMethodPlan[Worker, In, Out]] =
    workerPlan.methods.collectFirst {
      case p if p.metadata.name == name => p.asInstanceOf[ClientMethodPlan[Worker, In, Out]]
    }

  override def route(shardName: String, shardIndex: Int, input: String): Future[String] =
    (AgentClientRuntime
      .resolve[Worker, (String, Int)](workerPlan, (shardName, shardIndex)) match {
      case Left(err) =>
        Future.failed(new RuntimeException(err))
      case Right(resolved) =>
        workerMethod[String, String]("reverse") match {
          case None =>
            Future.failed(new IllegalStateException("Worker method plan not found: reverse"))
          case Some(m) =>
            resolved.call[String, String](m, input)
        }
    }).recover { case t: Throwable =>
      s"agent-to-agent failed: ${t.toString}"
    }

  override def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply] =
    (AgentClientRuntime
      .resolve[Worker, (String, Int)](workerPlan, (shardName, shardIndex)) match {
      case Left(err) =>
        Future.failed(new RuntimeException(err))
      case Right(resolved) =>
        workerMethod[TypedPayload, TypedReply]("handle") match {
          case None =>
            Future.failed(new IllegalStateException("Worker method plan not found: handle"))
          case Some(m) =>
            resolved.call[TypedPayload, TypedReply](m, payload)
        }
    }).recover { case t: Throwable =>
      TypedReply(
        shardName = shardName,
        shardIndex = shardIndex,
        reversed = s"agent-to-agent failed: ${t.toString}",
        payload = payload
      )
    }
}
