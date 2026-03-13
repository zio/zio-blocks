package example.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "approvalworkflow")
@description("Human-in-the-loop workflow using Golem promises (Scala equivalent of the Rust/TS HITL template).")
trait ApprovalWorkflow extends BaseAgent[String] {

  @prompt("Start approval process")
  @description("Starts a workflow that requires human approval before continuing")
  def begin(): Future[String]

  @description("Wait until the approval decision is provided, then return it")
  def awaitOutcome(): Future[String]

  @description("Complete the workflow decision")
  def complete(decision: String): Future[Boolean]
}

object ApprovalWorkflow extends AgentCompanion[ApprovalWorkflow, String]

@agentDefinition(typeName = "human")
@description("A minimal 'human' agent that can approve/reject workflows (used by ApprovalWorkflow examples).")
trait HumanAgent extends BaseAgent[String] {

  @prompt("Approve or reject a workflow")
  @description("Makes a decision on a workflow approval request")
  def decide(workflowId: String, decision: String): Future[String]
}

object HumanAgent extends AgentCompanion[HumanAgent, String]
