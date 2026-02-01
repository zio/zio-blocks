package golem

import golem.runtime.macros.{AgentClientMacro, AgentNameMacro}
import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.jvm.JvmAgentClient

import scala.annotation.unused

/**
 * JVM version of `AgentCompanion`.
 *
 * This companion provides a typed way to connect to agent instances from the
 * JVM (primarily useful for local tooling and tests).
 */
trait AgentCompanion[Trait <: BaseAgent[?]] extends AgentCompanionBase[Trait] {
  type Input = AgentCompanion.InputOf[Trait]

  /** Golem agent type name, from `@agentDefinition("...")`. */
  transparent inline def typeName: String =
    AgentNameMacro.typeName[Trait]

  /** Reflected agent type (schemas + function names). */
  transparent inline def agentType: AgentType[Trait, Input] =
    AgentClientMacro.agentType[Trait].asInstanceOf[AgentType[Trait, Input]]

  /** Connect to (or create) an agent instance from constructor input. */
  transparent inline def get(input: Input): Trait =
    JvmAgentClient.connect[Trait](agentType, input)

  /** Unit-constructor convenience. */
  transparent inline def get(): Trait =
    JvmAgentClient.connect[Trait](agentType, ())

  /** Tuple2 constructor convenience. */
  transparent inline def get[A1, A2](a1: A1, a2: A2): Trait =
    JvmAgentClient.connect[Trait](agentType, (a1, a2))

  /** Tuple3 constructor convenience. */
  transparent inline def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Trait =
    JvmAgentClient.connect[Trait](agentType, (a1, a2, a3))

  // Phantom instances are not currently supported by the CLI-backed JVM test client.
  final def getPhantom(@unused input: Any, @unused phantom: Uuid): Trait =
    throw new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet")
  final def getPhantom(@unused phantom: Uuid): Trait =
    throw new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet")
  final def getPhantom[A1, A2](@unused a1: A1, @unused a2: A2, @unused phantom: Uuid): Trait =
    throw new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet")
  final def getPhantom[A1, A2, A3](
    @unused a1: A1,
    @unused a2: A2,
    @unused a3: A3,
    @unused phantom: Uuid
  ): Trait =
    throw new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet")
}

object AgentCompanion {
  type InputOf[T] = T match {
    case BaseAgent[in] => in
  }
}
