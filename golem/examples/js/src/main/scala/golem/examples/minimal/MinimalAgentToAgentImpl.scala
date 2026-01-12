package golem.examples.minimal

import golem.runtime.annotations.agentImplementation

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
    Worker.get(shardName, shardIndex).flatMap(_.reverse(input))

  override def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply] =
    Worker.get(shardName, shardIndex).flatMap(_.handle(payload))
}
