package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._
import zio.test.TestAspect._

import java.time.Instant
import java.util.{Currency, UUID}

object ExtendedPrimitivesSpec extends ZIOSpecDefault {

  // Case class with all extended primitives
  case class ExtendedPrimitives(
    bigInt: BigInt,
    bigDecimal: BigDecimal,
    uuid: UUID,
    currency: Currency,
    instant: Instant,
    unit: Unit
  )

  // Individual type tests
  case class WithBigInt(value: BigInt)
  case class WithBigDecimal(value: BigDecimal)
  case class WithUUID(value: UUID)
  case class WithCurrency(value: Currency)
  case class WithInstant(value: Instant)
  case class WithUnit(value: Unit)

  // Nested extended primitives
  case class NestedExtended(name: String, data: WithBigDecimal)
  case class WithOptionalUUID(id: Option[UUID])
  case class WithListOfBigInt(values: List[BigInt])

  def spec = suite("ExtendedPrimitivesSpec (Scala 2)")(
    suite("ToStructural with Extended Primitives")(
      test("case class with all extended primitives") {
        val ts                                          = ToStructural.derived[ExtendedPrimitives]
        implicit val schema: Schema[ExtendedPrimitives] = Schema.derived[ExtendedPrimitives]
        val structSchema                                = ts.structuralSchema

        val uuid     = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val currency = Currency.getInstance("USD")
        val instant  = Instant.now()

        val s = ts.toStructural(
          ExtendedPrimitives(
            bigInt = BigInt("12345678901234567890"),
            bigDecimal = BigDecimal("123.456789"),
            uuid = uuid,
            currency = currency,
            instant = instant,
            unit = ()
          )
        )

        assertTrue(
          s.bigInt == BigInt("12345678901234567890"),
          s.bigDecimal == BigDecimal("123.456789"),
          s.uuid == uuid,
          s.currency == currency,
          s.instant == instant,
          s.unit == ()
        )

        assertTrue(
          structSchema.reflect.typeName.name == "{bigDecimal:BigDecimal,bigInt:BigInt,currency:Currency,instant:Instant,unit:Unit,uuid:UUID}"
        )
      },
      test("BigInt field") {
        val ts                                  = ToStructural.derived[WithBigInt]
        implicit val schema: Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema                        = ts.structuralSchema
        val s                                   = ts.toStructural(WithBigInt(BigInt("999999999999999999999999")))
        assertTrue(s.value == BigInt("999999999999999999999999"))
        assertTrue(structSchema.reflect.typeName.name == "{value:BigInt}")
      },
      test("BigDecimal field") {
        val ts                                      = ToStructural.derived[WithBigDecimal]
        implicit val schema: Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema                            = ts.structuralSchema
        val s                                       = ts.toStructural(WithBigDecimal(BigDecimal("3.14159265358979323846")))
        assertTrue(s.value == BigDecimal("3.14159265358979323846"))
        assertTrue(structSchema.reflect.typeName.name == "{value:BigDecimal}")
      },
      test("UUID field") {
        val ts                                = ToStructural.derived[WithUUID]
        implicit val schema: Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema                      = ts.structuralSchema
        val uuid                              = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val s                                 = ts.toStructural(WithUUID(uuid))
        assertTrue(s.value == uuid)
        assertTrue(structSchema.reflect.typeName.name == "{value:UUID}")
      },
      test("Currency field") {
        val ts                                    = ToStructural.derived[WithCurrency]
        implicit val schema: Schema[WithCurrency] = Schema.derived[WithCurrency]
        val structSchema                          = ts.structuralSchema
        val currency                              = Currency.getInstance("EUR")
        val s                                     = ts.toStructural(WithCurrency(currency))
        assertTrue(s.value == currency)
        assertTrue(structSchema.reflect.typeName.name == "{value:Currency}")
      },
      test("Instant field") {
        val ts                                   = ToStructural.derived[WithInstant]
        implicit val schema: Schema[WithInstant] = Schema.derived[WithInstant]
        val structSchema                         = ts.structuralSchema
        val instant                              = Instant.parse("2024-01-15T10:30:00Z")
        val s                                    = ts.toStructural(WithInstant(instant))
        assertTrue(s.value == instant)
        assertTrue(structSchema.reflect.typeName.name == "{value:Instant}")
      },
      test("Unit field") {
        val ts                                = ToStructural.derived[WithUnit]
        implicit val schema: Schema[WithUnit] = Schema.derived[WithUnit]
        val structSchema                      = ts.structuralSchema
        val s                                 = ts.toStructural(WithUnit(()))
        assertTrue(s.value == ())
        assertTrue(structSchema.reflect.typeName.name == "{value:Unit}")
      }
    ),
    suite("Round-trip with Extended Primitives")(
      test("BigInt round-trip") {
        val ts                                  = ToStructural.derived[WithBigInt]
        implicit val schema: Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema                        = ts.structuralSchema

        val original   = WithBigInt(BigInt("12345678901234567890123456789"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(BigInt("12345678901234567890123456789"))
        )
        assertTrue(structSchema.reflect.typeName.name == "{value:BigInt}")
      },
      test("BigDecimal round-trip") {
        val ts                                      = ToStructural.derived[WithBigDecimal]
        implicit val schema: Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema                            = ts.structuralSchema

        val original   = WithBigDecimal(BigDecimal("999.123456789"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(BigDecimal("999.123456789"))
        )
        assertTrue(structSchema.reflect.typeName.name == "{value:BigDecimal}")
      },
      test("UUID round-trip") {
        val ts                                = ToStructural.derived[WithUUID]
        implicit val schema: Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema                      = ts.structuralSchema

        val uuid       = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val original   = WithUUID(uuid)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(uuid)
        )
        assertTrue(structSchema.reflect.typeName.name == "{value:UUID}")
      },
      test("Currency round-trip") {
        val ts                                    = ToStructural.derived[WithCurrency]
        implicit val schema: Schema[WithCurrency] = Schema.derived[WithCurrency]
        val structSchema                          = ts.structuralSchema

        val currency   = Currency.getInstance("GBP")
        val original   = WithCurrency(currency)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(currency)
        )
        assertTrue(structSchema.reflect.typeName.name == "{value:Currency}")
      },
      test("Instant round-trip") {
        val ts                                   = ToStructural.derived[WithInstant]
        implicit val schema: Schema[WithInstant] = Schema.derived[WithInstant]
        val structSchema                         = ts.structuralSchema

        val instant    = Instant.parse("2024-06-15T14:30:00Z")
        val original   = WithInstant(instant)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(instant)
        )
        assertTrue(structSchema.reflect.typeName.name == "{value:Instant}")
      },
      test("Unit round-trip") {
        val ts                                = ToStructural.derived[WithUnit]
        implicit val schema: Schema[WithUnit] = Schema.derived[WithUnit]
        val structSchema                      = ts.structuralSchema

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
        val ts                                          = ToStructural.derived[ExtendedPrimitives]
        implicit val schema: Schema[ExtendedPrimitives] = Schema.derived[ExtendedPrimitives]
        val structSchema                                = ts.structuralSchema

        val uuid     = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val currency = Currency.getInstance("USD")
        val instant  = Instant.now()

        val original = ExtendedPrimitives(
          bigInt = BigInt("12345"),
          bigDecimal = BigDecimal("67.89"),
          uuid = uuid,
          currency = currency,
          instant = instant,
          unit = ()
        )
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.bigInt) == Right(BigInt("12345")),
          roundTrip.map(_.bigDecimal) == Right(BigDecimal("67.89")),
          roundTrip.map(_.uuid) == Right(uuid),
          roundTrip.map(_.currency) == Right(currency),
          roundTrip.map(_.instant) == Right(instant)
        )
      }
    ),
    suite("TypeName for Extended Primitives")(
      test("BigInt shows as BigInt in TypeName") {
        val ts                                  = ToStructural.derived[WithBigInt]
        implicit val schema: Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema                        = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:BigInt}")
      },
      test("BigDecimal shows as BigDecimal in TypeName") {
        val ts                                      = ToStructural.derived[WithBigDecimal]
        implicit val schema: Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema                            = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:BigDecimal}")
      },
      test("UUID shows as UUID in TypeName") {
        val ts                                = ToStructural.derived[WithUUID]
        implicit val schema: Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema                      = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        assertTrue(typeName == "{value:UUID}")
      }
    ),
    suite("Extended Primitives in Collections")(
      test("Optional UUID") {
        val ts                                        = ToStructural.derived[WithOptionalUUID]
        implicit val schema: Schema[WithOptionalUUID] = Schema.derived[WithOptionalUUID]
        val structSchema                              = ts.structuralSchema

        val uuid       = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479")
        val original   = WithOptionalUUID(Some(uuid))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.id) == Right(Some(uuid))
        )
        assertTrue(structSchema.reflect.typeName.name == "{id:Option[UUID]}")
      },
      test("Optional UUID with None") {
        val ts                                        = ToStructural.derived[WithOptionalUUID]
        implicit val schema: Schema[WithOptionalUUID] = Schema.derived[WithOptionalUUID]
        val structSchema                              = ts.structuralSchema

        val original   = WithOptionalUUID(None)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.id) == Right(None)
        )
        assertTrue(structSchema.reflect.typeName.name == "{id:Option[UUID]}")
      },
      test("List of BigInt") {
        val ts                                        = ToStructural.derived[WithListOfBigInt]
        implicit val schema: Schema[WithListOfBigInt] = Schema.derived[WithListOfBigInt]
        val structSchema                              = ts.structuralSchema

        val original = WithListOfBigInt(
          List(
            BigInt("1"),
            BigInt("12345678901234567890"),
            BigInt("999999999999999999999")
          )
        )
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.values) == Right(
            List(
              BigInt("1"),
              BigInt("12345678901234567890"),
              BigInt("999999999999999999999")
            )
          )
        )
        assertTrue(structSchema.reflect.typeName.name == "{values:List[BigInt]}")
      }
    ),
    suite("DynamicValue Format for Extended Primitives")(
      test("BigInt produces correct DynamicValue") {
        val ts                                  = ToStructural.derived[WithBigInt]
        implicit val schema: Schema[WithBigInt] = Schema.derived[WithBigInt]
        val structSchema                        = ts.structuralSchema

        val structural = ts.toStructural(WithBigInt(BigInt("123")))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            fieldMap("value") match {
              case DynamicValue.Primitive(PrimitiveValue.BigInt(bi)) =>
                assertTrue(bi == BigInt("123"))
              case _ =>
                assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
        assertTrue(structSchema.reflect.typeName.name == "{value:BigInt}")
      },
      test("BigDecimal produces correct DynamicValue") {
        val ts                                      = ToStructural.derived[WithBigDecimal]
        implicit val schema: Schema[WithBigDecimal] = Schema.derived[WithBigDecimal]
        val structSchema                            = ts.structuralSchema

        val structural = ts.toStructural(WithBigDecimal(BigDecimal("45.67")))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            fieldMap("value") match {
              case DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd)) =>
                assertTrue(bd == BigDecimal("45.67"))
              case _ =>
                assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
        assertTrue(structSchema.reflect.typeName.name == "{value:BigDecimal}")
      },
      test("UUID produces correct DynamicValue") {
        val ts                                = ToStructural.derived[WithUUID]
        implicit val schema: Schema[WithUUID] = Schema.derived[WithUUID]
        val structSchema                      = ts.structuralSchema

        val uuid       = UUID.fromString("12345678-1234-1234-1234-123456789012")
        val structural = ts.toStructural(WithUUID(uuid))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            fieldMap("value") match {
              case DynamicValue.Primitive(PrimitiveValue.UUID(u)) =>
                assertTrue(u == uuid)
              case _ =>
                assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
        assertTrue(structSchema.reflect.typeName.name == "{value:UUID}")
      }
    ),
    suite("Equality for Extended Primitives")(
      test("equal BigInt values produce equal records") {
        val ts = ToStructural.derived[WithBigInt]
        val s1 = ts.toStructural(WithBigInt(BigInt("123456789")))
        val s2 = ts.toStructural(WithBigInt(BigInt("123456789")))
        assertTrue(s1 == s2)
      },
      test("different BigInt values produce different records") {
        val ts = ToStructural.derived[WithBigInt]
        val s1 = ts.toStructural(WithBigInt(BigInt("123")))
        val s2 = ts.toStructural(WithBigInt(BigInt("456")))
        assertTrue(s1 != s2)
      },
      test("equal UUID values produce equal records") {
        val ts   = ToStructural.derived[WithUUID]
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val s1   = ts.toStructural(WithUUID(uuid))
        val s2   = ts.toStructural(WithUUID(uuid))
        assertTrue(s1 == s2)
      }
    )
  ) @@ exceptNative
}
