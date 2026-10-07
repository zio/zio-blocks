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

package zio.http

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

import zio.blocks.chunk.Chunk
import zio.blocks.maybe.Maybe

/**
 * Throughput benchmarks for the `Headers` hot path: the per-request lookups a
 * middleware performs on every call (`rawGet`, `get`, `has`) plus the
 * multi-value and strict variants.
 *
 * The target entry sits at the END of the collection (worst-case scan) with
 * `size` filler entries before it. Hit and miss benchmarks share the same
 * populated collection shape: a miss scans the same entries for an absent key
 * rather than an empty collection, so hit/miss numbers are comparable and no
 * speed claim follows from changing the workload. Results are returned (not
 * sunk into a `Blackhole`) to defeat dead-code elimination.
 *
 * Scope honesty: the state is `Level.Trial`, shared across the iterations of a
 * trial, so typed reads parse on the first iteration and hit the parsed-value
 * cache afterwards (steady-state cached reads). No cold-parse (per-invocation)
 * behaviour is measured or claimed here.
 *
 * Run with:
 * {{{
 * sbt --client "++3.9.0; http-model-benchmarks/Jmh/run HeadersBenchmark"
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xnoclassgc",
    "-Xms3g",
    "-Xmx3g",
    "-Xss4m",
    "-XX:NewSize=2g",
    "-XX:MaxNewSize=2g",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:TLABSize=16m",
    "-XX:-ResizeTLAB",
    "-XX:+UseParallelGC",
    "-XX:-UseAdaptiveSizePolicy",
    "-XX:MaxInlineLevel=20",
    "-XX:InlineSmallCode=2500",
    "-XX:+AlwaysPreTouch",
    "-XX:+PerfDisableSharedMem",
    "-XX:-UsePerfData",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields"
  )
)
class HeadersBenchmark {

  @Param(Array("5", "20"))
  var size: Int = 5

  var headers: Headers = Headers.empty
  var multi: Headers   = Headers.empty

  @Setup(Level.Trial)
  def setup(): Unit = {
    var h = Headers.empty
    var i = 0
    while (i < size) {
      h = h.add("x-filler-" + i, "v-" + i)
      i += 1
    }
    headers = h.add("content-length", "1024")
    var m = Headers.empty
    var j = 0
    while (j < size) {
      m = m.add("set-cookie", "a=" + j)
      j += 1
    }
    multi = m
  }

  @Benchmark
  def rawGetHit: Maybe[String] = headers.rawGet("Content-Length")

  @Benchmark
  def rawGetMiss: Maybe[String] = headers.rawGet("X-Missing")

  @Benchmark
  def getHit: Maybe[Header.ContentLength] = headers.get(Header.ContentLength)

  @Benchmark
  def getMiss: Maybe[Header.Host] = headers.get(Header.Host)

  @Benchmark
  def getStrictHit: Either[String, Maybe[Header.ContentLength]] = headers.getStrict(Header.ContentLength)

  @Benchmark
  def hasHit: Boolean = headers.has("Content-Length")

  @Benchmark
  def getLastHit: Maybe[Header.SetCookieHeader] = multi.getLast(Header.SetCookieHeader)

  @Benchmark
  def getAllHit: Chunk[Header.SetCookieHeader] = multi.getAll(Header.SetCookieHeader)
}
