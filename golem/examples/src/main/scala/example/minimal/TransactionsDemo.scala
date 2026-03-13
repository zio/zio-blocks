package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "transactions-demo")
@description("Demonstrates the Transactions API: infallible and fallible saga-style transactions.")
trait TransactionsDemo extends BaseAgent[String] {

  @description("Runs an infallible transaction with multiple operations (success path).")
  def infallibleDemo(): Future[String]

  @description("Runs a fallible transaction where all operations succeed.")
  def fallibleSuccessDemo(): Future[String]

  @description("Runs a fallible transaction where the last operation fails, triggering compensations.")
  def fallibleFailureDemo(): Future[String]
}

object TransactionsDemo extends AgentCompanion[TransactionsDemo, String]
