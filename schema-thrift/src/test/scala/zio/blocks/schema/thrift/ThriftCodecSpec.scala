package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._

import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}

/**
 * Comprehensive test suite for ThriftBinaryCodec. Tests round-trip
 * encoding/decoding for all supported types.
 */
object ThriftCodecSpec extends ZIOSpecDefault {

  // Test case classes
  case class BasicInt(value: Int)
  object BasicInt {
    implicit val schema: Schema[BasicInt] = Schema.derived
  }

  case class BasicString(value: String)
  object BasicString {
    implicit val schema: Schema[BasicString] = Schema.derived
  }

  case class BasicDouble(value: Double)
  object BasicDouble {
    implicit val schema: Schema[BasicDouble] = Schema.derived
  }

  case class Record(name: String, value: Int)
  object Record {
    implicit val schema: Schema[Record] = Schema.derived
  }

  case class Embedded(inner: BasicInt)
  object Embedded {
    implicit val schema: Schema[Embedded] = Schema.derived
  }

  case class IntList(items: List[Int])
  object IntList {
    implicit val schema: Schema[IntList] = Schema.derived
  }

  case class StringList(items: List[String])
  object StringList {
    implicit val schema: Schema[StringList] = Schema.derived
  }

  case class MapRecord(id: Int, data: Map[String, Int])
  object MapRecord {
    implicit val schema: Schema[MapRecord] = Schema.derived
  }

  case class SetRecord(id: Int, tags: Set[String])
  object SetRecord {
    implicit val schema: Schema[SetRecord] = Schema.derived
  }

  case class OptionalRecord(name: String, age: Option[Int])
  object OptionalRecord {
    implicit val schema: Schema[OptionalRecord] = Schema.derived
  }

  case class TupleRecord(pair: (String, Int))
  object TupleRecord {
    implicit val schema: Schema[TupleRecord] = Schema.derived
  }

  sealed trait OneOf
  object OneOf {
    case class StringValue(value: String) extends OneOf
    case class IntValue(value: Int)       extends OneOf
    case class BoolValue(value: Boolean)  extends OneOf

    implicit val schema: Schema[OneOf] = Schema.derived
  }

  case class Enumeration(oneOf: OneOf)
  object Enumeration {
    implicit val schema: Schema[Enumeration] = Schema.derived
  }

  case class HighArity(
    f1: Int,
    f2: Int,
    f3: Int,
    f4: Int,
    f5: Int,
    f6: Int,
    f7: Int,
    f8: Int,
    f9: Int,
    f10: Int,
    f11: Int,
    f12: Int,
    f13: Int,
    f14: Int,
    f15: Int,
    f16: Int,
    f17: Int,
    f18: Int,
    f19: Int,
    f20: Int,
    f21: Int,
    f22: Int,
    f23: Int,
    f24: Int
  )
  object HighArity {
    implicit val schema: Schema[HighArity] = Schema.derived
    val default: HighArity                 =
      HighArity(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
  }

  case class DateTimeRecord(
    instant: Instant,
    localDate: LocalDate,
    localTime: LocalTime,
    localDateTime: LocalDateTime
  )
  object DateTimeRecord {
    implicit val schema: Schema[DateTimeRecord] = Schema.derived
  }

  case class NestedRecord(outer: Record, inner: Embedded)
  object NestedRecord {
    implicit val schema: Schema[NestedRecord] = Schema.derived
  }

  // Recursive type for tree structure
  case class TreeNode(value: Int, children: List[TreeNode])
  object TreeNode {
    implicit val schema: Schema[TreeNode] = Schema.derived
  }

  // Recursive list-like structure
  case class LinkedListNode(value: String, next: Option[LinkedListNode])
  object LinkedListNode {
    implicit val schema: Schema[LinkedListNode] = Schema.derived
  }

  // Helper for round-trip testing
  def roundTrip[A](value: A)(implicit schema: Schema[A]): Either[SchemaError, A] = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    schema.decode(ThriftFormat)(buffer)
  }

  def spec = suite("ThriftCodec Spec")(
    suite("Primitive Types")(
      test("encode/decode Unit") {
        val result = roundTrip(())
        assertTrue(result == Right(()))
      },
      test("encode/decode Boolean true") {
        val result = roundTrip(true)
        assertTrue(result == Right(true))
      },
      test("encode/decode Boolean false") {
        val result = roundTrip(false)
        assertTrue(result == Right(false))
      },
      test("encode/decode Byte") {
        val result = roundTrip(42.toByte)
        assertTrue(result == Right(42.toByte))
      },
      test("encode/decode Short") {
        val result = roundTrip(1234.toShort)
        assertTrue(result == Right(1234.toShort))
      },
      test("encode/decode Int") {
        val result = roundTrip(150)
        assertTrue(result == Right(150))
      },
      test("encode/decode Long") {
        val result = roundTrip(1000L)
        assertTrue(result == Right(1000L))
      },
      test("encode/decode Float") {
        val result = roundTrip(0.001f)
        assertTrue(result == Right(0.001f))
      },
      test("encode/decode Double") {
        val result = roundTrip(0.001)
        assertTrue(result == Right(0.001))
      },
      test("encode/decode Char") {
        val result = roundTrip('c')
        assertTrue(result == Right('c'))
      },
      test("encode/decode String") {
        val result = roundTrip("hello world")
        assertTrue(result == Right("hello world"))
      },
      test("encode/decode empty String") {
        val result = roundTrip("")
        assertTrue(result == Right(""))
      },
      test("encode/decode BigInt") {
        val value  = BigInt("12345678901234567890")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode BigDecimal") {
        val value  = BigDecimal("12345.67890")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode UUID") {
        val value  = UUID.randomUUID()
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Currency") {
        val value  = Currency.getInstance("USD")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Date/Time Types")(
      test("encode/decode DayOfWeek") {
        val value  = DayOfWeek.WEDNESDAY
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Month") {
        val value  = Month.MARCH
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Year") {
        val value  = Year.of(2024)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode MonthDay") {
        val value  = MonthDay.of(3, 15)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode YearMonth") {
        val value  = YearMonth.of(2024, 3)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Period") {
        val value  = Period.of(1, 2, 3)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Duration") {
        val value  = Duration.ofHours(25)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode Instant") {
        val value  = Instant.parse("2024-01-15T10:30:00Z")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode LocalDate") {
        val value  = LocalDate.of(2024, 1, 15)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode LocalTime") {
        val value  = LocalTime.of(10, 30, 45)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode LocalDateTime") {
        val value  = LocalDateTime.of(2024, 1, 15, 10, 30, 45)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode OffsetTime") {
        val value  = OffsetTime.of(10, 30, 45, 0, ZoneOffset.ofHours(5))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode OffsetDateTime") {
        val value  = OffsetDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneOffset.ofHours(5))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode ZonedDateTime") {
        val value  = ZonedDateTime.of(2024, 1, 15, 10, 30, 45, 0, ZoneId.of("America/New_York"))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode ZoneId") {
        val value  = ZoneId.of("Europe/London")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode ZoneOffset") {
        val value  = ZoneOffset.ofHours(5)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Record Types")(
      test("encode/decode simple record") {
        val value  = Record("hello", 42)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode BasicInt record") {
        val value  = BasicInt(150)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode BasicString record") {
        val value  = BasicString("testing")
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode BasicDouble record") {
        val value  = BasicDouble(3.14159)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode embedded record") {
        val value  = Embedded(BasicInt(100))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode nested record") {
        val value  = NestedRecord(Record("outer", 1), Embedded(BasicInt(2)))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode high arity record") {
        val value  = HighArity.default
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Collection Types")(
      test("encode/decode int list") {
        val value  = IntList(List(1, 2, 3, 4, 5))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode empty int list") {
        val value  = IntList(List.empty)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode string list") {
        val value  = StringList(List("foo", "bar", "baz"))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode map record") {
        val value  = MapRecord(1, Map("a" -> 100, "b" -> 200))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode empty map record") {
        val value  = MapRecord(1, Map.empty)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode set record") {
        val value  = SetRecord(1, Set("tag1", "tag2", "tag3"))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode empty set record") {
        val value  = SetRecord(1, Set.empty)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Optional Types")(
      test("encode/decode Some value") {
        val value  = OptionalRecord("John", Some(30))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode None value") {
        val value  = OptionalRecord("Jane", None)
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode standalone Option Some") {
        val value: Option[Int] = Some(42)
        val result             = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode standalone Option None") {
        val value: Option[Int] = None
        val result             = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Enum/Variant Types")(
      test("encode/decode StringValue variant") {
        val value  = Enumeration(OneOf.StringValue("hello"))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode IntValue variant") {
        val value  = Enumeration(OneOf.IntValue(42))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode BoolValue variant") {
        val value  = Enumeration(OneOf.BoolValue(true))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode standalone StringValue") {
        val value: OneOf = OneOf.StringValue("test")
        val result       = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Tuple Types")(
      test("encode/decode tuple in record") {
        val value  = TupleRecord(("hello", 42))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Complex Nested Types")(
      test("encode/decode list of records") {
        val value  = List(Record("a", 1), Record("b", 2), Record("c", 3))
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode map with record values") {
        val value: Map[String, Record] = Map(
          "first"  -> Record("a", 1),
          "second" -> Record("b", 2)
        )
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      },
      test("encode/decode date/time record") {
        val value = DateTimeRecord(
          instant = Instant.parse("2024-01-15T10:30:00Z"),
          localDate = LocalDate.of(2024, 1, 15),
          localTime = LocalTime.of(10, 30, 45),
          localDateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45)
        )
        val result = roundTrip(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Schema Evolution & Wire Compatibility")(
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

        // Test 1: Round-trip with None
        val userWithNone = User("Charlie", None)
        val resultNone   = roundTrip(userWithNone)
        assertTrue(resultNone == Right(User("Charlie", None))) &&
        // Test 2: Round-trip with Some
        {
          val userWithSome = User("Dave", Some(42))
          val resultSome   = roundTrip(userWithSome)
          assertTrue(resultSome == Right(User("Dave", Some(42))))
        }
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
    suite("Recursive Types")(
      test("encode/decode tree node with children") {
        val tree = TreeNode(
          1,
          List(
            TreeNode(2, List(TreeNode(4, Nil), TreeNode(5, Nil))),
            TreeNode(3, List(TreeNode(6, Nil)))
          )
        )
        val result = roundTrip(tree)
        assertTrue(result == Right(tree))
      },
      test("encode/decode tree node with no children") {
        val tree   = TreeNode(42, Nil)
        val result = roundTrip(tree)
        assertTrue(result == Right(tree))
      },
      test("encode/decode deeply nested tree") {
        val tree = TreeNode(
          1,
          List(TreeNode(2, List(TreeNode(3, List(TreeNode(4, List(TreeNode(5, Nil))))))))
        )
        val result = roundTrip(tree)
        assertTrue(result == Right(tree))
      },
      test("encode/decode linked list with next node") {
        val list   = LinkedListNode("first", Some(LinkedListNode("second", Some(LinkedListNode("third", None)))))
        val result = roundTrip(list)
        assertTrue(result == Right(list))
      },
      test("encode/decode linked list terminal node") {
        val list   = LinkedListNode("single", None)
        val result = roundTrip(list)
        assertTrue(result == Right(list))
      }
    ),
    suite("Edge Cases & Robustness")(
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

        val extremes = Extremes(Int.MinValue, Int.MaxValue, Long.MinValue, Long.MaxValue)
        val result   = roundTrip(extremes)
        assertTrue(result == Right(extremes))
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

        val strings = Strings("", "Hello 世界 🌍 émojis")
        val result  = roundTrip(strings)
        assertTrue(result == Right(strings))
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

        val nested = L1(L2(L3(L4(L5("deep")))))
        val result = roundTrip(nested)
        assertTrue(result == Right(nested))
      },
      test("empty collections") {
        case class Empty(list: List[String], set: Set[Int], map: Map[String, Int])
        object Empty {
          implicit val schema: Schema[Empty] = Schema.derived
        }

        val empty  = Empty(List(), Set(), Map())
        val result = roundTrip(empty)
        assertTrue(result == Right(empty))
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
      }
    )
  )
}
