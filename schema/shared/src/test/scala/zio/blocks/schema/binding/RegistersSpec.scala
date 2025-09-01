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
    }
  )
}
