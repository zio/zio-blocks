package golem.runtime.macros

import golem.runtime.AgentMetadata

object AgentMacros {
  inline def agentMetadata[T]: AgentMetadata =
    AgentDefinitionMacro.generate[T]
}
