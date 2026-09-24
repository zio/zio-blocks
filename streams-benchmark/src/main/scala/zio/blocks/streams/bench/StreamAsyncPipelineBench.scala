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

import java.util.concurrent.atomic.AtomicInteger
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
import zio.blocks.async.{Async, block}
import zio.blocks.streams.Stream

/**
 * One-ready-effect-per-element counterparts for [[StreamPipelineBench]]. Ox
 * 1.0.6 has no matching public operator and is explicitly N/A in this matrix.
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
class StreamAsyncPipelineBench {
  @Param(Array("zb", "fs2", "kyo", "pekko"))
  var library: String = uninitialized

  @Param(
    Array(
      "filterMap",
      "map",
      "filter",
      "chainedMaps",
      "flatMap",
      "take",
      "takeWhile",
      "concat",
      "drain",
      "chainedMaps10",
      "chainedMaps100",
      "filterMapChain",
      "takeDrop"
    )
  )
  var operation: String = uninitialized

  @Param(Array("10000"))
  var N: Int = uninitialized

  private var pekkoSystem: ActorSystem = uninitialized
  private var pekkoMat: Materializer   = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    if (library == "pekko") {
      pekkoSystem = ActorSystem("async-pipeline-bench")
      pekkoMat = SystemMaterializer(pekkoSystem).materializer
    }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  @Benchmark
  def evaluate(): Long = if (operation == "drain") drain()
  else
    library match {
      case "zb"    => AsyncParity.fold(buildZb(operation))
      case "fs2"   => buildFs2(operation).compile.fold(0L)(_ + _).unsafeRunSync()
      case "kyo"   => KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(buildKyo(operation).foldPure(0L)(_ + _)).getOrThrow
      case "pekko" =>
        Await.result(buildPekko(operation).runWith(PekkoSink.fold(0L)(_ + _))(pekkoMat), ScalaDuration.Inf)
    }

  private def drain(): Long = {
    library match {
      case "zb"    => buildZb(operation).runDrainAsync.block.toOption.get
      case "fs2"   => buildFs2(operation).compile.drain.unsafeRunSync()
      case "kyo"   => KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(buildKyo(operation).discard).getOrThrow
      case "pekko" => Await.result(buildPekko(operation).runWith(PekkoSink.ignore)(pekkoMat), ScalaDuration.Inf)
    }
    0L
  }

  private def buildZb(op: String): Stream[Nothing, Int] = {
    def source(from: Int, until: Int) = AsyncParity.readyEffectSource(from, until)
    def maps(n: Int)                  = {
      var stream = source(0, N); var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "filterMap"      => source(0, N).filter(_ % 2 == 0).map(_ + 1)
      case "map"            => source(0, N).map(_ * 2)
      case "filter"         => source(0, N).filter(_ % 2 == 0)
      case "chainedMaps"    => maps(5)
      case "flatMap"        => source(0, N).flatMap(i => source(i * 2, i * 2 + 1))
      case "take"           => source(0, N).take((N / 2).toLong)
      case "takeWhile"      => source(0, N).takeWhile(_ < N / 2)
      case "concat"         => source(0, N / 2) ++ source(N / 2, N)
      case "drain"          => source(0, N)
      case "chainedMaps10"  => maps(10)
      case "chainedMaps100" => maps(100)
      case "filterMapChain" => filterMapChainZb(source(0, N))
      case "takeDrop"       => source(0, N).drop(N / 2).take((N / 2).toLong)
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
    def source(from: Int, until: Int) = Fs2Stream.range(from, until).covary[IO].evalMap(IO.pure)
    def maps(n: Int)                  = {
      var stream = source(0, N); var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "filterMap"      => source(0, N).filter(_ % 2 == 0).map(_ + 1)
      case "map"            => source(0, N).map(_ * 2)
      case "filter"         => source(0, N).filter(_ % 2 == 0)
      case "chainedMaps"    => maps(5)
      case "flatMap"        => source(0, N).flatMap(i => source(i * 2, i * 2 + 1))
      case "take"           => source(0, N).take((N / 2).toLong)
      case "takeWhile"      => source(0, N).takeWhile(_ < N / 2)
      case "concat"         => source(0, N / 2) ++ source(N / 2, N)
      case "drain"          => source(0, N)
      case "chainedMaps10"  => maps(10)
      case "chainedMaps100" => maps(100)
      case "filterMapChain" =>
        source(0, N)
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
      case "takeDrop" => source(0, N).drop(N / 2).take((N / 2).toLong)
    }
  }

  private def buildKyo(op: String): KyoStream[Int, KyoAsync] = {
    def source(from: Int, until: Int): KyoStream[Int, KyoAsync] =
      KyoStream.range(from, until).map((i: Int) => (i: Int < KyoAsync))
    def maps(n: Int) = {
      var stream = source(0, N); var i = 0
      while (i < n) { stream = stream.mapPure(_ + 1); i += 1 }
      stream
    }
    op match {
      case "filterMap"      => source(0, N).filterPure(_ % 2 == 0).mapPure(_ + 1)
      case "map"            => source(0, N).mapPure(_ * 2)
      case "filter"         => source(0, N).filterPure(_ % 2 == 0)
      case "chainedMaps"    => maps(5)
      case "flatMap"        => source(0, N).flatMap(i => source(i * 2, i * 2 + 1))
      case "take"           => source(0, N).take(N / 2)
      case "takeWhile"      => source(0, N).takeWhilePure(_ < N / 2)
      case "concat"         => source(0, N / 2).concat(source(N / 2, N))
      case "drain"          => source(0, N)
      case "chainedMaps10"  => maps(10)
      case "chainedMaps100" => maps(100)
      case "filterMapChain" =>
        source(0, N)
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
      case "takeDrop" => source(0, N).drop(N / 2).take(N / 2)
    }
  }

  private def buildPekko(op: String): Source[Int, ?] = {
    def source(from: Int, until: Int): Source[Int, ?] = Source(from until until).mapAsync(1)(i => Future.successful(i))
    def maps(n: Int)                                  = {
      var stream = source(0, N); var i = 0
      while (i < n) { stream = stream.map(_ + 1); i += 1 }
      stream
    }
    op match {
      case "filterMap"      => source(0, N).filter(_ % 2 == 0).map(_ + 1)
      case "map"            => source(0, N).map(_ * 2)
      case "filter"         => source(0, N).filter(_ % 2 == 0)
      case "chainedMaps"    => maps(5)
      case "flatMap"        => source(0, N).flatMapConcat(i => source(i * 2, i * 2 + 1))
      case "take"           => source(0, N).take(N / 2)
      case "takeWhile"      => source(0, N).takeWhile(_ < N / 2)
      case "concat"         => source(0, N / 2).concatLazy(source(N / 2, N))
      case "drain"          => source(0, N)
      case "chainedMaps10"  => maps(10)
      case "chainedMaps100" => maps(100)
      case "filterMapChain" =>
        source(0, N)
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
      case "takeDrop" => source(0, N).drop(N / 2).take(N / 2)
    }
  }

  /**
   * Untimed validation that the declared ready source runs one callback per
   * element.
   */
  def readyEffectInvocationCount(): Int = {
    val count = new AtomicInteger
    library match {
      case "zb" =>
        AsyncParity.fold(Stream.range(0, N).mapAsync { value =>
          count.incrementAndGet()
          Async.succeed(value)
        })
      case "fs2" =>
        Fs2Stream
          .range(0, N)
          .covary[IO]
          .evalMap { value =>
            count.incrementAndGet()
            IO.pure(value)
          }
          .compile
          .drain
          .unsafeRunSync()
      case "kyo" =>
        val stream = KyoStream.range(0, N).map { (value: Int) =>
          count.incrementAndGet()
          (value: Int < KyoAsync)
        }
        KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(stream.discard).getOrThrow
      case "pekko" =>
        val stream = Source(0 until N).mapAsync(1) { value =>
          count.incrementAndGet()
          Future.successful(value)
        }
        Await.result(stream.runWith(PekkoSink.ignore)(pekkoMat), ScalaDuration.Inf)
    }
    count.get()
  }
}
