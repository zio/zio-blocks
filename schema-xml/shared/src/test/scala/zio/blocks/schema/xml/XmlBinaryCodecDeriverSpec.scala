package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlBinaryCodecDeriverSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("XmlBinaryCodecDeriverSpec")(
    suite("record derivation")(
      test("encode simple case class") {
        val codec  = Schema[Person].derive(XmlBinaryCodecDeriver)
        val person = Person("Alice", 30)
        val xml    = codec.encodeToString(person)
        assertTrue(
          xml.contains("<name>Alice</name>") &&
            xml.contains("<age>30</age>")
        )
      },
      test("decode simple case class") {
        val codec  = Schema[Person].derive(XmlBinaryCodecDeriver)
        val xml    = "<Person><name>Bob</name><age>25</age></Person>"
        val result = codec.decode(xml)
        assertTrue(result == Right(Person("Bob", 25)))
      },
      test("round-trip simple case class") {
        val codec  = Schema[Person].derive(XmlBinaryCodecDeriver)
        val person = Person("Charlie", 35)
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      },
      test("encode nested case class") {
        val codec  = Schema[PersonWithAddress].derive(XmlBinaryCodecDeriver)
        val person = PersonWithAddress("Alice", Address("123 Main St", "Springfield"))
        val xml    = codec.encodeToString(person)
        assertTrue(
          xml.contains("<name>Alice</name>") &&
            xml.contains("<address>") &&
            xml.contains("<street>123 Main St</street>") &&
            xml.contains("<city>Springfield</city>")
        )
      },
      test("round-trip nested case class") {
        val codec  = Schema[PersonWithAddress].derive(XmlBinaryCodecDeriver)
        val person = PersonWithAddress("Bob", Address("456 Oak Ave", "Shelbyville"))
        val result = codec.decode(codec.encode(person))
        assertTrue(result == Right(person))
      }
    ),
    suite("variant derivation")(
      test("encode sealed trait case - Dog") {
        val codec          = Schema[Animal].derive(XmlBinaryCodecDeriver)
        val animal: Animal = Dog("Rex")
        val xml            = codec.encodeToString(animal)
        assertTrue(
          xml.contains("<Dog>") &&
            xml.contains("<name>Rex</name>")
        )
      },
      test("encode sealed trait case - Cat") {
        val codec          = Schema[Animal].derive(XmlBinaryCodecDeriver)
        val animal: Animal = Cat("Whiskers", 9)
        val xml            = codec.encodeToString(animal)
        assertTrue(
          xml.contains("<Cat>") &&
            xml.contains("<name>Whiskers</name>") &&
            xml.contains("<lives>9</lives>")
        )
      },
      test("round-trip sealed trait - Dog") {
        val codec          = Schema[Animal].derive(XmlBinaryCodecDeriver)
        val animal: Animal = Dog("Buddy")
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      },
      test("round-trip sealed trait - Cat") {
        val codec          = Schema[Animal].derive(XmlBinaryCodecDeriver)
        val animal: Animal = Cat("Mittens", 7)
        val result         = codec.decode(codec.encode(animal))
        assertTrue(result == Right(animal))
      }
    ),
    suite("sequence derivation")(
      test("encode list of strings") {
        val codec = Schema[Team].derive(XmlBinaryCodecDeriver)
        val team  = Team(List("Alice", "Bob", "Charlie"))
        val xml   = codec.encodeToString(team)
        assertTrue(
          xml.contains("<members>") &&
            xml.contains("<item>Alice</item>") &&
            xml.contains("<item>Bob</item>") &&
            xml.contains("<item>Charlie</item>")
        )
      },
      test("round-trip list of strings") {
        val codec  = Schema[Team].derive(XmlBinaryCodecDeriver)
        val team   = Team(List("Alice", "Bob"))
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      },
      test("encode empty list") {
        val codec = Schema[Team].derive(XmlBinaryCodecDeriver)
        val team  = Team(List.empty)
        val xml   = codec.encodeToString(team)
        assertTrue(xml.contains("<members"))
      },
      test("round-trip empty list") {
        val codec  = Schema[Team].derive(XmlBinaryCodecDeriver)
        val team   = Team(List.empty)
        val result = codec.decode(codec.encode(team))
        assertTrue(result == Right(team))
      }
    ),
    suite("map derivation")(
      test("encode map of string to int") {
        val codec  = Schema[Config].derive(XmlBinaryCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val xml    = codec.encodeToString(config)
        assertTrue(
          xml.contains("<settings>") &&
            xml.contains("<entry>") &&
            xml.contains("<key>") &&
            xml.contains("<value>")
        )
      },
      test("round-trip map of string to int") {
        val codec  = Schema[Config].derive(XmlBinaryCodecDeriver)
        val config = Config(Map("timeout" -> 30, "retries" -> 3))
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      },
      test("encode empty map") {
        val codec  = Schema[Config].derive(XmlBinaryCodecDeriver)
        val config = Config(Map.empty)
        val xml    = codec.encodeToString(config)
        assertTrue(xml.contains("<settings"))
      },
      test("round-trip empty map") {
        val codec  = Schema[Config].derive(XmlBinaryCodecDeriver)
        val config = Config(Map.empty)
        val result = codec.decode(codec.encode(config))
        assertTrue(result == Right(config))
      }
    ),
    suite("optional field handling")(
      test("encode with Some value") {
        val codec = Schema[OptionalField].derive(XmlBinaryCodecDeriver)
        val value = OptionalField("Alice", Some("Ali"))
        val xml   = codec.encodeToString(value)
        assertTrue(
          xml.contains("<name>Alice</name>") &&
            xml.contains("<nickname>Ali</nickname>")
        )
      },
      test("encode with None value") {
        val codec = Schema[OptionalField].derive(XmlBinaryCodecDeriver)
        val value = OptionalField("Bob", None)
        val xml   = codec.encodeToString(value)
        assertTrue(xml.contains("<name>Bob</name>"))
      },
      test("round-trip with Some value") {
        val codec  = Schema[OptionalField].derive(XmlBinaryCodecDeriver)
        val value  = OptionalField("Charlie", Some("Chuck"))
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip with None value") {
        val codec  = Schema[OptionalField].derive(XmlBinaryCodecDeriver)
        val value  = OptionalField("Dave", None)
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("primitive types")(
      test("round-trip Int") {
        val codec  = Schema[Int].derive(XmlBinaryCodecDeriver)
        val result = codec.decode(codec.encode(42))
        assertTrue(result == Right(42))
      },
      test("round-trip String") {
        val codec  = Schema[String].derive(XmlBinaryCodecDeriver)
        val result = codec.decode(codec.encode("hello"))
        assertTrue(result == Right("hello"))
      },
      test("round-trip Boolean") {
        val codec  = Schema[Boolean].derive(XmlBinaryCodecDeriver)
        val result = codec.decode(codec.encode(true))
        assertTrue(result == Right(true))
      },
      test("round-trip Long") {
        val codec  = Schema[Long].derive(XmlBinaryCodecDeriver)
        val result = codec.decode(codec.encode(123456789L))
        assertTrue(result == Right(123456789L))
      },
      test("round-trip Double") {
        val codec  = Schema[Double].derive(XmlBinaryCodecDeriver)
        val result = codec.decode(codec.encode(3.14159))
        assertTrue(result == Right(3.14159))
      }
    )
  )
}
