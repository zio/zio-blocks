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
import zio.blocks.async.block
import zio.blocks.streams.Stream

/**
 * One-ready-effect-per-source-element counterparts for supported cells in
 * [[StreamEvalBench]].
 *
 * Each source has one effectful transformation per element returning an
 * already-successful value: `mapAsync(Async.succeed)` for ZIO Blocks,
 * `evalMap(IO.pure)` for fs2, a ready effect value through an effectful Kyo
 * stream map, and `mapAsync(Future.successful)` for Pekko. This is not a
 * buffered boundary or genuine suspension. Ox 1.0.6 has no corresponding
 * operator, so it is N/A here and remains in the native-source suites.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgsPrepend = Array(
    "-Xss16m",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
  )
)
@State(org.openjdk.jmh.annotations.Scope.Thread)
class StreamAsyncEvalBench {
  @Param(
    Array(
      "zb:singleton",
      "zb:drain",
      "zb:map_1",
      "zb:filter_1",
      "zb:flatMap_1",
      "zb:takeDrop",
      "zb:mapFilterFlatMap",
      "zb:mixed_1",
      "zb:mixed_2",
      "zb:mixed_3",
      "zb:nested_flatMap",
      "zb:nested_concat",
      "fs2:singleton",
      "fs2:drain",
      "fs2:map_1",
      "fs2:filter_1",
      "fs2:flatMap_1",
      "fs2:takeDrop",
      "fs2:mapFilterFlatMap",
      "fs2:mixed_1",
      "fs2:mixed_2",
      "fs2:mixed_3",
      "fs2:nested_flatMap",
      "fs2:nested_concat",
      "kyo:singleton",
      "kyo:drain",
      "kyo:map_1",
      "kyo:filter_1",
      "kyo:flatMap_1",
      "kyo:takeDrop",
      "kyo:mapFilterFlatMap",
      "kyo:mixed_1",
      "kyo:mixed_2",
      "kyo:mixed_3",
      "kyo:nested_flatMap",
      "kyo:nested_concat",
      "pekko:singleton",
      "pekko:drain",
      "pekko:map_1",
      "pekko:filter_1",
      "pekko:flatMap_1",
      "pekko:takeDrop",
      "pekko:mapFilterFlatMap",
      "pekko:mixed_1",
      "pekko:mixed_2",
      "pekko:mixed_3",
      "pekko:nested_flatMap",
      "pekko:nested_concat"
    )
  )
  var cell: String = uninitialized

  @Param(Array("10000"))
  var N: Int = uninitialized

  private var library: String                     = uninitialized
  private var operation: String                   = uninitialized
  private var zb: Stream[Nothing, Int]            = uninitialized
  private var fs2: Fs2Stream[IO, Int]             = uninitialized
  private var kyoStream: KyoStream[Int, KyoAsync] = uninitialized
  private var pekko: Source[Int, ?]               = uninitialized

  private var pekkoSystem: ActorSystem = uninitialized
  private var pekkoMat: Materializer   = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val separator = cell.indexOf(':')
    library = cell.substring(0, separator)
    operation = cell.substring(separator + 1)
    library match {
      case "zb"    => zb = buildZb(operation)
      case "fs2"   => fs2 = buildFs2(operation)
      case "kyo"   => kyoStream = buildKyo(operation)
      case "pekko" =>
        pekkoSystem = ActorSystem("async-eval-bench")
        pekkoMat = SystemMaterializer(pekkoSystem).materializer
        pekko = buildPekko(operation)
    }
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  @Benchmark
  def evaluate(): Long = if (operation == "drain") drain()
  else
    library match {
      case "zb"    => AsyncParity.fold(zb)
      case "fs2"   => fs2.compile.fold(0L)(_ + _).unsafeRunSync()
      case "kyo"   => KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(kyoStream.foldPure(0L)(_ + _)).getOrThrow
      case "pekko" => Await.result(pekko.runWith(PekkoSink.fold(0L)(_ + _))(pekkoMat), ScalaDuration.Inf)
    }

  private def drain(): Long = {
    library match {
      case "zb"    => zb.runDrainAsync.block.toOption.get
      case "fs2"   => fs2.compile.drain.unsafeRunSync()
      case "kyo"   => KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(kyoStream.discard).getOrThrow
      case "pekko" => Await.result(pekko.runWith(PekkoSink.ignore)(pekkoMat), ScalaDuration.Inf)
    }
    0L
  }

  private def buildZb(op: String): Stream[Nothing, Int] = {
    def source(from: Int, until: Int) = AsyncParity.readyEffectSource(from, until)
    op match {
      case "singleton"        => source(42, 43)
      case "drain"            => source(0, N)
      case "map_1"            => source(0, N).map(_ + 1)
      case "filter_1"         => source(0, N).filter(_ % 2 != 0)
      case "flatMap_1"        => source(0, 100).flatMap(_ => source(0, 100))
      case "takeDrop"         => source(0, N).drop(N / 2).take((N / 2).toLong)
      case "mapFilterFlatMap" => source(0, N).map(_ + 1).filter(_ % 2 != 0).flatMap(_ => source(0, 100))
      case "mixed_1"          => AsyncParity.chainMixed(source(0, 200), 1, 100)
      case "mixed_2"          => AsyncParity.chainMixed(source(0, 100), 2, 20)
      case "mixed_3"          => AsyncParity.chainMixed(source(0, 100), 3, 10)
      case "nested_flatMap"   =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.flatMap(_ => source(1, 2)); i += 1 }
        stream
      case "nested_concat" =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream ++ source(1, 2); i += 1 }
        stream
    }
  }

  private def buildFs2(op: String): Fs2Stream[IO, Int] = {
    def source(from: Int, until: Int)                               = Fs2Stream.range(from, until).covary[IO].evalMap(IO.pure)
    def mixed(stream0: Fs2Stream[IO, Int], depth: Int, innerN: Int) = {
      var stream = stream0; var i = 0
      while (i < depth) { stream = stream.filter(_ % 2 != 0).flatMap(_ => source(0, innerN)).map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "singleton"        => source(42, 43)
      case "drain"            => source(0, N)
      case "map_1"            => source(0, N).map(_ + 1)
      case "filter_1"         => source(0, N).filter(_ % 2 != 0)
      case "flatMap_1"        => source(0, 100).flatMap(_ => source(0, 100))
      case "takeDrop"         => source(0, N).drop(N / 2).take((N / 2).toLong)
      case "mapFilterFlatMap" => source(0, N).map(_ + 1).filter(_ % 2 != 0).flatMap(_ => source(0, 100))
      case "mixed_1"          => mixed(source(0, 200), 1, 100)
      case "mixed_2"          => mixed(source(0, 100), 2, 20)
      case "mixed_3"          => mixed(source(0, 100), 3, 10)
      case "nested_flatMap"   =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.flatMap(_ => source(1, 2)); i += 1 }
        stream
      case "nested_concat" =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream ++ source(1, 2); i += 1 }
        stream
    }
  }

  private def buildKyo(op: String): KyoStream[Int, KyoAsync] = {
    def source(from: Int, until: Int): KyoStream[Int, KyoAsync] =
      KyoStream.range(from, until).map((i: Int) => (i: Int < KyoAsync))
    def mixed(stream0: KyoStream[Int, KyoAsync], depth: Int, innerN: Int) = {
      var stream = stream0; var i = 0
      while (i < depth) {
        stream = stream.filterPure(_ % 2 != 0).flatMap(_ => source(0, innerN)).mapPure(_ + 1); i += 1
      }
      stream
    }
    op match {
      case "singleton"        => source(42, 43)
      case "drain"            => source(0, N)
      case "map_1"            => source(0, N).mapPure(_ + 1)
      case "filter_1"         => source(0, N).filterPure(_ % 2 != 0)
      case "flatMap_1"        => source(0, 100).flatMap(_ => source(0, 100))
      case "takeDrop"         => source(0, N).drop(N / 2).take(N / 2)
      case "mapFilterFlatMap" => source(0, N).mapPure(_ + 1).filterPure(_ % 2 != 0).flatMap(_ => source(0, 100))
      case "mixed_1"          => mixed(source(0, 200), 1, 100)
      case "mixed_2"          => mixed(source(0, 100), 2, 20)
      case "mixed_3"          => mixed(source(0, 100), 3, 10)
      case "nested_flatMap"   =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.flatMap(_ => source(1, 2)); i += 1 }
        stream
      case "nested_concat" =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.concat(source(1, 2)); i += 1 }
        stream
    }
  }

  private def buildPekko(op: String): Source[Int, ?] = {
    def source(from: Int, until: Int): Source[Int, ?]           = Source(from until until).mapAsync(1)(i => Future.successful(i))
    def mixed(source0: Source[Int, ?], depth: Int, innerN: Int) = {
      var stream = source0; var i = 0
      while (i < depth) { stream = stream.filter(_ % 2 != 0).flatMapConcat(_ => source(0, innerN)).map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "singleton"        => source(42, 43)
      case "drain"            => source(0, N)
      case "map_1"            => source(0, N).map(_ + 1)
      case "filter_1"         => source(0, N).filter(_ % 2 != 0)
      case "flatMap_1"        => source(0, 100).flatMapConcat(_ => source(0, 100))
      case "takeDrop"         => source(0, N).drop(N / 2).take(N / 2)
      case "mapFilterFlatMap" => source(0, N).map(_ + 1).filter(_ % 2 != 0).flatMapConcat(_ => source(0, 100))
      case "mixed_1"          => mixed(source(0, 200), 1, 100)
      case "mixed_2"          => mixed(source(0, 100), 2, 20)
      case "mixed_3"          => mixed(source(0, 100), 3, 10)
      case "nested_flatMap"   =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.flatMapConcat(_ => source(1, 2)); i += 1 }
        stream
      case "nested_concat" =>
        var stream = source(1, 2); var i = 0
        while (i < 10000) { stream = stream.concatLazy(source(1, 2)); i += 1 }
        stream
    }
  }
}
