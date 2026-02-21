package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "trigger-target")
@description("Target agent whose methods are called via trigger (fire-and-forget).")
trait TriggerTarget extends BaseAgent[String] {

  @description("Multi-param method exercising trigger dispatch.")
  def process(x: Int, label: String): Future[Int]

  @description("No-arg method exercising trigger dispatch.")
  def ping(): Future[String]
}

object TriggerTarget extends AgentCompanion[TriggerTarget, String]

@agentDefinition(typeName = "trigger-caller")
@description("Calls TriggerTarget methods via trigger (fire-and-forget) and schedule.")
trait TriggerCaller extends BaseAgent[String] {

  @description("Fires TriggerTarget.process via trigger and returns confirmation.")
  def fireProcess(): Future[String]

  @description("Fires TriggerTarget.ping via trigger and returns confirmation.")
  def firePing(): Future[String]
}

object TriggerCaller extends AgentCompanion[TriggerCaller, String]
