package zio.blocks.schema.binding

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.test._

object BindingOfVersionSpecificScalaNextSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("BindingOfVersionSpecificScalaNextSpec")(
    suite("Reflect.Record")(
      test("derives binding for small named tuples") {
        type NamedTuple3 = (s: String, i: Int, b: Boolean)
        val binding   = Binding.of[NamedTuple3].asInstanceOf[Binding.Record[NamedTuple3]]
        val original  = (s = "hello", i = 42, b = true)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("derives binding for big named tuples") {
        type NamedTupleXXL = (
          bl: Boolean,
          b: Byte,
          ch: Char,
          sh: Short,
          f: Float,
          i: Int,
          d: Double,
          l: Long,
          s: String,
          i10: Int,
          i11: Int,
          i12: Int,
          i13: Int,
          i14: Int,
          i15: Int,
          i16: Int,
          i17: Int,
          i18: Int,
          i19: Int,
          i20: Int,
          i21: Int,
          i22: Int,
          i23: Int,
          i24: Int
        )
        val binding  = Binding.of[NamedTupleXXL].asInstanceOf[Binding.Record[NamedTupleXXL]]
        val original = (
          bl = true,
          b = 2: Byte,
          ch = '3',
          sh = 4: Short,
          f = 5.0f,
          i = 6,
          d = 7.0,
          l = 8L,
          s = "9",
          i10 = 10,
          i11 = 11,
          i12 = 12,
          i13 = 13,
          i14 = 14,
          i15 = 15,
          i16 = 16,
          i17 = 17,
          i18 = 18,
          i19 = 19,
          i20 = 20,
          i21 = 21,
          i22 = 22,
          i23 = 23,
          i24 = 24
        )
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    )
  )
}
