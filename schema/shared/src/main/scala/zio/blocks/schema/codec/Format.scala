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

package zio.blocks.schema.codec

import zio.blocks.schema.derive._

import java.nio.{ByteBuffer, CharBuffer}

/**
 * A serialization format, such as JSON or Avro: its codec type, the input and
 * output types of that codec, its MIME type and the default deriver that builds
 * its codecs from a schema. Pass a format to `Schema#derive` to get a codec, or
 * to `Schema#getInstance`, `Schema#encode` and `Schema#decode`, which derive
 * the codec once per schema value and reuse it.
 *
 * Every format is a [[BinaryFormat]], whose codecs work on `ByteBuffer`, or a
 * [[TextFormat]], whose codecs work on `CharBuffer`. JSON is a `BinaryFormat`:
 * its codecs read and write UTF-8 bytes. Each format is a singleton, such as
 * `JsonFormat`, and the formats other than JSON live in their own modules.
 *
 * {{{
 * import zio.blocks.schema.json.{JsonCodec, JsonFormat}
 *
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val mimeType               = JsonFormat.mimeType // application/json
 * val codec: JsonCodec[Book] = Book.schema.derive(JsonFormat)
 * val json                   = codec.encodeToString(Book("Zionomicon", 2021)) // {"title":"Zionomicon","year":2021}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/format/ Format]]
 */
sealed trait Format {

  /** The input that this format's codecs decode from. */
  type DecodeInput

  /** The output that this format's codecs encode into. */
  type EncodeOutput

  /** The codec type of this format, such as `JsonCodec` for JSON. */
  type TypeClass[A] <: Codec[DecodeInput, EncodeOutput, A]

  /** The MIME type of this format, such as `application/json`. */
  def mimeType: String

  /**
   * The deriver that `Schema#derive`, `Schema#getInstance`, `Schema#encode` and
   * `Schema#decode` use for this format. Pass a configured deriver to
   * `Schema#derive` instead to change the defaults.
   */
  def deriver: Deriver[TypeClass]
}

/**
 * A [[Format]] whose codecs, of type `TC`, decode from and encode into
 * `ByteBuffer`s. JSON, Avro, MessagePack, Thrift, TOON, XML and YAML are binary
 * formats; the text-based ones among them read and write UTF-8 bytes. Define a
 * new format as a singleton that extends it, with its MIME type and the deriver
 * of its codecs:
 *
 * {{{
 * import zio.blocks.schema.codec._
 * import zio.blocks.schema.derive.Deriver
 *
 * abstract class AvroCodec[A] extends BinaryCodec[A]
 * lazy val AvroDeriver: Deriver[AvroCodec] = ??? // the deriver of your codecs
 *
 * sealed abstract class Avro extends BinaryFormat[AvroCodec]("application/avro", AvroDeriver)
 * case object Avro extends Avro
 * }}}
 */
abstract class BinaryFormat[TC[A] <: BinaryCodec[A]](val mimeType: String, val deriver: Deriver[TC]) extends Format {

  /** The codec type of this format, `TC`. */
  final type TypeClass[A] = TC[A]

  /** Codecs of this format decode from, and encode into, a `ByteBuffer`. */
  type DecodeInput  = ByteBuffer
  type EncodeOutput = ByteBuffer

  /**
   * Lets `Schema#derive` take this format and use its `deriver`, as in
   * `schema.derive(JsonFormat)`.
   */
  implicit final def derivable: Derivable[this.type, TC] =
    new Derivable[this.type, TC] {
      def deriver(d: BinaryFormat.this.type): Deriver[TC] = BinaryFormat.this.deriver
    }
}

/**
 * A [[Format]] whose codecs, of type `TC`, decode from and encode into
 * `CharBuffer`s. Among the built-in formats, only CSV is a text format; JSON is
 * a [[BinaryFormat]]. Define a new format as a singleton that extends it, with
 * its MIME type and the deriver of its codecs:
 *
 * {{{
 * import zio.blocks.schema.codec._
 * import zio.blocks.schema.derive.Deriver
 *
 * abstract class ZioJsonCodec[A] extends TextCodec[A]
 * lazy val ZioJsonDeriver: Deriver[ZioJsonCodec] = ??? // the deriver of your codecs
 *
 * sealed abstract class ZioJson extends TextFormat[ZioJsonCodec]("application/zio-json", ZioJsonDeriver)
 * case object ZioJson extends ZioJson
 * }}}
 */
abstract class TextFormat[TC[A] <: TextCodec[A]](val mimeType: String, val deriver: Deriver[TC]) extends Format {

  /** The codec type of this format, `TC`. */
  final type TypeClass[A] = TC[A]

  /** Codecs of this format decode from, and encode into, a `CharBuffer`. */
  type DecodeInput  = CharBuffer
  type EncodeOutput = CharBuffer

  /**
   * Lets `Schema#derive` take this format and use its `deriver`, as in
   * `schema.derive(CsvFormat)`.
   */
  implicit final def derivable: Derivable[this.type, TC] =
    new Derivable[this.type, TC] {
      def deriver(d: TextFormat.this.type): Deriver[TC] = TextFormat.this.deriver
    }
}
