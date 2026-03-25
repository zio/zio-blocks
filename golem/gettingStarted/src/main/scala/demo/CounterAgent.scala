package demo

import golem.runtime.annotations.{agentDefinition, agentImplementation, constructor, description, prompt}
import golem.BaseAgent

import scala.concurrent.Future

@agentDefinition()
trait CounterAgent extends BaseAgent {

  @constructor def create(value: String): Unit = ()

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

@agentDefinition()
trait Example1 extends BaseAgent {
  @constructor def create(name: String, count: Int): Unit = ()

  def run(): Future[String]
}


@agentImplementation()
final case class Example1Impl(name: String, count: Int) extends Example1 {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  override def run(): Future[String] = {
    val client = CounterAgentClient.get(s"x-${name}")
    client.increment().map { n =>
      s"Result: ${n}"
    }
  }
}

