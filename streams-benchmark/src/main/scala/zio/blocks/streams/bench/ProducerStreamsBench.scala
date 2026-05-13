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

package zio.blocks.streams.bench

import org.openjdk.jmh.annotations._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.{NioStreams, ProducerStreams, ProducerSink, Stream}

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

/**
 * Benchmark: ProducerStreams push-to-pull bridge throughput.
 *
 * ==Motivation==
 * Measures the cost of bridging push-based producers into pull-based ZB streams
 * via [[ProducerStreams.fromProducer]] and
 * [[ProducerStreams.fromProducerSimple]], compared against pure pull baselines.
 *
 * ==Sections==
 *   - '''A. Throughput comparison (4)''' — raw emit throughput vs pull
 *     baselines
 *   - '''B. Pipeline overhead (4)''' — producer streams with downstream
 *     filter/map
 *   - '''C. Buffer size impact (4)''' — varying buffer sizes (1, 64, 256, 1024)
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgsPrepend = Array(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
  )
)
@State(Scope.Thread)
class ProducerStreamsBench {

  @Param(Array("10000"))
  var N: Int = uninitialized

  private var seq: Vector[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    seq = (0 until N).toVector

  // ===========================================================================
  // Fold helper
  // ===========================================================================

  private def zbFold(s: Stream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(_)  => 0L
    }

  // ===========================================================================
  // Producer helpers — fresh per invocation (measures real-world cost)
  // ===========================================================================

  private def producerStream(bufferSize: Int): Stream[Nothing, Int] =
    ProducerStreams.fromProducer[Nothing, Int](
      register = { sink =>
        val thread = new Thread(() => {
          var i = 0
          while (i < N) {
            sink.emit(i)
            i += 1
          }
          sink.end()
        })
        thread.setDaemon(true)
        thread.start()
        () => thread.interrupt()
      },
      bufferSize = bufferSize
    )

  private def producerSimpleStream(bufferSize: Int): Stream[Nothing, Int] =
    ProducerStreams.fromProducerSimple[Nothing, Int](
      produce = { sink =>
        var i = 0
        while (i < N) {
          sink.emit(i)
          i += 1
        }
      },
      bufferSize = bufferSize
    )

  // ===========================================================================
  // A. Throughput comparison (4 benchmarks)
  // ===========================================================================

  @Benchmark def baseline_range(): Long =
    zbFold(Stream.range(0, N))

  @Benchmark def baseline_iterable(): Long =
    zbFold(Stream.fromIterable(seq))

  @Benchmark def producer_fromProducer(): Long =
    zbFold(producerStream(64))

  @Benchmark def producer_fromProducerSimple(): Long =
    zbFold(producerSimpleStream(64))

  // ===========================================================================
  // B. Pipeline overhead (4 benchmarks)
  // ===========================================================================

  @Benchmark def baseline_range_filterMap(): Long =
    zbFold(Stream.range(0, N).filter(_ % 2 != 0).map(_ + 1))

  @Benchmark def baseline_iterable_filterMap(): Long =
    zbFold(Stream.fromIterable(seq).filter(_ % 2 != 0).map(_ + 1))

  @Benchmark def producer_fromProducer_filterMap(): Long =
    zbFold(producerStream(64).filter(_ % 2 != 0).map(_ + 1))

  @Benchmark def producer_fromProducerSimple_filterMap(): Long =
    zbFold(producerSimpleStream(64).filter(_ % 2 != 0).map(_ + 1))

  // ===========================================================================
  // C. Buffer size impact (4 benchmarks)
  // ===========================================================================

  @Benchmark def producer_bufferSize_1(): Long =
    zbFold(producerSimpleStream(1))

  @Benchmark def producer_bufferSize_64(): Long =
    zbFold(producerSimpleStream(64))

  @Benchmark def producer_bufferSize_256(): Long =
    zbFold(producerSimpleStream(256))

  @Benchmark def producer_bufferSize_1024(): Long =
    zbFold(producerSimpleStream(1024))

  // ===========================================================================
  // D. Chunk emission throughput (4 benchmarks)
  // ===========================================================================

  private def produceChunks(sink: ProducerSink[Int, Nothing], chunkSize: Int, total: Int): Unit = {
    val chunk   = Chunk.fromArray(Array.tabulate(chunkSize)(identity))
    var emitted = 0
    while (emitted + chunkSize <= total) {
      sink.emit(chunk)
      emitted += chunkSize
    }
    val remaining = total - emitted
    if (remaining > 0) {
      sink.emit(Chunk.fromArray(Array.tabulate(remaining)(identity)))
    }
  }

  @Benchmark def producer_chunk_10(): Long =
    zbFold(
      ProducerStreams.fromProducerSimple[Nothing, Int](
        produce = { sink => produceChunks(sink, 10, N) },
        bufferSize = 256
      )
    )

  @Benchmark def producer_chunk_100(): Long =
    zbFold(
      ProducerStreams.fromProducerSimple[Nothing, Int](
        produce = { sink => produceChunks(sink, 100, N) },
        bufferSize = 256
      )
    )

  @Benchmark def producer_chunk_1000(): Long =
    zbFold(
      ProducerStreams.fromProducerSimple[Nothing, Int](
        produce = { sink => produceChunks(sink, 1000, N) },
        bufferSize = 256
      )
    )

  @Benchmark def producer_chunk_single(): Long =
    zbFold(
      ProducerStreams.fromProducerSimple[Nothing, Int](
        produce = { sink => produceChunks(sink, 1, N) },
        bufferSize = 256
      )
    )

  // ===========================================================================
  // E. Byte stream throughput (3 benchmarks)
  // ===========================================================================

  private def zbByteFold(s: Stream[Nothing, Byte]): Long =
    s.runFold(0L)((acc, b) => acc + (b & 0xff)) match {
      case Right(v) => v
      case Left(_)  => 0L
    }

  @Benchmark def producer_bytes_chunk(): Long = {
    val chunkSize = 1024
    zbByteFold(
      ProducerStreams.fromProducerBytesSimple[Nothing](
        produce = { sink =>
          val chunk   = Chunk.fromArray(Array.fill[Byte](chunkSize)(1))
          var emitted = 0
          while (emitted < N) { sink.emit(chunk); emitted += chunkSize }
        },
        bufferSize = 256
      )
    )
  }

  @Benchmark def producer_bytes_individual(): Long =
    zbByteFold(
      ProducerStreams.fromProducerBytesSimple[Nothing](
        produce = { sink =>
          var i = 0
          while (i < N) { sink.emit(i.toByte); i += 1 }
        },
        bufferSize = 256
      )
    )

  @Benchmark def baseline_bytebuffer(): Long = {
    val buf = java.nio.ByteBuffer.allocate(N)
    var i   = 0
    while (i < N) { buf.put(1.toByte); i += 1 }
    buf.flip()
    zbByteFold(NioStreams.fromByteBuffer(buf))
  }

}
