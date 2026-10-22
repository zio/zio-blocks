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
import zio.blocks.schema.SchemaError

/**
 * Encodes values of type `Value` into an `EncodeOutput` and decodes them from a
 * `DecodeInput`. It is the base class of every format's codec, such as
 * `JsonCodec`, through [[BinaryCodec]] and [[TextCodec]]. Get one from a schema
 * with `Schema#derive` and a [[Format]] or a deriver. Some format-specific
 * codecs add overloads, such as `JsonCodec` for byte arrays, strings and
 * streams.
 *
 * {{{
 * import java.nio.ByteBuffer
 * import zio.blocks.schema.codec.BinaryCodec
 * import zio.blocks.schema.json.JsonFormat
 *
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val codec: BinaryCodec[Book] = Book.schema.derive(JsonFormat)
 * val buffer                   = ByteBuffer.allocate(64)
 * codec.encode(Book("Zionomicon", 2021), buffer)
 * buffer.flip()
 * val decoded = codec.decode(buffer) // Right(Book(Zionomicon,2021))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/codec/ Codec]]
 *
 * @tparam DecodeInput
 *   The type of input data used for decoding operations.
 * @tparam EncodeOutput
 *   The type of output data used for encoding operations.
 * @tparam Value
 *   The type of values being encoded or decoded.
 */
abstract class Codec[DecodeInput, EncodeOutput, Value] {

  /**
   * Encodes `value` into `output`, which the caller provides and which must
   * have room for the encoded value. What happens when it has too little room
   * depends on the codec: `JsonCodec` throws `BufferOverflowException` after
   * writing part of the value into a `ByteBuffer` (see `docs/dev/issues.md`).
   */
  def encode(value: Value, output: EncodeOutput): Unit

  /**
   * Decodes a value from `input`, or returns `Left` with a [[SchemaError]] when
   * the input does not decode.
   */
  def decode(input: DecodeInput): Either[SchemaError, Value]
}
