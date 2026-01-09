package object golem extends GolemPackageBase {
  // ---------------------------------------------------------------------------
  // Scala.js runtime registration / autowire (agent host integration)
  // ---------------------------------------------------------------------------
  val AgentImplementation: runtime.autowire.AgentImplementation.type =
    runtime.autowire.AgentImplementation

  type AgentDefinition[Trait] = runtime.autowire.AgentDefinition[Trait]
  type AgentMode              = runtime.autowire.AgentMode
  val AgentMode: runtime.autowire.AgentMode.type = runtime.autowire.AgentMode
}
