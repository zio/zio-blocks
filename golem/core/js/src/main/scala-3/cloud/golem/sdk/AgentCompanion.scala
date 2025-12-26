package cloud.golem.sdk

import cloud.golem.runtime.macros.AgentNameMacro
import cloud.golem.runtime.plan.AgentClientPlan
import cloud.golem.runtime.rpc.AgentClient

import scala.concurrent.Future

/**
 * Cross-version companion-style API for agent traits.
 *
 * Usage:
 *
 * {{{
 * @agentDefinition("my-agent")
 * trait MyAgent { type AgentInput = MyCtor; ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent]
 * }}}
 */
trait AgentCompanion[Trait] extends AgentCompanionBase[Trait] {

  /** Golem agent type name, from `@agentDefinition("...")`. */
  transparent inline def typeName: String =
    AgentNameMacro.typeName[Trait]

  /** Pre-computed client plan (schemas + function names). */
  transparent inline def plan: AgentClientPlan[Trait, ?] =
    AgentClient.plan[Trait]

  /** Connect to (or create) an agent instance from constructor input. */
  transparent inline def get[In](input: In): Future[Trait] =
    ${ AgentCompanionMacro.getImpl[Trait, In]('input) }

  /** Connect to a phantom (pre-provisioned) agent instance. */
  transparent inline def getPhantom[In](input: In, phantom: Uuid): Future[Trait] =
    ${ AgentCompanionMacro.getPhantomImpl[Trait, In]('input, 'phantom) }

  /** Unit-constructor convenience. */
  transparent inline def get(): Future[Trait] =
    ${ AgentCompanionMacro.getUnitImpl[Trait] }

  /** Unit-constructor + phantom convenience. */
  transparent inline def getPhantom(phantom: Uuid): Future[Trait] =
    ${ AgentCompanionMacro.getPhantomUnitImpl[Trait]('phantom) }

  /** Tuple2 constructor convenience. */
  transparent inline def get[A1, A2](a1: A1, a2: A2): Future[Trait] =
    ${ AgentCompanionMacro.getTuple2Impl[Trait, A1, A2]('a1, 'a2) }

  transparent inline def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Future[Trait] =
    ${ AgentCompanionMacro.getPhantomTuple2Impl[Trait, A1, A2]('a1, 'a2, 'phantom) }

  /** Tuple3 constructor convenience. */
  transparent inline def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Future[Trait] =
    ${ AgentCompanionMacro.getTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3) }

  transparent inline def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Future[Trait] =
    ${ AgentCompanionMacro.getPhantomTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3, 'phantom) }
}
