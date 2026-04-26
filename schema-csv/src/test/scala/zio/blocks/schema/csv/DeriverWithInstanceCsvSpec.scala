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

import zio.blocks.schema._
import zio.blocks.typeid.TypeId
import zio.test._

import java.nio.CharBuffer
import java.time.LocalDate

object DeriverWithInstanceCsvSpec extends SchemaBaseSpec {

  case class Meeting(title: String, date: LocalDate)
  object Meeting {
    implicit val schema: Schema[Meeting] = Schema.derived
  }

  private def csvRoundTrip(codec: CsvCodec[Meeting], value: Meeting): Either[SchemaError, Meeting] = {
    val buf = CharBuffer.allocate(4096)
    codec.encode(value, buf)
    buf.flip()
    codec.decode(CharBuffer.wrap(buf.toString))
  }

  def spec = suite("DeriverWithInstanceCsvSpec")(
    test("withInstance wraps CsvCodecDeriver and still roundtrips") {
      val deriver = CsvCodecDeriver.withModifier(
        TypeId.of[Meeting],
        "title",
        Modifier.alias("subject")
      )
      val codec   = Schema[Meeting].deriving(deriver).derive
      val meeting = Meeting("retro", LocalDate.of(2025, 8, 1))
      assertTrue(csvRoundTrip(codec, meeting) == Right(meeting))
    },
    test("chained overrides accumulate and still roundtrip") {
      val deriver = CsvCodecDeriver
        .withModifier(TypeId.of[Meeting], "title", Modifier.alias("subject"))
        .withModifier(TypeId.of[Meeting], "date", Modifier.alias("when"))
      val codec   = Schema[Meeting].deriving(deriver).derive
      val meeting = Meeting("standup", LocalDate.of(2025, 7, 4))
      assertTrue(csvRoundTrip(codec, meeting) == Right(meeting))
    },
    test("default CsvFormat derivation still works") {
      val codec   = Schema[Meeting].derive(CsvFormat)
      val meeting = Meeting("sync", LocalDate.of(2025, 5, 1))
      assertTrue(
        codec.headerNames == IndexedSeq("title", "date"),
        csvRoundTrip(codec, meeting) == Right(meeting)
      )
    }
  )
}
