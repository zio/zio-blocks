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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.openjdk.jmh.annotations.{Level, Scope, Setup, State, TearDown}

import scala.compiletime.uninitialized
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/** Pekko runtime injected only into Pekko benchmark methods. */
@State(Scope.Benchmark)
class PekkoBenchmarkRuntime {
  private var system: ActorSystem = uninitialized

  var materializer: Materializer         = uninitialized
  var executionContext: ExecutionContext = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem("streams-benchmark")
    materializer = SystemMaterializer(system).materializer
    executionContext = system.dispatcher
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (system != null) Await.result(system.terminate(), Duration(30, "s"))

  def fold(source: Source[Int, ?]): Long =
    Await.result(source.runWith(Sink.fold(0L)(_ + _))(materializer), Duration.Inf)

  def drain(source: Source[Int, ?]): Unit = {
    Await.result(source.runWith(Sink.ignore)(materializer), Duration.Inf)
    ()
  }
}
