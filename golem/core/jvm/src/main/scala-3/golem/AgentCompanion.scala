package golem

import golem.runtime.macros.{AgentClientMacro, AgentNameMacro}
import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.jvm.JvmAgentClient

import scala.annotation.unused
import scala.concurrent.Future

/**
 * JVM version of `AgentCompanion`.
 *
 * This companion provides a typed way to connect to agent instances from the
 * JVM (primarily useful for local tooling and tests).
 */
trait AgentCompanion[Trait <: AnyRef] extends AgentCompanionBase[Trait] {

  /** Golem agent type name, from `@agentDefinition("...")`. */
  transparent inline def typeName: String =
    AgentNameMacro.typeName[Trait]

  /** Reflected agent type (schemas + function names). */
  transparent inline def agentType: AgentType[Trait, ?] =
    AgentClientMacro.agentType[Trait]

  /** Connect to (or create) an agent instance from constructor input. */
  transparent inline def get[In](input: In): Future[Trait] =
    Future.successful(JvmAgentClient.connect[Trait](agentType, input))

  /** Unit-constructor convenience. */
  transparent inline def get(): Future[Trait] =
    Future.successful(JvmAgentClient.connect[Trait](agentType, ()))

  /** Tuple2 constructor convenience. */
  transparent inline def get[A1, A2](a1: A1, a2: A2): Future[Trait] =
    Future.successful(JvmAgentClient.connect[Trait](agentType, (a1, a2)))

  /** Tuple3 constructor convenience. */
  transparent inline def get[A1, A2, A3](a1: A1, a2: A2, a3: A3): Future[Trait] =
    Future.successful(JvmAgentClient.connect[Trait](agentType, (a1, a2, a3)))

  // Phantom instances are not currently supported by the CLI-backed JVM test client.
  final def getPhantom(@unused input: Any, @unused phantom: Uuid): Future[Trait] =
    Future.failed(new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet"))
  final def getPhantom(@unused phantom: Uuid): Future[Trait] =
    Future.failed(new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet"))
  final def getPhantom[A1, A2](@unused a1: A1, @unused a2: A2, @unused phantom: Uuid): Future[Trait] =
    Future.failed(new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet"))
  final def getPhantom[A1, A2, A3](
    @unused a1: A1,
    @unused a2: A2,
    @unused a3: A3,
    @unused phantom: Uuid
  ): Future[Trait] =
    Future.failed(new UnsupportedOperationException("JvmAgentClient does not support phantom agents yet"))
}
