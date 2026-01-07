package golem.runtime.autowire

/**
 * Defines the persistence mode for an agent.
 *
 * Agent modes affect how state is managed across invocations:
 *   - [[AgentMode.Durable]] - State persists across invocations (default)
 *   - [[AgentMode.Ephemeral]] - Fresh instance per invocation
 */
sealed trait AgentMode {

  /** The string value used in annotations and serialization. */
  def value: String
}

object AgentMode {

  /**
   * Parses an agent mode from its string representation.
   *
   * @param value
   *   The mode string (case-insensitive)
   * @return
   *   The parsed mode, or None if invalid
   */
  def fromString(value: String): Option[AgentMode] =
    Option(value).map(_.toLowerCase) match {
      case Some("durable")   => Some(Durable)
      case Some("ephemeral") => Some(Ephemeral)
      case _                 => None
    }

  /**
   * Durable mode - agent state persists across invocations.
   *
   * This is the default mode. Use when agents need to maintain state between
   * method calls.
   */
  case object Durable extends AgentMode {
    override val value: String = "durable"
  }

  /**
   * Ephemeral mode - fresh agent instance per invocation.
   *
   * Use for stateless agents or when each invocation should start with a clean
   * slate.
   */
  case object Ephemeral extends AgentMode {
    override val value: String = "ephemeral"
  }
}
