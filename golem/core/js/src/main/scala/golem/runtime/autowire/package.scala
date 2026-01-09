package golem.runtime

/**
 * Public naming alias (legacy).
 *
 * Prefer `golem.runtime.annotations.DurabilityMode` for annotations.
 */
package object autowire {
  type AgentDurabilityMode = AgentMode
  val AgentDurabilityMode: AgentMode.type = AgentMode
}
