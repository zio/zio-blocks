package golem

/**
 * Utility guards that mirror the ergonomics of the JS SDK's guard helpers.
 *
 * Each `use*` method applies a configuration change on the host and returns a
 * guard that will automatically restore the previous value when `drop()` (or
 * `close()`) is invoked. The `with*` variants execute the supplied block with
 * the new setting and guarantee restoration.
 */
/** Scoped runtime controls for Scala.js agents. */
object Guards {
  def withPersistenceLevel[A](level: HostApi.PersistenceLevel)(block: => A): A =
    withGuard(usePersistenceLevel(level))(block)

  def usePersistenceLevel(level: HostApi.PersistenceLevel): PersistenceLevelGuard = {
    val original = HostApi.getOplogPersistenceLevel()
    HostApi.setOplogPersistenceLevel(level)
    new PersistenceLevelGuard(() => HostApi.setOplogPersistenceLevel(original))
  }

  def withRetryPolicy[A](policy: HostApi.RetryPolicy)(block: => A): A =
    withGuard(useRetryPolicy(policy))(block)

  def useRetryPolicy(policy: HostApi.RetryPolicy): RetryPolicyGuard = {
    val original = HostApi.getRetryPolicy()
    HostApi.setRetryPolicy(policy)
    new RetryPolicyGuard(() => HostApi.setRetryPolicy(original))
  }

  def withIdempotenceMode[A](flag: Boolean)(block: => A): A =
    withGuard(useIdempotenceMode(flag))(block)

  def useIdempotenceMode(flag: Boolean): IdempotenceModeGuard = {
    val original = HostApi.getIdempotenceMode()
    HostApi.setIdempotenceMode(flag)
    new IdempotenceModeGuard(() => HostApi.setIdempotenceMode(original))
  }

  def atomically[A](block: => A): A =
    withGuard(markAtomicOperation())(block)

  def markAtomicOperation(): AtomicOperationGuard = {
    val begin = HostApi.markBeginOperation()
    new AtomicOperationGuard(() => HostApi.markEndOperation(begin))
  }

  private def withGuard[A, G <: Guard](guard: => G)(block: => A): A = {
    val active = guard
    try block
    finally active.drop()
  }

  sealed abstract class Guard private[golem] (release: () => Unit) extends AutoCloseable {
    private var active = true

    final override def close(): Unit = drop()

    final def drop(): Unit =
      if (active) {
        active = false
        release()
      }
  }

  final class PersistenceLevelGuard private[golem] (release: () => Unit) extends Guard(release)

  final class RetryPolicyGuard private[golem] (release: () => Unit) extends Guard(release)

  final class IdempotenceModeGuard private[golem] (release: () => Unit) extends Guard(release)

  final class AtomicOperationGuard private[golem] (release: () => Unit) extends Guard(release)
}
