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

package zio.blocks.schema.csv

import zio.blocks.schema.codec.TextFormat

/**
 * The CSV format. Pass it to `Schema#derive` to get a [[CsvCodec]] that writes
 * a value as one CSV row and reads it back. Its MIME type is `text/csv`.
 *
 * Use it for flat records whose fields are primitive values, such as one line
 * of an export of sensor readings. Deriving a codec for a type that is or
 * contains a sealed trait (`Option` included), a collection, a map or a
 * `DynamicValue` throws an `UnsupportedOperationException` when
 * `Schema#derive` runs; the code still compiles. A field whose type is another
 * case class is not rejected: a known bug writes the nested record as one
 * quoted cell with a line break inside.
 *
 * {{{
 * import java.nio.CharBuffer
 * import zio.blocks.schema._
 *
 * case class Reading(sensor: String, celsius: Double, ok: Boolean)
 * object Reading {
 *   implicit val schema: Schema[Reading] = Schema.derived
 * }
 *
 * val codec   = Reading.schema.derive(CsvFormat)
 * val columns = codec.headerNames // Vector(sensor, celsius, ok)
 * val buffer  = CharBuffer.allocate(64)
 * codec.encode(Reading("hall, north", 21.5, true), buffer)
 * buffer.flip()
 * val line = buffer.toString // "\"hall, north\",21.5,true\r\n"
 * val back = codec.decode(CharBuffer.wrap(line))
 * // Right(Reading(hall, north,21.5,true))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 */
object CsvFormat extends TextFormat[CsvCodec]("text/csv", CsvCodecDeriver)
