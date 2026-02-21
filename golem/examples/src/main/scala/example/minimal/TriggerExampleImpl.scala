package example.minimal

import golem._
import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class TriggerTargetImpl(@unused private val name: String) extends TriggerTarget {
  private var count: Int = 0

  override def process(x: Int, label: String): Future[Int] =
    Future.successful {
      count += x
      count
    }

  override def ping(): Future[String] =
    Future.successful("pong")
}

@agentImplementation()
final class TriggerCallerImpl(@unused private val name: String) extends TriggerCaller {
  private val target = TriggerTarget.get("target-instance")

  override def fireProcess(): Future[String] = {
    target.trigger.process(42, "hello")
    Future.successful("triggered process(42, \"hello\") on target-instance")
  }

  override def firePing(): Future[String] = {
    target.trigger.ping()
    Future.successful("triggered ping() on target-instance")
  }
}
