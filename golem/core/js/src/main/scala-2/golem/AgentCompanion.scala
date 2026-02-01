package golem

import golem.runtime.agenttype.AgentType

import scala.language.experimental.macros

/**
 * Cross-version companion-style API for agent traits (Scala 2 uses macros).
 *
 * Usage:
 *
 * {{{
 * @agentDefinition("my-agent")
 * trait MyAgent extends BaseAgent[Ctor] { ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent]
 * }}}
 */
// format: off
trait AgentCompanion[Trait <: BaseAgent[_]] extends AgentCompanionBase[Trait] {
  implicit final val implicitAgentCompanion: AgentCompanion[Trait] = this

  def typeName: String =
    macro golem.runtime.macros.AgentCompanionMacro.typeNameImpl

  def agentType: AgentType[Trait, _] =
    macro golem.runtime.macros.AgentCompanionMacro.agentTypeImpl

  def get[In](input: In): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getImpl

  def getRemote[In](input: In): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteImpl

  def getPhantom[In](input: In, phantom: Uuid): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomImpl

  def getRemotePhantom[In](input: In, phantom: Uuid): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomImpl

  def get(): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getUnitImpl

  def getRemote(): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteUnitImpl

  def getPhantom(phantom: Uuid): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomUnitImpl

  def getRemotePhantom(phantom: Uuid): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomUnitImpl

  def get[A1, A2](a1: A1, a2: A2): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple2Impl[A1, A2]

  def getRemote[A1, A2](a1: A1, a2: A2): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple2Impl[A1, A2]

  def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple2Impl[A1, A2]

  def getRemotePhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple2Impl[A1, A2]

  def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple3Impl[A1, A2, A3]

  def getRemote[A1, A2, A3](a1: A1, a2: A2, a3: A3): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple3Impl[A1, A2, A3]

  def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple3Impl[A1, A2, A3]

  def getRemotePhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple3Impl[A1, A2, A3]

  def get[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple4Impl[A1, A2, A3, A4]

  def getRemote[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple4Impl[A1, A2, A3, A4]

  def getPhantom[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4, phantom: Uuid): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple4Impl[A1, A2, A3, A4]

  def getRemotePhantom[A1, A2, A3, A4](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    phantom: Uuid
  ): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple4Impl[A1, A2, A3, A4]

  def get[A1, A2, A3, A4, A5](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple5Impl[A1, A2, A3, A4, A5]

  def getRemote[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple5Impl[A1, A2, A3, A4, A5]

  def getPhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    phantom: Uuid
  ): Trait =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple5Impl[A1, A2, A3, A4, A5]

  def getRemotePhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    phantom: Uuid
  ): RemoteAgent[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple5Impl[A1, A2, A3, A4, A5]
}
