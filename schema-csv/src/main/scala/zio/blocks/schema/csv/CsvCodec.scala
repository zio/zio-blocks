package zio.blocks.schema.csv

import zio.blocks.schema.codec.TextCodec

/**
 * Abstract codec for encoding and decoding values to/from CSV format.
 *
 * Extends `TextCodec[A]` to provide CSV-specific serialization.
 *
 * @tparam A
 *   The type being encoded/decoded
 */
abstract class CsvCodec[A] extends TextCodec[A] {

  /**
   * Returns the header names for CSV columns.
   *
   * @return
   *   field names that will appear in the CSV header row
   */
  def headerNames: IndexedSeq[String]

  /**
   * Returns the null/default value for this type.
   *
   * Used when decoding missing or empty fields.
   *
   * @return
   *   the default value
   */
  def nullValue: A
}
