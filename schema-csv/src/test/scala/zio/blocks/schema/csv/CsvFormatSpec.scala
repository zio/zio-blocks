package zio.blocks.schema.csv

import zio.test._

object CsvFormatSpec extends ZIOSpecDefault {
  def spec = suite("CsvFormatSpec")(
    test("mimeType is text/csv") {
      assertTrue(CsvFormat.mimeType == "text/csv")
    },
    test("deriver is CsvCodecDeriver") {
      assertTrue(CsvFormat.deriver == CsvCodecDeriver)
    }
  )
}
