package golem

import golem.runtime.agenttype.AgentType

/**
 * Pure metadata + reflected type for an agent trait.
 *
 * This lives in `model` so macros can derive it without depending on the
 * runtime (`core`).
 */
trait AgentApi[Trait] {
  type Constructor

  /** Golem agent type name, from `@agentDefinition("...")`. */
  def typeName: String

  /** Reflected agent type (schemas + function names). */
  def agentType: AgentType[Trait, Constructor]
}
