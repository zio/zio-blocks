package cloud.golem.sdk

import scala.annotation.unused
import scala.concurrent.Future

/**
 * JVM stub for `AgentCompanion` when compiling on Scala 2.
 *
 * The CLI-backed JVM test client is Scala 3-only (for now). This keeps
 * `+compile` working while the primary Scala SDK story remains Scala.js
 * (agent-to-agent calls inside Golem).
 */
trait AgentCompanion[Trait <: AnyRef] extends AgentCompanionBase[Trait] {
  private def unsupported[A]: Future[A] =
    Future.failed(
      new UnsupportedOperationException(
        "AgentCompanion JVM test client is Scala 3-only. " +
          "This Scala 2 JVM stub exists only to keep cross compilation working."
      )
    )

  // Minimal overload set used by quickstart/examples.
  final def get(@unused input: Any): Future[Trait]                                         = unsupported
  final def get(): Future[Trait]                                                           = unsupported
  final def get[A1, A2](@unused a1: A1, @unused a2: A2): Future[Trait]                     = unsupported
  final def get[A1, A2, A3](@unused a1: A1, @unused a2: A2, @unused a3: A3): Future[Trait] = unsupported

  final def getPhantom(@unused input: Any, @unused phantom: Uuid): Future[Trait]                     = unsupported
  final def getPhantom(@unused phantom: Uuid): Future[Trait]                                         = unsupported
  final def getPhantom[A1, A2](@unused a1: A1, @unused a2: A2, @unused phantom: Uuid): Future[Trait] = unsupported
  final def getPhantom[A1, A2, A3](
    @unused a1: A1,
    @unused a2: A2,
    @unused a3: A3,
    @unused phantom: Uuid
  ): Future[Trait] = unsupported
}
