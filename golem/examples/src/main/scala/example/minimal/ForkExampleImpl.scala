package example.minimal

import golem.HostApi
import golem.HostApi.ForkResult
import golem.runtime.annotations.agentImplementation
import zio.blocks.schema.Schema

import scala.annotation.unused
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class ForkState(count: Int)

object ForkState {
  implicit val schema: Schema[ForkState] = Schema.derived
}

@agentImplementation()
final class ForkDemoImpl(@unused private val name: String) extends ForkDemo {

  override def runFork(): Future[String] = {
    val promiseId = HostApi.createPromise()

    HostApi.fork() match {
      case ForkResult.Original(_) =>
        HostApi.awaitPromise(promiseId).map { result =>
          val msg = new String(result, "UTF-8")
          s"original-joined: $msg"
        }

      case ForkResult.Forked(_) =>
        val msg = "Hello from forked agent!"
        HostApi.completePromise(promiseId, msg.getBytes("UTF-8"))
        Future.successful(s"forked result")
    }
  }

  override def runForkJson(): Future[String] = {
    val promiseId = HostApi.createPromise()

    HostApi.fork() match {
      case ForkResult.Original(_) =>
        HostApi.awaitPromiseJson[ForkState](promiseId).map { state =>
          s"original-joined-json: count=${state.count}"
        }

      case ForkResult.Forked(_) =>
        val completed = HostApi.completePromiseJson(promiseId, ForkState(count = 42))
        Future.successful(s"forked-completed: $completed")
    }
  }
}
