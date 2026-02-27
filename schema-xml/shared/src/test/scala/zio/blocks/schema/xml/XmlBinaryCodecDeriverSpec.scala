package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.blocks.schema.JavaTimeGen._
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object XmlBinaryCodecDeriverSpec extends SchemaBaseSpec {

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
      test("round-trip Boolean") {
        val codec = Schema[Boolean].derive(XmlBinaryCodecDeriver)
        check(Gen.boolean)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Byte") {
        val codec = Schema[Byte].derive(XmlBinaryCodecDeriver)
        check(Gen.byte)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Char") {
        val codec = Schema[Char].derive(XmlBinaryCodecDeriver)
        check(Gen.char.filter(x => x >= ' ' && (x < 0xd800 || x > 0xdfff))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip Float") {
        val codec = Schema[Float].derive(XmlBinaryCodecDeriver)
        check(Gen.float)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Int") {
        val codec = Schema[Int].derive(XmlBinaryCodecDeriver)
        check(Gen.int)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Double") {
        val codec = Schema[Double].derive(XmlBinaryCodecDeriver)
        check(Gen.double)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Long") {
        val codec = Schema[Long].derive(XmlBinaryCodecDeriver)
        check(Gen.long)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip BigInt") {
        val codec = Schema[BigInt].derive(XmlBinaryCodecDeriver)
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip BigDecimal") {
        val codec = Schema[BigDecimal].derive(XmlBinaryCodecDeriver)
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20))) { x =>
          assertTrue(codec.decode(codec.encode(x)) == Right(x))
        }
      },
      test("round-trip String") {
        val codec = Schema[String].derive(XmlBinaryCodecDeriver)
        check(Gen.string)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip DayOfWeek") {
        val codec = Schema[DayOfWeek].derive(XmlBinaryCodecDeriver)
        check(genDayOfWeek)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Duration") {
        val codec = Schema[Duration].derive(XmlBinaryCodecDeriver)
        check(genDuration)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Instant") {
        val codec = Schema[Instant].derive(XmlBinaryCodecDeriver)
        check(genInstant)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalDate") {
        val codec = Schema[LocalDate].derive(XmlBinaryCodecDeriver)
        check(genLocalDate)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalDateTime") {
        val codec = Schema[LocalDateTime].derive(XmlBinaryCodecDeriver)
        check(genLocalDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip LocalTime") {
        val codec = Schema[LocalTime].derive(XmlBinaryCodecDeriver)
        check(genLocalTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Month") {
        val codec = Schema[Month].derive(XmlBinaryCodecDeriver)
        check(genMonth)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip MonthDay") {
        val codec = Schema[MonthDay].derive(XmlBinaryCodecDeriver)
        check(genMonthDay)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip OffsetDateTime") {
        val codec = Schema[OffsetDateTime].derive(XmlBinaryCodecDeriver)
        check(genOffsetDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip OffsetTime") {
        val codec = Schema[OffsetTime].derive(XmlBinaryCodecDeriver)
        check(genOffsetTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Period") {
        val codec = Schema[Period].derive(XmlBinaryCodecDeriver)
        check(genPeriod)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Year") {
        val codec = Schema[Year].derive(XmlBinaryCodecDeriver)
        check(genYear)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      } @@ TestAspect.jvmOnly,
      test("round-trip YearMonth") {
        val codec = Schema[YearMonth].derive(XmlBinaryCodecDeriver)
        check(genYearMonth)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZoneId") {
        val codec = Schema[ZoneId].derive(XmlBinaryCodecDeriver)
        check(genZoneId)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZoneOffset") {
        val codec = Schema[ZoneOffset].derive(XmlBinaryCodecDeriver)
        check(genZoneOffset)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip ZonedDateTime") {
        val codec = Schema[ZonedDateTime].derive(XmlBinaryCodecDeriver)
        check(genZonedDateTime)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Currency") {
        val codec = Schema[Currency].derive(XmlBinaryCodecDeriver)
        check(Gen.currency)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip UUID") {
        val codec = Schema[UUID].derive(XmlBinaryCodecDeriver)
        check(Gen.uuid)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      },
      test("round-trip Unit") {
        val codec = Schema[Unit].derive(XmlBinaryCodecDeriver)
        check(Gen.unit)(x => assertTrue(codec.decode(codec.encode(x)) == Right(x)))
      }
    )
  )
}
