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
import zio.blocks.streams.Stream

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

// ---- fs2 ----
import fs2.{Pure, Stream => Fs2Stream}

// ---- Pekko Streams ----
import org.apache.pekko.stream.scaladsl.Source

// ---- Kyo ----
import kyo.{Scope as _, Stream => KyoStream, *}

// ---- Ox ----
import ox.flow.Flow

/**
 * Benchmark: Stream pipeline construction cost — no evaluation.
 *
 * ==Motivation==
 * Measures ONLY the cost of constructing stream/pipeline objects. No elements
 * are ever pulled or materialized. This isolates the overhead of building the
 * pipeline description from the cost of executing it.
 *
 * ==What's measured==
 *   - ZB: `Stream.fromIterable(seq).map(_ + 1).map(_ + 1)...` — the Stream
 *     wrapper chain
 *   - fs2: `Fs2Stream.emits(seq).map(_ + 1)...` — pure stream object chain
 *   - Kyo: `KyoStream.init(seq).mapPure(_ + 1)...` — pure stream object chain
 *   - Pekko: `Source(seq).map(_ + 1)...` — source graph
 *
 * ==What's NOT measured==
 *   - Any element processing or sink execution
 *   - For ZB, `stream.start()` / Interpreter materialization (see
 *     `zb_setup_start_*`)
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
class StreamSetupBench {

  @Param(Array("10000"))
  var N: Int = uninitialized

  private var seq: Vector[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = seq = (0 until N).toVector

  // ===========================================================================
  // ZB setup — Stream object construction (no evaluation)
  // ===========================================================================

  private def zbChainMaps(n: Int): Stream[Nothing, Int] = {
    var s: Stream[Nothing, Int] = Stream.fromIterable(seq)
    var i                       = 0
    while (i < n) { s = s.map(_ + 1); i += 1 }
    s
  }

  @Benchmark def zb_map1(): Stream[Nothing, Int]   = zbChainMaps(1)
  @Benchmark def zb_map5(): Stream[Nothing, Int]   = zbChainMaps(5)
  @Benchmark def zb_map10(): Stream[Nothing, Int]  = zbChainMaps(10)
  @Benchmark def zb_map100(): Stream[Nothing, Int] = zbChainMaps(100)

  @Benchmark def zb_filter(): Stream[Nothing, Int] =
    Stream.fromIterable(seq).filter(_ % 2 == 0)

  @Benchmark def zb_filterMap(): Stream[Nothing, Int] =
    Stream.fromIterable(seq).filter(_ % 2 == 0).map(_ + 1)

  @Benchmark def zb_filterMapChain(): Stream[Nothing, Int] =
    Stream
      .fromIterable(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)

  @Benchmark def zb_takeDrop(): Stream[Nothing, Int] =
    Stream.fromIterable(seq).drop(N / 2).take((N / 2).toLong)

  @Benchmark def zb_drain(): Stream[Nothing, Int] =
    Stream.fromIterable(seq)

  // ===========================================================================
  // fs2 setup — Stream object construction (no evaluation)
  // ===========================================================================

  private def fs2ChainMaps(n: Int): Fs2Stream[Pure, Int] = {
    var s: Fs2Stream[Pure, Int] = Fs2Stream.emits(seq)
    var i                       = 0
    while (i < n) { s = s.map(_ + 1); i += 1 }
    s
  }

  @Benchmark def fs2_map1(): Fs2Stream[Pure, Int]   = fs2ChainMaps(1)
  @Benchmark def fs2_map5(): Fs2Stream[Pure, Int]   = fs2ChainMaps(5)
  @Benchmark def fs2_map10(): Fs2Stream[Pure, Int]  = fs2ChainMaps(10)
  @Benchmark def fs2_map100(): Fs2Stream[Pure, Int] = fs2ChainMaps(100)

  @Benchmark def fs2_filter(): Fs2Stream[Pure, Int] =
    Fs2Stream.emits(seq).filter(_ % 2 == 0)

  @Benchmark def fs2_filterMap(): Fs2Stream[Pure, Int] =
    Fs2Stream.emits(seq).filter(_ % 2 == 0).map(_ + 1)

  @Benchmark def fs2_filterMapChain(): Fs2Stream[Pure, Int] =
    Fs2Stream
      .emits(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)

  @Benchmark def fs2_takeDrop(): Fs2Stream[Pure, Int] =
    Fs2Stream.emits(seq).drop(N / 2).take((N / 2).toLong)

  @Benchmark def fs2_drain(): Fs2Stream[Pure, Int] =
    Fs2Stream.emits(seq)

  // ===========================================================================
  // Kyo setup — Stream object construction (no evaluation)
  // ===========================================================================

  private def kyoChainMaps(n: Int): KyoStream[Int, Any] = {
    var s: KyoStream[Int, Any] = KyoStream.init(seq)
    var i                      = 0
    while (i < n) { s = s.mapPure(_ + 1); i += 1 }
    s
  }

  @Benchmark def kyo_map1(): KyoStream[Int, Any]   = kyoChainMaps(1)
  @Benchmark def kyo_map5(): KyoStream[Int, Any]   = kyoChainMaps(5)
  @Benchmark def kyo_map10(): KyoStream[Int, Any]  = kyoChainMaps(10)
  @Benchmark def kyo_map100(): KyoStream[Int, Any] = kyoChainMaps(100)

  @Benchmark def kyo_filter(): KyoStream[Int, Any] =
    KyoStream.init(seq).filterPure(_ % 2 == 0)

  @Benchmark def kyo_filterMap(): KyoStream[Int, Any] =
    KyoStream.init(seq).filterPure(_ % 2 == 0).mapPure(_ + 1)

  @Benchmark def kyo_filterMapChain(): KyoStream[Int, Any] =
    KyoStream
      .init(seq)
      .filterPure(_ % 2 == 0)
      .mapPure(_ + 1)
      .filterPure(_ % 3 == 0)
      .mapPure(_ * 2)
      .filterPure(_ % 5 == 0)
      .mapPure(_ + 3)
      .filterPure(_ > 0)
      .mapPure(_ - 1)
      .filterPure(_ < 100000)
      .mapPure(_ + 7)

  @Benchmark def kyo_takeDrop(): KyoStream[Int, Any] =
    KyoStream.init(seq).drop(N / 2).take(N / 2)

  @Benchmark def kyo_drain(): KyoStream[Int, Any] =
    KyoStream.init(seq)

  // ===========================================================================
  // Pekko setup — Source + Flow construction (no materialization)
  // ===========================================================================

  private def pekkoChainMaps(n: Int): Source[Int, ?] = {
    var source = Source(seq)
    var i      = 0
    while (i < n) { source = source.map(_ + 1); i += 1 }
    source
  }

  @Benchmark def pekko_map1(): Source[Int, ?]   = pekkoChainMaps(1)
  @Benchmark def pekko_map5(): Source[Int, ?]   = pekkoChainMaps(5)
  @Benchmark def pekko_map10(): Source[Int, ?]  = pekkoChainMaps(10)
  @Benchmark def pekko_map100(): Source[Int, ?] = pekkoChainMaps(100)

  @Benchmark def pekko_filter(): Source[Int, ?] =
    Source(seq).filter(_ % 2 == 0)

  @Benchmark def pekko_filterMap(): Source[Int, ?] =
    Source(seq).filter(_ % 2 == 0).map(_ + 1)

  @Benchmark def pekko_filterMapChain(): Source[Int, ?] =
    Source(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)

  @Benchmark def pekko_takeDrop(): Source[Int, ?] =
    Source(seq).drop(N / 2).take(N / 2)

  @Benchmark def pekko_drain(): Source[Int, ?] =
    Source(seq)

  // ===========================================================================
  // Ox setup — Flow object construction (no evaluation)
  // ===========================================================================

  private def oxChainMaps(n: Int): Flow[Int] = {
    var f: Flow[Int] = Flow.fromIterable(seq)
    var i            = 0
    while (i < n) { f = f.map(_ + 1); i += 1 }
    f
  }

  @Benchmark def ox_map1(): Flow[Int]   = oxChainMaps(1)
  @Benchmark def ox_map5(): Flow[Int]   = oxChainMaps(5)
  @Benchmark def ox_map10(): Flow[Int]  = oxChainMaps(10)
  @Benchmark def ox_map100(): Flow[Int] = oxChainMaps(100)

  @Benchmark def ox_filter(): Flow[Int] =
    Flow.fromIterable(seq).filter(_ % 2 == 0)

  @Benchmark def ox_filterMap(): Flow[Int] =
    Flow.fromIterable(seq).filter(_ % 2 == 0).map(_ + 1)

  @Benchmark def ox_filterMapChain(): Flow[Int] =
    Flow
      .fromIterable(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)

  @Benchmark def ox_takeDrop(): Flow[Int] =
    Flow.fromIterable(seq).drop(N / 2).take(N / 2)

  @Benchmark def ox_drain(): Flow[Int] =
    Flow.fromIterable(seq)
}
