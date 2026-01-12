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

  def getPhantom(input: AgentInput, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomImpl

  def get(): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getUnitImpl

  def getPhantom(phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomUnitImpl

  def get[A1, A2](a1: A1, a2: A2): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple2Impl[A1, A2]

  def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple2Impl[A1, A2]

  def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getTuple3Impl[A1, A2, A3]

  def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Future[Trait] =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomTuple3Impl[A1, A2, A3]
}
