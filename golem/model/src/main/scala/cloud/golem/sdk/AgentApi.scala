package cloud.golem.sdk

import cloud.golem.runtime.plan.AgentClientPlan

/**
 * Pure metadata + plan for an agent trait.
 *
 * This lives in `model` so macros can derive it without depending on the
 * runtime (`core`).
 */
trait AgentApi[Trait] {
  type Constructor

  /** Golem agent type name, from `@agentDefinition("...")`. */
  def typeName: String

  /** Pre-computed client plan (schemas + function names). */
  def plan: AgentClientPlan[Trait, Constructor]
}
