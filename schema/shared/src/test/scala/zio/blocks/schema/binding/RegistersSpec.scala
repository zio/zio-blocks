package zio.blocks.schema.binding

import zio.test.Assertion._
import zio.test._

object RegistersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("RegistersSpec")(
    test("setRegisters") {
      val registers1 = Registers(RegisterOffset(bytes = 3, objects = 2))
      registers1.setByte(RegisterOffset.Zero, 0, 1: Byte)
      registers1.setByte(RegisterOffset.Zero, 1, 2: Byte)
      registers1.setByte(RegisterOffset.Zero, 2, 3: Byte)
      registers1.setObject(RegisterOffset.Zero, 0, "1")
      registers1.setObject(RegisterOffset.Zero, 1, "2")
      val registers2 = Registers(RegisterOffset(bytes = 1, objects = 1))
      registers2.setByte(RegisterOffset.Zero, 0, 4: Byte)
      registers2.setObject(RegisterOffset.Zero, 0, "3")
      registers1.setRegisters(RegisterOffset(bytes = 3, objects = 2), registers2)
      assert(registers1.getByte(RegisterOffset.Zero, 2))(equalTo(3: Byte)) &&
      assert(registers1.getByte(RegisterOffset.Zero, 3))(equalTo(4: Byte)) &&
      assert(registers1.getObject(RegisterOffset.Zero, 2))(equalTo("3"))
    },
    test("set and get booleans") {
      val value     = true
      val registers = Registers(RegisterOffset())
      registers.setBoolean(RegisterOffset.Zero, 0, value)
      assert(registers.getBoolean(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get bytes") {
      val value: Byte = 1
      val registers   = Registers(RegisterOffset())
      registers.setByte(RegisterOffset.Zero, 0, value)
      assert(registers.getByte(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get shorts") {
      val value: Short = 1
      val registers    = Registers(RegisterOffset())
      registers.setShort(RegisterOffset.Zero, 0, value)
      assert(registers.getShort(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get ints") {
      val value: Int = 1
      val registers  = Registers(RegisterOffset())
      registers.setInt(RegisterOffset.Zero, 0, value)
      assert(registers.getInt(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get longs") {
      val value: Long = 1L
      val registers   = Registers(RegisterOffset())
      registers.setLong(RegisterOffset.Zero, 0, value)
      assert(registers.getLong(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get floats") {
      val value: Float = 1.0f
      val registers    = Registers(RegisterOffset())
      registers.setFloat(RegisterOffset.Zero, 0, value)
      assert(registers.getFloat(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get doubles") {
      val value: Double = 1.0
      val registers     = Registers(RegisterOffset())
      registers.setDouble(RegisterOffset.Zero, 0, value)
      assert(registers.getDouble(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get chars") {
      val value: Char = '1'
      val registers   = Registers(RegisterOffset())
      registers.setChar(RegisterOffset.Zero, 0, value)
      assert(registers.getChar(RegisterOffset.Zero, 0))(equalTo(value))
    },
    test("set and get objects") {
      val value: AnyRef = "test"
      val registers     = Registers(RegisterOffset())
      registers.setObject(RegisterOffset.Zero, 0, value)
      assert(registers.getObject(RegisterOffset.Zero, 0))(equalTo(value))
    }
  )
}
