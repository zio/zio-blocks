package golem

private[golem] object BaseAgentPlatform {
  private def unavailable: Nothing =
    throw new UnsupportedOperationException("BaseAgent is only available when running inside a Golem JS guest runtime")

  def agentId: String =
    unavailable

  def agentType: String =
    unavailable

  def agentName: String =
    unavailable
}
