package golem.examples.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "approval-workflow")
@description("Human-in-the-loop workflow using Golem promises (Scala equivalent of the Rust/TS HITL template).")
trait ApprovalWorkflow extends BaseAgent {
  type AgentInput = String

  @prompt("Start approval process")
  @description("Starts a workflow that requires human approval before continuing")
  def begin(): Future[String]

  @description("Wait until the approval decision is provided, then return it")
  def awaitOutcome(): Future[String]

  @description("Internal: complete the workflow decision")
  def complete(decision: String): Future[Boolean]
}

object ApprovalWorkflow extends AgentCompanion[ApprovalWorkflow]

@agentDefinition(typeName = "human")
@description("A minimal 'human' agent that can approve/reject workflows (used by ApprovalWorkflow examples).")
trait HumanAgent extends BaseAgent {
  type AgentInput = String

  @prompt("Approve or reject a workflow")
  @description("Makes a decision on a workflow approval request")
  def decide(workflowId: String, decision: String): Future[String]
}

object HumanAgent extends AgentCompanion[HumanAgent]
