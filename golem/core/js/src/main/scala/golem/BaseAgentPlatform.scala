package golem

import golem.host.js.JsAgentMetadataRuntime
import golem.runtime.rpc.host.AgentHostApi

private[golem] object BaseAgentPlatform {
  private def self = AgentHostApi.getSelfMetadata()

  def agentId: String =
    self.agentId.agentId

  def agentType: String =
    self.asInstanceOf[JsAgentMetadataRuntime].agentType.getOrElse("")

  def agentName: String =
    self.asInstanceOf[JsAgentMetadataRuntime].agentName.getOrElse("")
}
