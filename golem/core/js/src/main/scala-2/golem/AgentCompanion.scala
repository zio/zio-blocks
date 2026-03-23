/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package golem

import golem.config.{ConfigOverride, RpcConfig}
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

  def getWithConfig(input: Input, configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getWithConfigImpl

  def getWithConfig(configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getWithConfigUnitImpl

  def getWithConfig[C](input: Input, config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getWithConfigTypedImpl

  def getWithConfig[C](config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getWithConfigTypedUnitImpl

  def newPhantom(input: Input): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomImpl

  def newPhantom(): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomUnitImpl

  def newPhantom[A1, A2](a1: A1, a2: A2): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomTuple2Impl[A1, A2]

  def newPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomTuple3Impl[A1, A2, A3]

  def newPhantom[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomTuple4Impl[A1, A2, A3, A4]

  def newPhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomTuple5Impl[A1, A2, A3, A4, A5]

  def getPhantomWithConfig(input: Input, phantom: Uuid, configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomWithConfigImpl

  def getPhantomWithConfig(phantom: Uuid, configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomWithConfigUnitImpl

  def getPhantomWithConfig[C](input: Input, phantom: Uuid, config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomWithConfigTypedImpl

  def getPhantomWithConfig[C](phantom: Uuid, config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.getPhantomWithConfigTypedUnitImpl

  def newPhantomWithConfig(input: Input, configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomWithConfigImpl

  def newPhantomWithConfig(configOverrides: List[ConfigOverride]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomWithConfigUnitImpl

  def newPhantomWithConfig[C](input: Input, config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomWithConfigTypedImpl

  def newPhantomWithConfig[C](config: RpcConfig[C]): Trait with TriggerSchedule =
    macro golem.runtime.macros.AgentCompanionMacro.newPhantomWithConfigTypedUnitImpl

}
