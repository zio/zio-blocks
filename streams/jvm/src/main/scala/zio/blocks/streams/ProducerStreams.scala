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

package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.ringbuffer.SpscRingBuffer
import zio.blocks.streams.internal.{ByteProducerReader, StreamError}
import zio.blocks.streams.io.Reader

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * A push-based sink that a producer uses to emit elements into a stream.
 *
 * '''SPSC contract:''' Only one thread may call [[emit]] at a time. The
 * implementation uses a single-producer single-consumer ring buffer. Concurrent
 * [[emit]] calls from multiple threads will corrupt the buffer.
 *
 * '''Contract:''' Producers MUST call either [[end]] or [[fail]] when done.
 * Failing to do so (e.g. due to an uncaught exception on the producer thread)
 * will cause the consumer to block indefinitely. Use
 * [[ProducerStreams.fromProducerSimple]] for automatic lifecycle management.
 *
 * [[end]] and [[fail]] are idempotent — the first call wins; subsequent calls
 * are no-ops.
 *
 * @note
 *   [[emit]] does not accept `null` values; passing `null` throws
 *   [[NullPointerException]].
 */
trait ProducerSink[-A, -E] {

  /**
   * Emit a single element. Returns `false` if the stream is closed/cancelled.
   *
   * @throws NullPointerException
   *   if `a` is `null`
   */
  def emit(a: A): Boolean

  /**
   * Emit a chunk of elements. Returns `false` if the stream is closed/cancelled
   * before all elements have been emitted.
   *
   * The default implementation iterates the chunk and calls [[emit(a:A)*]] for
   * each element, stopping on the first failure. Subclasses may override for
   * more efficient bulk transfer (e.g. putting the whole chunk into a ring
   * buffer as a single reference).
   */
  def emit(chunk: Chunk[A]): Boolean = {
    var i = 0
    while (i < chunk.length) {
      if (!emit(chunk(i))) return false
      i += 1
    }
    true
  }

  /** Signal normal completion. Idempotent — second call is a no-op. */
  def end(): Unit

  /** Signal a typed error. Idempotent — second call is a no-op. */
  def fail(error: E): Unit
}

/**
 * JVM-only stream constructor that bridges a push-based producer into a
 * pull-based [[Stream]]. Uses a lock-free
 * [[zio.blocks.ringbuffer.SpscRingBuffer]] with spin-wait + park blocking
 * wrappers internally.
 */
object ProducerStreams {

  // Virtual-thread starter (JDK 21+) with daemon-thread fallback (JDK 17).
  // Uses MethodHandles.publicLookup() on the public Thread.Builder interface
  // so no --add-opens or setAccessible is needed.
  private val startThread: Runnable => Thread =
    try {
      val mh = java.lang.invoke.MethodHandles
        .publicLookup()
        .findVirtual(
          Class.forName("java.lang.Thread$Builder"),
          "start",
          java.lang.invoke.MethodType.methodType(classOf[Thread], classOf[Runnable])
        )
      val builder = classOf[Thread].getMethod("ofVirtual").invoke(null)
      val bound   = mh.bindTo(builder)
      (r: Runnable) => bound.invoke(r).asInstanceOf[Thread]
    } catch {
      case _: Throwable =>
        (r: Runnable) => { val t = new Thread(r); t.setDaemon(true); t.start(); t }
    }

  /**
   * Creates a stream from a push-based producer. The `register` callback
   * receives a [[ProducerSink]] and returns a cancel callback (`() => Unit`)
   * that is invoked when the consumer closes the stream.
   *
   * If `register` throws synchronously, the exception is delivered to the
   * consumer as a stream error (the consumer will not deadlock).
   *
   * @param register
   *   Called during stream compilation. Receives a sink; returns a cancel
   *   callback.
   * @param knownLength
   *   Optional metadata hint for the number of elements.
   * @param bufferSize
   *   Internal queue capacity (default 256). Must be positive.
   */
  def fromProducer[E, A](
    register: ProducerSink[A, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, A](
      () => {
        val reader = new ProducerReader[A](bufferSize)
        try {
          val cancel = register(reader.sink)
          reader.setCancelCallback(cancel)
        } catch {
          case e: Throwable =>
            reader.sink.fail(e)
        }
        reader
      },
      "ProducerStreams.fromProducer(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  /**
   * Convenience wrapper around [[fromProducer]] that manages the producer
   * lifecycle automatically: runs `produce` on a new virtual thread, calls
   * [[ProducerSink.end]] on normal return, and [[ProducerSink.fail]] if
   * `produce` throws. Cancellation interrupts the producer thread.
   *
   * Use this instead of [[fromProducer]] when custom thread management is not
   * needed.
   */
  def fromProducerSimple[E, A](
    produce: ProducerSink[A, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, A] =
    fromProducer[E, A](
      register = { (sink: ProducerSink[A, E]) =>
        val thread = startThread { () =>
          try {
            produce(sink)
            sink.end()
          } catch {
            case e: Throwable =>
              sink.asInstanceOf[ProducerSink[A, Any]].fail(e)
          }
        }
        () => thread.interrupt()
      },
      knownLength = knownLength,
      bufferSize = bufferSize
    )

  /**
   * Creates a byte stream from a push-based producer. Optimized for
   * [[Chunk[Byte]]] emission — each chunk is transferred as a single reference
   * through the internal ring buffer, and bytes are pulled without boxing via
   * [[Reader.readByte()]].
   *
   * '''SPSC contract:''' The caller must ensure that only one thread calls
   * [[ProducerSink.emit]] at a time.
   */
  def fromProducerBytes[E](
    register: ProducerSink[Byte, E] => () => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] = {
    require(bufferSize > 0, s"bufferSize must be positive, got: $bufferSize")
    val theKnownLength = knownLength
    new Stream.FromReader[E, Byte](
      () => {
        val reader = new ByteProducerReader(bufferSize)
        try {
          val cancel = register(reader.sink)
          reader.setCancelCallback(cancel)
        } catch {
          case e: Throwable =>
            reader.sink.fail(e)
        }
        reader
      },
      "ProducerStreams.fromProducerBytes(...)"
    ) {
      override def knownLength: Option[Long] = theKnownLength
    }
  }

  /**
   * Convenience wrapper around [[fromProducerBytes]] that manages the producer
   * lifecycle automatically: runs `produce` on a virtual thread, calls
   * [[ProducerSink.end]] on normal return, and [[ProducerSink.fail]] if
   * `produce` throws. Cancellation interrupts the producer thread.
   */
  def fromProducerBytesSimple[E](
    produce: ProducerSink[Byte, E] => Unit,
    knownLength: Option[Long] = None,
    bufferSize: Int = 256
  ): Stream[E, Byte] =
    fromProducerBytes[E](
      register = { (sink: ProducerSink[Byte, E]) =>
        val thread = startThread { () =>
          try {
            produce(sink)
            sink.end()
          } catch {
            case e: Throwable =>
              sink.asInstanceOf[ProducerSink[Byte, Any]].fail(e)
          }
        }
        () => thread.interrupt()
      },
      knownLength = knownLength,
      bufferSize = bufferSize
    )

  // -- Internal sentinels --

  private[streams] val EndMarker: AnyRef = new AnyRef {
    override def toString: String = "EndMarker"
  }

  private[streams] final class FailMarker(val error: Any) {
    override def toString: String = s"FailMarker($error)"
  }

  // Poison pill offered to unblock a producer stuck on queue.put()
  private[streams] val PoisonPill: AnyRef = new AnyRef {
    override def toString: String = "PoisonPill"
  }

  // -- Blocking wrappers for SpscRingBuffer --
  // Spin 64 iterations with Thread.onSpinWait(), then LockSupport.parkNanos(1μs),
  // loop until the operation succeeds. After success, unpark the peer thread.

  private[streams] def blockingOffer(rb: SpscRingBuffer[AnyRef], item: AnyRef, consumerThread: => Thread): Unit = {
    if (rb.offer(item)) return
    // Slow path: queue was full — spin then park, unpark peer after
    var spins = 0
    while (!rb.offer(item)) {
      if (spins < 64) {
        Thread.onSpinWait()
        spins += 1
      } else {
        LockSupport.parkNanos(1000L)
        if (Thread.interrupted()) throw new InterruptedException()
      }
    }
    val ct = consumerThread
    if (ct ne null) LockSupport.unpark(ct)
  }

  private[streams] def blockingTake(rb: SpscRingBuffer[AnyRef], producerThread: => Thread): AnyRef = {
    val fast = rb.take()
    if (fast ne null) return fast
    var spins  = 0
    var result = rb.take()
    while (result eq null) {
      if (spins < 64) {
        Thread.onSpinWait()
        spins += 1
      } else {
        LockSupport.parkNanos(1000L)
      }
      result = rb.take()
    }
    val pt = producerThread
    if (pt ne null) LockSupport.unpark(pt)
    result
  }

  // -- Capacity rounding --

  private[streams] def nextPowerOf2(n: Int): Int = {
    require(n > 0 && n <= (1 << 30), s"capacity must be between 1 and ${1 << 30}, got: $n")
    if ((n & (n - 1)) == 0) n else Integer.highestOneBit(n) << 1
  }

  /**
   * A [[Reader]] backed by a [[SpscRingBuffer]]. The producer pushes elements
   * (or whole [[Chunk]]s) via the associated [[ProducerSink]]; the consumer
   * pulls via `read()`, draining chunks element-by-element.
   */
  private final class ProducerReader[A](bufferSize: Int) extends Reader[A] {

    private val rb: SpscRingBuffer[AnyRef]           = new SpscRingBuffer[AnyRef](nextPowerOf2(bufferSize))
    private val terminated: AtomicBoolean            = new AtomicBoolean(false)
    private val cancelled: AtomicBoolean             = new AtomicBoolean(false)
    @volatile private var cancelCallback: () => Unit = null
    @volatile private var _closed: Boolean           = false
    @volatile private var _producerThread: Thread    = null
    @volatile private var _consumerThread: Thread    = null

    // Consumer-thread-only state for chunk draining (no volatile needed)
    private var currentChunk: Chunk[_ <: A] = null
    private var chunkIndex: Int             = 0

    def setCancelCallback(cb: () => Unit): Unit = cancelCallback = cb

    val sink: ProducerSink[A, Any] = new ProducerSink[A, Any] {
      def emit(a: A): Boolean = {
        if (a == null) throw new NullPointerException("ProducerSink.emit does not accept null values")
        if (terminated.get() || cancelled.get()) return false
        if (_producerThread eq null) _producerThread = Thread.currentThread()
        try {
          blockingOffer(rb, a.asInstanceOf[AnyRef], _consumerThread)
          !cancelled.get()
        } catch {
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
            false
        }
      }

      override def emit(chunk: Chunk[A @unchecked]): Boolean = {
        if (chunk == null) throw new NullPointerException("ProducerSink.emit does not accept null values")
        if (chunk.isEmpty) return true
        if (terminated.get() || cancelled.get()) return false
        if (_producerThread eq null) _producerThread = Thread.currentThread()
        try {
          blockingOffer(rb, chunk.asInstanceOf[AnyRef], _consumerThread)
          !cancelled.get()
        } catch {
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
            false
        }
      }

      def end(): Unit =
        if (terminated.compareAndSet(false, true)) {
          try blockingOffer(rb, EndMarker, _consumerThread)
          catch { case _: InterruptedException => Thread.currentThread().interrupt() }
        }

      def fail(error: Any): Unit =
        if (terminated.compareAndSet(false, true)) {
          try blockingOffer(rb, new FailMarker(error), _consumerThread)
          catch { case _: InterruptedException => Thread.currentThread().interrupt() }
        }
    }

    def isClosed: Boolean = _closed

    def read[A1 >: A](sentinel: A1): A1 = {
      if (_consumerThread eq null) _consumerThread = Thread.currentThread()
      // Drain current chunk first
      if (currentChunk != null) {
        val a = currentChunk(chunkIndex).asInstanceOf[A1]
        chunkIndex += 1
        if (chunkIndex >= currentChunk.length) currentChunk = null
        return a
      }
      if (_closed) return sentinel
      val item = blockingTake(rb, _producerThread)
      item match {
        case EndMarker =>
          _closed = true
          sentinel
        case fm: FailMarker =>
          _closed = true
          throw new StreamError(fm.error)
        case _ if item eq PoisonPill =>
          _closed = true
          sentinel
        case chunk: Chunk[_] =>
          val c = chunk.asInstanceOf[Chunk[A]]
          if (c.isEmpty) {
            read(sentinel)
          } else {
            if (c.length > 1) {
              currentChunk = c
              chunkIndex = 1
            }
            c(0).asInstanceOf[A1]
          }
        case _ =>
          item.asInstanceOf[A1]
      }
    }

    def close(): Unit =
      if (!_closed) {
        _closed = true
        if (cancelled.compareAndSet(false, true)) {
          terminated.set(true) // prevents end()/fail() from blocking
          val cb = cancelCallback
          rb.offer(PoisonPill) // non-blocking, best effort
          val pt = _producerThread
          if (pt ne null) LockSupport.unpark(pt)
          if (cb != null) {
            try cb()
            catch { case _: Throwable => () }
          }
        }
      }
  }
}
