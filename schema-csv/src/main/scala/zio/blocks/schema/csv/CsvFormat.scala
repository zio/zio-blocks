package zio.blocks.schema.csv

import zio.blocks.schema.codec.TextFormat

object CsvFormat extends TextFormat[CsvCodec]("text/csv", CsvCodecDeriver)
