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

package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId
import zio.test._

import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tests for `Deriver.withInstance` and `Deriver.withModifier`.
 *
 * Verifies that pre-registering overrides on a deriver produces correct results
 * across JSON derivation, including type-level, field-level, modifier, chained,
 * Option, and nested override scenarios. Also checks backward compatibility
 * with existing `Schema.derive` and `DerivationBuilder` paths.
 */
object DeriverWithInstanceSpec extends SchemaBaseSpec {

  // -- Custom JsonCodec: LocalDate as "dd/MM/yyyy" instead of ISO ---

  private val customDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  private val customLocalDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
    def decodeValue(in: JsonReader): LocalDate =
      LocalDate.parse(in.readString(), customDateFmt)

    def encodeValue(x: LocalDate, out: JsonWriter): Unit =
      out.writeVal(customDateFmt.format(x))

    override def decodeValue(json: Json): LocalDate = json match {
      case s: Json.String => LocalDate.parse(s.value, customDateFmt)
      case _              => error("expected Json.String")
    }

    override def encodeValue(x: LocalDate): Json =
      new Json.String(customDateFmt.format(x))
  }

  // -- Domain types ---

  case class Event(name: String, date: LocalDate)
  object Event {
    implicit val schema: Schema[Event] = Schema.derived
  }

  case class DateRange(label: String, start: LocalDate, end: LocalDate)
  object DateRange extends CompanionOptics[DateRange] {
    implicit val schema: Schema[DateRange] = Schema.derived
    val start: Lens[DateRange, LocalDate]  = optic(_.start)
    val end: Lens[DateRange, LocalDate]    = optic(_.end)
  }

  case class MaybeEvent(name: String, date: Option[LocalDate])
  object MaybeEvent {
    implicit val schema: Schema[MaybeEvent] = Schema.derived
  }

  case class Outer(tag: String, inner: Event)
  object Outer {
    implicit val schema: Schema[Outer] = Schema.derived
  }

  case class Named(firstName: String, lastName: String)
  object Named {
    implicit val schema: Schema[Named] = Schema.derived
  }

  case class Scored(name: String, date: LocalDate, score: Int)
  object Scored {
    implicit val schema: Schema[Scored] = Schema.derived
  }

  // -- Helpers ---

  private def jsonRoundTrip[A](codec: JsonCodec[A], value: A): (String, Either[SchemaError, A]) = {
    val buf = ByteBuffer.allocate(4096)
    codec.encode(value, buf)
    val bytes   = java.util.Arrays.copyOf(buf.array, buf.position)
    val encoded = new String(bytes, "UTF-8")
    val decoded = codec.decode(bytes)
    (encoded, decoded)
  }

  private def jsonEncode[A](codec: JsonCodec[A], value: A): String = {
    val buf = ByteBuffer.allocate(4096)
    codec.encode(value, buf)
    new String(java.util.Arrays.copyOf(buf.array, buf.position), "UTF-8")
  }

  def spec: Spec[TestEnvironment, Any] = suite("DeriverWithInstanceSpec")(
    suite("JSON withInstance")(
      test("type-level override replaces all occurrences of a type") {
        val deriver         = JsonCodecDeriver.withInstance[LocalDate](customLocalDateCodec)
        val codec           = Schema[Event].deriving(deriver).derive
        val event           = Event("launch", LocalDate.of(2025, 6, 15))
        val (json, decoded) = jsonRoundTrip(codec, event)
        assertTrue(
          json == """{"name":"launch","date":"15/06/2025"}""",
          decoded == Right(event)
        )
      },
      test("field-level override applies only to the specified field") {
        val deriver = JsonCodecDeriver.withInstance[DateRange, LocalDate](
          TypeId.of[DateRange],
          "start",
          customLocalDateCodec
        )
        val codec = Schema[DateRange].deriving(deriver).derive
        val range = DateRange("q1", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))
        val json  = jsonEncode(codec, range)
        // start uses custom format, end uses default ISO format
        assertTrue(
          json == """{"label":"q1","start":"01/01/2025","end":"2025-03-31"}"""
        )
      },
      test("Option[A] with type-level override") {
        val deriver = JsonCodecDeriver.withInstance[LocalDate](customLocalDateCodec)
        val codec   = Schema[MaybeEvent].deriving(deriver).derive

        val withDate = MaybeEvent("party", Some(LocalDate.of(2025, 12, 25)))
        val noDate   = MaybeEvent("tbd", None)

        val (jsonWith, decodedWith) = jsonRoundTrip(codec, withDate)
        val (jsonNone, decodedNone) = jsonRoundTrip(codec, noDate)

        assertTrue(
          jsonWith == """{"name":"party","date":"25/12/2025"}""",
          decodedWith == Right(withDate),
          jsonNone == """{"name":"tbd"}""",
          decodedNone == Right(noDate)
        )
      },
      test("nested record with type-level override") {
        val deriver         = JsonCodecDeriver.withInstance[LocalDate](customLocalDateCodec)
        val codec           = Schema[Outer].deriving(deriver).derive
        val outer           = Outer("wrapper", Event("inner-event", LocalDate.of(2024, 2, 29)))
        val (json, decoded) = jsonRoundTrip(codec, outer)
        assertTrue(
          json == """{"tag":"wrapper","inner":{"name":"inner-event","date":"29/02/2024"}}""",
          decoded == Right(outer)
        )
      }
    ),
    suite("JSON withModifier")(
      test("field rename via withModifier") {
        val deriver = JsonCodecDeriver.withModifier(
          TypeId.of[Named],
          "firstName",
          Modifier.rename("first_name")
        )
        val codec           = Schema[Named].deriving(deriver).derive
        val named           = Named("Alice", "Smith")
        val (json, decoded) = jsonRoundTrip(codec, named)
        assertTrue(
          json == """{"first_name":"Alice","lastName":"Smith"}""",
          decoded == Right(named)
        )
      }
    ),
    suite("chaining")(
      test("withInstance + withModifier can be chained") {
        val deriver = JsonCodecDeriver
          .withInstance[LocalDate](customLocalDateCodec)
          .withModifier(TypeId.of[Event], "name", Modifier.rename("title"))
        val codec           = Schema[Event].deriving(deriver).derive
        val event           = Event("conf", LocalDate.of(2025, 9, 1))
        val (json, decoded) = jsonRoundTrip(codec, event)
        assertTrue(
          json == """{"title":"conf","date":"01/09/2025"}""",
          decoded == Right(event)
        )
      },
      test("multiple withInstance calls accumulate") {
        val stringifyInt: JsonCodec[Int] = new JsonCodec[Int] {
          def decodeValue(in: JsonReader): Int = in.readStringAsInt()

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)

          override def decodeValue(json: Json): Int = json match {
            case s: Json.String => s.value.toInt
            case _              => error("expected Json.String")
          }

          override def encodeValue(x: Int): Json = new Json.String(x.toString)
        }

        val deriver = JsonCodecDeriver
          .withInstance[LocalDate](customLocalDateCodec)
          .withInstance[Int](stringifyInt)

        val codec           = Schema[Scored].deriving(deriver).derive
        val scored          = Scored("test", LocalDate.of(2025, 1, 1), 42)
        val (json, decoded) = jsonRoundTrip(codec, scored)
        assertTrue(
          json == """{"name":"test","date":"01/01/2025","score":"42"}""",
          decoded == Right(scored)
        )
      }
    ),
    suite("backward compatibility")(
      test("Schema.derive(Format) still works") {
        val codec           = Schema[Event].derive(JsonFormat)
        val event           = Event("compat", LocalDate.of(2025, 6, 1))
        val (json, decoded) = jsonRoundTrip(codec, event)
        assertTrue(
          json == """{"name":"compat","date":"2025-06-01"}""",
          decoded == Right(event)
        )
      },
      test("DerivationBuilder path still works") {
        val codec = Schema[Event]
          .deriving(JsonCodecDeriver)
          .instance(TypeId.of[LocalDate], customLocalDateCodec)
          .derive
        val event           = Event("builder", LocalDate.of(2025, 3, 14))
        val (json, decoded) = jsonRoundTrip(codec, event)
        assertTrue(
          json == """{"name":"builder","date":"14/03/2025"}""",
          decoded == Right(event)
        )
      }
    )
  )
}
