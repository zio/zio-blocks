package golem

import golem.runtime.rpc.host.AgentHostApi

private[golem] object BaseAgentPlatform {
  private def self = AgentHostApi.getSelfMetadata()

  def agentId: String =
    self.agentId.agentId

  def agentType: String =
    self.agentType

  def agentName: String =
    self.agentName
}
