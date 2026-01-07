package golem

/**
 * Marker supertype for agent companion objects.
 *
 * This lives in `model` so Scala 2 macro expansion can reliably find the agent
 * trait type from the companion object's supertypes without depending on
 * `core`.
 */
trait AgentCompanionBase[Trait]
