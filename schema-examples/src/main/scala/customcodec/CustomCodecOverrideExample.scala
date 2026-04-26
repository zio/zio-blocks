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

package customcodec

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId

import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Configure-once, use-everywhere codec overrides with `Deriver.withInstance`.
 *
 * Instead of repeating `.instance(...)` on every `DerivationBuilder`, create a
 * pre-configured deriver once and derive codecs for any schema that contains
 * the overridden type.
 *
 * Run with: sbt "schema-examples/runMain
 * customcodec.CustomCodecOverrideExample"
 */
object CustomCodecOverrideExample extends App {

  // Domain types
  case class Event(name: String, date: LocalDate)
  object Event {
    implicit val schema: Schema[Event] = Schema.derived
  }

  case class Schedule(owner: String, events: List[Event])
  object Schedule {
    implicit val schema: Schema[Schedule] = Schema.derived
  }

  // Step 1: Define a custom codec for LocalDate using dd/MM/yyyy
  private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  private val europeanDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
    def decodeValue(in: JsonReader): LocalDate =
      LocalDate.parse(in.readString(), dateFmt)

    def encodeValue(x: LocalDate, out: JsonWriter): Unit =
      out.writeVal(dateFmt.format(x))

    override def decodeValue(json: Json): LocalDate = json match {
      case s: Json.String => LocalDate.parse(s.value, dateFmt)
      case _              => error("expected Json.String")
    }

    override def encodeValue(x: LocalDate): Json =
      new Json.String(dateFmt.format(x))
  }

  // Step 2: Create a pre-configured deriver — configure once
  val myDeriver = JsonCodecDeriver
    .withInstance[LocalDate](europeanDateCodec)
    .withModifier(TypeId.of[Event], "name", Modifier.rename("title"))

  // Step 3: Derive codecs for any schema — use everywhere
  val eventCodec: JsonCodec[Event]       = Schema[Event].deriving(myDeriver).derive
  val scheduleCodec: JsonCodec[Schedule] = Schema[Schedule].deriving(myDeriver).derive

  // Demonstrate
  val event    = Event("ZIO World", LocalDate.of(2025, 6, 15))
  val schedule = Schedule("alice", List(event, Event("ScalaCon", LocalDate.of(2025, 9, 1))))

  def encode[A](codec: JsonCodec[A], value: A): String = {
    val buf = ByteBuffer.allocate(4096)
    codec.encode(value, buf)
    new String(java.util.Arrays.copyOf(buf.array, buf.position), "UTF-8")
  }

  println("=== Custom Codec Override Example ===")
  println()
  println("Event JSON:")
  println(s"  ${encode(eventCodec, event)}")
  println()
  println("Schedule JSON (override applies to nested Events too):")
  println(s"  ${encode(scheduleCodec, schedule)}")
  println()

  // Step 4: For comparison, default deriver uses ISO dates
  val defaultCodec = Schema[Event].derive(JsonFormat)
  println("Default Event JSON (ISO dates, original field names):")
  println(s"  ${encode(defaultCodec, event)}")
}
