package zio.blocks.scope

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import zio.blocks.scope.internal.ProxyFinalizer

/**
 * A resource pool that safely leases values as ordinary [[Resource]]s.
 */
sealed trait Pool[+A] {

  /**
   * Leases a value from the pool.
   *
   * The returned resource guarantees that leased values are always returned to
   * the pool (or destroyed if the pool is closed) when the scope exits.
   *
   * Allocating or acquiring the returned resource fails with
   * `IllegalStateException` if the pool is closed.
   */
  def lease: Resource[A]
}

object Pool {

  /**
   * Creates a pool from a regular resource.
   *
   * This mode does not reuse values: each lease acquires a fresh value and
   * finalizes it when released.
   */
  def unique[A](resource: Resource[A]): Resource[Pool[A]] =
    Resource.acquireRelease(new LivePool[A](resource, None))(_.close())

  /**
   * Creates a pool from a recyclable shared resource.
   */
  def shared[A](sharedResource: SharedResource[A]): Resource[Pool[A]] =
    Resource.acquireRelease(new LivePool[A](sharedResource.resource, Some(sharedResource.recycle)))(_.close())

  /**
   * Creates a pool from a resource and recycle callback.
   */
  def shared[A](resource: Resource[A])(recycle: A => Unit): Resource[Pool[A]] =
    shared(SharedResource(resource, recycle))

  /**
   * Creates a shared wire that provides a pool from a regular resource.
   */
  def wire[A](resource: Resource[A]): Wire.Shared[Any, Pool[A]] =
    Wire.Shared.fromFunction[Any, Pool[A]] { (finalizer, _) =>
      val pool = new LivePool[A](resource, None)
      finalizer.defer(pool.close())
      pool
    }

  /**
   * Creates a shared wire that provides a pool from a recyclable shared
   * resource.
   */
  def wire[A](sharedResource: SharedResource[A]): Wire.Shared[Any, Pool[A]] =
    Wire.Shared.fromFunction[Any, Pool[A]] { (finalizer, _) =>
      val pool = new LivePool[A](sharedResource.resource, Some(sharedResource.recycle))
      finalizer.defer(pool.close())
      pool
    }

  private final class LivePool[A](resource: Resource[A], recycle: Option[A => Unit]) extends Pool[A] {
    private val state = new AtomicReference[PoolState[A]](PoolState.Open(Nil))

    def lease: Resource[A] =
      Resource.acquireRelease(acquireEntry())(releaseEntry).map(_.value)

    private def acquireEntry(): Entry[A] = {
      if (isClosed) throw new IllegalStateException("Pool is closed")

      val entry =
        recycle match {
          case None    => createEntry(resource)
          case Some(_) => popIdle().getOrElse(createEntry(resource))
        }

      if (isClosed) {
        entry.destroy()
        throw new IllegalStateException("Pool is closed")
      }

      entry
    }

    private def releaseEntry(entry: Entry[A]): Unit =
      recycle match {
        case None =>
          entry.destroy()

        case Some(recycleValue) =>
          if (isClosed) entry.destroy()
          else {
            try recycleValue(entry.value)
            catch {
              case t: Throwable =>
                val finalizationError = destroyAndCapture(entry)
                if (finalizationError eq null) throw t
                finalizationError.addSuppressed(t)
                throw finalizationError
            }

            // Safe race: close may happen after recycle, so this must still
            // destroy the entry when Closed wins instead of reusing it.
            pushIdleOrDestroy(entry)
          }
      }

    private[scope] def close(): Unit =
      transitionToClosed() match {
        case Some(idleEntries) => runAll(idleEntries)
        case None              => ()
      }

    private def createEntry(resource: Resource[A]): Entry[A] = {
      val finalizer = new ProxyFinalizer
      try {
        val value = resource.make(finalizer)
        Entry(value, () => finalizer.runAll().orThrow())
      } catch {
        case t: Throwable =>
          finalizer.runAll().suppress(t)
          throw t
      }
    }

    private def popIdle(): Option[Entry[A]] = {
      var done   = false
      var result = Option.empty[Entry[A]]
      while (!done) {
        state.get() match {
          case PoolState.Closed =>
            done = true
          case open: PoolState.Open[A @unchecked] =>
            open.idle match {
              case Nil =>
                done = true
              case head :: tail =>
                done = state.compareAndSet(open, PoolState.Open(tail))
                if (done) result = Some(head)
            }
        }
      }
      result
    }

    private def pushIdleOrDestroy(entry: Entry[A]): Unit = {
      var done = false
      while (!done) {
        state.get() match {
          case PoolState.Closed =>
            entry.destroy()
            done = true
          case open: PoolState.Open[A @unchecked] =>
            done = state.compareAndSet(open, PoolState.Open(entry :: open.idle))
        }
      }
    }

    private def transitionToClosed(): Option[List[Entry[A]]] = {
      var done   = false
      var result = Option.empty[List[Entry[A]]]
      while (!done) {
        state.get() match {
          case PoolState.Closed =>
            done = true
          case open: PoolState.Open[A @unchecked] =>
            done = state.compareAndSet(open, PoolState.Closed)
            if (done) result = Some(open.idle)
        }
      }
      result
    }

    private def isClosed: Boolean = state.get() eq PoolState.Closed
  }

  private final case class Entry[A](value: A, destroyUnsafe: () => Unit) {
    private val destroyed = new AtomicBoolean(false)

    def destroy(): Unit =
      if (destroyed.compareAndSet(false, true)) destroyUnsafe()
  }

  private def runAll[A](entries: List[Entry[A]]): Unit = {
    var first: Throwable = null
    entries.foreach { entry =>
      try entry.destroy()
      catch {
        case t: Throwable =>
          if (first eq null) first = t
          else first.addSuppressed(t)
      }
    }
    if (first ne null) throw first
  }

  private def destroyAndCapture[A](entry: Entry[A]): Throwable =
    try {
      entry.destroy()
      null
    } catch {
      case t: Throwable => t
    }

  private sealed trait PoolState[+A]
  private object PoolState {
    final case class Open[A](idle: List[Entry[A]]) extends PoolState[A]
    case object Closed                             extends PoolState[Nothing]
  }
}
