package golem

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
