/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition()
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
