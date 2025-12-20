package zio.blocks.schema.binding

import zio.test._

object StructuralBindingSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("StructuralBindingSpec")(
    suite("StructuralConstructor")(
      test("constructs structural type with object fields") {
        val fields = IndexedSeq(
          StructuralFieldInfo("name", RegisterOffset(objects = 0), StructuralFieldInfo.Object),
          StructuralFieldInfo("city", RegisterOffset(objects = 1), StructuralFieldInfo.Object)
        )
        val totalRegisters = RegisterOffset(objects = 2)
        val constructor    = new StructuralConstructor[StructuralValue](fields, totalRegisters)

        val registers = Registers(totalRegisters)
        registers.setObject(0, 0, "Alice")
        registers.setObject(0, 1, "NYC")

        val result = constructor.construct(registers, 0)
        assertTrue(
          result.selectDynamic("name") == "Alice",
          result.selectDynamic("city") == "NYC"
        )
      },
      test("constructs structural type with int field") {
        val fields = IndexedSeq(
          StructuralFieldInfo("age", RegisterOffset(ints = 0), StructuralFieldInfo.Int)
        )
        val totalRegisters = RegisterOffset(ints = 1)
        val constructor    = new StructuralConstructor[StructuralValue](fields, totalRegisters)

        val registers = Registers(totalRegisters)
        registers.setInt(0, 0, 42)

        val result = constructor.construct(registers, 0)
        assertTrue(result.selectDynamic("age") == 42)
      },
      test("constructs structural type with mixed primitive and object fields") {
        // Layout: int at bytes 0-3, boolean at byte 4, object at index 0
        val fields = IndexedSeq(
          StructuralFieldInfo("name", RegisterOffset(objects = 0), StructuralFieldInfo.Object),
          StructuralFieldInfo("age", RegisterOffset(ints = 0), StructuralFieldInfo.Int), // byte offset 0
          StructuralFieldInfo(
            "active",
            RegisterOffset(ints = 1),
            StructuralFieldInfo.Boolean
          ) // byte offset 4 (after 1 int)
        )
        val totalRegisters = RegisterOffset(objects = 1, ints = 1, booleans = 1)
        val constructor    = new StructuralConstructor[StructuralValue](fields, totalRegisters)

        val registers = Registers(totalRegisters)
        registers.setObject(0, 0, "Bob")
        registers.setInt(0, 0, 30)
        registers.setBoolean(0, 4, true) // byte offset 4

        val result = constructor.construct(registers, 0)
        assertTrue(
          result.selectDynamic("name") == "Bob",
          result.selectDynamic("age") == 30,
          result.selectDynamic("active") == true
        )
      },
      test("constructs structural type with all primitive types") {
        // Layout with cumulative non-overlapping offsets:
        // bool: byte 0, byte: byte 1, short: bytes 2-3, char: bytes 4-5,
        // int: bytes 6-9, float: bytes 10-13, long: bytes 14-21, double: bytes 22-29
        val fields = IndexedSeq(
          StructuralFieldInfo("boolField", RegisterOffset.Zero, StructuralFieldInfo.Boolean),       // byte 0
          StructuralFieldInfo("byteField", RegisterOffset(booleans = 1), StructuralFieldInfo.Byte), // byte 1
          StructuralFieldInfo(
            "shortField",
            RegisterOffset(booleans = 1, bytes = 1),
            StructuralFieldInfo.Short
          ), // byte 2
          StructuralFieldInfo(
            "charField",
            RegisterOffset(booleans = 1, bytes = 1, shorts = 1),
            StructuralFieldInfo.Char
          ), // byte 4
          StructuralFieldInfo(
            "intField",
            RegisterOffset(booleans = 1, bytes = 1, shorts = 1, chars = 1),
            StructuralFieldInfo.Int
          ), // byte 6
          StructuralFieldInfo(
            "floatField",
            RegisterOffset(booleans = 1, bytes = 1, shorts = 1, chars = 1, ints = 1),
            StructuralFieldInfo.Float
          ), // byte 10
          StructuralFieldInfo(
            "longField",
            RegisterOffset(booleans = 1, bytes = 1, shorts = 1, chars = 1, ints = 1, floats = 1),
            StructuralFieldInfo.Long
          ), // byte 14
          StructuralFieldInfo(
            "doubleField",
            RegisterOffset(booleans = 1, bytes = 1, shorts = 1, chars = 1, ints = 1, floats = 1, longs = 1),
            StructuralFieldInfo.Double
          ) // byte 22
        )
        val totalRegisters =
          RegisterOffset(booleans = 1, bytes = 1, shorts = 1, chars = 1, ints = 1, floats = 1, longs = 1, doubles = 1)
        val constructor = new StructuralConstructor[StructuralValue](fields, totalRegisters)

        val registers = Registers(totalRegisters)
        registers.setBoolean(0, 0, true)
        registers.setByte(0, 1, 42.toByte)
        registers.setShort(0, 2, 1000.toShort)
        registers.setChar(0, 4, 'X')
        registers.setInt(0, 6, 123456)
        registers.setFloat(0, 10, 3.14f)
        registers.setLong(0, 14, 9876543210L)
        registers.setDouble(0, 22, 2.71828)

        val result = constructor.construct(registers, 0)
        assertTrue(
          result.selectDynamic("boolField") == true,
          result.selectDynamic("byteField") == 42.toByte,
          result.selectDynamic("shortField") == 1000.toShort,
          result.selectDynamic("charField") == 'X',
          result.selectDynamic("intField") == 123456,
          result.selectDynamic("floatField") == 3.14f,
          result.selectDynamic("longField") == 9876543210L,
          result.selectDynamic("doubleField") == 2.71828
        )
      }
    ),
    suite("StructuralDeconstructor")(
      test("deconstructs structural type with object fields") {
        val fields = IndexedSeq(
          StructuralFieldInfo("name", RegisterOffset(objects = 0), StructuralFieldInfo.Object),
          StructuralFieldInfo("city", RegisterOffset(objects = 1), StructuralFieldInfo.Object)
        )
        val totalRegisters = RegisterOffset(objects = 2)
        val deconstructor  = new StructuralDeconstructor[StructuralValue](fields, totalRegisters)

        val input     = new StructuralValue(Map("name" -> "Alice", "city" -> "NYC"))
        val registers = Registers(totalRegisters)
        deconstructor.deconstruct(registers, 0, input)

        assertTrue(
          registers.getObject(0, 0) == "Alice",
          registers.getObject(0, 1) == "NYC"
        )
      },
      test("deconstructs structural type with int field") {
        val fields = IndexedSeq(
          StructuralFieldInfo("age", RegisterOffset(ints = 0), StructuralFieldInfo.Int)
        )
        val totalRegisters = RegisterOffset(ints = 1)
        val deconstructor  = new StructuralDeconstructor[StructuralValue](fields, totalRegisters)

        val input     = new StructuralValue(Map("age" -> 42))
        val registers = Registers(totalRegisters)
        deconstructor.deconstruct(registers, 0, input)

        assertTrue(registers.getInt(0, 0) == 42)
      },
      test("deconstructs structural type with mixed fields") {
        // Layout: int at bytes 0-3, boolean at byte 4, object at index 0
        val fields = IndexedSeq(
          StructuralFieldInfo("name", RegisterOffset(objects = 0), StructuralFieldInfo.Object),
          StructuralFieldInfo("age", RegisterOffset(ints = 0), StructuralFieldInfo.Int), // byte offset 0
          StructuralFieldInfo(
            "active",
            RegisterOffset(ints = 1),
            StructuralFieldInfo.Boolean
          ) // byte offset 4 (after 1 int)
        )
        val totalRegisters = RegisterOffset(objects = 1, ints = 1, booleans = 1)
        val deconstructor  = new StructuralDeconstructor[StructuralValue](fields, totalRegisters)

        val input     = new StructuralValue(Map("name" -> "Bob", "age" -> 30, "active" -> true))
        val registers = Registers(totalRegisters)
        deconstructor.deconstruct(registers, 0, input)

        assertTrue(
          registers.getObject(0, 0) == "Bob",
          registers.getInt(0, 0) == 30,
          registers.getBoolean(0, 4) == true // byte offset 4
        )
      }
    ),
    suite("roundtrip")(
      test("construct then deconstruct preserves values") {
        val fields = IndexedSeq(
          StructuralFieldInfo("name", RegisterOffset(objects = 0), StructuralFieldInfo.Object),
          StructuralFieldInfo("age", RegisterOffset(ints = 0), StructuralFieldInfo.Int)
        )
        val totalRegisters = RegisterOffset(objects = 1, ints = 1)
        val constructor    = new StructuralConstructor[StructuralValue](fields, totalRegisters)
        val deconstructor  = new StructuralDeconstructor[StructuralValue](fields, totalRegisters)

        // Setup initial values
        val inputRegisters = Registers(totalRegisters)
        inputRegisters.setObject(0, 0, "Charlie")
        inputRegisters.setInt(0, 0, 25)

        // Construct
        val structural = constructor.construct(inputRegisters, 0)

        // Deconstruct
        val outputRegisters = Registers(totalRegisters)
        deconstructor.deconstruct(outputRegisters, 0, structural)

        assertTrue(
          outputRegisters.getObject(0, 0) == "Charlie",
          outputRegisters.getInt(0, 0) == 25
        )
      }
    ),
    // Accessing a non-existent field should return null
    suite("StructuralValue")(
      test("provides selectDynamic access to fields") {
        val value = new StructuralValue(Map("x" -> 10, "y" -> 20, "label" -> "point"))

        assertTrue(
          value.selectDynamic("x") == 10,
          value.selectDynamic("y") == 20,
          value.selectDynamic("label") == "point"
        )
      },
      test("missing field returns null") {
        val value = new StructuralValue(Map("x" -> 10))
        assertTrue(value.selectDynamic("nonexistent") == null)
      },

      test("extra fields in source are preserved but not accessed") {
        val value = new StructuralValue(Map("x" -> 10, "y" -> 20, "extra" -> "ignored"))

        // We can access expected fields
        assertTrue(value.selectDynamic("x") == 10) &&
        assertTrue(value.selectDynamic("y") == 20) &&
        // Extra fields are still accessible if needed
        assertTrue(value.selectDynamic("extra") == "ignored")
      }
    )
  )
}
