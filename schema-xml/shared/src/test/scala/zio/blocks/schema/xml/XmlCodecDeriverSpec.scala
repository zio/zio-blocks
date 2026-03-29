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

package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.blocks.schema.JavaTimeGen._
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object XmlCodecDeriverSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Short)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, address: Address)
  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  sealed trait Animal
  case class Dog(name: String)              extends Animal
  case class Cat(name: String, lives: Byte) extends Animal
  object Animal {
    implicit val dogSchema: Schema[Dog] = Schema.derived
    implicit val catSchema: Schema[Cat] = Schema.derived
    implicit val schema: Schema[Animal] = Schema.derived
  }

  case class Team(members: List[String])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  case class Config(settings: Map[String, Float])
  object Config {
    implicit val schema: Schema[Config] = Schema.derived
  }

  case class OptionalField(name: String, nickname: Option[String])
  object OptionalField {
    implicit val schema: Schema[OptionalField] = Schema.derived
  }

  case class Recursive(value: String, next: Option[Recursive])
  object Recursive {
    implicit val schema: Schema[Recursive] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("XmlCodecDeriverSpec")(
    suite("record derivation")(
      test("encode simple case class") {
        val codec  = Schema[Person].derive(XmlCodecDeriver)
        val person = Person("Alice", 30)
        val xml    = codec.encodeToString(person)
        assertTrue(xml == "<Person><name>Alice</name><age>30</age></Person>")
      },
      test("decode simple case class") {
        val codec  = Schema[Person].derive(XmlCodecDeriver)
        val xml    = "<Person><name>Bob</name><age>25</age></Person>"
        val result = codec.decode(xml)
        assertTrue(result == Right(Person("Bob", 25)))
      },
      test("decode error for missing field") {
        val codec  = Schema[Person].derive(XmlCodecDeriver)
        val xml    = "<Person><name>Bob</name></Person>"
        val result = codec.decode(xml)
        assertTrue(result.swap.toOption.get.getMessage == "Missing required field: age at: .")
      },
      test("decode error for illegal value") {
        val codec  = Schema[Person].derive(XmlCodecDeriver)
        val xml    = "<Person><name>Bob</name><age>Bob</age></Person>"
        val result = codec.decode(xml)
        assertTrue(result.swap.toOption.get.getMessage == "Invalid short value: Bob at: .age")
      },
      test("round-trip simple case class") {
        val codec  = Schema[Person].derive(XmlCodecDeriver)
        val person = Person("Charlie", 35)
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      },
      test("encode nested case class") {
        val codec  = Schema[PersonWithAddress].derive(XmlCodecDeriver)
        val person = PersonWithAddress("Alice", Address("123 Main St", "Springfield"))
        val xml    = codec.encodeToString(person)
        assertTrue(
          xml == "<PersonWithAddress><name>Alice</name><address><street>123 Main St</street><city>Springfield</city></address></PersonWithAddress>"
        )
      },
      test("round-trip nested case class") {
        val codec  = Schema[PersonWithAddress].derive(XmlCodecDeriver)
        val person = PersonWithAddress("Bob", Address("456 Oak Ave", "Shelbyville"))
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      },
      test("round-trip nested recursive") {
        val codec     = Schema[Recursive].derive(XmlCodecDeriver)
        val recursive = Recursive("Bob", Some(Recursive("Alice", None)))
        val result    = codec.decode(codec.encode(recursive))
        assertTrue(result == Right(recursive))
      }
    ),
    suite("variant derivation")(
      test("encode sealed trait case - Dog") {
        val codec          = Schema[Animal].derive(XmlCodecDeriver)
        val animal: Animal = Dog("Rex")
        val xml            = codec.encodeToString(animal)
        assertTrue(xml == "<Dog><name>Rex</name></Dog>")
      },
      test("encode sealed trait case - Cat") {
        val codec          = Schema[Animal].derive(XmlCodecDeriver)
        val animal: Animal = Cat("Whiskers", 9)
        val xml            = codec.encodeToString(animal)
        assertTrue(xml == "<Cat><name>Whiskers</name><lives>9</lives></Cat>")
      },
      test("round-trip sealed trait - Dog") {
        val codec          = Schema[Animal].derive(XmlCodecDeriver)
        val animal: Animal = Dog("Buddy")
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      },
      test("round-trip sealed trait - Cat") {
        val codec          = Schema[Animal].derive(XmlCodecDeriver)
        val animal: Animal = Cat("Mittens", 7)
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      },
      test("decode error for invalid case") {
        val codec  = Schema[Animal].derive(XmlCodecDeriver)
        val result = codec.decode("<Octocat><name>Whiskers</name><lives>9</lives></Octocat>")
        assertTrue(result.swap.toOption.get.getMessage == "Unknown variant case: Octocat at: .")
      }
    ),
    suite("sequence derivation")(
      test("encode list of strings") {
        val codec = Schema[Team].derive(XmlCodecDeriver)
        val team  = Team(List("Alice", "Bob", "Charlie"))
        val xml   = codec.encodeToString(team)
        assertTrue(xml == "<Team><members><item>Alice</item><item>Bob</item><item>Charlie</item></members></Team>")
      },
      test("round-trip list of strings") {
        val codec  = Schema[Team].derive(XmlCodecDeriver)
        val team   = Team(List("Alice", "Bob"))
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      },
      test("encode empty list") {
        val codec = Schema[Team].derive(XmlCodecDeriver)
        val team  = Team(List.empty)
        val xml   = codec.encodeToString(team)
        assertTrue(xml == "<Team><members/></Team>")
      },
      test("round-trip empty list") {
        val codec  = Schema[Team].derive(XmlCodecDeriver)
        val team   = Team(List.empty)
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      },
      test("decode error") {
        val codec  = Schema[Team].derive(XmlCodecDeriver)
        val result = codec.decode("<Team><members><item>Alice</item><item><unit/></item></members></Team>")
        assertTrue(result.swap.toOption.get.getMessage == "Expected text content in <value> element at: .members.at(1)")
      }
    ),
    suite("map derivation")(
      test("encode map of string to int") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val xml    = codec.encodeToString(config)
        assertTrue(
          xml == "<Config><settings><entry><key>timeout</key><value>30.0</value></entry><entry><key>retries</key><value>3.0</value></entry></settings></Config>"
        )
      },
      test("round-trip map of string to int") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      },
      test("encode empty map") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val config = Config(Map.empty)
        val xml    = codec.encodeToString(config)
        assertTrue(xml == "<Config><settings/></Config>")
      },
      test("round-trip empty map") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val config = Config(Map.empty)
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      },
      test("decode error for key") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val result = codec.decode("<Config><settings><entry><key><x/></key><value></value></entry></settings></Config>")
        assertTrue(
          result.swap.toOption.get.getMessage == "Expected text content in <value> element at: .settings.at(0)"
        )
      },
      test("decode error for value") {
        val codec  = Schema[Config].derive(XmlCodecDeriver)
        val result = codec.decode("<Config><settings><entry><key>x</key><value>y</value></entry></settings></Config>")
        assertTrue(result.swap.toOption.get.getMessage == "Invalid float value: y at: .settings.atKey(\"x\")")
      }
    ),
    suite("optional field handling")(
      test("encode with Some value") {
        val codec = Schema[OptionalField].derive(XmlCodecDeriver)
        val value = OptionalField("Alice", Some("Ali"))
        val xml   = codec.encodeToString(value)
        assertTrue(xml == "<OptionalField><name>Alice</name><nickname>Ali</nickname></OptionalField>")
      },
      test("encode with None value") {
        val codec = Schema[OptionalField].derive(XmlCodecDeriver)
        val value = OptionalField("Bob", None)
        val xml   = codec.encodeToString(value)
        assertTrue(xml == "<OptionalField><name>Bob</name></OptionalField>")
      },
      test("round-trip with Some value") {
        val codec  = Schema[OptionalField].derive(XmlCodecDeriver)
        val value  = OptionalField("Charlie", Some("Chuck"))
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip with None value") {
        val codec  = Schema[OptionalField].derive(XmlCodecDeriver)
        val value  = OptionalField("Dave", None)
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("primitive types")(
      test("round-trip Boolean") {
        val codec = Schema[Boolean].derive(XmlCodecDeriver)
        check(Gen.boolean)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Byte") {
        val codec = Schema[Byte].derive(XmlCodecDeriver)
        check(Gen.byte)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Char") {
        val codec = Schema[Char].derive(XmlCodecDeriver)
        check(Gen.char.filter(x => x >= ' ' && (x < 0xd800 || x > 0xdfff))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip Float") {
        val codec = Schema[Float].derive(XmlCodecDeriver)
        check(Gen.float)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Int") {
        val codec = Schema[Int].derive(XmlCodecDeriver)
        check(Gen.int)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Double") {
        val codec = Schema[Double].derive(XmlCodecDeriver)
        check(Gen.double)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Long") {
        val codec = Schema[Long].derive(XmlCodecDeriver)
        check(Gen.long)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip BigInt") {
        val codec = Schema[BigInt].derive(XmlCodecDeriver)
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip BigDecimal") {
        val codec = Schema[BigDecimal].derive(XmlCodecDeriver)
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip String") {
        val codec = Schema[String].derive(XmlCodecDeriver)
        check(Gen.string)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip DayOfWeek") {
        val codec = Schema[DayOfWeek].derive(XmlCodecDeriver)
        check(genDayOfWeek)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Duration") {
        val codec = Schema[Duration].derive(XmlCodecDeriver)
        check(genDuration)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Instant") {
        val codec = Schema[Instant].derive(XmlCodecDeriver)
        check(genInstant)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalDate") {
        val codec = Schema[LocalDate].derive(XmlCodecDeriver)
        check(genLocalDate)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalDateTime") {
        val codec = Schema[LocalDateTime].derive(XmlCodecDeriver)
        check(genLocalDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalTime") {
        val codec = Schema[LocalTime].derive(XmlCodecDeriver)
        check(genLocalTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Month") {
        val codec = Schema[Month].derive(XmlCodecDeriver)
        check(genMonth)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip MonthDay") {
        val codec = Schema[MonthDay].derive(XmlCodecDeriver)
        check(genMonthDay)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip OffsetDateTime") {
        val codec = Schema[OffsetDateTime].derive(XmlCodecDeriver)
        check(genOffsetDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip OffsetTime") {
        val codec = Schema[OffsetTime].derive(XmlCodecDeriver)
        check(genOffsetTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Period") {
        val codec = Schema[Period].derive(XmlCodecDeriver)
        check(genPeriod)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Year") {
        val codec = Schema[Year].derive(XmlCodecDeriver)
        check(genYear)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      } @@ TestAspect.jvmOnly,
      test("round-trip YearMonth") {
        val codec = Schema[YearMonth].derive(XmlCodecDeriver)
        check(genYearMonth)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZoneId") {
        val codec = Schema[ZoneId].derive(XmlCodecDeriver)
        check(genZoneId)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZoneOffset") {
        val codec = Schema[ZoneOffset].derive(XmlCodecDeriver)
        check(genZoneOffset)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZonedDateTime") {
        val codec = Schema[ZonedDateTime].derive(XmlCodecDeriver)
        check(genZonedDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Currency") {
        val codec = Schema[Currency].derive(XmlCodecDeriver)
        check(Gen.currency)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip UUID") {
        val codec = Schema[UUID].derive(XmlCodecDeriver)
        check(Gen.uuid)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Unit") {
        val codec = Schema[Unit].derive(XmlCodecDeriver)
        check(Gen.unit)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      }
    )
  )
}
