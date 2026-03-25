package example.minimal

import golem.{BaseAgent, Principal}
import golem.runtime.annotations.{agentDefinition, constructor, description}

import scala.concurrent.Future

@agentDefinition()
@description("Example agent with Principal injection in constructor")
trait PrincipalAgent extends BaseAgent {
  @constructor def create(value: String): Unit = ()

  @description("Returns who created this agent")
  def whoCreated(): Future[String]

  @description("Returns the current caller principal")
  def currentCaller(principal: Principal): Future[String]
}

