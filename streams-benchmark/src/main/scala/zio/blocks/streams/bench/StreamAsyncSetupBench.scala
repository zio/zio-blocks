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

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration as ScalaDuration
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream as Fs2Stream
import kyo.{Async as KyoAsync, KyoApp, Stream as KyoStream, *}
import kyo.AllowUnsafe.embrace.danger
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink as PekkoSink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import zio.blocks.streams.Stream

/**
 * Ready-effect graph-construction counterparts for supported cells in
 * [[StreamSetupBench]].
 *
 * The timed method only constructs a stream graph. [[checksum]] materializes
 * that graph for untimed fixture validation. Every included provider builds a
 * source with one effectful transformation per element returning an
 * already-successful value. This is not a buffered boundary or genuine
 * suspension. Ox 1.0.6 has no corresponding public operator and is explicitly
 * N/A in this matrix.
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
@State(org.openjdk.jmh.annotations.Scope.Thread)
class StreamAsyncSetupBench {
  @Param(Array("zb", "fs2", "kyo", "pekko"))
  var library: String = uninitialized

  @Param(Array("map1", "map5", "map10", "map100", "filter", "filterMap", "filterMapChain", "takeDrop", "drain"))
  var operation: String = uninitialized

  @Param(Array("10000"))
  var N: Int = uninitialized

  private var pekkoSystem: ActorSystem = uninitialized
  private var pekkoMat: Materializer   = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    if (library == "pekko") {
      pekkoSystem = ActorSystem("async-setup-bench")
      pekkoMat = SystemMaterializer(pekkoSystem).materializer
    }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  @Benchmark
  def construct(): AnyRef = library match {
    case "zb"    => buildZb(operation)
    case "fs2"   => buildFs2(operation)
    case "kyo"   => buildKyo(operation)
    case "pekko" => buildPekko(operation)
  }

  def checksum(): Long = {
    val stream = construct()
    library match {
      case "zb"  => AsyncParity.fold(stream.asInstanceOf[Stream[Nothing, Int]])
      case "fs2" =>
        stream.asInstanceOf[Fs2Stream[IO, Int]].compile.fold(0L)(_ + _).unsafeRunSync()
      case "kyo" =>
        KyoApp.Unsafe
          .runAndBlock(kyo.Duration.Infinity)(stream.asInstanceOf[KyoStream[Int, KyoAsync]].foldPure(0L)(_ + _))
          .getOrThrow
      case "pekko" =>
        Await.result(
          stream.asInstanceOf[Source[Int, ?]].runWith(PekkoSink.fold(0L)(_ + _))(pekkoMat),
          ScalaDuration.Inf
        )
    }
  }

  private def buildZb(op: String): Stream[Nothing, Int] = {
    def source       = AsyncParity.readyEffectSource(0, N)
    def maps(n: Int) = {
      var stream = source; var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "map1"           => maps(1)
      case "map5"           => maps(5)
      case "map10"          => maps(10)
      case "map100"         => maps(100)
      case "filter"         => source.filter(_ % 2 == 0)
      case "filterMap"      => source.filter(_ % 2 == 0).map(_ + 1)
      case "filterMapChain" => filterMapChainZb(source)
      case "takeDrop"       => source.drop(N / 2).take((N / 2).toLong)
      case "drain"          => source
    }
  }

  private def filterMapChainZb(stream: Stream[Nothing, Int]): Stream[Nothing, Int] =
    stream
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

  private def buildFs2(op: String): Fs2Stream[IO, Int] = {
    def source       = Fs2Stream.range(0, N).covary[IO].evalMap(IO.pure)
    def maps(n: Int) = {
      var stream = source; var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "map1"           => maps(1)
      case "map5"           => maps(5)
      case "map10"          => maps(10)
      case "map100"         => maps(100)
      case "filter"         => source.filter(_ % 2 == 0)
      case "filterMap"      => source.filter(_ % 2 == 0).map(_ + 1)
      case "filterMapChain" =>
        source
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
      case "takeDrop" => source.drop(N / 2).take((N / 2).toLong)
      case "drain"    => source
    }
  }

  private def buildKyo(op: String): KyoStream[Int, KyoAsync] = {
    def source: KyoStream[Int, KyoAsync] = KyoStream.range(0, N).map((i: Int) => (i: Int < KyoAsync))
    def maps(n: Int)                     = {
      var stream = source; var i = 0
      while (i < n) { stream = stream.mapPure(_ + 1); i += 1 }
      stream
    }
    op match {
      case "map1"           => maps(1)
      case "map5"           => maps(5)
      case "map10"          => maps(10)
      case "map100"         => maps(100)
      case "filter"         => source.filterPure(_ % 2 == 0)
      case "filterMap"      => source.filterPure(_ % 2 == 0).mapPure(_ + 1)
      case "filterMapChain" =>
        source
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
      case "takeDrop" => source.drop(N / 2).take(N / 2)
      case "drain"    => source
    }
  }

  private def buildPekko(op: String): Source[Int, ?] = {
    def source: Source[Int, ?] = Source(0 until N).mapAsync(1)(i => Future.successful(i))
    def maps(n: Int)           = {
      var stream = source; var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "map1"           => maps(1)
      case "map5"           => maps(5)
      case "map10"          => maps(10)
      case "map100"         => maps(100)
      case "filter"         => source.filter(_ % 2 == 0)
      case "filterMap"      => source.filter(_ % 2 == 0).map(_ + 1)
      case "filterMapChain" =>
        source
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
      case "takeDrop" => source.drop(N / 2).take(N / 2)
      case "drain"    => source
    }
  }
}
