package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

import java.time.Instant
import java.util.{Currency, UUID}

/**
 * Tests for extended primitive types in structural types. Covers: BigInt,
 * BigDecimal, UUID, Currency, java.time.Instant, Unit
 */
object ExtendedPrimitivesSpec extends ZIOSpecDefault {

  // Case class with all extended primitives
  case class AllExtendedPrimitives(
    bigInt: BigInt,
    bigDecimal: BigDecimal,
    uuid: UUID,
    currency: Currency,
    instant: Instant
  )

  // Individual case classes for focused testing
  case class WithBigInt(value: BigInt)
  case class WithBigDecimal(value: BigDecimal)
  case class WithUUID(value: UUID)
  case class WithCurrency(value: Currency)
  case class WithInstant(value: Instant)
  case class WithUnit(value: Unit)

  // Nested case class with extended primitives
  case class Order(
    id: UUID,
    amount: BigDecimal,
    currency: Currency,
    createdAt: Instant
  )
  case class OrderWrapper(order: Order, metadata: BigInt)

  // Collections of extended primitives
  case class WithListOfUUID(ids: List[UUID])
  case class WithOptionBigDecimal(amount: Option[BigDecimal])
  case class WithMapOfInstant(events: Map[String, Instant])

  def spec = suite("ExtendedPrimitivesSpec")(
    suite("ToStructural - Individual Types")(
      test("BigInt field") {
        val ts = ToStructural.derived[WithBigInt]
        val s  = ts.toStructural(WithBigInt(BigInt("12345678901234567890")))
        assertTrue(s.value == BigInt("12345678901234567890"))
      },
      test("BigDecimal field") {
        val ts = ToStructural.derived[WithBigDecimal]
        val s  = ts.toStructural(WithBigDecimal(BigDecimal("123456.789012345")))
        assertTrue(s.value == BigDecimal("123456.789012345"))
      },
      test("UUID field") {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val ts   = ToStructural.derived[WithUUID]
        val s    = ts.toStructural(WithUUID(uuid))
        assertTrue(s.value == uuid)
      },
      test("Currency field") {
        val currency = Currency.getInstance("USD")
        val ts       = ToStructural.derived[WithCurrency]
        val s        = ts.toStructural(WithCurrency(currency))
        assertTrue(s.value == currency)
      },
      test("Instant field") {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val ts      = ToStructural.derived[WithInstant]
        val s       = ts.toStructural(WithInstant(instant))
        assertTrue(s.value == instant)
      },
      test("Unit field") {
        val ts = ToStructural.derived[WithUnit]
        val s  = ts.toStructural(WithUnit(()))
        assertTrue(s.value == ())
      }
    ),
    suite("ToStructural - All Extended Primitives Combined")(
      test("case class with all extended primitives") {
        val uuid     = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val currency = Currency.getInstance("EUR")
        val instant  = Instant.parse("2024-06-15T14:30:00Z")

        val ts = ToStructural.derived[AllExtendedPrimitives]
        val s  = ts.toStructural(
          AllExtendedPrimitives(
            bigInt = BigInt("999999999999999999999"),
            bigDecimal = BigDecimal("12345.6789"),
            uuid = uuid,
            currency = currency,
            instant = instant
          )
        )

        assertTrue(
          s.bigInt == BigInt("999999999999999999999"),
          s.bigDecimal == BigDecimal("12345.6789"),
          s.uuid == uuid,
          s.currency == currency,
          s.instant == instant
        )
      },
      test("selectDynamic works for all extended primitives") {
        val uuid     = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val currency = Currency.getInstance("GBP")
        val instant  = Instant.parse("2024-12-25T00:00:00Z")

        val ts = ToStructural.derived[AllExtendedPrimitives]
        val s  = ts.toStructural(
          AllExtendedPrimitives(
            bigInt = BigInt(42),
            bigDecimal = BigDecimal("3.14159"),
            uuid = uuid,
            currency = currency,
            instant = instant
          )
        )

        assertTrue(
          s.selectDynamic("bigInt") == BigInt(42),
          s.selectDynamic("bigDecimal") == BigDecimal("3.14159"),
          s.selectDynamic("uuid") == uuid,
          s.selectDynamic("currency") == currency,
          s.selectDynamic("instant") == instant
        )
      }
    ),
    suite("ToStructural - Nested with Extended Primitives")(
      test("nested case class with extended primitives") {
        val orderId  = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val currency = Currency.getInstance("JPY")
        val instant  = Instant.parse("2024-03-20T09:15:00Z")

        val ts = ToStructural.derived[OrderWrapper]
        val s  = ts.toStructural(
          OrderWrapper(
            order = Order(orderId, BigDecimal("1500.00"), currency, instant),
            metadata = BigInt(12345)
          )
        )

        assertTrue(
          s.order.id == orderId,
          s.order.amount == BigDecimal("1500.00"),
          s.order.currency == currency,
          s.order.createdAt == instant,
          s.metadata == BigInt(12345)
        )
      }
    ),
    suite("ToStructural - Collections of Extended Primitives")(
      test("List of UUID") {
        val uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        val ts    = ToStructural.derived[WithListOfUUID]
        val s     = ts.toStructural(WithListOfUUID(List(uuid1, uuid2)))
        assertTrue(s.ids == List(uuid1, uuid2))
      },
      test("Option of BigDecimal - Some") {
        val ts = ToStructural.derived[WithOptionBigDecimal]
        val s  = ts.toStructural(WithOptionBigDecimal(Some(BigDecimal("999.99"))))
        assertTrue(s.amount == Some(BigDecimal("999.99")))
      },
      test("Option of BigDecimal - None") {
        val ts = ToStructural.derived[WithOptionBigDecimal]
        val s  = ts.toStructural(WithOptionBigDecimal(None))
        assertTrue(s.amount == None)
      },
      test("Map of String to Instant") {
        val instant1 = Instant.parse("2024-01-01T00:00:00Z")
        val instant2 = Instant.parse("2024-12-31T23:59:59Z")
        val ts       = ToStructural.derived[WithMapOfInstant]
        val s        = ts.toStructural(WithMapOfInstant(Map("start" -> instant1, "end" -> instant2)))
        assertTrue(s.events == Map("start" -> instant1, "end" -> instant2))
      }
    ),
    suite("StructuralSchema - Extended Primitives Round-Trip")(
      test("BigInt round-trip") {
        val ts                   = ToStructural.derived[WithBigInt]
        given Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema         = ts.structuralSchema

        val original   = WithBigInt(BigInt("12345678901234567890123456789"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(BigInt("12345678901234567890123456789"))
        )
      },
      test("BigDecimal round-trip") {
        val ts                       = ToStructural.derived[WithBigDecimal]
        given Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema             = ts.structuralSchema

        val original   = WithBigDecimal(BigDecimal("123456789.123456789"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(BigDecimal("123456789.123456789"))
        )
      },
      test("UUID round-trip") {
        val ts                 = ToStructural.derived[WithUUID]
        given Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema       = ts.structuralSchema

        val uuid       = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val original   = WithUUID(uuid)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(uuid)
        )
      },
      test("Currency round-trip") {
        val ts                     = ToStructural.derived[WithCurrency]
        given Schema[WithCurrency] = Schema.derived[WithCurrency]
        val structSchema           = ts.structuralSchema

        val currency   = Currency.getInstance("CHF")
        val original   = WithCurrency(currency)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(currency)
        )
      },
      test("Instant round-trip") {
        val ts                    = ToStructural.derived[WithInstant]
        given Schema[WithInstant] = Schema.derived[WithInstant]
        val structSchema          = ts.structuralSchema

        val instant    = Instant.parse("2024-07-04T12:00:00Z")
        val original   = WithInstant(instant)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(instant)
        )
      },
      test("Unit round-trip") {
        val ts                 = ToStructural.derived[WithUnit]
        given Schema[WithUnit] = Schema.derived[WithUnit]
        val structSchema       = ts.structuralSchema

        val original   = WithUnit(())
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(())
        )
      },
      test("all extended primitives round-trip") {
        val ts                              = ToStructural.derived[AllExtendedPrimitives]
        given Schema[AllExtendedPrimitives] = Schema.derived[AllExtendedPrimitives]
        val structSchema                    = ts.structuralSchema

        val uuid     = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val currency = Currency.getInstance("USD")
        val instant  = Instant.parse("2024-01-01T00:00:00Z")

        val original = AllExtendedPrimitives(
          bigInt = BigInt("999"),
          bigDecimal = BigDecimal("1.5"),
          uuid = uuid,
          currency = currency,
          instant = instant
        )
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.bigInt) == Right(BigInt("999")),
          roundTrip.map(_.bigDecimal) == Right(BigDecimal("1.5")),
          roundTrip.map(_.uuid) == Right(uuid),
          roundTrip.map(_.currency) == Right(currency),
          roundTrip.map(_.instant) == Right(instant)
        )
      }
    ),
    suite("TypeName - Extended Primitives")(
      test("TypeName for BigInt field") {
        val ts                   = ToStructural.derived[WithBigInt]
        given Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema         = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:BigInt}")
      },
      test("TypeName for BigDecimal field") {
        val ts                       = ToStructural.derived[WithBigDecimal]
        given Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema             = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:BigDecimal}")
      },
      test("TypeName for UUID field") {
        val ts                 = ToStructural.derived[WithUUID]
        given Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema       = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:UUID}")
      },
      test("TypeName for Currency field") {
        val ts                     = ToStructural.derived[WithCurrency]
        given Schema[WithCurrency] = Schema.derived[WithCurrency]
        val structSchema           = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:Currency}")
      },
      test("TypeName for Instant field") {
        val ts                    = ToStructural.derived[WithInstant]
        given Schema[WithInstant] = Schema.derived[WithInstant]
        val structSchema          = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:Instant}")
      },
      test("TypeName for Unit field") {
        val ts                 = ToStructural.derived[WithUnit]
        given Schema[WithUnit] = Schema.derived[WithUnit]
        val structSchema       = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:Unit}")
      },
      test("TypeName for all extended primitives - alphabetically sorted") {
        val ts                              = ToStructural.derived[AllExtendedPrimitives]
        given Schema[AllExtendedPrimitives] = Schema.derived[AllExtendedPrimitives]
        val structSchema                    = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        // Fields sorted alphabetically: bigDecimal, bigInt, currency, instant, uuid
        assertTrue(typeName == "{bigDecimal:BigDecimal,bigInt:BigInt,currency:Currency,instant:Instant,uuid:UUID}")
      }
    ),
    suite("DynamicValue Format - Extended Primitives")(
      test("BigInt produces correct DynamicValue") {
        val ts                   = ToStructural.derived[WithBigInt]
        given Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema         = ts.structuralSchema

        val structural = ts.toStructural(WithBigInt(BigInt("42")))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            assertTrue(fieldMap("value") == DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("42"))))
          case _ =>
            assertTrue(false)
        }
      },
      test("UUID produces correct DynamicValue") {
        val ts                 = ToStructural.derived[WithUUID]
        given Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema       = ts.structuralSchema

        val uuid       = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val structural = ts.toStructural(WithUUID(uuid))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            assertTrue(fieldMap("value") == DynamicValue.Primitive(PrimitiveValue.UUID(uuid)))
          case _ =>
            assertTrue(false)
        }
      },
      test("Instant produces correct DynamicValue") {
        val ts                    = ToStructural.derived[WithInstant]
        given Schema[WithInstant] = Schema.derived[WithInstant]
        val structSchema          = ts.structuralSchema

        val instant    = Instant.parse("2024-01-15T10:30:00Z")
        val structural = ts.toStructural(WithInstant(instant))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            assertTrue(fieldMap("value") == DynamicValue.Primitive(PrimitiveValue.Instant(instant)))
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Equality - Extended Primitives")(
      test("structural records with same extended primitive values are equal") {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val ts   = ToStructural.derived[WithUUID]
        val s1   = ts.toStructural(WithUUID(uuid))
        val s2   = ts.toStructural(WithUUID(uuid))
        assertTrue(s1 == s2)
      },
      test("structural records with different extended primitive values are not equal") {
        val uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        val ts    = ToStructural.derived[WithUUID]
        val s1    = ts.toStructural(WithUUID(uuid1))
        val s2    = ts.toStructural(WithUUID(uuid2))
        assertTrue(s1 != s2)
      },
      test("hashCode is consistent for extended primitives") {
        val instant = Instant.parse("2024-01-01T00:00:00Z")
        val ts      = ToStructural.derived[WithInstant]
        val s1      = ts.toStructural(WithInstant(instant))
        val s2      = ts.toStructural(WithInstant(instant))
        assertTrue(s1.hashCode == s2.hashCode)
      }
    )
  )
}
