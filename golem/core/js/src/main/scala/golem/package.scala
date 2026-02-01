package object golem extends GolemPackageBase {

  /** Register `@agentImplementation` implementations (Scala.js). */
  val AgentImplementation: runtime.autowire.AgentImplementation.type =
    runtime.autowire.AgentImplementation

  type AgentDefinition[Trait] = runtime.autowire.AgentDefinition[Trait]
  type AgentMode              = runtime.autowire.AgentMode
  val AgentMode: runtime.autowire.AgentMode.type = runtime.autowire.AgentMode
}
