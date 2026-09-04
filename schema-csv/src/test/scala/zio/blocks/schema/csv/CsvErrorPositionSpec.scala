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

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object CsvErrorPositionSpec extends SchemaBaseSpec {
  def spec = suite("CsvErrorPositionSpec")(
    suite("multi-line error positions")(
      test("readAll reports the document row for an unclosed quote (CRLF)") {
        val input  = "a,b\r\nc,d\r\n\"unclosed,e"
        val result = CsvReader.readAll(input, CsvConfig.default)
        result match {
          case Left(CsvError.ParseError(_, row, column)) => assertTrue(row == 3, column == 12)
          case _                                         => assertTrue(false)
        }
      },
      test("readAll reports the document row for an unclosed quote (LF)") {
        val input  = "a,b\nc,d\n\"unclosed,e"
        val result = CsvReader.readAll(input, CsvConfig.default)
        result match {
          case Left(CsvError.ParseError(_, row, column)) => assertTrue(row == 3, column == 12)
          case _                                         => assertTrue(false)
        }
      },
      test("readAll reports the document row for garbage after a closing quote") {
        val input  = "h1,h2\r\nok,ok\r\n\"a\"x,b\r\n"
        val result = CsvReader.readAll(input, CsvConfig.default)
        result match {
          case Left(CsvError.ParseError(_, row, column)) => assertTrue(row == 3, column == 4)
          case _                                         => assertTrue(false)
        }
      },
      test("embedded newlines advance the row within a single readRow") {
        val result = CsvReader.readRow("\"a\nb\"x,c", 0, CsvConfig.default, initialRow = 2)
        result match {
          case Left(CsvError.ParseError(_, row, column)) => assertTrue(row == 3, column == 3)
          case _                                         => assertTrue(false)
        }
      },
      test("readRow without initialRow reports positions relative to the offset") {
        val result = CsvReader.readRow("\"unclosed", 0, CsvConfig.default)
        result match {
          case Left(CsvError.ParseError(_, row, _)) => assertTrue(row == 1)
          case _                                    => assertTrue(false)
        }
      }
    ),
    suite("unquoted whitespace policy")(
      test("unquoted fields keep surrounding spaces") {
        val result = CsvReader.readRow("  a  ,b\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("  a  ", "b"), 9)))
      },
      test("quoted fields keep their exact content") {
        val result = CsvReader.readRow("\"  a  \",b\r\n", 0, CsvConfig.default)
        assertTrue(result == Right((Vector("  a  ", "b"), 11)))
      }
    )
  )
}
