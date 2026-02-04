package golem

import scala.annotation.unused

/**
 * JVM stub for `AgentCompanion` when compiling on Scala 2.
 *
 * The CLI-backed JVM test client is Scala 3-only (for now). This keeps
 * `+compile` working while the primary Scala SDK story remains Scala.js
 * (agent-to-agent calls inside Golem).
 */
trait AgentCompanion[Trait <: AnyRef] extends AgentCompanionBase[Trait] {
  private def unsupported[A]: A =
    throw new UnsupportedOperationException(
      "AgentCompanion JVM test client is Scala 3-only. " +
        "This Scala 2 JVM stub exists only to keep cross compilation working."
    )

  // Minimal overload set used by quickstart/examples.
  final def get(@unused input: Any): Trait                                         = unsupported
  final def get(): Trait                                                           = unsupported
  final def get[A1, A2](@unused a1: A1, @unused a2: A2): Trait                     = unsupported
  final def get[A1, A2, A3](@unused a1: A1, @unused a2: A2, @unused a3: A3): Trait = unsupported

  final def getPhantom(@unused input: Any, @unused phantom: Uuid): Trait                     = unsupported
  final def getPhantom(@unused phantom: Uuid): Trait                                         = unsupported
  final def getPhantom[A1, A2](@unused a1: A1, @unused a2: A2, @unused phantom: Uuid): Trait = unsupported
  final def getPhantom[A1, A2, A3](
    @unused a1: A1,
    @unused a2: A2,
    @unused a3: A3,
    @unused phantom: Uuid
  ): Trait = unsupported
}
