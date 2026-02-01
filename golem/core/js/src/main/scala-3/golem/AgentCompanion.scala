package golem

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
 * trait MyAgent extends BaseAgent[MyCtor] { ... }
 *
 * object MyAgent extends AgentCompanion[MyAgent]
 * }}}
 */
trait AgentCompanion[Trait <: BaseAgent[?]] extends AgentCompanionBase[Trait] {
  type Input = AgentCompanion.InputOf[Trait]

  /** Golem agent type name, from `@agentDefinition("...")`. */
  transparent inline def typeName: String =
    AgentNameMacro.typeName[Trait]

  /** Reflected agent type (schemas + function names). */
  transparent inline def agentType: AgentType[Trait, Input] =
    AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Input]]

  /** Connect to (or create) an agent instance from constructor input. */
  transparent inline def get(input: Input): Trait =
    ${ AgentCompanionMacro.getImpl[Trait, Input]('input) }

  /**
   * Connect to an agent instance, returning a handle that supports
   * await/trigger/schedule for any method.
   */
  transparent inline def getRemote(input: Input): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteImpl[Trait, Input]('input) }

  /** Connect to a phantom (pre-provisioned) agent instance. */
  transparent inline def getPhantom(input: Input, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomImpl[Trait, Input]('input, 'phantom) }

  /** Phantom variant returning the remote handle. */
  transparent inline def getRemotePhantom(input: Input, phantom: Uuid): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemotePhantomImpl[Trait, Input]('input, 'phantom) }

  /** Unit-constructor convenience. */
  transparent inline def get(): Trait =
    ${ AgentCompanionMacro.getUnitImpl[Trait] }

  /** Unit-constructor convenience returning the remote handle. */
  transparent inline def getRemote(): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteUnitImpl[Trait] }

  /** Unit-constructor + phantom convenience. */
  transparent inline def getPhantom(phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomUnitImpl[Trait]('phantom) }

  /** Unit-constructor + phantom convenience returning the remote handle. */
  transparent inline def getRemotePhantom(phantom: Uuid): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemotePhantomUnitImpl[Trait]('phantom) }

  /** Tuple2 constructor convenience. */
  transparent inline def get[A1, A2](a1: A1, a2: A2): Trait =
    ${ AgentCompanionMacro.getTuple2Impl[Trait, A1, A2]('a1, 'a2) }

  /** Tuple2 constructor convenience returning the remote handle. */
  transparent inline def getRemote[A1, A2](a1: A1, a2: A2): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteTuple2Impl[Trait, A1, A2]('a1, 'a2) }

  transparent inline def getPhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomTuple2Impl[Trait, A1, A2]('a1, 'a2, 'phantom) }

  transparent inline def getRemotePhantom[A1, A2](a1: A1, a2: A2, phantom: Uuid): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemotePhantomTuple2Impl[Trait, A1, A2]('a1, 'a2, 'phantom) }

  /** Tuple3 constructor convenience. */
  transparent inline def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait =
    ${ AgentCompanionMacro.getTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3) }

  /** Tuple3 constructor convenience returning the remote handle. */
  transparent inline def getRemote[A1, A2, A3](a1: A1, a2: A2, a3: A3): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3) }

  transparent inline def getPhantom[A1, A2, A3](a1: A1, a2: A2, a3: A3, phantom: Uuid): Trait =
    ${ AgentCompanionMacro.getPhantomTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3, 'phantom) }

  transparent inline def getRemotePhantom[A1, A2, A3](
    a1: A1,
    a2: A2,
    a3: A3,
    phantom: Uuid
  ): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemotePhantomTuple3Impl[Trait, A1, A2, A3]('a1, 'a2, 'a3, 'phantom) }

  /** Tuple4 constructor convenience. */
  transparent inline def get[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4): Trait =
    ${ AgentCompanionMacro.getTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4) }

  /** Tuple4 constructor convenience returning the remote handle. */
  transparent inline def getRemote[A1, A2, A3, A4](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4
  ): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4) }

  transparent inline def getPhantom[A1, A2, A3, A4](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    phantom: Uuid
  ): Trait =
    ${ AgentCompanionMacro.getPhantomTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4, 'phantom) }

  transparent inline def getRemotePhantom[A1, A2, A3, A4](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    phantom: Uuid
  ): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemotePhantomTuple4Impl[Trait, A1, A2, A3, A4]('a1, 'a2, 'a3, 'a4, 'phantom) }

  /** Tuple5 constructor convenience. */
  transparent inline def get[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): Trait =
    ${ AgentCompanionMacro.getTuple5Impl[Trait, A1, A2, A3, A4, A5]('a1, 'a2, 'a3, 'a4, 'a5) }

  /** Tuple5 constructor convenience returning the remote handle. */
  transparent inline def getRemote[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5
  ): RemoteAgent[Trait] =
    ${ AgentCompanionMacro.getRemoteTuple5Impl[Trait, A1, A2, A3, A4, A5]('a1, 'a2, 'a3, 'a4, 'a5) }

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

  transparent inline def getRemotePhantom[A1, A2, A3, A4, A5](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    phantom: Uuid
  ): RemoteAgent[Trait] =
    ${
      AgentCompanionMacro.getRemotePhantomTuple5Impl[Trait, A1, A2, A3, A4, A5](
        'a1,
        'a2,
        'a3,
        'a4,
        'a5,
        'phantom
      )
    }
}

object AgentCompanion {
  type InputOf[T] = T match {
    case BaseAgent[in] => in
  }
}
