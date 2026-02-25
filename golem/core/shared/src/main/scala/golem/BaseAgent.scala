package golem

/**
 * Base trait for Scala agent interfaces.
 *
 * When running inside Golem, these values are provided by the host runtime.
 */
trait BaseAgent[Input] {
  final def agentId: String = BaseAgentPlatform.agentId

  final def agentType: String = BaseAgentPlatform.agentType

  final def agentName: String = BaseAgentPlatform.agentName
}
