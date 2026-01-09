package golem.runtime.autowire

import scala.collection.mutable

/**
 * Global registry of agent definitions available in this component.
 *
 * When you register an agent via [[AgentImplementation.register]], the
 * resulting [[AgentDefinition]] is stored here. The Golem runtime queries this
 * registry to discover available agent types and route incoming RPC calls.
 *
 * ==Thread Safety==
 * All operations are synchronized for safe concurrent access.
 *
 * ==Usage==
 * You typically don't interact with this directly. Agent definitions are
 * registered automatically by the autowiring macros.
 *
 * {{{
 * // Registered by macro
 * AgentImplementation.register[MyAgent]("my-agent")(new MyAgentImpl)
 *
 * // Query programmatically
 * val definition = AgentRegistry.get("my-agent")
 * val allAgents = AgentRegistry.all
 * }}}
 */
object AgentRegistry {
  private val definitions: mutable.LinkedHashMap[String, AgentDefinition[Any]] =
    mutable.LinkedHashMap.empty

  /**
   * Registers an agent definition.
   *
   * If a definition with the same type name already exists, it is replaced.
   *
   * @param definition
   *   The agent definition to register
   */
  def register[A](definition: AgentDefinition[A]): Unit =
    synchronized {
      definitions.update(definition.typeName, definition.asInstanceOf[AgentDefinition[Any]])
    }

  /**
   * Retrieves an agent definition by type name.
   *
   * @param typeName
   *   The unique type name of the agent
   * @return
   *   The definition if found, None otherwise
   */
  def get(typeName: String): Option[AgentDefinition[Any]] =
    synchronized {
      definitions.get(typeName)
    }

  /**
   * Returns all registered agent definitions.
   *
   * Definitions are returned in registration order.
   *
   * @return
   *   List of all registered definitions
   */
  def all: List[AgentDefinition[Any]] =
    synchronized {
      definitions.values.toList
    }
}
