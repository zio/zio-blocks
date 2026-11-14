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

package zio.http.schema

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scala.compiletime.uninitialized
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.http.{Headers, HeadersBuilder, QueryParams, QueryParamsBuilder}

/**
 * End-to-end baseline for the derived query/header codecs: one record with 8
 * primitives plus `Option[Int]` and `List[Int]`, encoded and decoded whole.
 *
 * Encode benchmarks reuse a single builder per thread (`encode` into it, then
 * `build()`, consume, `reset()`), so the measured cost is the codec plus the
 * unavoidable `build()` copy. Decode benchmarks decode inputs prepared once in
 * `@Setup` by encoding the canonical record, so encode and decode stay
 * symmetric.
 *
 * Baseline run (throughput + allocation rate):
 * {{{
 * sbt -Dsbt.color=false "http-model-schema-benchmarks/Jmh/run -f 1 -wi 3 -i 5 -prof gc 'zio.http.schema.HttpCodecBenchmark'"
 * }}}
 */
class HttpCodecBenchmark extends BaseBenchmark {

  final case class HttpBenchRecord(
    id: Int,
    userId: Long,
    score: Double,
    active: Boolean,
    name: String,
    ratio: Float,
    rank: Short,
    flags: Byte,
    count: Option[Int],
    tags: List[Int]
  )
  object HttpBenchRecord {
    implicit val schema: Schema[HttpBenchRecord] = Schema.derived[HttpBenchRecord]
  }

  private val record: HttpBenchRecord =
    HttpBenchRecord(
      id = 7,
      userId = 4294967297L,
      score = 9.75,
      active = true,
      name = "alice",
      ratio = 0.5f,
      rank = 3,
      flags = 9,
      count = Some(42),
      tags = List(1, 2, 3)
    )

  private var queryCodec: QueryCodec[HttpBenchRecord]   = uninitialized
  private var headerCodec: HeaderCodec[HttpBenchRecord] = uninitialized

  private var queryBuilder: QueryParamsBuilder = uninitialized
  private var headersBuilder: HeadersBuilder   = uninitialized

  private var queryInput: QueryParams = uninitialized
  private var headersInput: Headers   = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    queryCodec = Schema[HttpBenchRecord].derive(DefaultQueryFormat)
    headerCodec = Schema[HttpBenchRecord].derive(DefaultHeaderFormat)
    queryBuilder = QueryParamsBuilder.make()
    headersBuilder = HeadersBuilder.make()
    queryInput = queryCodec.encodeToQueryParams(record)
    headersInput = headerCodec.encodeToHeaders(record)
    require(queryCodec.decode(queryInput).isRight, "query fixture must decode")
    require(headerCodec.decode(headersInput).isRight, "header fixture must decode")
  }

  @Benchmark
  def queryEncode(bh: Blackhole): Unit = {
    queryCodec.encode(record, queryBuilder)
    val built = queryBuilder.build()
    bh.consume(built)
    queryBuilder.reset()
  }

  @Benchmark
  def queryDecode(bh: Blackhole): Unit =
    bh.consume(queryCodec.decode(queryInput))

  @Benchmark
  def headerEncode(bh: Blackhole): Unit = {
    headerCodec.encode(record, headersBuilder)
    val built = headersBuilder.build()
    bh.consume(built)
    headersBuilder.reset()
  }

  @Benchmark
  def headerDecode(bh: Blackhole): Unit =
    bh.consume(headerCodec.decode(headersInput))
}
