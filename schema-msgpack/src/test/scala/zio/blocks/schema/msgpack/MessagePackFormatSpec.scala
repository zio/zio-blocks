/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.schema.msgpack

import zio.blocks.schema._
import zio.test._
import java.time._
import java.util.UUID

object MessagePackFormatSpec extends ZIOSpecDefault {
  def spec = suite("MessagePackFormatSpec")(
    suite("Primitive Types")(
      test("Unit roundtrip") {
        val codec   = MessagePackFormat.derive[Unit]
        val encoded = codec.encode(())
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(()))
      },
      test("Boolean roundtrip") {
        val codec   = MessagePackFormat.derive[Boolean]
        val encoded = codec.encode(true)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(true))
      },
      test("Int roundtrip") {
        val codec   = MessagePackFormat.derive[Int]
        val encoded = codec.encode(42)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(42))
      },
      test("Long roundtrip") {
        val codec   = MessagePackFormat.derive[Long]
        val encoded = codec.encode(123456789L)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(123456789L))
      },
      test("Float roundtrip") {
        val codec   = MessagePackFormat.derive[Float]
        val encoded = codec.encode(3.14f)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(3.14f))
      },
      test("Double roundtrip") {
        val codec   = MessagePackFormat.derive[Double]
        val encoded = codec.encode(3.14159)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(3.14159))
      },
      test("String roundtrip") {
        val codec   = MessagePackFormat.derive[String]
        val encoded = codec.encode("hello world")
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right("hello world"))
      },
      test("Byte roundtrip") {
        val codec   = MessagePackFormat.derive[Byte]
        val encoded = codec.encode(127.toByte)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(127.toByte))
      },
      test("Short roundtrip") {
        val codec   = MessagePackFormat.derive[Short]
        val encoded = codec.encode(1000.toShort)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(1000.toShort))
      },
      test("Char roundtrip") {
        val codec   = MessagePackFormat.derive[Char]
        val encoded = codec.encode('A')
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right('A'))
      }
    ),
    suite("Time Types")(
      test("Instant roundtrip") {
        val codec   = MessagePackFormat.derive[java.time.Instant]
        val instant = java.time.Instant.now()
        val encoded = codec.encode(instant)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(instant))
      },
      test("LocalDate roundtrip") {
        val codec   = MessagePackFormat.derive[java.time.LocalDate]
        val date    = java.time.LocalDate.of(2024, 1, 15)
        val encoded = codec.encode(date)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(date))
      },
      test("LocalTime roundtrip") {
        val codec   = MessagePackFormat.derive[java.time.LocalTime]
        val time    = java.time.LocalTime.of(14, 30, 45)
        val encoded = codec.encode(time)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(time))
      },
      test("LocalDateTime roundtrip") {
        val codec    = MessagePackFormat.derive[java.time.LocalDateTime]
        val dateTime = java.time.LocalDateTime.of(2024, 1, 15, 14, 30)
        val encoded  = codec.encode(dateTime)
        val decoded  = codec.decode(encoded)
        assertTrue(decoded == Right(dateTime))
      },
      test("OffsetDateTime roundtrip") {
        val codec    = MessagePackFormat.derive[java.time.OffsetDateTime]
        val dateTime = java.time.OffsetDateTime.now()
        val encoded  = codec.encode(dateTime)
        val decoded  = codec.decode(encoded)
        assertTrue(decoded == Right(dateTime))
      },
      test("ZonedDateTime roundtrip") {
        val codec    = MessagePackFormat.derive[java.time.ZonedDateTime]
        val dateTime = java.time.ZonedDateTime.now()
        val encoded  = codec.encode(dateTime)
        val decoded  = codec.decode(encoded)
        assertTrue(decoded == Right(dateTime))
      },
      test("Duration roundtrip") {
        val codec    = MessagePackFormat.derive[java.time.Duration]
        val duration = java.time.Duration.ofMinutes(123)
        val encoded  = codec.encode(duration)
        val decoded  = codec.decode(encoded)
        assertTrue(decoded == Right(duration))
      },
      test("Period roundtrip") {
        val codec   = MessagePackFormat.derive[java.time.Period]
        val period  = java.time.Period.of(1, 2, 3)
        val encoded = codec.encode(period)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(period))
      },
      test("ZoneId roundtrip") {
        val codec   = MessagePackFormat.derive[java.time.ZoneId]
        val zoneId  = java.time.ZoneId.of("America/New_York")
        val encoded = codec.encode(zoneId)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(zoneId))
      }
    ),
    suite("Collection Types")(
      test("List[Int] roundtrip") {
        val codec   = MessagePackFormat.derive[List[Int]]
        val list    = List(1, 2, 3, 4, 5)
        val encoded = codec.encode(list)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(list))
      },
      test("Vector[String] roundtrip") {
        val codec   = MessagePackFormat.derive[Vector[String]]
        val vec     = Vector("a", "b", "c")
        val encoded = codec.encode(vec)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(vec))
      },
      test("Option[Int] Some roundtrip") {
        val codec   = MessagePackFormat.derive[Option[Int]]
        val opt     = Some(42)
        val encoded = codec.encode(opt)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(opt))
      },
      test("Option[Int] None roundtrip") {
        val codec            = MessagePackFormat.derive[Option[Int]]
        val opt: Option[Int] = None
        val encoded          = codec.encode(opt)
        val decoded          = codec.decode(encoded)
        assertTrue(decoded == Right(None))
      },
      test("Map[String, Int] roundtrip") {
        val codec   = MessagePackFormat.derive[Map[String, Int]]
        val map     = Map("key1" -> 1, "key2" -> 2)
        val encoded = codec.encode(map)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(map))
      }
    ),
    suite("Recursive and Complex Types")(
      test("Recursive structure roundtrip") {
        case class ListNode(value: Int, next: Option[ListNode])
        object ListNode {
          implicit val schema: Schema[ListNode] = Derivation.deriveSchema
        }
        val codec   = MessagePackFormat.derive[ListNode]
        val list    = ListNode(1, Some(ListNode(2, Some(ListNode(3, None)))))
        val encoded = codec.encode(list)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(list))
      }
    ),
    suite("Forward Compatibility (Schema Evolution)")(
      test("Ignore unknown fields") {
        case class UserV1(id: Int, name: String)
        case class UserV2(id: Int, name: String, email: String)

        object UserV1 { implicit val schema: Schema[UserV1] = Derivation.deriveSchema }
        object UserV2 { implicit val schema: Schema[UserV2] = Derivation.deriveSchema }

        val codec1 = MessagePackFormat.derive[UserV1]
        val codec2 = MessagePackFormat.derive[UserV2]

        val userV2  = UserV2(1, "John", "john@example.com")
        val encoded = codec2.encode(userV2)
        val decoded = codec1.decode(encoded)

        assertTrue(decoded == Right(UserV1(1, "John")))
      }
    )
  )
}
