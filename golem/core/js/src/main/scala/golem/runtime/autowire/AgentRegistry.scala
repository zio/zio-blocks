package golem.runtime.autowire

import scala.collection.mutable

private[golem] object AgentRegistry {
  private val definitions: mutable.LinkedHashMap[String, AgentDefinition[Any]] =
    mutable.LinkedHashMap.empty

  /**
   * Registers an agent definition.
   *
   * @throws IllegalArgumentException
   *   if a definition with the same type name already exists
   */
  def register[A](definition: AgentDefinition[A]): Unit =
    synchronized {
      if (definitions.contains(definition.typeName)) {
        throw new IllegalArgumentException(
          s"Duplicate agent typeName registered: '${definition.typeName}'. Each agentDefinition typeName must be unique."
        )
      }
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
