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

import zio.blocks.schema.{DynamicOptic, Schema, SchemaError}
import zio.http.{Headers, HeadersBuilder, QueryParams, QueryParamsBuilder}

/**
 * Shared custom codecs and record shapes for child-override regression tests.
 * Keeps the specs concise by defining each bespoke codec once.
 */
private[schema] object CustomCodecFixtures {
  final case class Tag(value: Int)
  object Tag {
    implicit val schema: Schema[Tag] = Schema[Int].transform(Tag(_), _.value)
  }

  final case class NestedInts(maybe: Option[Int], many: List[Int], tag: Tag)
  object NestedInts {
    implicit val schema: Schema[NestedInts] = Schema.derived[NestedInts]
  }

  final case class IntPair(first: Int, second: Int)
  object IntPair {
    implicit val schema: Schema[IntPair] = Schema.derived[IntPair]
  }

  def prefixedQueryIntCodec(prefix: String = "custom-"): QueryCodec[Int] = new QueryCodec[Int] {
    def encode(value: Int, output: QueryParamsBuilder): Unit =
      output.add("value", s"$prefix$value")

    def decode(input: QueryParams): Either[SchemaError, Int] =
      input.getFirst("value") match {
        case Some(raw) if raw.startsWith(prefix) => Right(raw.stripPrefix(prefix).toInt)
        case other                               => Left(SchemaError(s"Expected $prefix prefix, got: $other"))
      }
  }

  def prefixedHeaderIntCodec(prefix: String = "custom-"): HeaderCodec[Int] = new HeaderCodec[Int] {
    def encode(value: Int, output: HeadersBuilder): Unit =
      output.add("value", s"$prefix$value")

    def decode(input: Headers): Either[SchemaError, Int] =
      input.rawGet("value") match {
        case Some(raw) if raw.startsWith(prefix) => Right(raw.stripPrefix(prefix).toInt)
        case other                               => Left(SchemaError(s"Expected $prefix prefix, got: $other"))
      }
  }

  def capitalValueHeaderIntCodec(prefix: String = "custom-"): HeaderCodec[Int] = new HeaderCodec[Int] {
    def encode(value: Int, output: HeadersBuilder): Unit =
      output.add("Value", s"$prefix$value")

    def decode(input: Headers): Either[SchemaError, Int] =
      input.rawGet("Value") match {
        case Some(raw) if raw.startsWith(prefix) => Right(raw.stripPrefix(prefix).toInt)
        case other                               => Left(SchemaError(s"Expected $prefix prefix, got: $other"))
      }
  }

  def offKeyQueryIntCodec: QueryCodec[Int] = new QueryCodec[Int] {
    def encode(value: Int, output: QueryParamsBuilder): Unit =
      output.add("foo", value.toString)

    def decode(input: QueryParams): Either[SchemaError, Int] =
      input.getFirst("foo").map(_.toInt).toRight(SchemaError("Missing foo"))
  }

  def offKeyHeaderIntCodec: HeaderCodec[Int] = new HeaderCodec[Int] {
    def encode(value: Int, output: HeadersBuilder): Unit =
      output.add("foo", value.toString)

    def decode(input: Headers): Either[SchemaError, Int] =
      input.rawGet("foo").map(_.toInt).toRight(SchemaError("Missing foo"))
  }

  def pathQueryIntCodec: QueryCodec[Int] = new QueryCodec[Int] {
    def encode(value: Int, output: QueryParamsBuilder): Unit =
      output.add("value", value.toString)

    def decode(input: QueryParams): Either[SchemaError, Int] = Left(
      SchemaError.conversionFailed(
        List(DynamicOptic.Node.Field("value"), DynamicOptic.Node.Field("value")),
        "custom boom"
      )
    )
  }

  def pathHeaderIntCodec: HeaderCodec[Int] = new HeaderCodec[Int] {
    def encode(value: Int, output: HeadersBuilder): Unit =
      output.add("value", value.toString)

    def decode(input: Headers): Either[SchemaError, Int] = Left(
      SchemaError.conversionFailed(
        List(DynamicOptic.Node.Field("value"), DynamicOptic.Node.Field("value")),
        "custom boom"
      )
    )
  }
}
