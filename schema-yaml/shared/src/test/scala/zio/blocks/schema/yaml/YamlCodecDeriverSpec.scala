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

package zio.blocks.schema.yaml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object YamlCodecDeriverSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
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
  case class Dog(name: String)             extends Animal
  case class Cat(name: String, lives: Int) extends Animal
  object Animal {
    implicit val dogSchema: Schema[Dog] = Schema.derived
    implicit val catSchema: Schema[Cat] = Schema.derived
    implicit val schema: Schema[Animal] = Schema.derived
  }

  case class Team(members: List[String])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  case class Config(settings: Map[String, Int])
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

  def spec: Spec[TestEnvironment, Any] = suite("YamlCodecDeriverSpec")(
    suite("record derivation")(
      test("encode simple case class") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val person = Person("Alice", 30)
        val yaml   = codec.encodeToString(person)
        assertTrue(yaml == "name: Alice\nage: 30")
      },
      test("decode simple case class") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val yaml   = "name: Bob\nage: 25"
        val result = codec.decode(yaml)
        assertTrue(result == Right(Person("Bob", 25)))
      },
      test("decode error for missing field") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val yaml   = "name: Bob"
        val result = codec.decode(yaml)
        assertTrue(result.swap.toOption.get.getMessage == "Missing required field: age at: .")
      },
      test("decode error for illegal value") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val yaml   = "name: Bob\nage: Bob"
        val result = codec.decode(yaml)
        assertTrue(result.swap.toOption.get.getMessage == "Invalid int value: Bob at: .age")
      },
      test("round-trip simple case class") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val person = Person("Charlie", 35)
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      },
      test("encode nested case class") {
        val codec  = Schema[PersonWithAddress].derive(YamlCodecDeriver)
        val person = PersonWithAddress("Alice", Address("123 Main St", "Springfield"))
        val yaml   = codec.encodeToString(person)
        assertTrue(yaml == "name: Alice\naddress:\n  street: \"123 Main St\"\n  city: Springfield")
      },
      test("round-trip nested case class") {
        val codec  = Schema[PersonWithAddress].derive(YamlCodecDeriver)
        val person = PersonWithAddress("Bob", Address("456 Oak Ave", "Shelbyville"))
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      },
      test("round-trip nested recursive") {
        val codec     = Schema[Recursive].derive(YamlCodecDeriver)
        val recursive = Recursive("Bob", Some(Recursive("Alice", None)))
        val result    = codec.decode(codec.encode(recursive))
        assertTrue(result == Right(recursive))
      }
    ),
    suite("variant derivation")(
      test("encode sealed trait case - Dog") {
        val codec          = Schema[Animal].derive(YamlCodecDeriver)
        val animal: Animal = Dog("Rex")
        val yaml           = codec.encodeToString(animal)
        assertTrue(yaml == "Dog:\n  name: Rex")
      },
      test("encode sealed trait case - Cat") {
        val codec          = Schema[Animal].derive(YamlCodecDeriver)
        val animal: Animal = Cat("Whiskers", 9)
        val yaml           = codec.encodeToString(animal)
        assertTrue(yaml == "Cat:\n  name: Whiskers\n  lives: 9")
      },
      test("round-trip sealed trait - Dog") {
        val codec          = Schema[Animal].derive(YamlCodecDeriver)
        val animal: Animal = Dog("Buddy")
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      },
      test("round-trip sealed trait - Cat") {
        val codec          = Schema[Animal].derive(YamlCodecDeriver)
        val animal: Animal = Cat("Mittens", 7)
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      },
      test("decode error for invalid case") {
        val codec  = Schema[Animal].derive(YamlCodecDeriver)
        val result = codec.decode("Octocat:\n  name: Mittens")
        assertTrue(result.swap.toOption.get.getMessage == "Unknown variant case: Octocat at: .")
      }
    ),
    suite("sequence derivation")(
      test("encode list of strings") {
        val codec = Schema[Team].derive(YamlCodecDeriver)
        val team  = Team(List("Alice", "Bob", "Charlie"))
        val yaml  = codec.encodeToString(team)
        assertTrue(yaml == "members:\n  - Alice\n  - Bob\n  - Charlie")
      },
      test("round-trip list of strings") {
        val codec  = Schema[Team].derive(YamlCodecDeriver)
        val team   = Team(List("Alice", "Bob"))
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      },
      test("round-trip empty list") {
        val codec  = Schema[Team].derive(YamlCodecDeriver)
        val team   = Team(List.empty)
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      }
    ),
    suite("map derivation")(
      test("encode map of string to int") {
        val codec  = Schema[Config].derive(YamlCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val yaml   = codec.encodeToString(config)
        assertTrue(yaml == "settings:\n  timeout: 30\n  retries: 3")
      },
      test("round-trip map of string to int") {
        val codec  = Schema[Config].derive(YamlCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      },
      test("round-trip empty map") {
        val codec  = Schema[Config].derive(YamlCodecDeriver)
        val config = Config(Map.empty)
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      },
      test("decode error for key") {
        val codec  = Schema[Config].derive(YamlCodecDeriver)
        val result = codec.decode("settings:\n  : 30\n  retries: 3")
        assertTrue(
          result.swap.toOption.get.getMessage == "Expected mapping for map at: .settings"
        )
      },
      test("decode error for value") {
        val codec  = Schema[Config].derive(YamlCodecDeriver)
        val result = codec.decode("settings:\n  timeout: 30\n  retries: y")
        assertTrue(result.swap.toOption.get.getMessage == "Invalid int value: y at: .settings.atKey(\"retries\")")
      }
    ),
    suite("optional field handling")(
      test("encode with Some value") {
        val codec = Schema[OptionalField].derive(YamlCodecDeriver)
        val value = OptionalField("Alice", Some("Ali"))
        val yaml  = codec.encodeToString(value)
        assertTrue(yaml == "name: Alice\nnickname: Ali")
      },
      test("encode with None value") {
        val codec = Schema[OptionalField].derive(YamlCodecDeriver)
        val value = OptionalField("Bob", None)
        val yaml  = codec.encodeToString(value)
        assertTrue(yaml == "name: Bob")
      },
      test("round-trip with Some value") {
        val codec  = Schema[OptionalField].derive(YamlCodecDeriver)
        val value  = OptionalField("Charlie", Some("Chuck"))
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip with None value") {
        val codec  = Schema[OptionalField].derive(YamlCodecDeriver)
        val value  = OptionalField("Dave", None)
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("primitive types")(
      test("round-trip Int") {
        val codec  = Schema[Int].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(42))
        assertTrue(result == Right(42))
      },
      test("round-trip String") {
        val codec  = Schema[String].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode("hello"))
        assertTrue(result == Right("hello"))
      },
      test("round-trip Boolean") {
        val codec  = Schema[Boolean].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(true))
        assertTrue(result == Right(true))
      },
      test("round-trip Long") {
        val codec  = Schema[Long].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(123456789L))
        assertTrue(result == Right(123456789L))
      },
      test("round-trip Double") {
        val codec  = Schema[Double].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(3.14159))
        assertTrue(result == Right(3.14159))
      }
    ),
    suite("round-trip through bytes")(
      test("round-trip case class through Array[Byte]") {
        val codec  = Schema[Person].derive(YamlCodecDeriver)
        val person = Person("Alice", 30)
        val bytes  = codec.encode(person)
        val result = codec.decode(bytes)
        assertTrue(result == Right(person))
      }
    )
  )
}
