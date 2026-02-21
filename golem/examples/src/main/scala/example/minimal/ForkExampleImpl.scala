package example.minimal

import golem.HostApi
import golem.HostApi.ForkResult
import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class ForkDemoImpl(@unused private val name: String) extends ForkDemo {

  override def runFork(): Future[String] =
    HostApi.fork() match {
      case ForkResult.Original(d) =>
        Future.successful(
          s"original(phantom=${d.forkedPhantomId}, agentId=${d.agentId.agentId}, " +
            s"oplogIndex=${d.oplogIndex}, revision=${d.componentRevision})"
        )

      case ForkResult.Forked(d) =>
        Future.successful(
          s"forked(phantom=${d.forkedPhantomId}, agentId=${d.agentId.agentId}, " +
            s"oplogIndex=${d.oplogIndex}, revision=${d.componentRevision})"
        )
    }
}
