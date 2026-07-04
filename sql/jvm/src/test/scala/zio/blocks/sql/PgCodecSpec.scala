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

package zio.blocks.sql

import zio.blocks.schema.json.Json
import zio.test.*

object PgCodecSpec extends ZIOSpecDefault {
  import PgCodec.given

  private final class StringReader(value: String) extends DbResultReader {
    private def unsupported(name: String) = throw new UnsupportedOperationException(name)

    def getInt(index: Int): Int                                  = unsupported("getInt(index)")
    def getInt(label: String): Int                               = unsupported("getInt(label)")
    def getLong(index: Int): Long                                = unsupported("getLong(index)")
    def getLong(label: String): Long                             = unsupported("getLong(label)")
    def getDouble(index: Int): Double                            = unsupported("getDouble(index)")
    def getDouble(label: String): Double                         = unsupported("getDouble(label)")
    def getFloat(index: Int): Float                              = unsupported("getFloat(index)")
    def getFloat(label: String): Float                           = unsupported("getFloat(label)")
    def getBoolean(index: Int): Boolean                          = unsupported("getBoolean(index)")
    def getBoolean(label: String): Boolean                       = unsupported("getBoolean(label)")
    def getString(index: Int): String                            = value
    def getString(label: String): String                         = value
    def getBigDecimal(index: Int): java.math.BigDecimal          = unsupported("getBigDecimal(index)")
    def getBigDecimal(label: String): java.math.BigDecimal       = unsupported("getBigDecimal(label)")
    def getBytes(index: Int): Array[Byte]                        = unsupported("getBytes(index)")
    def getBytes(label: String): Array[Byte]                     = unsupported("getBytes(label)")
    def getShort(index: Int): Short                              = unsupported("getShort(index)")
    def getShort(label: String): Short                           = unsupported("getShort(label)")
    def getByte(index: Int): Byte                                = unsupported("getByte(index)")
    def getByte(label: String): Byte                             = unsupported("getByte(label)")
    def getLocalDate(index: Int): java.time.LocalDate            = unsupported("getLocalDate(index)")
    def getLocalDate(label: String): java.time.LocalDate         = unsupported("getLocalDate(label)")
    def getLocalDateTime(index: Int): java.time.LocalDateTime    = unsupported("getLocalDateTime(index)")
    def getLocalDateTime(label: String): java.time.LocalDateTime = unsupported("getLocalDateTime(label)")
    def getLocalTime(index: Int): java.time.LocalTime            = unsupported("getLocalTime(index)")
    def getLocalTime(label: String): java.time.LocalTime         = unsupported("getLocalTime(label)")
    def getInstant(index: Int): java.time.Instant                = unsupported("getInstant(index)")
    def getInstant(label: String): java.time.Instant             = unsupported("getInstant(label)")
    def getDuration(index: Int): java.time.Duration              = unsupported("getDuration(index)")
    def getDuration(label: String): java.time.Duration           = unsupported("getDuration(label)")
    def getUUID(index: Int): java.util.UUID                      = unsupported("getUUID(index)")
    def getUUID(label: String): java.util.UUID                   = unsupported("getUUID(label)")
    def columnLabel(index: Int): String                          = "value"
    def hasColumn(label: String): Boolean                        = label == "value"
    def wasNull: Boolean                                         = false
  }

  def spec: Spec[TestEnvironment, Any] = suite("PgCodecSpec")(
    suite("DbCodec[Json]")(
      test("round-trips a nested Json.Object through encode/decode") {
        val codec      = summon[DbCodec[Json]]
        val json: Json = Json.Object(
          "name"    -> Json.String("Ada"),
          "age"     -> Json.Number(36),
          "active"  -> Json.Boolean(true),
          "missing" -> Json.Null,
          "tags"    -> Json.Array(Json.String("admin"), Json.String("user")),
          "scores"  -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)),
          "address" -> Json.Object("city" -> Json.String("Berlin"), "zip" -> Json.String("10115"))
        )

        val encoded = codec.toDbValues(json)
        val decoded = codec.readValue(new StringReader(json.print), 1)

        assertTrue(
          encoded == IndexedSeq(DbValue.DbString(json.print)),
          decoded == json
        )
      },
      test("decode is the inverse of encode for a Json.Array") {
        val codec      = summon[DbCodec[Json]]
        val json: Json = Json.Array(Json.Number(1), Json.String("two"), Json.Boolean(false), Json.Null)

        val roundTripped = codec.readValue(new StringReader(json.print), 1)

        assertTrue(roundTripped == json)
      }
    )
  )
}
