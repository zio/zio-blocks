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

package deriveroverrides

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId
import zio.blocks.schema.CompanionOptics

/**
 * Customizing Type Class Derivation with Instance Overrides — Complete Example
 *
 * Demonstrates combining all three override levels and understanding resolution
 * order:
 *   1. Optic-based (exact path) — highest priority
 *   2. Term-name-based (field within parent type) — medium priority
 *   3. Type-based (all occurrences) — lowest priority
 *
 * In this example, we show how different overrides apply to different fields
 * based on their specificity.
 *
 * Run with: sbt "schema-examples/runMain deriveroverrides.CompleteExample"
 */
object CompleteExample extends App {

  // Custom codecs
  val stringifyInt = new JsonBinaryCodec[Int] {
    def decodeValue(in: JsonReader): Int           = in.readStringAsInt()
    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x.toString)
  }

  val padStringCodec = new JsonBinaryCodec[String] {
    def decodeValue(in: JsonReader): String =
      in.readString().trim

    def encodeValue(x: String, out: JsonWriter): Unit =
      out.writeVal(s"[${x}]") // Add brackets for visibility
  }

  // Domain types
  case class Company(name: String, foundedYear: Int)

  object Company extends CompanionOptics[Company] {
    implicit val schema: Schema[Company] = Schema.derived
    val name: Lens[Company, String]      = optic(_.name)
  }

  case class Address(city: String, zipCode: String)

  object Address extends CompanionOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
    val city: Lens[Address, String]      = optic(_.city)
  }

  case class UserProfile(
    id: Int,
    username: String,
    age: Int,
    score: Int,
    address: Address,
    company: Company
  )

  object UserProfile extends CompanionOptics[UserProfile] {
    implicit val schema: Schema[UserProfile] = Schema.derived

    val id: Lens[UserProfile, Int]          = optic(_.id)
    val age: Lens[UserProfile, Int]         = optic(_.age)
    val score: Lens[UserProfile, Int]       = optic(_.score)
    val address: Lens[UserProfile, Address] = optic(_.address)
    val company: Lens[UserProfile, Company] = optic(_.company)
  }

  // Derive with multiple overrides at different levels
  val codec: JsonBinaryCodec[UserProfile] = UserProfile.schema
    .deriving(JsonBinaryCodecDeriver)
    // Level 3: Type-based — affects all Int fields
    .instance(
      TypeId.of[Int],
      stringifyInt
    )
    // Level 2: Term-name-based — overrides Int override for 'score' field only
    .instance(
      TypeId.of[UserProfile],
      "score",
      new JsonBinaryCodec[Int] {
        def decodeValue(in: JsonReader): Int           = in.readInt()
        def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)
      }
    )
    // Level 1: Optic-based — highest priority, overrides both above for exact path
    .instance(
      UserProfile.age,
      new JsonBinaryCodec[Int] {
        def decodeValue(in: JsonReader): Int =
          in.readStringAsInt()

        def encodeValue(x: Int, out: JsonWriter): Unit =
          out.writeVal(s"AGE:${x}") // Special format for visibility
      }
    )
    .derive

  // Test
  val user = UserProfile(
    id = 123,
    username = "alice",
    age = 30,
    score = 850,
    address = Address("NYC", "10001"),
    company = Company("TechCorp", 2020)
  )

  println("=== Complete Example: All Three Override Levels ===")
  println(s"Original: $user")
  println()

  val encoded = codec.encode(user)
  println(s"Encoded (${encoded.length} bytes)")

  val decoded = codec.decode(encoded)
  println(s"Decoded: $decoded")
  println()

  println("Resolution order demonstration:")
  println("  - 'id': Uses type-based override (all Ints) → stringified")
  println("  - 'age': Uses optic-based override (exact path) → special format")
  println("  - 'score': Uses term-name override (overrides type-based) → regular number")
  println("  - 'foundedYear' in Company: Uses type-based override → stringified")
}
