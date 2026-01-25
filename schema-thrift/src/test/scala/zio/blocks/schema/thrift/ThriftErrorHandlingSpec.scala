package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer
import org.apache.thrift.protocol._
import org.apache.thrift.transport.TMemoryBuffer

/**
 * Tests for error handling and edge cases in ThriftFormat.
 */
object ThriftErrorHandlingSpec extends SchemaBaseSpec {

  case class SimpleRecord(id: Int, name: String)

  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class RecordWithOption(value: Option[Int])

  object RecordWithOption {
    implicit val schema: Schema[RecordWithOption] = Schema.derived
  }

  sealed trait SimpleVariant

  object SimpleVariant {
    case class IntCase(value: Int)       extends SimpleVariant
    case class StringCase(value: String) extends SimpleVariant

    implicit val schema: Schema[SimpleVariant] = Schema.derived
  }

  case class DeepNested(
    level1: Level1
  )

  object DeepNested {
    implicit val schema: Schema[DeepNested] = Schema.derived
  }

  case class Level1(level2: Level2)

  object Level1 {
    implicit val schema: Schema[Level1] = Schema.derived
  }

  case class Level2(level3: Level3)

  object Level2 {
    implicit val schema: Schema[Level2] = Schema.derived
  }

  case class Level3(value: Int)

  object Level3 {
    implicit val schema: Schema[Level3] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftErrorHandlingSpec")(
    suite("truncated input handling")(
      test("decode with empty input returns error") {
        val buffer = ByteBuffer.allocate(0)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)
        assertTrue(result.isLeft)
      },
      test("decode with only STOP field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)
        protocol.writeFieldStop()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        // This should succeed - empty struct with STOP is valid but fields are null/default
        val result = Schema[RecordWithOption].decode(ThriftFormat)(buffer)
        // RecordWithOption has an optional field, so empty is valid
        assertTrue(result.isRight)
      }
    ),
    suite("skip unknown field types")(
      test("skip unknown BOOL field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(42)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("test")
        protocol.writeFieldEnd()
        // Unknown bool field
        protocol.writeFieldBegin(new TField("unknown", TType.BOOL, 99))
        protocol.writeBool(true)
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(42, "test")))
      },
      test("skip unknown I16 field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(100)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("unknown", TType.I16, 50))
        protocol.writeI16(12345)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("hello")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(100, "hello")))
      },
      test("skip unknown I64 field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(200)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("unknown", TType.I64, 77))
        protocol.writeI64(Long.MaxValue)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("world")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(200, "world")))
      },
      test("skip unknown DOUBLE field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(300)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("unknown", TType.DOUBLE, 88))
        protocol.writeDouble(3.14159)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("pi")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(300, "pi")))
      },
      test("skip unknown BYTE field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("unknown", TType.BYTE, 66))
        protocol.writeByte(127)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(400)
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("byte-skip")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(400, "byte-skip")))
      }
    ),
    suite("skip complex unknown fields")(
      test("skip unknown set field") {
        val transport = new TMemoryBuffer(1024)
        val protocol  = new TBinaryProtocol(transport)

        protocol.writeStructBegin(new TStruct("Simple"))
        protocol.writeFieldBegin(new TField("id", TType.I32, 1))
        protocol.writeI32(500)
        protocol.writeFieldEnd()
        // Unknown set field
        protocol.writeFieldBegin(new TField("unknownSet", TType.SET, 33))
        protocol.writeSetBegin(new TSet(TType.STRING, 2))
        protocol.writeString("item1")
        protocol.writeString("item2")
        protocol.writeSetEnd()
        protocol.writeFieldEnd()
        protocol.writeFieldBegin(new TField("name", TType.STRING, 2))
        protocol.writeString("set-skip")
        protocol.writeFieldEnd()
        protocol.writeFieldStop()
        protocol.writeStructEnd()

        val bytes  = transport.getArray().take(transport.length())
        val buffer = ByteBuffer.wrap(bytes)
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)

        assertTrue(result == Right(SimpleRecord(500, "set-skip")))
      }
    ),
    suite("deeply nested structures")(
      test("encode/decode deeply nested record") {
        val deep = DeepNested(Level1(Level2(Level3(42))))
        roundTrip(deep)
      }
    ),
    suite("heap vs direct ByteBuffer")(
      test("encode/decode with heap ByteBuffer") {
        val value  = SimpleRecord(111, "heap")
        val buffer = ByteBuffer.allocate(1024) // heap buffer
        Schema[SimpleRecord].encode(ThriftFormat)(buffer)(value)
        buffer.flip()
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)
        assertTrue(result == Right(value))
      },
      test("encode/decode with direct ByteBuffer") {
        val value  = SimpleRecord(222, "direct")
        val buffer = ByteBuffer.allocateDirect(1024) // direct buffer
        Schema[SimpleRecord].encode(ThriftFormat)(buffer)(value)
        buffer.flip()
        val result = Schema[SimpleRecord].decode(ThriftFormat)(buffer)
        assertTrue(result == Right(value))
      }
    )
  )
}
