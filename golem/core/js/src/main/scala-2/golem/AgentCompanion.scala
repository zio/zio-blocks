package golem

import golem.runtime.agenttype.AgentType

import scala.language.experimental.macros
import scala.scalajs.js

trait TriggerSchedule {
  def trigger: js.Dynamic
  def schedule: js.Dynamic
}

/**
 * Cross-version companion-style API for agent traits (Scala 2 uses macros).
 *
 * Usage:
 *
 * {{{
 * @agentDefinition("my-agent")
 * trait MyAgent extends BaseAgent[Ctor] { ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent, Ctor]
 * }}}
 */
// format: off
trait AgentCompanion[Trait <: BaseAgent[Input], Input] extends AgentCompanionBase[Trait] {
  implicit final val implicitAgentCompanion: AgentCompanion[Trait, Input] = this

  def typeName: String =
    macro golem.runtime.macros.AgentCompanionMacro.typeNameImpl

  def agentType: AgentType[Trait, _] =
    macro golem.runtime.macros.AgentCompanionMacro.agentTypeImpl

  def get(input: Input): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getImpl

  def getPhantom(input: Input, phantom: Uuid): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomImpl

  def get(): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getUnitImpl

  def getPhantom(phantom: Uuid): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomUnitImpl

  def get[A1, A2](a1: A1, a2: A2): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple2Impl[A1, A2]

  def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple2Impl[A1, A2]

  def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple3Impl[A1, A2, A3]

  def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple3Impl[A1, A2, A3]

  def get[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple4Impl[A1, A2, A3, A4]

  def getPhantom[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4, phantom: Uuid): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple4Impl[A1, A2, A3, A4]

  def get[A1, A2, A3, A4, A5](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple5Impl[A1, A2, A3, A4, A5]

  def getPhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    phantom: Uuid
  ): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple5Impl[A1, A2, A3, A4, A5]

}
