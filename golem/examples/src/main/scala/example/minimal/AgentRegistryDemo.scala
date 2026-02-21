package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "agent-registry-demo")
@description("Demonstrates agent type registry, metadata queries, resolution, lifecycle, and phantom RPC.")
trait AgentRegistryDemo extends BaseAgent[String] {

  @description("Explores registeredAgentType, getAllAgentTypes, parseAgentId, resolveComponentId, resolveAgentId.")
  def exploreRegistry(): Future[String]

  @description("Explores getSelfMetadata, getAgentMetadata, getAgents/nextAgentBatch, generateIdempotencyKey.")
  def exploreAgentQuery(): Future[String]

  @description("Exercises agent lifecycle: updateAgent, forkAgent, revertAgent (best-effort, may fail locally).")
  def exploreLifecycle(): Future[String]

  @description("Exercises AgentCompanion.getPhantom to create a deterministic agent instance.")
  def phantomDemo(): Future[String]
}

object AgentRegistryDemo extends AgentCompanion[AgentRegistryDemo, String]
