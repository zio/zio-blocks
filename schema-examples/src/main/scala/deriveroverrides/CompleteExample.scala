package deriveroverrides

import zio.blocks.schema._
import zio.blocks.schema.CompanionOptics
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId

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
 * Run with: cd schema-examples && sbt "runMain deriveroverrides.CompleteExample"
 */
object CompleteExample extends App {

  // Domain types
  case class Company(name: String, foundedYear: Int)

  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  case class Address(city: String, zipCode: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
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

    val id: Lens[UserProfile, Int] = optic(_.id)
  }

  // An Int codec that writes the number as a JSON string with a prefix and a
  // suffix, so the output shows which override was applied to each field.
  def labelledIntCodec(prefix: String, suffix: String): JsonCodec[Int] = new JsonCodec[Int] {
    def decodeValue(in: JsonReader): Int = {
      val s = in.readString()
      if (s.startsWith(prefix) && s.endsWith(suffix))
        s.substring(prefix.length, s.length - suffix.length).toInt
      else error(s"expected $prefix<number>$suffix, got: $s")
    }

    def encodeValue(x: Int, out: JsonWriter): Unit =
      out.writeVal(s"$prefix$x$suffix")
  }

  val everyInt   = labelledIntCodec("", "")        // type-based: every Int
  val ageInYears = labelledIntCodec("", " years")  // term-name-based: UserProfile.age
  val idByName   = labelledIntCodec("id-", "")     // term-name-based: UserProfile.id
  val idByOptic  = labelledIntCodec("user-", "")   // optic-based: UserProfile.id

  val codec: JsonCodec[UserProfile] =
    Schema[UserProfile]
      .deriving(JsonCodecDeriver)
      .instance(TypeId.of[Int], everyInt)
      .instance(TypeId.of[UserProfile], "age", ageInYears)
      .instance(TypeId.of[UserProfile], "id", idByName)
      .instance(UserProfile.id, idByOptic)
      .derive

  val user = UserProfile(
    id = 123,
    username = "alice",
    age = 30,
    score = 850,
    address = Address("NYC", "10001"),
    company = Company("TechCorp", 2020)
  )

  val json = codec.encodeToString(user)

  println("=== Customizing Type Class Derivation: Resolution Order ===")
  println(s"Default:    ${Schema[UserProfile].jsonCodec.encodeToString(user)}")
  println(s"Overridden: $json")
  println()
  println("id                  -> optic override wins over the term-name override for the same field")
  println("age                 -> term-name override wins over the type-based override")
  println("score, foundedYear  -> type-based override applies to every other Int, nested ones included")
  println()
  println(s"Round trip: ${codec.decode(json) == Right(user)}")
}
