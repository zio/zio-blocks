package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import zio.Random
import java.time._

object DiffSpec extends SchemaBaseSpec {

  def spec = suite("Diff")(
    suite("Empty patch")(
      test("diff(value, value) produces empty patch for primitives") {
        val schema = Schema[Int]
        val patch  = schema.diff(42, 42)
        assertTrue(patch.isEmpty)
      },
      test("diff(value, value) produces empty patch for records") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived
        val p                                     = Person("Alice", 30)
        val patch                                 = Schema[Person].diff(p, p)
        assertTrue(patch.isEmpty)
      },
      test("diff(value, value) produces empty patch for sequences") {
        val schema = Schema[Vector[Int]]
        val v      = Vector(1, 2, 3)
        val patch  = schema.diff(v, v)
        assertTrue(patch.isEmpty)
      }
    ),
    suite("Primitive diffing")(
      test("diff on non-numeric primitives uses Set") {
        val schema = Schema[String]
        val patch  = schema.diff("hello", "world")

        // Check that the patch works
        val result = patch("hello", PatchMode.Strict)
        assertTrue(result == Right("world"))
      },
      test("diff on boolean uses Set") {
        val schema = Schema[Boolean]
        val patch  = schema.diff(true, false)
        val result = patch(true, PatchMode.Strict)
        assertTrue(result == Right(false))
      },
      test("diff on UUID uses Set") {
        for {
          uuid1 <- Random.nextUUID
          uuid2 <- Random.nextUUID
          schema = Schema[java.util.UUID]
          patch  = schema.diff(uuid1, uuid2)
          result = patch(uuid1, PatchMode.Strict)
        } yield assertTrue(result == Right(uuid2))
      }
    ),
    suite("Numeric diffing")(
      test("diff on Int uses IntDelta") {
        val schema = Schema[Int]
        val patch  = schema.diff(10, 15)

        // Verify it's a delta operation
        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(5)) => true
            case _                                                             => false
          }
        )
      },
      test("diff on Long uses LongDelta") {
        val schema = Schema[Long]
        val patch  = schema.diff(100L, 150L)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LongDelta(50L)) => true
            case _                                                                => false
          }
        )
      },
      test("diff on Double uses DoubleDelta") {
        val schema = Schema[Double]
        val patch  = schema.diff(1.5, 2.5)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DoubleDelta(delta)) =>
              Math.abs(delta - 1.0) < 0.0001
            case _ => false
          }
        )
      },
      test("diff on Float uses FloatDelta") {
        val schema = Schema[Float]
        val patch  = schema.diff(1.5f, 2.5f)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.FloatDelta(delta)) =>
              Math.abs(delta - 1.0f) < 0.0001f
            case _ => false
          }
        )
      },
      test("diff on Short uses ShortDelta") {
        val schema = Schema[Short]
        val patch  = schema.diff(10.toShort, 15.toShort)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ShortDelta(5)) => true
            case _                                                               => false
          }
        )
      },
      test("diff on Byte uses ByteDelta") {
        val schema = Schema[Byte]
        val patch  = schema.diff(10.toByte, 15.toByte)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ByteDelta(5)) => true
            case _                                                              => false
          }
        )
      },
      test("diff on BigInt uses BigIntDelta") {
        val schema = Schema[BigInt]
        val patch  = schema.diff(BigInt(100), BigInt(150))

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigIntDelta(delta)) =>
              delta == BigInt(50)
            case _ => false
          }
        )
      },
      test("diff on BigDecimal uses BigDecimalDelta") {
        val schema = Schema[BigDecimal]
        val patch  = schema.diff(BigDecimal("1.5"), BigDecimal("2.5"))

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigDecimalDelta(delta)) =>
              delta == BigDecimal("1.0")
            case _ => false
          }
        )
      },
      test("numeric delta with negative values (decrement)") {
        val schema = Schema[Int]
        val patch  = schema.diff(15, 10)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(-5)) => true
            case _                                                              => false
          }
        )
      }
    ),
    suite("String diffing")(
      test("diff on strings uses StringEdit for insert when cheaper") {
        val schema  = Schema[String]
        val patch   = schema.diff("hello", "hello world")
        val applied = patch("hello", PatchMode.Strict)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.StringEdit(ops)) =>
              ops == Vector(Patch.StringOp.Insert(5, " world"))
            case _ => false
          }
        ) &&
        assertTrue(applied == Right("hello world"))
      },
      test("diff on strings uses StringEdit for delete when cheaper") {
        val schema  = Schema[String]
        val patch   = schema.diff("hello world", "hello")
        val applied = patch("hello world", PatchMode.Strict)

        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.StringEdit(ops)) =>
              ops == Vector(Patch.StringOp.Delete(5, 6))
            case _ => false
          }
        ) &&
        assertTrue(applied == Right("hello"))
      },
      test("diff on strings uses StringEdit for replacements when cheaper") {
        val schema  = Schema[String]
        val patch   = schema.diff("abcdef", "abXYef")
        val applied = patch("abcdef", PatchMode.Strict)

        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.StringEdit(ops)) =>
              ops == Vector(Patch.StringOp.Delete(2, 2), Patch.StringOp.Insert(2, "XY"))
            case _ => false
          }
        ) &&
        assertTrue(applied == Right("abXYef"))
      },
      test("diff on strings uses Set for complete replacement") {
        val schema  = Schema[String]
        val patch   = schema.diff("hello world", "xyz")
        val applied = patch("hello world", PatchMode.Strict)

        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("xyz"))) => true
            case _                                                                         => false
          }
        ) &&
        assertTrue(applied == Right("xyz"))
      },
      test("diff on empty string to non-empty uses Set fallback") {
        val schema  = Schema[String]
        val patch   = schema.diff("", "hello")
        val applied = patch("", PatchMode.Strict)

        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("hello"))) => true
            case _                                                                           => false
          }
        ) &&
        assertTrue(applied == Right("hello"))
      },
      test("diff on non-empty string to empty uses Set fallback") {
        val schema  = Schema[String]
        val patch   = schema.diff("hello", "")
        val applied = patch("hello", PatchMode.Strict)

        assertTrue(
          patch.dynamicPatch.ops.head.operation match {
            case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String(""))) => true
            case _                                                                      => false
          }
        ) &&
        assertTrue(applied == Right(""))
      }
    ),
    suite("Temporal diffing")(
      test("diff on Instant uses InstantDelta") {
        val schema = Schema[Instant]
        val t1     = Instant.parse("2023-01-01T00:00:00Z")
        val t2     = Instant.parse("2023-01-01T01:00:00Z")
        val patch  = schema.diff(t1, t2)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.InstantDelta(duration)) =>
              duration == Duration.ofHours(1)
            case _ => false
          }
        )
      },
      test("diff on Duration uses DurationDelta") {
        val schema = Schema[Duration]
        val d1     = Duration.ofHours(1)
        val d2     = Duration.ofHours(3)
        val patch  = schema.diff(d1, d2)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DurationDelta(delta)) =>
              delta == Duration.ofHours(2)
            case _ => false
          }
        )
      },
      test("diff on LocalDate uses LocalDateDelta") {
        val schema = Schema[LocalDate]
        val d1     = LocalDate.of(2023, 1, 1)
        val d2     = LocalDate.of(2023, 1, 10)
        val patch  = schema.diff(d1, d2)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateDelta(period)) =>
              period == Period.ofDays(9)
            case _ => false
          }
        )
      },
      test("diff on LocalDateTime uses LocalDateTimeDelta") {
        val schema = Schema[LocalDateTime]
        val dt1    = LocalDateTime.of(2023, 1, 1, 10, 0, 0)
        val dt2    = LocalDateTime.of(2023, 1, 2, 11, 30, 0)
        val patch  = schema.diff(dt1, dt2)

        val opMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateTimeDelta(period, duration)) =>
                  period == Period.ofDays(1) && duration == Duration.ofMinutes(90)
                case _ => false
              }
            case None => false
          }

        val result = patch(dt1, PatchMode.Strict)
        assertTrue(opMatch) && assertTrue(result == Right(dt2))
      },
      test("diff on LocalDateTime handles negative deltas") {
        val schema = Schema[LocalDateTime]
        val dt1    = LocalDateTime.of(2023, 3, 5, 8, 15, 0)
        val dt2    = LocalDateTime.of(2023, 2, 28, 7, 45, 0)
        val patch  = schema.diff(dt1, dt2)

        val expectedPeriod   = Period.between(dt1.toLocalDate, dt2.toLocalDate)
        val expectedDuration = Duration.between(dt1.plus(expectedPeriod), dt2)

        val opMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateTimeDelta(period, duration)) =>
                  period == expectedPeriod && duration == expectedDuration
                case _ => false
              }
            case None => false
          }

        val result = patch(dt1, PatchMode.Strict)
        assertTrue(opMatch) && assertTrue(result == Right(dt2))
      },
      test("diff on Period uses PeriodDelta") {
        val schema = Schema[Period]
        val p1     = Period.of(1, 2, 3)
        val p2     = Period.of(2, 4, 6)
        val patch  = schema.diff(p1, p2)

        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.PeriodDelta(delta)) =>
              delta == Period.of(1, 2, 3)
            case _ => false
          }
        )
      }
    ),
    suite("Record diffing")(
      test("diff on records patches only changed fields") {
        case class Person(name: String, age: Int, city: String)
        implicit val personSchema: Schema[Person] = Schema.derived
        val old                                   = Person("Alice", 30, "NYC")
        val updated                               = Person("Alice", 31, "NYC")

        val patch = Schema[Person].diff(old, updated)

        // Should only have one operation (for the age field)
        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(patch.dynamicPatch.ops(0).path.nodes == Vector(DynamicOptic.Node.Field("age")))
      },
      test("diff on records with multiple changed fields") {
        case class Person(name: String, age: Int, city: String)
        implicit val personSchema: Schema[Person] = Schema.derived
        val old                                   = Person("Alice", 30, "NYC")
        val updated                               = Person("Bob", 31, "LA")

        val patch = Schema[Person].diff(old, updated)

        // Should have three operations
        assertTrue(patch.dynamicPatch.ops.length == 3)
      },
      test("diff on nested records produces nested patches") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived

        val old     = Person("Alice", Address("123 Main St", "NYC"))
        val updated = Person("Alice", Address("456 Elm St", "NYC"))

        val patch = Schema[Person].diff(old, updated)

        // Should have one operation for address.street
        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).path.nodes == Vector(
            DynamicOptic.Node.Field("address"),
            DynamicOptic.Node.Field("street")
          )
        )
      },
      test("diff on deeply nested records") {
        case class City(name: String)
        case class Address(street: String, city: City)
        case class Person(name: String, address: Address)
        implicit val citySchema: Schema[City]       = Schema.derived
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived

        val old     = Person("Alice", Address("123 Main St", City("NYC")))
        val updated = Person("Alice", Address("123 Main St", City("LA")))

        val patch = Schema[Person].diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      }
    ),
    suite("Sequence diffing")(
      test("diff on sequences: append detected") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector(1, 2, 3)
        val updated = Vector(1, 2, 3, 4, 5)

        val patch = schema.diff(old, updated)

        // Should detect append
        assertTrue(patch.dynamicPatch.ops.length == 1) &&
        assertTrue(
          patch.dynamicPatch.ops(0).operation match {
            case Patch.Operation.SequenceEdit(ops) =>
              ops.length == 1 && ops(0).isInstanceOf[Patch.SeqOp.Append]
            case _ => false
          }
        )
      },
      test("diff on sequences: insert detected") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector(1, 3)
        val updated = Vector(1, 2, 3)

        val patch = schema.diff(old, updated)

        val opsMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.SequenceEdit(ops) =>
                  ops == Vector(
                    Patch.SeqOp.Insert(
                      1,
                      Chunk(DynamicValue.Primitive(PrimitiveValue.Int(2)))
                    )
                  )
                case _ => false
              }
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opsMatch) && assertTrue(result == Right(updated))
      },
      test("diff on sequences: delete detected") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector(1, 2, 3)
        val updated = Vector(1, 3)

        val patch = schema.diff(old, updated)

        val opsMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.SequenceEdit(ops) =>
                  ops == Vector(Patch.SeqOp.Delete(1, 1))
                case _ => false
              }
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opsMatch) && assertTrue(result == Right(updated))
      },
      test("diff on sequences: complex changes") {
        val schema  = Schema[Vector[String]]
        val old     = Vector("a", "b", "c", "d")
        val updated = Vector("a", "x", "c", "y")

        val patch = schema.diff(old, updated)

        val expectedOps = Vector(
          Patch.SeqOp.Delete(1, 1),
          Patch.SeqOp.Insert(1, Chunk(DynamicValue.Primitive(PrimitiveValue.String("x")))),
          Patch.SeqOp.Delete(3, 1),
          Patch.SeqOp.Append(Chunk(DynamicValue.Primitive(PrimitiveValue.String("y"))))
        )

        val opsMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.SequenceEdit(ops) => ops == expectedOps
                case _                                 => false
              }
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opsMatch) && assertTrue(result == Right(updated))
      },
      test("diff on empty sequence to non-empty") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector.empty[Int]
        val updated = Vector(1, 2, 3)

        val patch = schema.diff(old, updated)

        val opsMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.SequenceEdit(ops) =>
                  ops == Vector(
                    Patch.SeqOp.Append(
                      Chunk(
                        DynamicValue.Primitive(PrimitiveValue.Int(1)),
                        DynamicValue.Primitive(PrimitiveValue.Int(2)),
                        DynamicValue.Primitive(PrimitiveValue.Int(3))
                      )
                    )
                  )
                case _ => false
              }
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opsMatch) && assertTrue(result == Right(updated))
      },
      test("diff on non-empty sequence to empty") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector(1, 2, 3)
        val updated = Vector.empty[Int]

        val patch = schema.diff(old, updated)

        val opsMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.operation match {
                case Patch.Operation.SequenceEdit(ops) =>
                  ops == Vector(Patch.SeqOp.Delete(0, 3))
                case _ => false
              }
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opsMatch) && assertTrue(result == Right(updated))
      }
    ),
    suite("Map diffing")(
      test("diff on maps: added keys use Add") {
        val schema  = Schema[Map[String, Int]]
        val old     = Map("a" -> 1, "b" -> 2)
        val updated = Map("a" -> 1, "b" -> 2, "c" -> 3)

        val patch = schema.diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      },
      test("diff on maps: removed keys use Remove") {
        val schema  = Schema[Map[String, Int]]
        val old     = Map("a" -> 1, "b" -> 2, "c" -> 3)
        val updated = Map("a" -> 1, "b" -> 2)

        val patch = schema.diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      },
      test("diff on maps: changed values") {
        val schema  = Schema[Map[String, Int]]
        val old     = Map("a" -> 1, "b" -> 2)
        val updated = Map("a" -> 1, "b" -> 5)

        val patch = schema.diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      },
      test("diff on maps with complex values") {
        case class Value(x: Int, y: Int)
        implicit val valueSchema: Schema[Value] = Schema.derived
        val schema                              = Schema[Map[String, Value]]

        val old     = Map("a" -> Value(1, 2), "b" -> Value(3, 4))
        val updated = Map("a" -> Value(1, 5), "b" -> Value(3, 4))

        val patch = schema.diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      },
      test("diff on empty map to non-empty") {
        val schema  = Schema[Map[String, Int]]
        val old     = Map.empty[String, Int]
        val updated = Map("a" -> 1, "b" -> 2)

        val patch = schema.diff(old, updated)

        val result = patch(old, PatchMode.Strict)
        assertTrue(result == Right(updated))
      }
    ),
    suite("Variant diffing")(
      test("diff on variants: case change uses Set") {
        sealed trait Status
        case class Active(since: String)    extends Status
        case class Inactive(reason: String) extends Status
        implicit val activeSchema: Schema[Active]     = Schema.derived
        implicit val inactiveSchema: Schema[Inactive] = Schema.derived
        implicit val statusSchema: Schema[Status]     = Schema.derived

        val old: Status     = Active("2023-01-01")
        val updated: Status = Inactive("test")

        val patch = Schema[Status].diff(old, updated)

        val opMatch =
          patch.dynamicPatch.ops.headOption match {
            case Some(op) =>
              op.path.nodes.isEmpty &&
              (op.operation match {
                case Patch.Operation.Set(DynamicValue.Variant("Inactive", _)) => true
                case _                                                        => false
              })
            case None => false
          }

        val result = patch(old, PatchMode.Strict)
        assertTrue(opMatch) && assertTrue(result == Right(updated))
      },
      test("diff on variants: same case with different value emits nested patch") {
        sealed trait Status
        case class Active(since: String)    extends Status
        case class Inactive(reason: String) extends Status
        implicit val activeSchema: Schema[Active]     = Schema.derived
        implicit val inactiveSchema: Schema[Inactive] = Schema.derived
        implicit val statusSchema: Schema[Status]     = Schema.derived

        val scenarios = List(
          (Active("2023-01-01"), Active("2024-01-01"), "Active", "since"),
          (Inactive("maintenance"), Inactive("back soon"), "Inactive", "reason")
        )

        val checks =
          scenarios.map { case (old, updated, caseName, fieldName) =>
            val patch            = Schema[Status].diff(old, updated)
            val hasCaseFieldPath = patch.dynamicPatch.ops.exists(
              _.path.nodes == Vector(
                DynamicOptic.Node.Case(caseName),
                DynamicOptic.Node.Field(fieldName)
              )
            )
            hasCaseFieldPath && patch(old, PatchMode.Strict) == Right(updated)
          }

        assertTrue(checks.forall(identity))
      }
    ),
    suite("Roundtrip law")(
      test("roundtrip law holds for primitives") {
        val intSchema = Schema[Int]
        assertTrue(intSchema.diff(10, 20)(10, PatchMode.Strict) == Right(20)) &&
        assertTrue(intSchema.diff(10, 10)(10, PatchMode.Strict) == Right(10))
      },
      test("roundtrip law holds for strings") {
        val schema = Schema[String]
        assertTrue(schema.diff("hello", "world")("hello", PatchMode.Strict) == Right("world")) &&
        assertTrue(schema.diff("", "test")("", PatchMode.Strict) == Right("test")) &&
        assertTrue(schema.diff("test", "")("test", PatchMode.Strict) == Right(""))
      },
      test("roundtrip law holds for records") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived
        val old                                   = Person("Alice", 30)
        val updated                               = Person("Bob", 25)

        val patch = Schema[Person].diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("roundtrip law holds for nested records") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived

        val old     = Person("Alice", Address("123 Main", "NYC"))
        val updated = Person("Alice", Address("456 Elm", "LA"))

        val patch = Schema[Person].diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("roundtrip law holds for sequences") {
        val schema  = Schema[Vector[Int]]
        val old     = Vector(1, 2, 3)
        val updated = Vector(1, 4, 3, 5)

        val patch = schema.diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("roundtrip law holds for maps") {
        val schema  = Schema[Map[String, Int]]
        val old     = Map("a" -> 1, "b" -> 2, "c" -> 3)
        val updated = Map("a" -> 10, "c" -> 3, "d" -> 4)

        val patch = schema.diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("roundtrip law holds for complex structures") {
        case class Item(id: Int, name: String)
        case class Order(items: Vector[Item], total: BigDecimal)
        implicit val itemSchema: Schema[Item]   = Schema.derived
        implicit val orderSchema: Schema[Order] = Schema.derived

        val old     = Order(Vector(Item(1, "A"), Item(2, "B")), BigDecimal("10.50"))
        val updated = Order(Vector(Item(1, "A"), Item(3, "C")), BigDecimal("15.75"))

        val patch = Schema[Order].diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("roundtrip law holds for all temporal types") {
        val instantSchema  = Schema[Instant]
        val t1             = Instant.parse("2023-01-01T00:00:00Z")
        val t2             = Instant.parse("2023-01-02T00:00:00Z")
        val durationSchema = Schema[Duration]
        val d1             = Duration.ofHours(1)
        val d2             = Duration.ofHours(5)
        val dateSchema     = Schema[LocalDate]
        val ld1            = LocalDate.of(2023, 1, 1)
        val ld2            = LocalDate.of(2023, 2, 15)

        assertTrue(instantSchema.diff(t1, t2)(t1, PatchMode.Strict) == Right(t2)) &&
        assertTrue(durationSchema.diff(d1, d2)(d1, PatchMode.Strict) == Right(d2)) &&
        assertTrue(dateSchema.diff(ld1, ld2)(ld1, PatchMode.Strict) == Right(ld2))
      }
    ),
    suite("Edge cases")(
      test("diff handles unicode in strings") {
        val schema  = Schema[String]
        val old     = "Hello 世界"
        val updated = "Hello 世界!"

        val patch = schema.diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("diff handles large sequences") {
        val schema  = Schema[Vector[Int]]
        val old     = (1 to 100).toVector
        val updated = (1 to 100).toVector :+ 101

        val patch = schema.diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("diff handles nested Options") {
        case class Person(name: String, nickname: Option[String])
        implicit val personSchema: Schema[Person] = Schema.derived
        val old                                   = Person("Alice", None)
        val updated                               = Person("Alice", Some("Ally"))

        val patch = Schema[Person].diff(old, updated)
        assertTrue(patch(old, PatchMode.Strict) == Right(updated))
      },
      test("diff handles NaN to NaN (Double)") {
        val schema = Schema[Double]
        val patch  = schema.diff(Double.NaN, Double.NaN)
        assertTrue(patch.isEmpty)
      },
      test("diff handles NaN to number (Double)") {
        val schema = Schema[Double]
        val patch  = schema.diff(Double.NaN, 42.0)

        val usesSet = patch.dynamicPatch.ops.headOption match {
          case Some(op) =>
            op.operation match {
              case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Double(42.0))) => true
              case _                                                                        => false
            }
          case None => false
        }

        val result = patch(Double.NaN, PatchMode.Strict)
        assertTrue(usesSet) && assertTrue(result == Right(42.0))
      },
      test("diff handles number to NaN (Double)") {
        val schema = Schema[Double]
        val patch  = schema.diff(42.0, Double.NaN)

        val usesSet = patch.dynamicPatch.ops.headOption match {
          case Some(op) =>
            op.operation match {
              case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Double(value))) =>
                java.lang.Double.isNaN(value)
              case _ => false
            }
          case None => false
        }

        val result = patch(42.0, PatchMode.Strict)
        assertTrue(usesSet) && assertTrue(result.exists(v => java.lang.Double.isNaN(v)))
      },
      test("diff handles NaN to NaN (Float)") {
        val schema = Schema[Float]
        val patch  = schema.diff(Float.NaN, Float.NaN)
        assertTrue(patch.isEmpty)
      },
      test("diff handles NaN to number (Float)") {
        val schema = Schema[Float]
        val patch  = schema.diff(Float.NaN, 42.0f)

        val result = patch(Float.NaN, PatchMode.Strict)
        assertTrue(result == Right(42.0f))
      },
      test("diff handles number to NaN (Float)") {
        val schema = Schema[Float]
        val patch  = schema.diff(42.0f, Float.NaN)

        val result = patch(42.0f, PatchMode.Strict)
        assertTrue(result.exists(v => java.lang.Float.isNaN(v)))
      }
    ),
    suite("Sequences of records")(
      test("diff sequences of records with nested changes") {
        case class Item(id: Int, name: String, price: Double)
        implicit val itemSchema: Schema[Item] = Schema.derived

        val old = Vector(
          Item(1, "Apple", 1.0),
          Item(2, "Banana", 2.0),
          Item(3, "Cherry", 3.0)
        )

        val updated = Vector(
          Item(1, "Apple", 1.5),  // price changed
          Item(2, "Banana", 2.0), // unchanged
          Item(3, "Cherry", 3.0)  // unchanged
        )

        val schema = Schema[Vector[Item]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff sequences of records with additions") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived

        val old     = Vector(Person("Alice", 30), Person("Bob", 25))
        val updated = Vector(Person("Alice", 30), Person("Bob", 25), Person("Charlie", 35))

        val schema = Schema[Vector[Person]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff sequences of records with deletions") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived

        val old     = Vector(Person("Alice", 30), Person("Bob", 25), Person("Charlie", 35))
        val updated = Vector(Person("Alice", 30), Person("Charlie", 35))

        val schema = Schema[Vector[Person]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff sequences of records with reordering") {
        case class Tag(id: Int, label: String)
        implicit val tagSchema: Schema[Tag] = Schema.derived

        val old     = Vector(Tag(1, "urgent"), Tag(2, "bug"), Tag(3, "feature"))
        val updated = Vector(Tag(1, "urgent"), Tag(3, "feature"), Tag(2, "bug"))

        val schema = Schema[Vector[Tag]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      }
    ),
    suite("Maps with deeply nested values")(
      test("diff maps with nested records") {
        case class Address(street: String, city: String, zip: String)
        case class Person(name: String, age: Int, address: Address)
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived

        val old = Map(
          "user1" -> Person("Alice", 30, Address("123 Main St", "NYC", "10001")),
          "user2" -> Person("Bob", 25, Address("456 Elm St", "LA", "90001"))
        )

        val updated = Map(
          "user1" -> Person("Alice", 31, Address("123 Main St", "NYC", "10001")), // age changed
          "user2" -> Person("Bob", 25, Address("456 Elm St", "LA", "90002"))      // zip changed
        )

        val schema = Schema[Map[String, Person]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff maps with deeply nested structures (3+ levels)") {
        case class Coordinate(lat: Double, lon: Double)
        case class Location(name: String, coord: Coordinate)
        case class Store(id: Int, location: Location, inventory: Vector[String])
        implicit val coordSchema: Schema[Coordinate] = Schema.derived
        implicit val locSchema: Schema[Location]     = Schema.derived
        implicit val storeSchema: Schema[Store]      = Schema.derived

        val old = Map(
          "store1" -> Store(1, Location("Downtown", Coordinate(40.7, -74.0)), Vector("apple", "banana")),
          "store2" -> Store(2, Location("Uptown", Coordinate(40.8, -73.9)), Vector("cherry"))
        )

        val updated = Map(
          "store1" -> Store(1, Location("Downtown", Coordinate(40.7, -74.0)), Vector("apple", "banana")),
          "store2" -> Store(
            2,
            Location("Uptown", Coordinate(40.8, -73.95)),
            Vector("cherry", "date")
          ) // coord.lon and inventory changed
        )

        val schema = Schema[Map[String, Store]]
        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff maps with nested maps") {
        val schema = Schema[Map[String, Map[String, Int]]]

        val old = Map(
          "group1" -> Map("a" -> 1, "b" -> 2),
          "group2" -> Map("c" -> 3, "d" -> 4)
        )

        val updated = Map(
          "group1" -> Map("a" -> 1, "b" -> 5),          // value changed
          "group2" -> Map("c" -> 3, "d" -> 4, "e" -> 6) // key added
        )

        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      },
      test("diff maps with vectors of nested records") {
        case class Task(id: Int, desc: String, done: Boolean)
        implicit val taskSchema: Schema[Task] = Schema.derived

        val schema = Schema[Map[String, Vector[Task]]]

        val old = Map(
          "project1" -> Vector(Task(1, "Design", false), Task(2, "Implement", false)),
          "project2" -> Vector(Task(3, "Test", false))
        )

        val updated = Map(
          "project1" -> Vector(Task(1, "Design", true), Task(2, "Implement", false)), // done changed
          "project2" -> Vector(Task(3, "Test", false), Task(4, "Deploy", false))      // task added
        )

        val patch  = schema.diff(old, updated)
        val result = patch(old, PatchMode.Strict)

        assertTrue(result == Right(updated))
      }
    ),
    suite("DynamicValue.Null diffing")(
      test("diff(Null, Null) produces empty patch") {
        val patch = Differ.diff(DynamicValue.Null, DynamicValue.Null)
        assertTrue(patch.isEmpty)
      },
      test("diff(Null, other) produces Set patch") {
        val other = DynamicValue.int(42)
        val patch = Differ.diff(DynamicValue.Null, other)
        assertTrue(!patch.isEmpty) &&
        assertTrue(
          patch.ops.head.operation match {
            case Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))) => true
            case _                                                                   => false
          }
        )
      },
      test("diff(other, Null) produces Set patch") {
        val other = DynamicValue.string("hello")
        val patch = Differ.diff(other, DynamicValue.Null)
        assertTrue(!patch.isEmpty) &&
        assertTrue(
          patch.ops.head.operation match {
            case Patch.Operation.Set(DynamicValue.Null) => true
            case _                                      => false
          }
        )
      }
    ),
    suite("Empty-to-empty diffs")(
      test("empty-to-empty for String") {
        val schema = Schema[String]
        val patch  = schema.diff("", "")
        assertTrue(patch.isEmpty)
      },
      test("empty-to-empty for Vector") {
        val schema = Schema[Vector[Int]]
        val patch  = schema.diff(Vector.empty, Vector.empty)
        assertTrue(patch.isEmpty)
      },
      test("empty-to-empty for Map") {
        val schema = Schema[Map[String, Int]]
        val patch  = schema.diff(Map.empty, Map.empty)
        assertTrue(patch.isEmpty)
      },
      test("empty-to-empty for Option (None)") {
        val schema = Schema[Option[String]]
        val patch  = schema.diff(None, None)
        assertTrue(patch.isEmpty)
      },
      test("empty-to-empty for nested structures") {
        case class Container(items: Vector[String], metadata: Map[String, Int])
        implicit val containerSchema: Schema[Container] = Schema.derived

        val empty = Container(Vector.empty, Map.empty)
        val patch = Schema[Container].diff(empty, empty)
        assertTrue(patch.isEmpty)
      }
    )
  )
}
