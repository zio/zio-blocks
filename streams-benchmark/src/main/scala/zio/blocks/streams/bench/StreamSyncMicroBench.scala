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
import zio.blocks.streams.Stream

// ZIO Blocks-only state and primitive diagnostics, excluded from cross-provider rankings.

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamSyncStateAllocationBench {
  @Param(Array("0", "1", "1000")) var N: Int   = uninitialized
  @Benchmark def staticSyncMaterialize(): Long = Stream.range(0, N).runFold(0L)(_ + _).toOption.get
  @Benchmark def selectedSyncWrapper(): Long   = Stream.range(0, N).map(_ + 1).runFold(0L)(_ + _).toOption.get
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
class StreamSyncPrimitiveTerminalBench {
  @Param(Array("0", "1", "1000")) var N: Int = uninitialized
  private def source                         = Stream.range(0, N)
  @Benchmark def booleanSyncParity(): Int    =
    source.map(value => (value & 1) == 0).runFold(0)((sum, value) => sum + (if (value) 1 else 0)).toOption.get
  @Benchmark def byteSyncParity(): Int  = source.map(_.toByte).runFold(0)((sum, value) => sum + value).toOption.get
  @Benchmark def charSyncParity(): Int  = source.map(_.toChar).runFold(0)((sum, value) => sum + value.toInt).toOption.get
  @Benchmark def shortSyncParity(): Int = source.map(_.toShort).runFold(0)((sum, value) => sum + value).toOption.get
}
