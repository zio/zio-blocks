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
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration as ScalaDuration
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream as Fs2Stream
import kyo.{Async as KyoAsync, KyoApp, Stream as KyoStream, Sync as KyoSync, *}
import kyo.AllowUnsafe.embrace.danger
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink as PekkoSink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import zio.blocks.streams.Stream

/**
 * One-ready-effect-per-source-element counterparts for
 * [[StreamConcurrentBench]]. Each provider then uses its native bounded
 * unordered map or bounded fan-in operator. Kyo 1.0.0-RC6 participates in
 * `mapPar`; its public APIs do not expose the bounded dynamic fan-in contract,
 * so `mergeAll` and `flatMapPar` are N/A. Ox 1.0.6 has no
 * one-ready-effect-per-element source operator, so it is N/A here. `mergeAll`
 * and `flatMapPar` measure bounded active-stream fan-in under inner work, not
 * equivalent CPU scheduler scaling.
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
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
class StreamAsyncConcurrentBench {
  @Param(
    Array(
      "zb:mapPar",
      "zb:mergeAll",
      "zb:flatMapPar",
      "fs2:mapPar",
      "fs2:mergeAll",
      "fs2:flatMapPar",
      "kyo:mapPar",
      "pekko:mapPar",
      "pekko:mergeAll",
      "pekko:flatMapPar"
    )
  )
  var cell: String = uninitialized

  @Param(Array("1", "8", "16"))
  var parallelism: Int = uninitialized

  @Param(Array("light", "heavy"))
  var workload: String = uninitialized

  @Param(Array("1000000"))
  var N: Int = uninitialized

  private var work: Int => Int              = uninitialized
  private var pekkoSystem: ActorSystem      = uninitialized
  private var pekkoMat: Materializer        = uninitialized
  implicit private var ec: ExecutionContext = uninitialized
  private val kyoBufferSize                 = 16

  @Setup(Level.Trial)
  def setup(): Unit = {
    work = workload match {
      case "heavy" => { x =>
        var result = x.toLong
        var i      = 0
        while (i < 100) { result = (result * 31 + 17) ^ (result >>> 3); i += 1 }
        result.toInt
      }
      case _ => x => x + 1
    }
    if (library == "pekko") {
      pekkoSystem = ActorSystem("async-concurrent-bench")
      pekkoMat = SystemMaterializer(pekkoSystem).materializer
      ec = pekkoSystem.dispatcher
    }
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  @Benchmark
  def evaluate(): Long = library match {
    case "zb"    => runZb(operation)
    case "fs2"   => runFs2(operation)
    case "kyo"   => runKyo()
    case "pekko" => runPekko(operation)
  }

  private def library: String   = cell.substring(0, cell.indexOf(':'))
  private def operation: String = cell.substring(cell.indexOf(':') + 1)

  private def runZb(op: String): Long = {
    def source(from: Int, until: Int) = AsyncParity.readyEffectSource(from, until)
    val stream                        = op match {
      case "mapPar"   => source(0, N).mapPar(parallelism)(work)
      case "mergeAll" =>
        val innerN = N / 100
        Stream.mergeAll(parallelism)(
          source(0, 100).map(index => source(index * innerN, (index + 1) * innerN).map(work))
        )
      case "flatMapPar" =>
        val innerN = N / 1000
        source(0, 1000).flatMapPar(parallelism)(index => source(0, innerN).map(offset => work(index * innerN + offset)))
    }
    AsyncParity.fold(stream)
  }

  private def runFs2(op: String): Long = {
    def source(from: Int, until: Int) = Fs2Stream.range(from, until).covary[IO].evalMap(IO.pure)
    val stream                        = op match {
      case "mapPar"   => source(0, N).parEvalMapUnordered(parallelism)(value => IO(work(value)))
      case "mergeAll" =>
        val innerN = N / 100
        source(0, 100)
          .map(index => source(index * innerN, (index + 1) * innerN).map(work))
          .parJoin(parallelism)
      case "flatMapPar" =>
        val innerN = N / 1000
        source(0, 1000)
          .map(index => source(0, innerN).map(offset => work(index * innerN + offset)))
          .parJoin(parallelism)
    }
    stream.compile.fold(0L)(_ + _).unsafeRunSync()
  }

  private def kyoSource(from: Int, until: Int): KyoStream[Int, KyoSync] =
    KyoStream.range(from, until).map((value: Int) => (value: Int < KyoSync))

  private def runKyo(): Long = {
    val stream =
      kyoSource(0, N).mapParUnordered(parallelism, kyoBufferSize)(value => (KyoSync.defer(work(value)): Int < KyoSync))
    KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(stream.foldPure(0L)(_ + _)).getOrThrow
  }

  /**
   * Untimed Kyo checksum and maximum-in-flight observation for correctness
   * checks.
   */
  def kyoParallelismObservation(): (Long, Int) = {
    val active                           = new AtomicInteger
    val maximum                          = new AtomicInteger
    val source: KyoStream[Int, KyoAsync] =
      KyoStream.range(0, N).map((value: Int) => (value: Int < KyoAsync))
    val stream = source.mapParUnordered(parallelism, kyoBufferSize) { value =>
      (KyoSync.defer {
        val now = active.incrementAndGet()
        maximum.accumulateAndGet(now, Math.max)
        try work(value)
        finally active.decrementAndGet()
      }: Int < KyoSync)
    }
    val checksum = KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(stream.foldPure(0L)(_ + _)).getOrThrow
    (checksum, maximum.get())
  }

  def configuredKyoBufferSize: Int = kyoBufferSize

  private def runPekko(op: String): Long = {
    def source(from: Int, until: Int): Source[Int, ?] =
      Source(from until until).mapAsync(1)(value => Future.successful(value))
    val stream = op match {
      case "mapPar"   => source(0, N).mapAsyncUnordered(parallelism)(value => Future(work(value)))
      case "mergeAll" =>
        val innerN = N / 100
        source(0, 100).flatMapMerge(
          parallelism,
          index => source(index * innerN, (index + 1) * innerN).map(work)
        )
      case "flatMapPar" =>
        val innerN = N / 1000
        source(0, 1000).flatMapMerge(
          parallelism,
          index => source(0, innerN).map(offset => work(index * innerN + offset))
        )
    }
    Await.result(stream.runWith(PekkoSink.fold(0L)(_ + _))(pekkoMat), ScalaDuration.Inf)
  }

}
