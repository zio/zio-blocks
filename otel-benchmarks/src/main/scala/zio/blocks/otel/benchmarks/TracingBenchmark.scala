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

package zio.blocks.otel.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import zio.blocks.otel.{Measurement => _, _}
import scala.compiletime.uninitialized

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
class TracingBenchmark {

  private var tracer: Tracer = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val provider = TracerProvider.builder
      .addSpanProcessor(SpanProcessor.noop)
      .build()
    tracer = provider.get("benchmark")
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = ()

  /** Create and end a simple span — measures span lifecycle overhead */
  @Benchmark
  def spanSimple(bh: Blackhole): Unit = {
    val result = tracer.span("operation") { _ =>
      42
    }
    bh.consume(result)
  }

  /** Create span with attributes */
  @Benchmark
  def spanWithAttributes(bh: Blackhole): Unit = {
    val attrs = Attributes.builder
      .put("http.method", "GET")
      .put("http.status_code", 200L)
      .build
    val result = tracer.span("request", SpanKind.Internal, attrs) { _ =>
      42
    }
    bh.consume(result)
  }

  /** Nested parent-child spans */
  @Benchmark
  def spanNested(bh: Blackhole): Unit = {
    val result = tracer.span("parent") { _ =>
      tracer.span("child") { _ =>
        42
      }
    }
    bh.consume(result)
  }
}
