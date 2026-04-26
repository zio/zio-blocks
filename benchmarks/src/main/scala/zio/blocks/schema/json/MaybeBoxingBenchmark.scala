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

package zio.blocks.schema.json

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema.{Maybe, Schema}
import scala.compiletime.uninitialized

class MaybeBoxingBenchmark extends BaseBenchmark {
  import MaybeBoxingBenchmark._

  var optionValue: Option[String]    = uninitialized
  var maybeValue: Maybe[String]      = uninitialized
  var optionEncoded: Array[Byte]     = uninitialized
  var maybeEncoded: Array[Byte]      = uninitialized
  var optionNullEncoded: Array[Byte] = uninitialized
  var maybeNullEncoded: Array[Byte]  = uninitialized

  @Setup
  def setup(): Unit = {
    optionValue = Some("hello world")
    maybeValue = Maybe.present("hello world")
    optionEncoded = optionCodec.encode(optionValue)
    maybeEncoded = maybeCodec.encode(maybeValue)
    optionNullEncoded = optionCodec.encode(None)
    maybeNullEncoded = maybeCodec.encode(Maybe.absent[String])
  }

  @Benchmark
  def decodeOptionPresent: Option[String] = optionCodec.decode(optionEncoded) match {
    case Right(v) => v
    case Left(e)  => throw e
  }

  @Benchmark
  def decodeMaybePresent: Maybe[String] = maybeCodec.decode(maybeEncoded) match {
    case Right(v) => v
    case Left(e)  => throw e
  }

  @Benchmark
  def decodeOptionAbsent: Option[String] = optionCodec.decode(optionNullEncoded) match {
    case Right(v) => v
    case Left(e)  => throw e
  }

  @Benchmark
  def decodeMaybeAbsent: Maybe[String] = maybeCodec.decode(maybeNullEncoded) match {
    case Right(v) => v
    case Left(e)  => throw e
  }

  @Benchmark
  def encodeOptionPresent: Array[Byte] = optionCodec.encode(optionValue)

  @Benchmark
  def encodeMaybePresent: Array[Byte] = maybeCodec.encode(maybeValue)

  @Benchmark
  def encodeOptionAbsent: Array[Byte] = optionCodec.encode(None)

  @Benchmark
  def encodeMaybeAbsent: Array[Byte] = maybeCodec.encode(Maybe.absent[String])
}

object MaybeBoxingBenchmark {
  val optionCodec: JsonCodec[Option[String]] =
    Schema[Option[String]].deriving(JsonFormat.deriver).derive

  val maybeCodec: JsonCodec[Maybe[String]] =
    Schema[Maybe[String]].deriving(JsonFormat.deriver).derive
}
