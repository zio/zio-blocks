package example.minimal

import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

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
final class CoordinatorImpl(@unused id: String) extends Coordinator {
  override def route(shardName: String, shardIndex: Int, input: String): Future[String] = {
    val worker = Worker.get(shardName, shardIndex)
    worker.reverse(input)
  }

  override def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply] = {
    val worker = Worker.get(shardName, shardIndex)
    worker.handle(payload)
  }
}
