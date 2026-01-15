package golem

import golem.runtime.agenttype.AgentType

import scala.concurrent.Future
import scala.language.experimental.macros

/**
 * Cross-version companion-style API for agent traits (Scala 2 uses macros).
 *
 * Usage:
 *
 * {{{
 * @agentDefinition("my-agent")
 * trait MyAgent { type AgentInput = Ctor; ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent]
 * }}}
 */
// format: off
trait AgentCompanion[Trait <: AnyRef { type AgentInput }] extends AgentCompanionBase[Trait] {
  type AgentInput = Trait#AgentInput

  def typeName: String =
    macro golem.runtime.macros.AgentCompanionMacro.typeNameImpl

  def agentType: AgentType[Trait, AgentInput] =
    macro golem.runtime.macros.AgentCompanionMacro.agentTypeImpl

  def get(input: AgentInput): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getImpl

  def getRemote(input: AgentInput): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteImpl

  def getPhantom(input: AgentInput, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomImpl

  def getRemotePhantom(input: AgentInput, phantom: Uuid): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomImpl

  def get(): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getUnitImpl

  def getRemote(): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteUnitImpl

  def getPhantom(phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomUnitImpl

  def getRemotePhantom(phantom: Uuid): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomUnitImpl

  def get[A1, A2](a1: A1, a2: A2): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple2Impl[A1, A2]

  def getRemote[A1, A2](a1: A1, a2: A2): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple2Impl[A1, A2]

  def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple2Impl[A1, A2]

  def getRemotePhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple2Impl[A1, A2]

  def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple3Impl[A1, A2, A3]

  def getRemote[A1, A2, A3](a1: A1, a2: A2, a3: A3): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemoteTuple3Impl[A1, A2, A3]

  def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple3Impl[A1, A2, A3]

  def getRemotePhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Future[RemoteAgent[Trait]] =
    macro golem.runtime.macros.AgentCompanionMacro.getRemotePhantomTuple3Impl[A1, A2, A3]
}
