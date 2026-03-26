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

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.test._

import java.time._
import java.util.{Currency, UUID}

object YamlCodecDeriverExtendedSpec extends YamlBaseSpec {

  case class WithByte(value: Byte)
  object WithByte {
    implicit val schema: Schema[WithByte] = Schema.derived
  }

  case class WithShort(value: Short)
  object WithShort {
    implicit val schema: Schema[WithShort] = Schema.derived
  }

  case class WithFloat(value: Float)
  object WithFloat {
    implicit val schema: Schema[WithFloat] = Schema.derived
  }

  case class WithChar(value: Char)
  object WithChar {
    implicit val schema: Schema[WithChar] = Schema.derived
  }

  case class WithBigInt(value: BigInt)
  object WithBigInt {
    implicit val schema: Schema[WithBigInt] = Schema.derived
  }

  case class WithBigDecimal(value: BigDecimal)
  object WithBigDecimal {
    implicit val schema: Schema[WithBigDecimal] = Schema.derived
  }

  case class WithUnit(value: Unit)
  object WithUnit {
    implicit val schema: Schema[WithUnit] = Schema.derived
  }

  case class AllPrimitives(
    b: Boolean,
    by: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String
  )
  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  case class Wrapper(inner: String)
  object Wrapper {
    implicit val schema: Schema[Wrapper] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("YamlCodecDeriverExtended")(
    suite("primitive field types in records")(
      test("round-trip Byte field") {
        val codec  = Schema[WithByte].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithByte(42.toByte)))
        assertTrue(result == Right(WithByte(42.toByte)))
      },
      test("round-trip Short field") {
        val codec  = Schema[WithShort].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithShort(1000.toShort)))
        assertTrue(result == Right(WithShort(1000.toShort)))
      },
      test("round-trip Float field") {
        val codec  = Schema[WithFloat].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithFloat(1.5f)))
        assertTrue(result == Right(WithFloat(1.5f)))
      },
      test("round-trip Char field") {
        val codec  = Schema[WithChar].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithChar('A')))
        assertTrue(result == Right(WithChar('A')))
      },
      test("round-trip BigInt field") {
        val codec  = Schema[WithBigInt].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithBigInt(BigInt("12345678901234567890"))))
        assertTrue(result == Right(WithBigInt(BigInt("12345678901234567890"))))
      },
      test("round-trip BigDecimal field") {
        val codec  = Schema[WithBigDecimal].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(WithBigDecimal(BigDecimal("3.14159"))))
        assertTrue(result == Right(WithBigDecimal(BigDecimal("3.14159"))))
      },
      test("round-trip all primitive types in single record") {
        val codec  = Schema[AllPrimitives].derive(YamlCodecDeriver)
        val value  = AllPrimitives(true, 1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, 'x', "hello")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("java.time primitive codecs")(
      test("round-trip DayOfWeek") {
        val codec  = Schema[DayOfWeek].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(DayOfWeek.MONDAY))
        assertTrue(result == Right(DayOfWeek.MONDAY))
      },
      test("round-trip Duration") {
        val codec  = Schema[Duration].derive(YamlCodecDeriver)
        val dur    = Duration.ofHours(2)
        val result = codec.decode(codec.encode(dur))
        assertTrue(result == Right(dur))
      },
      test("round-trip Instant") {
        val codec  = Schema[Instant].derive(YamlCodecDeriver)
        val inst   = Instant.parse("2024-01-01T00:00:00Z")
        val result = codec.decode(codec.encode(inst))
        assertTrue(result == Right(inst))
      },
      test("round-trip LocalDate") {
        val codec  = Schema[LocalDate].derive(YamlCodecDeriver)
        val ld     = LocalDate.of(2024, 6, 15)
        val result = codec.decode(codec.encode(ld))
        assertTrue(result == Right(ld))
      },
      test("round-trip LocalDateTime") {
        val codec  = Schema[LocalDateTime].derive(YamlCodecDeriver)
        val ldt    = LocalDateTime.of(2024, 6, 15, 12, 30, 0)
        val result = codec.decode(codec.encode(ldt))
        assertTrue(result == Right(ldt))
      },
      test("round-trip LocalTime") {
        val codec  = Schema[LocalTime].derive(YamlCodecDeriver)
        val lt     = LocalTime.of(12, 30, 0)
        val result = codec.decode(codec.encode(lt))
        assertTrue(result == Right(lt))
      },
      test("round-trip Month") {
        val codec  = Schema[Month].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(Month.MARCH))
        assertTrue(result == Right(Month.MARCH))
      },
      test("round-trip MonthDay") {
        val codec  = Schema[MonthDay].derive(YamlCodecDeriver)
        val md     = MonthDay.of(6, 15)
        val result = codec.decode(codec.encode(md))
        assertTrue(result == Right(md))
      },
      test("round-trip OffsetDateTime") {
        val codec  = Schema[OffsetDateTime].derive(YamlCodecDeriver)
        val odt    = OffsetDateTime.parse("2024-01-01T00:00:00+00:00")
        val result = codec.decode(codec.encode(odt))
        assertTrue(result == Right(odt))
      },
      test("round-trip OffsetTime") {
        val codec  = Schema[OffsetTime].derive(YamlCodecDeriver)
        val ot     = OffsetTime.parse("12:00:00+00:00")
        val result = codec.decode(codec.encode(ot))
        assertTrue(result == Right(ot))
      },
      test("round-trip Period") {
        val codec  = Schema[Period].derive(YamlCodecDeriver)
        val p      = Period.ofDays(30)
        val result = codec.decode(codec.encode(p))
        assertTrue(result == Right(p))
      },
      test("round-trip Year") {
        val codec  = Schema[Year].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(Year.of(2024)))
        assertTrue(result == Right(Year.of(2024)))
      },
      test("round-trip YearMonth") {
        val codec  = Schema[YearMonth].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(YearMonth.of(2024, 6)))
        assertTrue(result == Right(YearMonth.of(2024, 6)))
      },
      test("round-trip ZoneId") {
        val codec  = Schema[ZoneId].derive(YamlCodecDeriver)
        val zi     = ZoneId.of("America/New_York")
        val result = codec.decode(codec.encode(zi))
        assertTrue(result == Right(zi))
      },
      test("round-trip ZoneOffset") {
        val codec  = Schema[ZoneOffset].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(ZoneOffset.UTC))
        assertTrue(result == Right(ZoneOffset.UTC))
      },
      test("round-trip ZonedDateTime") {
        val codec  = Schema[ZonedDateTime].derive(YamlCodecDeriver)
        val zdt    = ZonedDateTime.parse("2024-01-01T00:00:00Z[UTC]")
        val result = codec.decode(codec.encode(zdt))
        assertTrue(result == Right(zdt))
      },
      test("round-trip Currency") {
        val codec  = Schema[Currency].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(Currency.getInstance("USD")))
        assertTrue(result == Right(Currency.getInstance("USD")))
      },
      test("round-trip UUID") {
        val codec  = Schema[UUID].derive(YamlCodecDeriver)
        val uuid   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val result = codec.decode(codec.encode(uuid))
        assertTrue(result == Right(uuid))
      },
      test("invalid DayOfWeek") {
        val codec  = Schema[DayOfWeek].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("INVALID_DAY"))
        assertTrue(result.isLeft)
      },
      test("invalid Instant") {
        val codec  = Schema[Instant].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("not-an-instant"))
        assertTrue(result.isLeft)
      },
      test("non-scalar for time type fails") {
        val codec  = Schema[DayOfWeek].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Mapping.empty)
        assertTrue(result.isLeft)
      }
    ) @@ TestAspect.jvmOnly,
    suite("DynamicValue codec")(
      test("encode/decode DynamicValue.Primitive String") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.String("hello"))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Int") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Int(42))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Long") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Long(999L))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Double") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Double(3.14))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Float") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Float(1.5f))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Boolean") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Byte") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Byte(1.toByte))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Short") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Short(5.toShort))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Char") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.Char('Z'))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive BigInt") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.BigInt(BigInt(1000)))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive BigDecimal") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(BigDecimal("1.23")))
        val encoded = codec.encodeValue(dv)
        val decoded = codec.decodeValue(encoded)
        assertTrue(decoded.isRight)
      },
      test("encode/decode DynamicValue.Primitive Unit") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(PrimitiveValue.Unit)
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded == Yaml.NullValue)
      },
      test("encode/decode DynamicValue.Null") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val encoded = codec.encodeValue(DynamicValue.Null)
        assertTrue(encoded == Yaml.NullValue)
      },
      test("encode/decode DynamicValue.Record") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val dv = new DynamicValue.Record(
          Chunk(
            ("name", new DynamicValue.Primitive(new PrimitiveValue.String("Alice"))),
            ("age", new DynamicValue.Primitive(new PrimitiveValue.Int(30)))
          )
        )
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Mapping])
      },
      test("encode/decode DynamicValue.Variant") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv    = new DynamicValue.Variant(
          "Dog",
          new DynamicValue.Primitive(new PrimitiveValue.String("Rex"))
        )
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Mapping])
      },
      test("encode/decode DynamicValue.Sequence") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val dv = new DynamicValue.Sequence(
          Chunk(
            new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
            new DynamicValue.Primitive(new PrimitiveValue.Int(2))
          )
        )
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Sequence])
      },
      test("encode/decode DynamicValue.Map with string keys") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val dv = new DynamicValue.Map(
          Chunk(
            (
              new DynamicValue.Primitive(new PrimitiveValue.String("key1")),
              new DynamicValue.Primitive(new PrimitiveValue.Int(1))
            )
          )
        )
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Mapping])
      },
      test("encode/decode DynamicValue.Map with non-string keys") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val dv = new DynamicValue.Map(
          Chunk(
            (
              new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
              new DynamicValue.Primitive(new PrimitiveValue.String("one"))
            )
          )
        )
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Sequence])
      },
      test("decode Mapping to DynamicValue") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        val yaml  = Yaml.Mapping.fromStringKeys(
          "name" -> Yaml.Scalar("Alice")
        )
        val decoded = codec.decodeValue(yaml)
        assertTrue(decoded.isRight)
      },
      test("decode Sequence to DynamicValue") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        val yaml  = Yaml.Sequence(
          zio.blocks.chunk.Chunk(Yaml.Scalar("a"), Yaml.Scalar("b"))
        )
        val decoded = codec.decodeValue(yaml)
        assertTrue(decoded.isRight)
      },
      test("decode NullValue to DynamicValue.Null") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.NullValue)
        assertTrue(decoded == Right(DynamicValue.Null))
      },
      test("decode boolean-like scalar to DynamicValue.Primitive Boolean") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("true"))
        assertTrue(decoded.isRight)
      },
      test("decode False-like scalar to DynamicValue.Primitive Boolean") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("FALSE"))
        assertTrue(decoded.isRight)
      },
      test("decode numeric scalar to DynamicValue.Primitive Int") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("42"))
        assertTrue(decoded.isRight)
      },
      test("decode large numeric to DynamicValue.Primitive Long") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("9999999999"))
        assertTrue(decoded.isRight)
      },
      test("decode decimal to DynamicValue.Primitive BigDecimal") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("3.14"))
        assertTrue(decoded.isRight)
      },
      test("decode non-numeric string to DynamicValue.Primitive String") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val decoded = codec.decodeValue(Yaml.Scalar("hello world"))
        assertTrue(decoded.isRight)
      },
      test("decode mapping with non-scalar key") {
        val codec = Schema[DynamicValue].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml    = Yaml.Mapping(Chunk((Yaml.Sequence(Chunk(Yaml.Scalar("k"))), Yaml.Scalar("v"))))
        val decoded = codec.decodeValue(yaml)
        assertTrue(decoded.isRight)
      },
      test("encode other PrimitiveValue falls through to toString") {
        val codec   = Schema[DynamicValue].derive(YamlCodecDeriver)
        val dv      = new DynamicValue.Primitive(new PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))
        val encoded = codec.encodeValue(dv)
        assertTrue(encoded.isInstanceOf[Yaml.Scalar])
      } @@ TestAspect.jvmOnly
    ),
    suite("error cases in deriver")(
      test("missing required field returns error") {
        val codec  = Schema[Wrapper].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Mapping.empty)
        assertTrue(result.isLeft)
      },
      test("wrong YAML type for record returns error") {
        val codec  = Schema[Wrapper].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("not a mapping"))
        assertTrue(result.isLeft)
      },
      test("wrong variant case returns error") {
        sealed trait Animal
        case class Dog(name: String) extends Animal
        object Dog {
          implicit val schema: Schema[Dog] = Schema.derived
        }
        object Animal {
          implicit val schema: Schema[Animal] = Schema.derived
        }
        val codec = Schema[Animal].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("Unknown"), Yaml.Scalar("data"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("wrong YAML type for variant returns error") {
        sealed trait Color
        case class Red(shade: String) extends Color
        object Red {
          implicit val schema: Schema[Red] = Schema.derived
        }
        object Color {
          implicit val schema: Schema[Color] = Schema.derived
        }
        val codec  = Schema[Color].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("not a mapping"))
        assertTrue(result.isLeft)
      },
      test("non-string key for variant returns error") {
        sealed trait Shape
        case class Circle(radius: Int) extends Shape
        object Circle {
          implicit val schema: Schema[Circle] = Schema.derived
        }
        object Shape {
          implicit val schema: Schema[Shape] = Schema.derived
        }
        val codec = Schema[Shape].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.NullValue, Yaml.Scalar("data"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("sequence expects Yaml.Sequence") {
        val codec  = Schema[List[Int]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("not a sequence"))
        assertTrue(result.isLeft)
      },
      test("map expects Yaml.Mapping") {
        val codec  = Schema[Map[String, Int]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("not a map"))
        assertTrue(result.isLeft)
      },
      test("sequence error propagation") {
        val codec = Schema[List[Int]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Sequence(Chunk(Yaml.Scalar("not_a_number")))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("map key error propagation") {
        val codec = Schema[Map[Int, String]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("not_int"), Yaml.Scalar("v"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("map value error propagation") {
        val codec = Schema[Map[String, Int]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("key"), Yaml.Scalar("not_int"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      }
    ),
    suite("Option handling")(
      test("decode null as None") {
        val codec  = Schema[Option[String]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.NullValue)
        assertTrue(result == Right(None))
      },
      test("decode scalar 'null' as None") {
        val codec  = Schema[Option[String]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("null"))
        assertTrue(result == Right(None))
      },
      test("decode scalar '~' as None") {
        val codec  = Schema[Option[String]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("~"))
        assertTrue(result == Right(None))
      },
      test("decode mapping with None key as None") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("None"), Yaml.NullValue)))
        val result = codec.decodeValue(yaml)
        assertTrue(result == Right(None))
      },
      test("decode mapping with Some key") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("Some"), Yaml.Scalar("hello"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result == Right(Some("hello")))
      },
      test("decode mapping with Some key but no value fails") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk.empty)
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("decode other mapping falls through to inner codec") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml   = Yaml.Mapping(Chunk((Yaml.Scalar("other"), Yaml.Scalar("value"))))
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      },
      test("decode scalar value as Some") {
        val codec  = Schema[Option[String]].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Scalar("hello"))
        assertTrue(result == Right(Some("hello")))
      },
      test("encode None") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        val yaml  = codec.encodeValue(None)
        assertTrue(yaml == Yaml.NullValue)
      },
      test("encode Some") {
        val codec = Schema[Option[String]].derive(YamlCodecDeriver)
        val yaml  = codec.encodeValue(Some("hello"))
        assertTrue(yaml == Yaml.Scalar("hello"))
      }
    ),
    suite("sequence and map codecs")(
      test("encode/decode empty list") {
        val codec  = Schema[List[String]].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(List.empty[String]))
        assertTrue(result == Right(List.empty[String]))
      },
      test("encode/decode list") {
        val codec  = Schema[List[String]].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(List("a", "b", "c")))
        assertTrue(result == Right(List("a", "b", "c")))
      },
      test("encode/decode empty map") {
        val codec  = Schema[Map[String, Int]].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(Map.empty[String, Int]))
        assertTrue(result == Right(Map.empty[String, Int]))
      },
      test("encode/decode map") {
        val codec  = Schema[Map[String, Int]].derive(YamlCodecDeriver)
        val result = codec.decode(codec.encode(Map("a" -> 1, "b" -> 2)))
        assertTrue(result == Right(Map("a" -> 1, "b" -> 2)))
      }
    ),
    suite("YamlFormat")(
      test("YamlFormat media type") {
        assertTrue(YamlFormat.mimeType == "application/yaml")
      }
    ),
    suite("toKebabCase")(
      test("camelCase field names are kebab-cased") {
        case class CamelCase(firstName: String, lastName: String)
        object CamelCase {
          implicit val schema: Schema[CamelCase] = Schema.derived
        }
        val codec = Schema[CamelCase].derive(YamlCodecDeriver)
        val yaml  = codec.encodeToString(CamelCase("Alice", "Smith"))
        assertTrue(yaml.contains("first-name:") && yaml.contains("last-name:"))
      },
      test("kebab-cased field names decode correctly") {
        case class CamelCase(firstName: String, lastName: String)
        object CamelCase {
          implicit val schema: Schema[CamelCase] = Schema.derived
        }
        val codec  = Schema[CamelCase].derive(YamlCodecDeriver)
        val result = codec.decode("first-name: Alice\nlast-name: Smith")
        assertTrue(result == Right(CamelCase("Alice", "Smith")))
      }
    ),
    suite("record field error propagation")(
      test("invalid field value returns error") {
        case class TypedRecord(count: Int)
        object TypedRecord {
          implicit val schema: Schema[TypedRecord] = Schema.derived
        }
        val codec  = Schema[TypedRecord].derive(YamlCodecDeriver)
        val result = codec.decodeValue(Yaml.Mapping.fromStringKeys("count" -> Yaml.Scalar("not_a_number")))
        assertTrue(result.isLeft)
      }
    ),
    suite("variant mapping with multiple entries fails")(
      test("mapping with 2 entries fails variant decode") {
        sealed trait V
        case class A(x: Int) extends V
        case class B(y: Int) extends V
        object A {
          implicit val schema: Schema[A] = Schema.derived
        }
        object B {
          implicit val schema: Schema[B] = Schema.derived
        }
        object V {
          implicit val schema: Schema[V] = Schema.derived
        }
        val codec = Schema[V].derive(YamlCodecDeriver)
        import zio.blocks.chunk.Chunk
        val yaml = Yaml.Mapping(
          Chunk(
            (Yaml.Scalar("A"), Yaml.Mapping.fromStringKeys("x" -> Yaml.Scalar("1", tag = Some(YamlTag.Int)))),
            (Yaml.Scalar("B"), Yaml.Mapping.fromStringKeys("y" -> Yaml.Scalar("2", tag = Some(YamlTag.Int))))
          )
        )
        val result = codec.decodeValue(yaml)
        assertTrue(result.isLeft)
      }
    )
  )
}
