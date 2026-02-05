package golem

/**
 * Scala 3 IDE-friendly accessors for trigger/schedule.
 *
 * These are implemented as macros so IntelliJ can see a concrete structural type
 * for the returned trigger/schedule objects (instead of Dynamic).
 */
object TriggerScheduleSyntax {
  type TriggerOps[Trait]  = Selectable
  type ScheduleOps[Trait] = Selectable

  extension [Trait <: BaseAgent[?]](agent: Trait) {
    transparent inline def trigger: TriggerOps[Trait] =
      ${ golem.AgentCompanionMacro.triggerOpsImpl[Trait]('agent) }

    transparent inline def schedule: ScheduleOps[Trait] =
      ${ golem.AgentCompanionMacro.scheduleOpsImpl[Trait]('agent) }
  }
}
