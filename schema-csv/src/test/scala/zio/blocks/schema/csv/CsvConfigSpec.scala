package zio.blocks.schema.csv

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object CsvConfigSpec extends SchemaBaseSpec {
  def spec = suite("CsvConfigSpec")(
    suite("default CsvConfig")(
      test("creates default config with RFC 4180 standards") {
        val config = CsvConfig.default
        assertTrue(
          config.delimiter == ',' &&
            config.quoteChar == '"' &&
            config.lineTerminator == "\r\n" &&
            config.hasHeader == true &&
            config.nullValue == ""
        )
      },
      test("CsvConfig() uses defaults") {
        val config = CsvConfig()
        assertTrue(config == CsvConfig.default)
      }
    ),
    suite("custom CsvConfig")(
      test("can create config with custom delimiter") {
        val config = CsvConfig(delimiter = ';')
        assertTrue(config.delimiter == ';' && config.quoteChar == '"')
      },
      test("can create config with custom quoteChar") {
        val config = CsvConfig(quoteChar = '\'')
        assertTrue(config.quoteChar == '\'' && config.delimiter == ',')
      },
      test("can create config with custom lineTerminator") {
        val config = CsvConfig(lineTerminator = "\n")
        assertTrue(config.lineTerminator == "\n")
      },
      test("can create config with hasHeader = false") {
        val config = CsvConfig(hasHeader = false)
        assertTrue(config.hasHeader == false && config.hasHeader != CsvConfig.default.hasHeader)
      },
      test("can create config with custom nullValue") {
        val config = CsvConfig(nullValue = "N/A")
        assertTrue(config.nullValue == "N/A")
      }
    ),
    suite("TSV preset")(
      test("CsvConfig.tsv uses tab delimiter") {
        assertTrue(CsvConfig.tsv.delimiter == '\t')
      },
      test("CsvConfig.tsv preserves other defaults") {
        val tsv = CsvConfig.tsv
        assertTrue(
          tsv.quoteChar == '"' &&
            tsv.lineTerminator == "\r\n" &&
            tsv.hasHeader == true &&
            tsv.nullValue == ""
        )
      },
      test("CsvConfig.tsv is different from default") {
        assertTrue(CsvConfig.tsv.delimiter != CsvConfig.default.delimiter)
      }
    ),
    suite("case class behavior")(
      test("CsvConfig equality works") {
        val config1 = CsvConfig(delimiter = ';')
        val config2 = CsvConfig(delimiter = ';')
        assertTrue(config1 == config2)
      },
      test("CsvConfig inequality works") {
        val config1 = CsvConfig(delimiter = ';')
        val config2 = CsvConfig(delimiter = ',')
        assertTrue(config1 != config2)
      },
      test("CsvConfig copy works") {
        val config1 = CsvConfig.default
        val config2 = config1.copy(delimiter = '\t', hasHeader = false)
        assertTrue(config2.delimiter == '\t' && config2.hasHeader == false && config2.quoteChar == '"')
      }
    )
  )
}
