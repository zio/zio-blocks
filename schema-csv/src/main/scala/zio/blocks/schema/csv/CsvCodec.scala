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

import zio.blocks.schema.codec.TextCodec

/**
 * A [[zio.blocks.schema.codec.TextCodec]] that writes a value as one CSV row
 * into a `CharBuffer` and reads it back, and names the row's columns in
 * `headerNames`. Get one with `Schema#derive(CsvFormat)`; see [[CsvFormat]] for
 * the types it accepts.
 *
 * For a record, `encode(value, output)` writes one row, cells separated by
 * commas, quoted where needed and ending with `\r\n`, into a buffer you
 * provide. It throws `java.nio.BufferOverflowException` when the buffer has
 * too little room, and also when one cell is longer than 8192 characters (a
 * known bug). It never writes the header line: write `headerNames` yourself,
 * for example with [[CsvWriter.writeHeader]].
 *
 * `decode(input)` reads the first row of `input`, ignores anything after it,
 * and expects one cell per column in the order of `headerNames`; a header line
 * is read as data. It returns `Left` with a [[zio.blocks.schema.SchemaError]]
 * when the row does not parse, has a different number of cells, or a cell does
 * not convert to its field's type. A missing cell is an error even when the
 * field has a default value, and cells are not trimmed. The error quotes the
 * bad value but does not name the column (a known bug). When a field's schema
 * comes from `Schema#transform` and its function throws, `decode` throws that
 * exception instead of returning `Left` (a known bug).
 *
 * The derived codecs always use [[CsvConfig.default]]. To write or read another
 * delimiter, pass a [[CsvConfig]] to [[CsvWriter]] and [[CsvReader]].
 *
 * {{{
 * import java.nio.CharBuffer
 * import zio.blocks.schema._
 *
 * case class Reading(sensor: String, celsius: Double)
 * object Reading {
 *   implicit val schema: Schema[Reading] = Schema.derived
 * }
 *
 * val codec: CsvCodec[Reading] = Reading.schema.derive(CsvFormat)
 * val columns = codec.headerNames.mkString(",") // sensor,celsius
 * val good    = codec.decode(CharBuffer.wrap("t-7,19.25"))
 * // Right(Reading(t-7,19.25))
 * val bad = codec.decode(CharBuffer.wrap("t-7,warm")).left.map(_.getMessage)
 * // Left(Cannot parse 'warm' as Double: For input string: "warm" at: .)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 *
 * @tparam A
 *   The type of the values the codec encodes and decodes.
 */
abstract class CsvCodec[A] extends TextCodec[A] {

  /**
   * Returns the column names, in the order `encode` writes the cells and
   * `decode` expects them. For a record, they are its field names in
   * declaration order; `Modifier.rename` on a field does not change them (a
   * known bug). A nested record or `AnyVal` case class field is one column. For
   * a primitive type the only name is `value`, and a wrapper built with
   * `Schema#transform` has the names of the type it wraps.
   */
  def headerNames: IndexedSeq[String]
}
