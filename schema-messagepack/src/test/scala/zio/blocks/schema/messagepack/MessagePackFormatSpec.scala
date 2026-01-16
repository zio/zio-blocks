package zio.blocks.schema.messagepack

import zio.blocks.schema._
import zio.blocks.schema.messagepack.MessagePackTestUtils._
import zio.blocks.schema.binding.Binding
import zio.test._
import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferOutput
import java.time._
import java.util.UUID
import java.util.Currency
import scala.collection.immutable.ArraySeq

object MessagePackFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean") {
        roundTrip(true) &&
        roundTrip(false) &&
        decodeError[Boolean](Array.empty[Byte], "Unexpected end of input")
      },
      test("Byte") {
        roundTrip(1: Byte) &&
        roundTrip(Byte.MinValue) &&
        roundTrip(Byte.MaxValue) &&
        decodeError[Byte](Array.empty[Byte], "Unexpected end of input")
      },
      test("Short") {
        roundTrip(1: Short) &&
        roundTrip(Short.MinValue) &&
        roundTrip(Short.MaxValue) &&
        decodeError[Short](Array.empty[Byte], "Unexpected end of input")
      },
      test("Int") {
        roundTrip(1) &&
        roundTrip(Int.MinValue) &&
        roundTrip(Int.MaxValue) &&
        decodeError[Int](Array.empty[Byte], "Unexpected end of input")
      },
      test("Long") {
        roundTrip(1L) &&
        roundTrip(Long.MinValue) &&
        roundTrip(Long.MaxValue) &&
        decodeError[Long](Array.empty[Byte], "Unexpected end of input")
      },
      test("Float") {
        roundTrip(42.0f) &&
        roundTrip(Float.MinValue) &&
        roundTrip(Float.MaxValue) &&
        roundTrip(Float.NaN) &&
        roundTrip(Float.PositiveInfinity) &&
        roundTrip(Float.NegativeInfinity) &&
        decodeError[Float](Array.empty[Byte], "Unexpected end of input")
      },
      test("Double") {
        roundTrip(42.0) &&
        roundTrip(Double.MinValue) &&
        roundTrip(Double.MaxValue) &&
        roundTrip(Double.NaN) &&
        roundTrip(Double.PositiveInfinity) &&
        roundTrip(Double.NegativeInfinity) &&
        decodeError[Double](Array.empty[Byte], "Unexpected end of input")
      },
      test("Char") {
        roundTrip('7') &&
        roundTrip(Char.MinValue) &&
        roundTrip(Char.MaxValue) &&
        decodeError[Char](Array.empty[Byte], "Unexpected end of input")
      },
      test("String") {
        roundTrip("Hello") &&
        roundTrip("") &&
        roundTrip("Hello World with unicode characters") &&
        decodeError[String](Array.empty[Byte], "Unexpected end of input")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20)) &&
        roundTrip(BigInt(0)) &&
        roundTrip(BigInt(-1)) &&
        roundTrip(BigInt(Long.MaxValue) * 2)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345")) &&
        roundTrip(BigDecimal(0)) &&
        roundTrip(BigDecimal("123.456"))
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY) &&
        roundTrip(java.time.DayOfWeek.MONDAY) &&
        roundTrip(java.time.DayOfWeek.SUNDAY)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L)) &&
        roundTrip(java.time.Duration.ZERO) &&
        roundTrip(java.time.Duration.ofDays(365))
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z")) &&
        roundTrip(java.time.Instant.EPOCH) &&
        roundTrip(java.time.Instant.now())
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18")) &&
        roundTrip(java.time.LocalDate.MIN) &&
        roundTrip(java.time.LocalDate.MAX)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459")) &&
        roundTrip(java.time.LocalDateTime.MIN) &&
        roundTrip(java.time.LocalDateTime.MAX)
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459")) &&
        roundTrip(java.time.LocalTime.MIN) &&
        roundTrip(java.time.LocalTime.MAX)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12)) &&
        roundTrip(java.time.Month.JANUARY) &&
        roundTrip(java.time.Month.DECEMBER)
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31)) &&
        roundTrip(java.time.MonthDay.of(1, 1))
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00")) &&
        roundTrip(java.time.OffsetDateTime.MIN) &&
        roundTrip(java.time.OffsetDateTime.MAX)
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00")) &&
        roundTrip(java.time.OffsetTime.MIN) &&
        roundTrip(java.time.OffsetTime.MAX)
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31)) &&
        roundTrip(java.time.Period.ZERO) &&
        roundTrip(java.time.Period.ofDays(100))
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025)) &&
        roundTrip(java.time.Year.MIN_VALUE) &&
        roundTrip(java.time.Year.MAX_VALUE)
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7)) &&
        roundTrip(java.time.YearMonth.of(2000, 1))
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC")) &&
        roundTrip(java.time.ZoneId.of("America/New_York")) &&
        roundTrip(java.time.ZoneId.of("Europe/London"))
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600)) &&
        roundTrip(java.time.ZoneOffset.UTC) &&
        roundTrip(java.time.ZoneOffset.MIN) &&
        roundTrip(java.time.ZoneOffset.MAX)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]")) &&
        roundTrip(java.time.ZonedDateTime.now())
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD")) &&
        roundTrip(Currency.getInstance("EUR")) &&
        roundTrip(Currency.getInstance("GBP"))
      },
      test("UUID") {
        roundTrip(UUID.randomUUID()) &&
        roundTrip(new UUID(0L, 0L)) &&
        roundTrip(new UUID(Long.MaxValue, Long.MaxValue))
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")) &&
        decodeError[Record1](Array.empty[Byte], "Unexpected end of input")
      },
      test("record can decode out-of-order fields and ignore unknown fields") {
        val value = Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
        val codec = Schema[Record1].derive(MessagePackFormat.deriver)

        val out    = new ArrayBufferOutput()
        val packer = MessagePack.newDefaultPacker(out)

        // 9 known fields + 1 unknown field.
        packer.packMapHeader(10)

        // Write in reverse-ish order (MessagePack maps are unordered).
        packer.packString("s"); packer.packString(value.s)
        packer.packString("c"); packer.packInt(value.c.toInt)
        packer.packString("d"); packer.packDouble(value.d)
        packer.packString("f"); packer.packFloat(value.f)
        packer.packString("l"); packer.packLong(value.l)
        packer.packString("i"); packer.packInt(value.i)
        packer.packString("sh"); packer.packShort(value.sh)
        packer.packString("b"); packer.packByte(value.b)
        // Unknown field should be ignored
        packer.packString("extra"); packer.packInt(123)
        packer.packString("bl"); packer.packBoolean(value.bl)

        packer.flush()

        assertTrue(codec.decode(out.toByteArray) == Right(value))
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))))
      },
      test("record with unit and variant fields") {
        roundTrip(Record4((), Some("VVV"))) &&
        roundTrip(Record4((), None))
      },
      test("empty record") {
        roundTrip(EmptyRecord())
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]         = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]]   = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]         = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]       = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]         = Schema.derived
        implicit val arrayOfFloatSchema: Schema[Array[Float]]       = Schema.derived
        implicit val arrayOfIntSchema: Schema[Array[Int]]           = Schema.derived
        implicit val arrayOfDoubleSchema: Schema[Array[Double]]     = Schema.derived
        implicit val arrayOfLongSchema: Schema[Array[Long]]         = Schema.derived
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived

        roundTrip(Array[Unit]((), (), ())) &&
        roundTrip(Array[Boolean](true, false, true)) &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte)) &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short)) &&
        roundTrip(Array('1', '2', '3')) &&
        roundTrip(Array[Float](1.0f, 2.0f, 3.0f)) &&
        roundTrip(Array[Int](1, 2, 3)) &&
        roundTrip(Array[Double](1.0, 2.0, 3.0)) &&
        roundTrip(Array[Long](1, 2, 3)) &&
        roundTrip((1 to 100).toList) &&
        roundTrip(Set(1L, 2L, 3L)) &&
        roundTrip(ArraySeq(1.0f, 2.0f, 3.0f)) &&
        roundTrip(Vector(1.0, 2.0, 3.0)) &&
        roundTrip(List("1", "2", "3")) &&
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3))) &&
        roundTrip(List(BigDecimal(1.0), BigDecimal(2.0), BigDecimal(3.0))) &&
        roundTrip(List(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 2))) &&
        roundTrip((1 to 32).map(x => new java.util.UUID(x, x)).toList) &&
        decodeError[List[Int]](Array.empty[Byte], "Unexpected end of input")
      },
      test("complex values") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("recursive values") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      },
      test("empty sequences") {
        roundTrip(List.empty[Int]) &&
        roundTrip(Vector.empty[String]) &&
        roundTrip(Set.empty[Long])
      }
    ),
    suite("maps")(
      test("string keys and primitive values") {
        roundTrip(Map("VVV" -> (), "WWW" -> ())) &&
        roundTrip(Map("VVV" -> true, "WWW" -> false)) &&
        roundTrip(Map("VVV" -> (1: Byte), "WWW" -> (2: Byte))) &&
        roundTrip(Map("VVV" -> (1: Short), "WWW" -> (2: Short))) &&
        roundTrip(Map("VVV" -> '1', "WWW" -> '2')) &&
        roundTrip(Map("VVV" -> 1, "WWW" -> 2)) &&
        roundTrip(Map("VVV" -> 1L, "WWW" -> 2L)) &&
        roundTrip(Map("VVV" -> 1.0f, "WWW" -> 2.0f)) &&
        roundTrip(Map("VVV" -> 1.0, "WWW" -> 2.0)) &&
        roundTrip(Map("VVV" -> "1", "WWW" -> "2")) &&
        roundTrip(Map("VVV" -> BigInt(1), "WWW" -> BigInt(2))) &&
        roundTrip(Map("VVV" -> BigDecimal(1.0), "WWW" -> BigDecimal(2.0))) &&
        roundTrip(Map("VVV" -> java.time.LocalDate.of(2025, 1, 1), "WWW" -> java.time.LocalDate.of(2025, 1, 2))) &&
        roundTrip(Map("VVV" -> new java.util.UUID(1L, 1L), "WWW" -> new java.util.UUID(2L, 2L))) &&
        decodeError[Map[String, Int]](Array.empty[Byte], "Unexpected end of input")
      },
      test("string keys and complex values") {
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("string keys and recursive values") {
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      },
      test("non string key map") {
        roundTrip(Map(1 -> 1L, 2 -> 2L)) &&
        decodeError[Map[Int, Long]](Array.empty[Byte], "Unexpected end of input")
      },
      test("non string key with recursive values") {
        roundTrip(
          Map(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))) -> 1,
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil))))) -> 2
          )
        )
      },
      test("nested maps") {
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L))) &&
        roundTrip(Map(Map(1 -> 1L, 2 -> 2L) -> "WWW"))
      },
      test("empty maps") {
        roundTrip(Map.empty[String, Int]) &&
        roundTrip(Map.empty[Int, String])
      }
    ),
    suite("variants")(
      test("constant values") {
        roundTrip[TrafficLight](TrafficLight.Green) &&
        roundTrip[TrafficLight](TrafficLight.Yellow) &&
        roundTrip[TrafficLight](TrafficLight.Red)
      },
      test("option") {
        roundTrip(Option(42)) &&
        roundTrip[Option[Int]](None)
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42)) &&
        roundTrip[Either[String, Int]](Left("VVV"))
      },
      test("nested variants") {
        roundTrip[Either[Option[Int], Either[String, Boolean]]](Right(Left("hello"))) &&
        roundTrip[Either[Option[Int], Either[String, Boolean]]](Left(Some(42))) &&
        roundTrip[Either[Option[Int], Either[String, Boolean]]](Left(None))
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L)) &&
        roundTrip[Email](Email("john@gmail.com"))
      },
      test("as a record field") {
        roundTrip[Record3](Record3(UserId(1234567890123456789L), Email("backup@gmail.com")))
      }
    ),
    suite("dynamic value")(
      test("top-level primitives") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit)) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Short(1: Short))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(1L))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Float(1.0f))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(1.0))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Char('1'))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV"))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(123))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigDecimal(123.45))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofSeconds(60)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Instant(Instant.EPOCH))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.MAX))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.MAX))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.MAX))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Month(Month.MAY))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetTime(OffsetTime.MAX))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(1)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2025)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2025, 1)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC")))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.MAX))) &&
        roundTrip[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
          )
        ) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD")))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())))
      },
      test("record") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      },
      test("variant") {
        roundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))))
      },
      test("sequence") {
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          )
        )
      },
      test("map") {
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      },
      test("nested dynamic values") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              (
                "nested",
                DynamicValue.Sequence(
                  Vector(
                    DynamicValue.Map(
                      Vector(
                        (
                          DynamicValue.Primitive(PrimitiveValue.String("key")),
                          DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("as record field values") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        roundTrip[Dynamic](value)
      }
    ),
    suite("edge cases")(
      test("deeply nested structures") {
        def createNested(depth: Int): Recursive =
          if (depth <= 0) Recursive(0, Nil)
          else Recursive(depth, List(createNested(depth - 1)))
        roundTrip(createNested(10))
      },
      test("large collections") {
        roundTrip((1 to 1000).toList) &&
        roundTrip((1 to 1000).map(i => s"item$i" -> i).toMap)
      },
      test("special string values") {
        roundTrip("") &&
        roundTrip(" ") &&
        roundTrip("\n\t\r") &&
        roundTrip("hello\u0000world") &&
        roundTrip("Unicode: \u00e9\u00e8\u00ea")
      },
      test("boundary number values") {
        roundTrip(Byte.MinValue) &&
        roundTrip(Byte.MaxValue) &&
        roundTrip(Short.MinValue) &&
        roundTrip(Short.MaxValue) &&
        roundTrip(Int.MinValue) &&
        roundTrip(Int.MaxValue) &&
        roundTrip(Long.MinValue) &&
        roundTrip(Long.MaxValue)
      },
      test("zero values") {
        roundTrip(0: Byte) &&
        roundTrip(0: Short) &&
        roundTrip(0) &&
        roundTrip(0L) &&
        roundTrip(0.0f) &&
        roundTrip(0.0)
      },
      test("negative values") {
        roundTrip(-1: Byte) &&
        roundTrip(-1: Short) &&
        roundTrip(-1) &&
        roundTrip(-1L) &&
        roundTrip(-1.0f) &&
        roundTrip(-1.0)
      },
      test("multiple records in list") {
        roundTrip(List.fill(100)(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "test")))
      },
      test("deeply nested maps") {
        roundTrip(Map("a" -> Map("b" -> Map("c" -> Map("d" -> 1)))))
      },
      test("mixed collection types") {
        roundTrip(List(Vector(1, 2, 3), Vector(4, 5, 6))) &&
        roundTrip(Vector(List(1, 2, 3), List(4, 5, 6)))
      },
      test("option in list") {
        roundTrip(List(Some(1), None, Some(3)))
      },
      test("either in list") {
        roundTrip(List[Either[String, Int]](Right(1), Left("error"), Right(3)))
      },
      test("record with all field types") {
        roundTrip(
          AllFieldTypes(
            true,
            1,
            2,
            3,
            4L,
            5.0f,
            6.0,
            'x',
            "test",
            BigInt(123),
            BigDecimal("123.456"),
            java.time.LocalDate.now(),
            java.time.LocalTime.now(),
            java.util.UUID.randomUUID()
          )
        )
      },
      test("very long string") {
        roundTrip("a" * 10000)
      },
      test("unicode strings") {
        roundTrip("Cafe") &&
        roundTrip("Hallo") &&
        roundTrip("Nihao")
      },
      test("nested option") {
        roundTrip[Option[Option[Int]]](Some(Some(42))) &&
        roundTrip[Option[Option[Int]]](Some(None)) &&
        roundTrip[Option[Option[Int]]](None)
      },
      test("list of either") {
        implicit val listEitherSchema: Schema[List[Either[String, Int]]] = Schema.derived
        roundTrip(List(Right(1), Left("a"), Right(2)))
      },
      test("map with complex values") {
        roundTrip(Map("k1" -> List(1, 2, 3), "k2" -> List(4, 5, 6)))
      },
      test("variant with record payload") {
        roundTrip[Either[Record1, Record1]](Left(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "test"))) &&
        roundTrip[Either[Record1, Record1]](Right(Record1(false, 2, 3, 4, 5L, 6.0f, 7.0, '8', "test2")))
      },
      test("empty and non-empty strings in record") {
        roundTrip(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "")) &&
        roundTrip(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "non-empty"))
      }
    )
  )

  implicit val eitherRecord1Schema: Schema[Either[Record1, Record1]] = Schema.derived
  implicit val nestedOptionSchema: Schema[Option[Option[Int]]]       = Schema.derived

  case class Record1(
    bl: Boolean,
    b: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String
  )

  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1] = Schema.derived

    val i: Lens[Record1, Int] = $(_.i)
  }

  case class Record2(
    r1_1: Record1,
    r1_2: Record1
  )

  object Record2 extends CompanionOptics[Record2] {
    implicit val schema: Schema[Record2] = Schema.derived

    val r1_1: Lens[Record2, Record1] = $(_.r1_1)
    val r1_2: Lens[Record2, Record1] = $(_.r1_2)
    val r1_1_i: Lens[Record2, Int]   = $(_.r1_1.i)
    val r1_2_i: Lens[Record2, Int]   = $(_.r1_2.i)
  }

  case class Recursive(i: Int, ln: List[Recursive])

  object Recursive extends CompanionOptics[Recursive] {
    implicit val schema: Schema[Recursive]   = Schema.derived
    val i: Lens[Recursive, Int]              = $(_.i)
    val ln: Lens[Recursive, List[Recursive]] = $(_.ln)
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived

    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight
  }

  implicit val eitherSchema: Schema[Either[String, Int]]                                = Schema.derived
  implicit val nestedEitherSchema: Schema[Either[Option[Int], Either[String, Boolean]]] = Schema.derived

  case class UserId(value: Long)

  object UserId {
    implicit val schema: Schema[UserId] = Schema.derived.wrapTotal(x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit val schema: Schema[Email] = new Schema(
      new Reflect.Wrapper[Binding, Email, String](
        Schema[String].reflect,
        TypeName(Namespace(Seq("zio", "blocks", "messagepack"), Seq("MessagePackFormatSpec")), "Email"),
        None,
        new Binding.Wrapper(
          {
            case x @ EmailRegex(_*) => new Right(new Email(x))
            case _                  => new Left("Expected Email")
          },
          _.value
        )
      )
    )
  }

  case class Record3(userId: UserId, email: Email)

  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4] = Schema.derived

    val hidden: Lens[Record4, Unit]               = $(_.hidden)
    val optKey: Lens[Record4, Option[String]]     = $(_.optKey)
    val optKey_None: Optional[Record4, None.type] = $(_.optKey.when[None.type])
  }

  case class Dynamic(primitive: DynamicValue, map: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue]       = $(_.map)
  }

  case class EmptyRecord()

  object EmptyRecord {
    implicit val schema: Schema[EmptyRecord] = Schema.derived
  }

  case class AllFieldTypes(
    b: Boolean,
    by: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String,
    bi: BigInt,
    bd: BigDecimal,
    ld: java.time.LocalDate,
    lt: java.time.LocalTime,
    uuid: java.util.UUID
  )

  object AllFieldTypes {
    implicit val schema: Schema[AllFieldTypes] = Schema.derived
  }
}
