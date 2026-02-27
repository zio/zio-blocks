package zio.blocks.schema.csv

import zio.blocks.schema.codec.TextFormat

/**
 * CSV text format for schema-driven codec derivation.
 *
 * Use with `Schema[A].derive(CsvFormat)` to obtain a `CsvCodec[A]`. Supports
 * flat case classes (records) and wrapper/newtype types. Variant (sealed
 * trait), sequence, map, and dynamic types are not supported.
 */
object CsvFormat extends TextFormat[CsvCodec]("text/csv", CsvCodecDeriver)
