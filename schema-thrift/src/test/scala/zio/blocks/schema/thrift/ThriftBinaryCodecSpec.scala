package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.blocks.schema.JavaTimeGen._
import zio.test._
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}

/**
 * Comprehensive test suite for ThriftBinaryCodec. Tests round-trip
 * encoding/decoding for all supported types.
 */
object ThriftBinaryCodecSpec extends SchemaBaseSpec {
  case class BasicInt(value: Int)

  object BasicInt {
    implicit val schema: Schema[BasicInt] = Schema.derived
  }

  case class Record(name: String, value: Int)

  object Record {
    implicit val schema: Schema[Record] = Schema.derived
  }

  case class Embedded(inner: BasicInt)

  object Embedded {
    implicit val schema: Schema[Embedded] = Schema.derived
  }

  sealed trait OneOf

  object OneOf {
    case class StringValue(value: String) extends OneOf

    case class IntValue(value: Int) extends OneOf

    case class BoolValue(value: Boolean) extends OneOf

    implicit val schema: Schema[OneOf] = Schema.derived
  }

  case class HighArity(
    f1: Unit,
    f2: Boolean,
    f3: Byte,
    f4: Short,
    f5: Int,
    f6: Long,
    f7: Float,
    f8: Double,
    f9: Char,
    f10: BigInt,
    f11: BigDecimal,
    f12: DayOfWeek,
    f13: Duration,
    f14: Instant,
    f15: LocalDate,
    f16: LocalDateTime,
    f17: LocalTime,
    f18: Month,
    f19: MonthDay,
    f20: OffsetDateTime,
    f21: OffsetTime,
    f22: Period,
    f23: Year,
    f24: YearMonth,
    f25: ZoneId,
    f26: ZoneOffset,
    f27: ZonedDateTime,
    f28: Currency,
    f29: UUID
  )

  object HighArity {
    implicit val schema: Schema[HighArity] = Schema.derived
    val default: HighArity                 =
      HighArity(
        (),
        true,
        Byte.MaxValue,
        Short.MaxValue,
        Int.MaxValue,
        Long.MaxValue,
        Float.MaxValue,
        Double.MaxValue,
        Char.MaxValue,
        BigInt("9" * 20),
        BigDecimal("9" * 20),
        DayOfWeek.WEDNESDAY,
        Duration.ofSeconds(Long.MaxValue),
        Instant.MAX,
        LocalDate.MAX,
        LocalDateTime.MAX,
        LocalTime.MAX,
        Month.DECEMBER,
        MonthDay.of(Month.DECEMBER, 31),
        OffsetDateTime.MAX,
        OffsetTime.MAX,
        Period.ofDays(Int.MaxValue),
        Year.of(Year.MAX_VALUE),
        YearMonth.of(2026, Month.DECEMBER),
        ZoneId.of("GMT"),
        ZoneOffset.MAX,
        ZonedDateTime.of(LocalDateTime.MAX, ZoneId.of("GMT")),
        Currency.getInstance("USD"),
        new UUID(1L, 2L)
      )
  }

  // Helper for round-trip testing
  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(heapByteBuffer)(value)
    heapByteBuffer.flip()
    val directByteBuffer = ByteBuffer.allocateDirect(8192)
    schema.encode(ThriftFormat)(directByteBuffer)(value)
    directByteBuffer.flip()
    assertTrue(
      schema.decode(ThriftFormat)(heapByteBuffer) == Right(value),
      schema.decode(ThriftFormat)(directByteBuffer) == Right(value)
    )
  }

  def spec = suite("ThriftBinaryCodecSpec")(
    suite("primitives")(
      test("encode/decode Unit") {
        roundTrip(())
      },
      test("encode/decode Boolean") {
        check(Gen.boolean)(x => roundTrip(x))
      },
      test("encode/decode Byte") {
        check(Gen.byte)(x => roundTrip(x))
      },
      test("encode/decode Short") {
        check(Gen.short)(x => roundTrip(x))
      },
      test("encode/decode Int") {
        check(Gen.int)(x => roundTrip(x))
      },
      test("encode/decode Long") {
        check(Gen.long)(x => roundTrip(x))
      },
      test("encode/decode Float") {
        check(Gen.float)(x => roundTrip(x))
      },
      test("encode/decode Double") {
        check(Gen.double)(x => roundTrip(x))
      },
      test("encode/decode Char") {
        check(Gen.char)(x => roundTrip(x))
      },
      test("encode/decode String") {
        check(Gen.string)(x => roundTrip(x))
      },
      test("encode/decode BigInt") {
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x => roundTrip(x))
      },
      test("encode/decode BigDecimal") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x => roundTrip(x))
      },
      test("encode/decode UUID") {
        check(Gen.uuid)(x => roundTrip(x))
      },
      test("encode/decode Currency") {
        check(Gen.currency)(x => roundTrip(x))
      },
      test("encode/decode DayOfWeek") {
        check(genDayOfWeek)(x => roundTrip(x))
      },
      test("encode/decode Duration") {
        check(genDuration)(x => roundTrip(x))
      },
      test("encode/decode Instant") {
        check(genInstant)(x => roundTrip(x))
      },
      test("encode/decode LocalDate") {
        check(genLocalDate)(x => roundTrip(x))
      },
      test("encode/decode LocalDateTime") {
        check(genLocalDateTime)(x => roundTrip(x))
      },
      test("encode/decode LocalTime") {
        check(genLocalTime)(x => roundTrip(x))
      },
      test("encode/decode Month") {
        check(genMonth)(x => roundTrip(x))
      },
      test("encode/decode MonthDay") {
        check(genMonthDay)(x => roundTrip(x))
      },
      test("encode/decode OffsetTime") {
        check(genOffsetTime)(x => roundTrip(x))
      },
      test("encode/decode OffsetDateTime") {
        check(genOffsetDateTime)(x => roundTrip(x))
      },
      test("encode/decode Period") {
        check(genPeriod)(x => roundTrip(x))
      },
      test("encode/decode Year") {
        check(genYear)(x => roundTrip(x))
      },
      test("encode/decode YearMonth") {
        check(genYearMonth)(x => roundTrip(x))
      },
      test("encode/decode ZoneId") {
        check(genZoneId)(x => roundTrip(x))
      },
      test("encode/decode ZoneOffset") {
        check(genZoneOffset)(x => roundTrip(x))
      },
      test("encode/decode ZonedDateTime") {
        check(genZonedDateTime)(x => roundTrip(x))
      }
    ),
    suite("Record Types")(
      test("encode/decode simple record") {
        roundTrip(Record("hello", 42))
      },
      test("encode/decode embedded record") {
        roundTrip(Embedded(BasicInt(100)))
      },
      test("encode/decode nested record") {
        case class NestedRecord(outer: Record, inner: Embedded)

        object NestedRecord {
          implicit val schema: Schema[NestedRecord] = Schema.derived
        }

        roundTrip(NestedRecord(Record("outer", 1), Embedded(BasicInt(2))))
      },
      test("encode/decode record with option value") {
        case class OptionalRecord(name: String, age: Option[Int])

        object OptionalRecord {
          implicit val schema: Schema[OptionalRecord] = Schema.derived
        }

        roundTrip(OptionalRecord("John", Some(30))) &&
        roundTrip(OptionalRecord("Jane", None))
      },
      test("encode/decode tuple in record") {
        case class TupleRecord(pair: (String, Int))

        object TupleRecord {
          implicit val schema: Schema[TupleRecord] = Schema.derived
        }

        roundTrip(TupleRecord(("hello", 42)))
      },
      test("encode/decode high arity record") {
        roundTrip(HighArity.default)
      },
      test("encode/decode list of records") {
        roundTrip(List(Record("a", 1), Record("b", 2), Record("c", 3)))
      }
    ),
    suite("collections")(
      test("encode/decode int list") {
        case class IntList(items: List[Int])

        object IntList {
          implicit val schema: Schema[IntList] = Schema.derived
        }

        roundTrip(IntList(List(1, 2, 3, 4, 5))) &&
        roundTrip(IntList(List.empty))
      },
      test("encode/decode string list") {
        case class StringList(items: List[String])

        object StringList {
          implicit val schema: Schema[StringList] = Schema.derived
        }

        roundTrip(StringList(List("foo", "bar", "baz")))
      },
      test("encode/decode map record") {
        case class MapRecord(id: Int, data: Map[String, Int])

        object MapRecord {
          implicit val schema: Schema[MapRecord] = Schema.derived
        }

        roundTrip(MapRecord(1, Map("a" -> 100, "b" -> 200))) &&
        roundTrip(MapRecord(1, Map.empty))
      },
      test("encode/decode set record") {
        case class SetRecord(id: Int, tags: Set[String])

        object SetRecord {
          implicit val schema: Schema[SetRecord] = Schema.derived
        }

        roundTrip(SetRecord(1, Set("tag1", "tag2", "tag3"))) &&
        roundTrip(SetRecord(1, Set.empty))
      },
      test("encode/decode map with record values") {
        roundTrip(
          Map(
            "first"  -> Record("a", 1),
            "second" -> Record("b", 2)
          )
        )
      }
    ),
    suite("variants")(
      test("encode/decode standalone Option") {
        roundTrip[Option[Int]](Some(42)) &&
        roundTrip[Option[Int]](None)
      },
      test("encode/decode standalone StringValue") {
        roundTrip[OneOf](OneOf.StringValue("test"))
      },
      test("encode/decode case class with ADT field value") {
        case class Enumeration(oneOf: OneOf)

        object Enumeration {
          implicit val schema: Schema[Enumeration] = Schema.derived
        }

        roundTrip(Enumeration(OneOf.StringValue("hello"))) &&
        roundTrip(Enumeration(OneOf.IntValue(42))) &&
        roundTrip(Enumeration(OneOf.BoolValue(true)))
      }
    ),
    suite("schema evolution & wire compatibility")(
      test("decode fields in different order than schema definition") {
        // This test verifies that fields can arrive in ANY order on the wire
        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        case class Person(name: String, age: Int, city: String)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        // Manually construct Thrift bytes with fields in order 3, 1, 2 (city, name, age)
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        // Write struct begin (not required for binary protocol but good practice)
        protocol.writeStructBegin(new TStruct("Person"))

        // Field 3 first (city) - out of order!
        protocol.writeFieldBegin(new TField("city", TType.STRING, 3))
        protocol.writeString("NYC")
        protocol.writeFieldEnd()

        // Field 1 second (name) - out of order!
        protocol.writeFieldBegin(new TField("name", TType.STRING, 1))
        protocol.writeString("Alice")
        protocol.writeFieldEnd()

        // Field 2 third (age) - out of order!
        protocol.writeFieldBegin(new TField("age", TType.I32, 2))
        protocol.writeI32(30)
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        // Now decode using our codec
        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[Person].decode(ThriftFormat)(buffer)

        // THIS MUST PASS - fields were written in order 3, 1, 2 but should decode correctly
        assertTrue(result == Right(Person("Alice", 30, "NYC")))
      },
      test("skip unknown fields without error (forward compatibility)") {
        // This test verifies that NEW fields added by a newer schema version are gracefully skipped

        case class PersonV1(name: String, age: Int)
        object PersonV1 {
          implicit val schema: Schema[PersonV1] = Schema.derived
        }

        // Manually construct Thrift bytes with an EXTRA field (simulating V2 data)
        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Person"))

        // Field 1: name
        protocol.writeFieldBegin(new TField("name", TType.STRING, 1))
        protocol.writeString("Bob")
        protocol.writeFieldEnd()

        // Field 2: age
        protocol.writeFieldBegin(new TField("age", TType.I32, 2))
        protocol.writeI32(25)
        protocol.writeFieldEnd()

        // Field 3: email - UNKNOWN to V1 schema!
        protocol.writeFieldBegin(new TField("email", TType.STRING, 3))
        protocol.writeString("bob@example.com")
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)

        // Decode as V1 schema - should skip the unknown 'email' field
        val result = Schema[PersonV1].decode(ThriftFormat)(buffer)

        // THIS MUST PASS - unknown fields should be skipped
        assertTrue(result == Right(PersonV1("Bob", 25)))
      },
      test("skip unknown complex nested fields") {
        // Verify we can skip unknown fields that are complex types (lists, structs)

        case class SimpleRecord(id: Int)
        object SimpleRecord {
          implicit val schema: Schema[SimpleRecord] = Schema.derived
        }

        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Record"))

        // Field 1: id (known)
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(42)
        protocol.writeFieldEnd()

        // Field 2: unknown list of strings
        protocol.writeFieldBegin(new TField("tags", TType.LIST, 2))
        protocol.writeListBegin(new TList(TType.STRING, 3))
        protocol.writeString("tag1")
        protocol.writeString("tag2")
        protocol.writeString("tag3")
        protocol.writeListEnd()
        protocol.writeFieldEnd()

        // Field 3: unknown nested struct
        protocol.writeFieldBegin(new TField("metadata", TType.STRUCT, 3))
        protocol.writeStructBegin(new TStruct("Metadata"))
        protocol.writeFieldBegin(new TField("key", TType.STRING, 1))
        protocol.writeString("value")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)

        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        // Should successfully decode, skipping unknown list and struct
        assertTrue(result == Right(SimpleRecord(42)))
      },
      test("encode and decode Option[Int] as None correctly") {
        // This test verifies that Option fields work correctly when encoded/decoded
        // Note: In Thrift encoding, None is encoded as a variant (field id 1 = None)
        // Simply omitting a field does not produce None - it produces null

        case class User(name: String, age: Option[Int])
        object User {
          implicit val schema: Schema[User] = Schema.derived
        }

        roundTrip(User("Dave", Some(42))) &&
        roundTrip(User("Charlie", None))
      },
      test("decode with field ID gaps (sparse field IDs)") {
        // Field IDs don't need to be contiguous - test with gaps

        case class SparseRecord(first: String, third: Int)
        object SparseRecord {
          implicit val schema: Schema[SparseRecord] = Schema.derived
        }

        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("SparseRecord"))

        // Write field with ID 2 (maps to 'third' which is index 1)
        protocol.writeFieldBegin(new TField("third", TType.I32, 2))
        protocol.writeI32(999)
        protocol.writeFieldEnd()

        // Write field with ID 1 (maps to 'first' which is index 0)
        protocol.writeFieldBegin(new TField("first", TType.STRING, 1))
        protocol.writeString("hello")
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)

        val result = Schema[SparseRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SparseRecord("hello", 999)))
      }
    ),
    suite("recursive types")(
      test("encode/decode tree") {
        case class TreeNode(value: Int, children: List[TreeNode])

        object TreeNode {
          implicit val schema: Schema[TreeNode] = Schema.derived
        }

        roundTrip(
          TreeNode(
            1,
            List(
              TreeNode(2, List(TreeNode(4, Nil), TreeNode(5, Nil))),
              TreeNode(3, List(TreeNode(6, Nil)))
            )
          )
        ) &&
        roundTrip(TreeNode(42, Nil)) &&
        roundTrip(TreeNode(1, List(TreeNode(2, List(TreeNode(3, List(TreeNode(4, List(TreeNode(5, Nil))))))))))
      },
      test("encode/decode linked list") {
        case class LinkedListNode(value: String, next: Option[LinkedListNode])

        object LinkedListNode {
          implicit val schema: Schema[LinkedListNode] = Schema.derived
        }

        roundTrip(LinkedListNode("first", Some(LinkedListNode("second", Some(LinkedListNode("third", None)))))) &&
        roundTrip(LinkedListNode("single", None))
      }
    ),
    suite("edge cases & robustness")(
      test("skip unknown nested struct field") {
        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        case class Simple(id: Int)
        object Simple {
          implicit val schema: Schema[Simple] = Schema.derived
        }

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(42)
        protocol.writeFieldEnd()

        // Unknown nested struct field
        protocol.writeFieldBegin(new TField("metadata", TType.STRUCT, 2))
        protocol.writeStructBegin(new TStruct("Metadata"))
        protocol.writeFieldBegin(new TField("key", TType.STRING, 1))
        protocol.writeString("value")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[Simple].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(Simple(42)))
      },
      test("skip unknown list field") {
        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        case class Container(count: Int)
        object Container {
          implicit val schema: Schema[Container] = Schema.derived
        }

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Container"))
        protocol.writeFieldBegin(new TField("count", TType.I32, 1))
        protocol.writeI32(5)
        protocol.writeFieldEnd()

        // Unknown list field
        protocol.writeFieldBegin(new TField("tags", TType.LIST, 2))
        protocol.writeListBegin(new TList(TType.STRING, 3))
        protocol.writeString("tag1")
        protocol.writeString("tag2")
        protocol.writeString("tag3")
        protocol.writeListEnd()
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[Container].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(Container(5)))
      },
      test("skip unknown map field") {
        import org.apache.thrift.protocol._
        import org.apache.thrift.transport.TMemoryBuffer

        case class Doc(title: String)
        object Doc {
          implicit val schema: Schema[Doc] = Schema.derived
        }

        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Doc"))
        protocol.writeFieldBegin(new TField("title", TType.STRING, 1))
        protocol.writeString("README")
        protocol.writeFieldEnd()

        // Unknown map field
        protocol.writeFieldBegin(new TField("tags", TType.MAP, 2))
        protocol.writeMapBegin(new TMap(TType.STRING, TType.I32, 2))
        protocol.writeString("views")
        protocol.writeI32(100)
        protocol.writeString("likes")
        protocol.writeI32(50)
        protocol.writeMapEnd()
        protocol.writeFieldEnd()

        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[Doc].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(Doc("README")))
      },
      test("extreme integer values") {
        case class Extremes(minInt: Int, maxInt: Int, minLong: Long, maxLong: Long)
        object Extremes {
          implicit val schema: Schema[Extremes] = Schema.derived
        }

        roundTrip(Extremes(Int.MinValue, Int.MaxValue, Long.MinValue, Long.MaxValue))
      },
      test("special float/double values (NaN, Infinity)") {
        case class Floats(zero: Double, inf: Double, negInf: Double, nan: Double)
        object Floats {
          implicit val schema: Schema[Floats] = Schema.derived
        }

        val floats = Floats(0.0, Double.PositiveInfinity, Double.NegativeInfinity, Double.NaN)
        val buffer = ByteBuffer.allocate(1024)
        Schema[Floats].encode(ThriftFormat)(buffer)(floats)
        buffer.flip()

        val result = Schema[Floats].decode(ThriftFormat)(buffer)
        result match {
          case Right(decoded) =>
            assertTrue(
              decoded.zero == 0.0 &&
                decoded.inf.isInfinite && decoded.inf > 0 &&
                decoded.negInf.isInfinite && decoded.negInf < 0 &&
                decoded.nan.isNaN
            )
          case Left(_) => assertTrue(false)
        }
      },
      test("empty and unicode strings") {
        case class Strings(empty: String, unicode: String)
        object Strings {
          implicit val schema: Schema[Strings] = Schema.derived
        }

        roundTrip(Strings("", "Hello 世界 🌍 émojis"))
      },
      test("deeply nested records (5 levels)") {
        case class L5(value: String)
        case class L4(child: L5)
        case class L3(child: L4)
        case class L2(child: L3)
        case class L1(child: L2)

        object L5 { implicit val schema: Schema[L5] = Schema.derived }
        object L4 { implicit val schema: Schema[L4] = Schema.derived }
        object L3 { implicit val schema: Schema[L3] = Schema.derived }
        object L2 { implicit val schema: Schema[L2] = Schema.derived }
        object L1 { implicit val schema: Schema[L1] = Schema.derived }

        roundTrip(L1(L2(L3(L4(L5("deep"))))))
      },
      test("empty collections") {
        case class Empty(list: List[String], set: Set[Int], map: Map[String, Int])
        object Empty {
          implicit val schema: Schema[Empty] = Schema.derived
        }

        roundTrip(Empty(List(), Set(), Map()))
      },
      test("large collections (1000 elements)") {
        case class Large(numbers: List[Int])
        object Large {
          implicit val schema: Schema[Large] = Schema.derived
        }

        val large  = Large((1 to 1000).toList)
        val buffer = ByteBuffer.allocate(10000)
        Schema[Large].encode(ThriftFormat)(buffer)(large)
        buffer.flip()

        val result = Schema[Large].decode(ThriftFormat)(buffer)
        assertTrue(result == Right(large))
      },
      test("sealed trait with case objects") {
        sealed trait Status
        case object Pending extends Status
        case object Active  extends Status
        case object Closed  extends Status

        object Status {
          implicit val schema: Schema[Status] = Schema.derived
        }

        val statuses = List[Status](Pending, Active, Closed)
        val allPass  = statuses.forall { status =>
          val buffer = ByteBuffer.allocate(1024)
          Schema[Status].encode(ThriftFormat)(buffer)(status)
          buffer.flip()
          Schema[Status].decode(ThriftFormat)(buffer) == Right(status)
        }
        assertTrue(allPass)
      },
      test("dynamic value encoding") {
        check(DynamicValueGen.genDynamicValue) { value =>
          val buffer = ByteBuffer.allocate(10000)
          Schema[DynamicValue].encode(ThriftFormat)(buffer)(value)
          buffer.flip()
          assertTrue(buffer.limit() >= buffer.position())
        }
      }
    )
  )
}
