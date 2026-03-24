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
import golem.runtime.macros.AgentNameMacro
import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.AgentClient

/**
 * Cross-version companion-style API for agent traits.
 *
 * Usage:
 *
 * {{{
 * @agentDefinition("my-agent")
 * trait MyAgent extends BaseAgent { ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent]
 * }}}
 */
trait AgentCompanion[Trait <: BaseAgent] extends AgentCompanionBase[Trait] {

  /** Golem agent type name, from `@agentDefinition("...")`. */
  transparent inline def typeName: String =
    AgentNameMacro.typeName[Trait]

  /** Reflected agent type (schemas + function names). */
  transparent inline def agentType: AgentType[Trait, Any] =
    AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Any]]

  /** Connect to (or create) an agent instance from constructor input. */
  transparent inline def get(input: Any): Trait =
    ${ AgentCompanionMacro.getImpl[Trait, Any]('input) }

  /** Connect to a phantom (pre-provisioned) agent instance. */
  transparent inline def getPhantom(input: Any, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomImpl[Trait, Any]('input, 'phantom) }

  /** Unit-constructor convenience. */
  transparent inline def get(): Trait =
    ${ AgentCompanionMacro.getUnitImpl[Trait] }

  /** Unit-constructor + phantom convenience. */
  transparent inline def getPhantom(phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomUnitImpl[Trait]('phantom) }

  /** Tuple2 constructor convenience. */
  transparent inline def get[A1, A2](a1: A1, a2: A2): Trait =
    ${ AgentCompanionMacro.getTuple2Impl[Trait, A1, A2]('a1, 'a2) }

  transparent inline def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomTuple2Impl[Trait, A1, A2]('a1, 'a2, 'phantom) }

  /** Tuple3 constructor convenience. */
  transparent inline def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait =
    ${ AgentCompanionMacro.getTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3) }

  transparent inline def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3, 'phantom) }

  /** Tuple4 constructor convenience. */
  transparent inline def get[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait =
    ${ AgentCompanionMacro.getTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4) }

  transparent inline def getPhantom[A1, A2, A3, A4](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    phantom: Uuid
  ): Trait =
    ${ AgentCompanionMacro.getPhantomTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4, 'phantom) }

  /** Tuple5 constructor convenience. */
  transparent inline def get[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): Trait =
    ${ AgentCompanionMacro.getTuple5Impl[Trait, A1, A2, A3, A4, A5]('a1, 'a2, 'a3, 'a4, 'a5) }

  transparent inline def getPhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    phantom: Uuid
  ): Trait =
    ${
      AgentCompanionMacro.getPhantomTuple5Impl[Trait, A1, A2, A3, A4, A5]('a1, 'a2, 'a3, 'a4, 'a5, 'phantom)
    }

  /** Connect to (or create) an agent instance with config overrides. */
  transparent inline def getWithConfig(input: Any, configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.getWithConfigImpl[Trait, Any]('input, 'configOverrides) }

  /** Unit-constructor + config overrides convenience. */
  transparent inline def getWithConfig(configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.getWithConfigUnitImpl[Trait]('configOverrides) }

  /** Connect to (or create) an agent instance with typed config overrides. */
  transparent inline def getWithConfig[C](input: Any, config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.getWithConfigImpl[Trait, Any]('input, '{ config.toOverrides }) }

  /** Unit-constructor + typed config overrides convenience. */
  transparent inline def getWithConfig[C](config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.getWithConfigUnitImpl[Trait]('{ config.toOverrides }) }

  /** Connect to a new phantom agent with a freshly generated UUID. */
  transparent inline def newPhantom(input: Any): Trait =
    ${ AgentCompanionMacro.newPhantomImpl[Trait, Any]('input) }

  /** Unit-constructor new phantom convenience. */
  transparent inline def newPhantom(): Trait =
    ${ AgentCompanionMacro.newPhantomUnitImpl[Trait] }

  /** Tuple2 constructor new phantom convenience. */
  transparent inline def newPhantom[A1, A2](a1: A1, a2: A2): Trait =
    ${ AgentCompanionMacro.newPhantomTuple2Impl[Trait, A1, A2]('a1, 'a2) }

  /** Tuple3 constructor new phantom convenience. */
  transparent inline def newPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait =
    ${ AgentCompanionMacro.newPhantomTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3) }

  /** Tuple4 constructor new phantom convenience. */
  transparent inline def newPhantom[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait =
    ${ AgentCompanionMacro.newPhantomTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4) }

  /** Tuple5 constructor new phantom convenience. */
  transparent inline def newPhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): Trait =
    ${ AgentCompanionMacro.newPhantomTuple5Impl[Trait, A1, A2, A3, A4, A5]('a1, 'a2, 'a3, 'a4, 'a5) }

  /** Connect to a phantom agent with config overrides. */
  transparent inline def getPhantomWithConfig(input: Any, phantom: Uuid, configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.getPhantomWithConfigImpl[Trait, Any]('input, 'phantom, 'configOverrides) }

  /** Unit-constructor phantom + config overrides convenience. */
  transparent inline def getPhantomWithConfig(phantom: Uuid, configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.getPhantomWithConfigUnitImpl[Trait]('phantom, 'configOverrides) }

  /** Connect to a phantom agent with typed config. */
  transparent inline def getPhantomWithConfig[C](input: Any, phantom: Uuid, config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.getPhantomWithConfigImpl[Trait, Any]('input, 'phantom, '{ config.toOverrides }) }

  /** Unit-constructor phantom + typed config convenience. */
  transparent inline def getPhantomWithConfig[C](phantom: Uuid, config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.getPhantomWithConfigUnitImpl[Trait]('phantom, '{ config.toOverrides }) }

  /** Connect to a new phantom agent (fresh UUID) with config overrides. */
  transparent inline def newPhantomWithConfig(input: Any, configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.newPhantomWithConfigImpl[Trait, Any]('input, 'configOverrides) }

  /** Unit-constructor new phantom + config overrides convenience. */
  transparent inline def newPhantomWithConfig(configOverrides: List[ConfigOverride]): Trait =
    ${ AgentCompanionMacro.newPhantomWithConfigUnitImpl[Trait]('configOverrides) }

  /** Connect to a new phantom agent (fresh UUID) with typed config. */
  transparent inline def newPhantomWithConfig[C](input: Any, config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.newPhantomWithConfigImpl[Trait, Any]('input, '{ config.toOverrides }) }

  /** Unit-constructor new phantom + typed config convenience. */
  transparent inline def newPhantomWithConfig[C](config: RpcConfig[C]): Trait =
    ${ AgentCompanionMacro.newPhantomWithConfigUnitImpl[Trait]('{ config.toOverrides }) }

}
