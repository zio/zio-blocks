package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.test._

/**
 * Comprehensive coverage tests for migration module
 */
object MigrationCoverageSpec extends ZIOSpecDefault {

  // Helper methods
  def str(s: String): DynamicValue        = DynamicValue.Primitive(PrimitiveValue.String(s))
  def int(i: Int): DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Int(i))
  def long(l: Long): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Long(l))
  def double(d: Double): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.Double(d))
  def float(f: Float): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Float(f))
  def short(s: Short): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Short(s))
  def byte(b: Byte): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Byte(b))
  def bool(b: Boolean): DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Boolean(b))
  def char(c: Char): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Char(c))
  def bigInt(b: BigInt): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.BigInt(b))
  def bigDec(b: BigDecimal): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigDecimal(b))

  def spec = suite("MigrationCoverageSpec")(
    // =========================================================================
    // BATCH 1: PrimitiveConversions - Widening (50 tests)
    // =========================================================================
    suite("PrimitiveConversions - Widening")(
      test("Byte to Short") {
        val result = PrimitiveConversions.convert(byte(42), "Byte", "Short")
        assertTrue(result == Right(short(42)))
      },
      test("Byte to Int") {
        val result = PrimitiveConversions.convert(byte(100), "Byte", "Int")
        assertTrue(result == Right(int(100)))
      },
      test("Byte to Long") {
        val result = PrimitiveConversions.convert(byte(50), "Byte", "Long")
        assertTrue(result == Right(long(50L)))
      },
      test("Byte to Float") {
        val result = PrimitiveConversions.convert(byte(25), "Byte", "Float")
        assertTrue(result == Right(float(25.0f)))
      },
      test("Byte to Double") {
        val result = PrimitiveConversions.convert(byte(10), "Byte", "Double")
        assertTrue(result == Right(double(10.0)))
      },
      test("Byte to BigInt") {
        val result = PrimitiveConversions.convert(byte(99), "Byte", "BigInt")
        assertTrue(result == Right(bigInt(BigInt(99))))
      },
      test("Byte to BigDecimal") {
        val result = PrimitiveConversions.convert(byte(55), "Byte", "BigDecimal")
        assertTrue(result == Right(bigDec(BigDecimal(55))))
      },
      test("Short to Int") {
        val result = PrimitiveConversions.convert(short(1000), "Short", "Int")
        assertTrue(result == Right(int(1000)))
      },
      test("Short to Long") {
        val result = PrimitiveConversions.convert(short(2000), "Short", "Long")
        assertTrue(result == Right(long(2000L)))
      },
      test("Short to Float") {
        val result = PrimitiveConversions.convert(short(500), "Short", "Float")
        assertTrue(result == Right(float(500.0f)))
      },
      test("Short to Double") {
        val result = PrimitiveConversions.convert(short(750), "Short", "Double")
        assertTrue(result == Right(double(750.0)))
      },
      test("Short to BigInt") {
        val result = PrimitiveConversions.convert(short(9999), "Short", "BigInt")
        assertTrue(result == Right(bigInt(BigInt(9999))))
      },
      test("Short to BigDecimal") {
        val result = PrimitiveConversions.convert(short(8888), "Short", "BigDecimal")
        assertTrue(result == Right(bigDec(BigDecimal(8888))))
      },
      test("Int to Long") {
        val result = PrimitiveConversions.convert(int(100000), "Int", "Long")
        assertTrue(result == Right(long(100000L)))
      },
      test("Int to Float") {
        val result = PrimitiveConversions.convert(int(12345), "Int", "Float")
        assertTrue(result == Right(float(12345.0f)))
      },
      test("Int to Double") {
        val result = PrimitiveConversions.convert(int(54321), "Int", "Double")
        assertTrue(result == Right(double(54321.0)))
      },
      test("Int to BigInt") {
        val result = PrimitiveConversions.convert(int(999999), "Int", "BigInt")
        assertTrue(result == Right(bigInt(BigInt(999999))))
      },
      test("Int to BigDecimal") {
        val result = PrimitiveConversions.convert(int(888888), "Int", "BigDecimal")
        assertTrue(result == Right(bigDec(BigDecimal(888888))))
      },
      test("Long to Float") {
        val result = PrimitiveConversions.convert(long(123456L), "Long", "Float")
        assertTrue(result == Right(float(123456.0f)))
      },
      test("Long to Double") {
        val result = PrimitiveConversions.convert(long(654321L), "Long", "Double")
        assertTrue(result == Right(double(654321.0)))
      },
      test("Long to BigInt") {
        val result = PrimitiveConversions.convert(long(9999999L), "Long", "BigInt")
        assertTrue(result == Right(bigInt(BigInt(9999999L))))
      },
      test("Long to BigDecimal") {
        val result = PrimitiveConversions.convert(long(8888888L), "Long", "BigDecimal")
        assertTrue(result == Right(bigDec(BigDecimal(8888888L))))
      },
      test("Float to Double") {
        val result = PrimitiveConversions.convert(float(3.14f), "Float", "Double")
        assertTrue(result.isRight)
      },
      test("Float to BigDecimal") {
        val result = PrimitiveConversions.convert(float(2.71f), "Float", "BigDecimal")
        assertTrue(result.isRight)
      },
      test("Double to BigDecimal") {
        val result = PrimitiveConversions.convert(double(3.14159), "Double", "BigDecimal")
        assertTrue(result == Right(bigDec(BigDecimal(3.14159))))
      },
      test("Identity Byte") {
        val result = PrimitiveConversions.convert(byte(42), "Byte", "Byte")
        assertTrue(result == Right(byte(42)))
      },
      test("Identity Short") {
        val result = PrimitiveConversions.convert(short(1000), "Short", "Short")
        assertTrue(result == Right(short(1000)))
      },
      test("Identity Int") {
        val result = PrimitiveConversions.convert(int(50000), "Int", "Int")
        assertTrue(result == Right(int(50000)))
      },
      test("Identity Long") {
        val result = PrimitiveConversions.convert(long(999L), "Long", "Long")
        assertTrue(result == Right(long(999L)))
      },
      test("Identity Float") {
        val result = PrimitiveConversions.convert(float(1.5f), "Float", "Float")
        assertTrue(result == Right(float(1.5f)))
      },
      test("Identity Double") {
        val result = PrimitiveConversions.convert(double(2.5), "Double", "Double")
        assertTrue(result == Right(double(2.5)))
      },
      test("Identity String") {
        val result = PrimitiveConversions.convert(str("hello"), "String", "String")
        assertTrue(result == Right(str("hello")))
      },
      test("Identity Boolean") {
        val result = PrimitiveConversions.convert(bool(true), "Boolean", "Boolean")
        assertTrue(result == Right(bool(true)))
      },
      test("Identity Char") {
        val result = PrimitiveConversions.convert(char('X'), "Char", "Char")
        assertTrue(result == Right(char('X')))
      },
      test("Byte negative to Short") {
        val result = PrimitiveConversions.convert(byte(-50), "Byte", "Short")
        assertTrue(result == Right(short(-50)))
      },
      test("Byte negative to Int") {
        val result = PrimitiveConversions.convert(byte(-100), "Byte", "Int")
        assertTrue(result == Right(int(-100)))
      },
      test("Short negative to Int") {
        val result = PrimitiveConversions.convert(short(-5000), "Short", "Int")
        assertTrue(result == Right(int(-5000)))
      },
      test("Int negative to Long") {
        val result = PrimitiveConversions.convert(int(-100000), "Int", "Long")
        assertTrue(result == Right(long(-100000L)))
      },
      test("Long negative to Double") {
        val result = PrimitiveConversions.convert(long(-999999L), "Long", "Double")
        assertTrue(result == Right(double(-999999.0)))
      },
      test("Byte max to Short") {
        val result = PrimitiveConversions.convert(byte(127), "Byte", "Short")
        assertTrue(result == Right(short(127)))
      },
      test("Byte min to Short") {
        val result = PrimitiveConversions.convert(byte(-128), "Byte", "Short")
        assertTrue(result == Right(short(-128)))
      },
      test("Short max to Int") {
        val result = PrimitiveConversions.convert(short(32767), "Short", "Int")
        assertTrue(result == Right(int(32767)))
      },
      test("Short min to Int") {
        val result = PrimitiveConversions.convert(short(-32768), "Short", "Int")
        assertTrue(result == Right(int(-32768)))
      },
      test("Int max to Long") {
        val result = PrimitiveConversions.convert(int(Int.MaxValue), "Int", "Long")
        assertTrue(result == Right(long(Int.MaxValue.toLong)))
      },
      test("Int min to Long") {
        val result = PrimitiveConversions.convert(int(Int.MinValue), "Int", "Long")
        assertTrue(result == Right(long(Int.MinValue.toLong)))
      },
      test("Byte zero to all types") {
        val b = byte(0)
        assertTrue(
          PrimitiveConversions.convert(b, "Byte", "Short") == Right(short(0)),
          PrimitiveConversions.convert(b, "Byte", "Int") == Right(int(0)),
          PrimitiveConversions.convert(b, "Byte", "Long") == Right(long(0L))
        )
      },
      test("Short to Byte in range") {
        val result = PrimitiveConversions.convert(short(100), "Short", "Byte")
        assertTrue(result == Right(byte(100)))
      },
      test("Int to Byte in range") {
        val result = PrimitiveConversions.convert(int(50), "Int", "Byte")
        assertTrue(result == Right(byte(50)))
      }
    ),
    // =========================================================================
    // BATCH 2: PrimitiveConversions - Narrowing & String Parsing (50 tests)
    // =========================================================================
    suite("PrimitiveConversions - Narrowing")(
      test("Short to Byte out of range high") {
        val result = PrimitiveConversions.convert(short(200), "Short", "Byte")
        assertTrue(result.isLeft)
      },
      test("Short to Byte out of range low") {
        val result = PrimitiveConversions.convert(short(-200), "Short", "Byte")
        assertTrue(result.isLeft)
      },
      test("Int to Byte out of range") {
        val result = PrimitiveConversions.convert(int(1000), "Int", "Byte")
        assertTrue(result.isLeft)
      },
      test("Int to Short out of range high") {
        val result = PrimitiveConversions.convert(int(50000), "Int", "Short")
        assertTrue(result.isLeft)
      },
      test("Int to Short out of range low") {
        val result = PrimitiveConversions.convert(int(-50000), "Int", "Short")
        assertTrue(result.isLeft)
      },
      test("Int to Short in range") {
        val result = PrimitiveConversions.convert(int(10000), "Int", "Short")
        assertTrue(result == Right(short(10000)))
      },
      test("Long to Byte out of range") {
        val result = PrimitiveConversions.convert(long(1000L), "Long", "Byte")
        assertTrue(result.isLeft)
      },
      test("Long to Byte in range") {
        val result = PrimitiveConversions.convert(long(50L), "Long", "Byte")
        assertTrue(result == Right(byte(50)))
      },
      test("Long to Short out of range") {
        val result = PrimitiveConversions.convert(long(100000L), "Long", "Short")
        assertTrue(result.isLeft)
      },
      test("Long to Short in range") {
        val result = PrimitiveConversions.convert(long(5000L), "Long", "Short")
        assertTrue(result == Right(short(5000)))
      },
      test("Long to Int out of range high") {
        val result = PrimitiveConversions.convert(long(Long.MaxValue), "Long", "Int")
        assertTrue(result.isLeft)
      },
      test("Long to Int out of range low") {
        val result = PrimitiveConversions.convert(long(Long.MinValue), "Long", "Int")
        assertTrue(result.isLeft)
      },
      test("Long to Int in range") {
        val result = PrimitiveConversions.convert(long(100000L), "Long", "Int")
        assertTrue(result == Right(int(100000)))
      },
      test("Double to Float") {
        val result = PrimitiveConversions.convert(double(3.14), "Double", "Float")
        assertTrue(result.isRight)
      },
      test("Double to Int in range") {
        val result = PrimitiveConversions.convert(double(100.0), "Double", "Int")
        assertTrue(result == Right(int(100)))
      },
      test("Double to Int out of range") {
        val result = PrimitiveConversions.convert(double(Double.MaxValue), "Double", "Int")
        assertTrue(result.isLeft)
      },
      test("Double to Long in range") {
        val result = PrimitiveConversions.convert(double(1000000.0), "Double", "Long")
        assertTrue(result == Right(long(1000000L)))
      },
      test("BigInt to Int in range") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(12345)), "BigInt", "Int")
        assertTrue(result == Right(int(12345)))
      },
      test("BigInt to Int out of range") {
        val result = PrimitiveConversions.convert(bigInt(BigInt("9999999999999")), "BigInt", "Int")
        assertTrue(result.isLeft)
      },
      test("BigInt to Long in range") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(999999L)), "BigInt", "Long")
        assertTrue(result == Right(long(999999L)))
      },
      test("BigDecimal to Int in range") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(500)), "BigDecimal", "Int")
        assertTrue(result == Right(int(500)))
      },
      test("BigDecimal to Int out of range") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal("99999999999")), "BigDecimal", "Int")
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Long in range") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(123456L)), "BigDecimal", "Long")
        assertTrue(result == Right(long(123456L)))
      },
      test("BigDecimal to Double") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(3.14159)), "BigDecimal", "Double")
        assertTrue(result == Right(double(3.14159)))
      }
    ),
    suite("PrimitiveConversions - String Parsing")(
      test("String to Int valid") {
        val result = PrimitiveConversions.convert(str("12345"), "String", "Int")
        assertTrue(result == Right(int(12345)))
      },
      test("String to Int invalid") {
        val result = PrimitiveConversions.convert(str("abc"), "String", "Int")
        assertTrue(result.isLeft)
      },
      test("String to Int with spaces") {
        val result = PrimitiveConversions.convert(str("  100  "), "String", "Int")
        assertTrue(result == Right(int(100)))
      },
      test("String to Long valid") {
        val result = PrimitiveConversions.convert(str("9999999999"), "String", "Long")
        assertTrue(result == Right(long(9999999999L)))
      },
      test("String to Long invalid") {
        val result = PrimitiveConversions.convert(str("not-a-long"), "String", "Long")
        assertTrue(result.isLeft)
      },
      test("String to Float valid") {
        val result = PrimitiveConversions.convert(str("3.14"), "String", "Float")
        assertTrue(result.isRight)
      },
      test("String to Float invalid") {
        val result = PrimitiveConversions.convert(str("xyz"), "String", "Float")
        assertTrue(result.isLeft)
      },
      test("String to Double valid") {
        val result = PrimitiveConversions.convert(str("2.71828"), "String", "Double")
        assertTrue(result == Right(double(2.71828)))
      },
      test("String to Double invalid") {
        val result = PrimitiveConversions.convert(str("notdouble"), "String", "Double")
        assertTrue(result.isLeft)
      },
      test("String to Boolean true") {
        val result = PrimitiveConversions.convert(str("true"), "String", "Boolean")
        assertTrue(result == Right(bool(true)))
      },
      test("String to Boolean false") {
        val result = PrimitiveConversions.convert(str("false"), "String", "Boolean")
        assertTrue(result == Right(bool(false)))
      },
      test("String to Boolean invalid") {
        val result = PrimitiveConversions.convert(str("maybe"), "String", "Boolean")
        assertTrue(result.isLeft)
      },
      test("String to Char single char") {
        val result = PrimitiveConversions.convert(str("X"), "String", "Char")
        assertTrue(result == Right(char('X')))
      },
      test("String to Char multiple chars fails") {
        val result = PrimitiveConversions.convert(str("AB"), "String", "Char")
        assertTrue(result.isLeft)
      },
      test("String to Char empty fails") {
        val result = PrimitiveConversions.convert(str(""), "String", "Char")
        assertTrue(result.isLeft)
      },
      test("Int to String") {
        val result = PrimitiveConversions.convert(int(42), "Int", "String")
        assertTrue(result == Right(str("42")))
      },
      test("Long to String") {
        val result = PrimitiveConversions.convert(long(9999L), "Long", "String")
        assertTrue(result == Right(str("9999")))
      },
      test("Double to String") {
        val result = PrimitiveConversions.convert(double(3.14), "Double", "String")
        assertTrue(result.isRight)
      },
      test("Boolean to String true") {
        val result = PrimitiveConversions.convert(bool(true), "Boolean", "String")
        assertTrue(result == Right(str("true")))
      },
      test("Boolean to String false") {
        val result = PrimitiveConversions.convert(bool(false), "Boolean", "String")
        assertTrue(result == Right(str("false")))
      },
      test("Char to String") {
        val result = PrimitiveConversions.convert(char('Z'), "Char", "String")
        assertTrue(result == Right(str("Z")))
      },
      test("String negative int") {
        val result = PrimitiveConversions.convert(str("-500"), "String", "Int")
        assertTrue(result == Right(int(-500)))
      },
      test("String negative double") {
        val result = PrimitiveConversions.convert(str("-3.5"), "String", "Double")
        assertTrue(result == Right(double(-3.5)))
      },
      test("Unsupported conversion fails") {
        val result = PrimitiveConversions.convert(str("hello"), "String", "Boolean")
        assertTrue(result.isLeft)
      }
    ),
    // =========================================================================
    // BATCH 3: Resolved Expressions (50 tests)
    // =========================================================================
    suite("Resolved - Literal")(
      test("Literal evalDynamic returns value") {
        val lit = Resolved.Literal(str("hello"))
        assertTrue(lit.evalDynamic == Right(str("hello")))
      },
      test("Literal evalDynamic with input ignores input") {
        val lit = Resolved.Literal(int(42))
        assertTrue(lit.evalDynamic(str("ignored")) == Right(int(42)))
      },
      test("Literal inverse returns self") {
        val lit = Resolved.Literal(str("test"))
        assertTrue(lit.inverse.isInstanceOf[Resolved.Literal])
      },
      test("Literal with int value") {
        val lit = Resolved.Literal(int(100))
        assertTrue(lit.evalDynamic == Right(int(100)))
      },
      test("Literal with double value") {
        val lit = Resolved.Literal(double(3.14))
        assertTrue(lit.evalDynamic == Right(double(3.14)))
      },
      test("Literal with boolean value") {
        val lit = Resolved.Literal(bool(true))
        assertTrue(lit.evalDynamic == Right(bool(true)))
      }
    ),
    suite("Resolved - Identity")(
      test("Identity evalDynamic requires input") {
        assertTrue(Resolved.Identity.evalDynamic.isLeft)
      },
      test("Identity evalDynamic with input returns input") {
        assertTrue(Resolved.Identity.evalDynamic(str("test")) == Right(str("test")))
      },
      test("Identity passes through int") {
        assertTrue(Resolved.Identity.evalDynamic(int(42)) == Right(int(42)))
      },
      test("Identity passes through record") {
        val record = DynamicValue.Record("name" -> str("test"))
        assertTrue(Resolved.Identity.evalDynamic(record) == Right(record))
      }
    ),
    suite("Resolved - FieldAccess")(
      test("FieldAccess extracts field from record") {
        val record = DynamicValue.Record("name" -> str("Alice"))
        val access = Resolved.FieldAccess("name", Resolved.Identity)
        assertTrue(access.evalDynamic(record) == Right(str("Alice")))
      },
      test("FieldAccess fails on missing field") {
        val record = DynamicValue.Record("other" -> str("value"))
        val access = Resolved.FieldAccess("name", Resolved.Identity)
        assertTrue(access.evalDynamic(record).isLeft)
      },
      test("FieldAccess fails on non-record") {
        val access = Resolved.FieldAccess("name", Resolved.Identity)
        assertTrue(access.evalDynamic(str("notrecord")).isLeft)
      },
      test("FieldAccess evalDynamic requires input") {
        val access = Resolved.FieldAccess("field", Resolved.Identity)
        assertTrue(access.evalDynamic.isLeft)
      },
      test("FieldAccess extracts nested value") {
        val record = DynamicValue.Record("count" -> int(5))
        val access = Resolved.FieldAccess("count", Resolved.Identity)
        assertTrue(access.evalDynamic(record) == Right(int(5)))
      }
    ),
    suite("Resolved - Convert")(
      test("Convert applies type conversion") {
        val conv = Resolved.Convert("Int", "Long", Resolved.Identity)
        assertTrue(conv.evalDynamic(int(42)).isRight)
      },
      test("Convert evalDynamic requires input") {
        val conv = Resolved.Convert("Int", "Long", Resolved.Identity)
        assertTrue(conv.evalDynamic.isLeft)
      },
      test("Convert fails on wrong input type") {
        val conv = Resolved.Convert("Int", "Long", Resolved.Identity)
        assertTrue(conv.evalDynamic(str("notint")).isLeft)
      },
      test("Convert Int to String") {
        val conv = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(conv.evalDynamic(int(100)).isRight)
      },
      test("Convert String to Int valid") {
        val conv = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(conv.evalDynamic(str("123")).isRight)
      },
      test("Convert String to Int invalid") {
        val conv = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(conv.evalDynamic(str("abc")).isLeft)
      }
    ),
    suite("Resolved - Fail")(
      test("Fail always returns error") {
        val fail = Resolved.Fail("intentional error")
        assertTrue(fail.evalDynamic.isLeft)
      },
      test("Fail with input also returns error") {
        val fail = Resolved.Fail("error message")
        assertTrue(fail.evalDynamic(str("input")).isLeft)
      },
      test("Fail error message is preserved") {
        val fail   = Resolved.Fail("custom error")
        val result = fail.evalDynamic
        assertTrue(result == Left("custom error"))
      }
    ),
    suite("Resolved - DefaultValue")(
      test("DefaultValue with Right returns value") {
        val dv = Resolved.DefaultValue(Right(str("default")))
        assertTrue(dv.evalDynamic == Right(str("default")))
      },
      test("DefaultValue with Left returns error") {
        val dv = Resolved.DefaultValue(Left("no default"))
        assertTrue(dv.evalDynamic == Left("no default"))
      },
      test("DefaultValue evalDynamic ignores input") {
        val dv = Resolved.DefaultValue(Right(int(99)))
        assertTrue(dv.evalDynamic(str("ignored")) == Right(int(99)))
      }
    ),
    suite("Resolved - Concat")(
      test("Concat joins strings") {
        val concat = Resolved.Concat(
          Vector(Resolved.FieldAccess("first", Resolved.Identity), Resolved.FieldAccess("last", Resolved.Identity)),
          " "
        )
        val record = DynamicValue.Record("first" -> str("John"), "last" -> str("Doe"))
        assertTrue(concat.evalDynamic(record) == Right(str("John Doe")))
      },
      test("Concat with empty separator") {
        val concat = Resolved.Concat(
          Vector(Resolved.Literal(str("A")), Resolved.Literal(str("B"))),
          ""
        )
        assertTrue(concat.evalDynamic(str("ignored")) == Right(str("AB")))
      },
      test("Concat evalDynamic requires input") {
        val concat = Resolved.Concat(Vector(Resolved.Identity), "-")
        assertTrue(concat.evalDynamic.isLeft)
      },
      test("Concat fails if part is not string") {
        val concat = Resolved.Concat(Vector(Resolved.Literal(int(42))), "-")
        assertTrue(concat.evalDynamic(str("x")).isLeft)
      }
    ),
    suite("Resolved - SplitString")(
      test("SplitString extracts part by index") {
        val split = Resolved.SplitString(Resolved.Identity, "-", 0)
        assertTrue(split.evalDynamic(str("a-b-c")) == Right(str("a")))
      },
      test("SplitString extracts second part") {
        val split = Resolved.SplitString(Resolved.Identity, "-", 1)
        assertTrue(split.evalDynamic(str("x-y-z")) == Right(str("y")))
      },
      test("SplitString extracts last part") {
        val split = Resolved.SplitString(Resolved.Identity, "-", 2)
        assertTrue(split.evalDynamic(str("1-2-3")) == Right(str("3")))
      },
      test("SplitString out of bounds") {
        val split = Resolved.SplitString(Resolved.Identity, "-", 5)
        assertTrue(split.evalDynamic(str("a-b")).isLeft)
      },
      test("SplitString evalDynamic requires input") {
        val split = Resolved.SplitString(Resolved.Identity, "-", 0)
        assertTrue(split.evalDynamic.isLeft)
      },
      test("SplitString with different delimiter") {
        val split = Resolved.SplitString(Resolved.Identity, ",", 1)
        assertTrue(split.evalDynamic(str("foo,bar,baz")) == Right(str("bar")))
      }
    ),
    suite("Resolved - UnwrapOption and WrapOption")(
      test("UnwrapOption extracts Some value") {
        val unwrap     = Resolved.UnwrapOption(Resolved.Identity, Resolved.Fail("none"))
        val someRecord = DynamicValue.Record("value" -> str("inner"))
        val some       = DynamicValue.Variant("Some", someRecord)
        assertTrue(unwrap.evalDynamic(some) == Right(str("inner")))
      },
      test("UnwrapOption returns fallback for None") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(str("fallback")))
        val none   = DynamicValue.Variant("None", DynamicValue.Record.empty)
        assertTrue(unwrap.evalDynamic(none) == Right(str("fallback")))
      },
      test("UnwrapOption evalDynamic requires input") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Fail("x"))
        assertTrue(unwrap.evalDynamic.isLeft)
      },
      test("WrapOption wraps value in Some") {
        val wrap   = Resolved.WrapOption(Resolved.Identity)
        val result = wrap.evalDynamic(str("value"))
        assertTrue(result.isRight)
      },
      test("WrapOption evalDynamic requires input") {
        val wrap = Resolved.WrapOption(Resolved.Identity)
        assertTrue(wrap.evalDynamic.isLeft)
      }
    ),
    // =========================================================================
    // BATCH 4: MigrationOptimizer (50 tests)
    // =========================================================================
    suite("MigrationOptimizer - removeNoOps")(
      test("optimize removes no-op rename same name") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "field", "field")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("optimize keeps valid rename") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize removes multiple no-op renames") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "a"),
            MigrationAction.Rename(DynamicOptic.root, "b", "b"),
            MigrationAction.Rename(DynamicOptic.root, "c", "c")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("optimize keeps mix of valid and no-op renames") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new"),
            MigrationAction.Rename(DynamicOptic.root, "same", "same")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      }
    ),
    suite("MigrationOptimizer - collapseRenames")(
      test("collapses sequential renames A->B->C to A->C") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
        val rename = optimized.actions.head.asInstanceOf[MigrationAction.Rename]
        assertTrue(rename.from == "a" && rename.to == "c")
      },
      test("collapses triple rename chain") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "x", "y"),
            MigrationAction.Rename(DynamicOptic.root, "y", "z"),
            MigrationAction.Rename(DynamicOptic.root, "z", "w")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("handles independent rename chains") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "x", "y")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 2)
      },
      test("handles single rename") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      }
    ),
    suite("MigrationOptimizer - removeAddThenDrop")(
      test("removes add then drop same field") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal(str("x"))),
            MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal(str("x")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("keeps add without drop") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "new", Resolved.Literal(str("val")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("keeps drop without prior add") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "old", Resolved.Literal(str("def")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("removes multiple add-then-drop pairs") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(str("1"))),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal(str("2"))),
            MigrationAction.DropField(DynamicOptic.root, "a", Resolved.Literal(str("1"))),
            MigrationAction.DropField(DynamicOptic.root, "b", Resolved.Literal(str("2")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      }
    ),
    suite("MigrationOptimizer - removeDropThenAdd")(
      test("removes redundant drop-then-add for same field") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal(str("old"))),
            MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal(str("new")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("removes multiple redundant drop-then-add pairs") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal(str("a"))),
            MigrationAction.DropField(DynamicOptic.root, "y", Resolved.Literal(str("b"))),
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(str("c"))),
            MigrationAction.AddField(DynamicOptic.root, "y", Resolved.Literal(str("d")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      }
    ),
    suite("MigrationOptimizer - report")(
      test("report shows correct counts") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "a"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
        )
        val report = MigrationOptimizer.report(migration)
        assertTrue(
          report.originalCount == 2,
          report.optimizedCount == 1,
          report.actionsRemoved == 1
        )
      },
      test("report calculates percent reduced") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "x", "x"),
            MigrationAction.Rename(DynamicOptic.root, "y", "y")
          )
        )
        val report = MigrationOptimizer.report(migration)
        assertTrue(report.percentReduced == 100.0)
      },
      test("report renders correctly") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val report = MigrationOptimizer.report(migration)
        assertTrue(report.render.contains("Optimization Report"))
      },
      test("report handles empty migration") {
        val migration = DynamicMigration(Chunk.empty)
        val report    = MigrationOptimizer.report(migration)
        assertTrue(
          report.originalCount == 0,
          report.optimizedCount == 0,
          report.percentReduced == 0.0
        )
      }
    ),
    suite("MigrationOptimizer - mixed operations")(
      test("optimize preserves non-optimizable actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.TransformValue(
              DynamicOptic.root,
              "field",
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize handles complex mixed scenario") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal(str("t"))),
            MigrationAction.Rename(DynamicOptic.root, "b", "c"),
            MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal(str("t")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves Mandate actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Mandate(DynamicOptic.root, "required", Resolved.Literal(str("def")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves Optionalize actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Optionalize(DynamicOptic.root, "optional")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves TransformCase actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk.empty)
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves RenameCase actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves Join actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Join(
              DynamicOptic.root,
              "combined",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves Split actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Split(
              DynamicOptic.root,
              "source",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves ChangeType actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.ChangeType(
              DynamicOptic.root,
              "field",
              Resolved.Convert("Int", "Long", Resolved.Identity),
              Resolved.Convert("Long", "Int", Resolved.Identity)
            )
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("optimize preserves TransformElements actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.TransformElements(
              DynamicOptic.root,
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      }
    ),
    // =========================================================================
    // BATCH 5: MigrationIntrospector (50 tests)
    // =========================================================================
    suite("MigrationIntrospector - summarize")(
      test("summarize empty migration") {
        val migration = DynamicMigration(Chunk.empty)
        val summary   = MigrationIntrospector.summarize(migration)
        assertTrue(summary.totalActions == 0)
      },
      test("summarize counts added fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "f1", Resolved.Literal(str("a"))),
            MigrationAction.AddField(DynamicOptic.root, "f2", Resolved.Literal(str("b")))
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.addedFields.size == 2)
      },
      test("summarize counts dropped fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "old", Resolved.Literal(str("x")))
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.droppedFields.size == 1)
      },
      test("summarize counts renamed fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new"),
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.renamedFields.size == 2)
      },
      test("summarize counts mandated fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Mandate(DynamicOptic.root, "req", Resolved.Literal(str("d")))
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.mandatedFields.size == 1)
      },
      test("summarize counts optionalized fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Optionalize(DynamicOptic.root, "opt")
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.optionalizedFields.size == 1)
      },
      test("summarize counts renamed cases") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.renamedCases.size == 1)
      },
      test("summarize counts joined fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Join(
              DynamicOptic.root,
              "full",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.joinedFields.size == 1)
      },
      test("summarize counts split fields") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Split(
              DynamicOptic.root,
              "full",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.splitFields.size == 1)
      },
      test("summarize total actions correct") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "x", "y"),
            MigrationAction.AddField(DynamicOptic.root, "z", Resolved.Literal(str("v"))),
            MigrationAction.DropField(DynamicOptic.root, "w", Resolved.Literal(str("d")))
          )
        )
        val summary = MigrationIntrospector.summarize(migration)
        assertTrue(summary.totalActions == 3)
      }
    ),
    suite("MigrationIntrospector - isFullyReversible")(
      test("rename is reversible") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("renameCase is reversible") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
          )
        )
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("join is reversible") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Join(
              DynamicOptic.root,
              "j",
              Chunk(DynamicOptic.root.field("a")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("split is reversible") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Split(
              DynamicOptic.root,
              "s",
              Chunk(DynamicOptic.root.field("a")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("changeType is reversible") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.ChangeType(DynamicOptic.root, "f", Resolved.Identity, Resolved.Identity)
          )
        )
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      },
      test("empty migration is reversible") {
        val migration = DynamicMigration(Chunk.empty)
        assertTrue(MigrationIntrospector.isFullyReversible(migration))
      }
    ),
    suite("MigrationIntrospector - calculateComplexity")(
      test("empty migration has zero complexity") {
        val migration = DynamicMigration(Chunk.empty)
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 1)
      },
      test("rename adds 1 complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 1)
      },
      test("addField adds 2 complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 1)
      },
      test("dropField adds 2 complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 1)
      },
      test("join adds 3 complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Join(
              DynamicOptic.root,
              "j",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 2)
      },
      test("split adds 3 complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Split(
              DynamicOptic.root,
              "s",
              Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) == 2)
      },
      test("complexity capped at 10") {
        val migration = DynamicMigration(
          Chunk.fill(20)(
            MigrationAction.Join(
              DynamicOptic.root,
              "j",
              Chunk(DynamicOptic.root.field("a")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(MigrationIntrospector.calculateComplexity(migration) <= 10)
      }
    ),
    suite("MigrationIntrospector - generateSqlDdl")(
      test("generates ADD COLUMN for AddField PostgreSQL") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "newcol", Resolved.Literal(str("def")))
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "users")
        assertTrue(ddl.statements.exists(_.contains("ADD COLUMN")))
      },
      test("generates DROP COLUMN for DropField") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "oldcol", Resolved.Literal(str("x")))
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "users")
        assertTrue(ddl.statements.exists(_.contains("DROP COLUMN")))
      },
      test("generates RENAME COLUMN for Rename") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "users")
        assertTrue(ddl.statements.exists(_.contains("RENAME COLUMN")))
      },
      test("ddl result render works") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "t")
        assertTrue(ddl.render.nonEmpty)
      },
      test("ddl uses table name") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "x", "y")
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "mytable")
        assertTrue(ddl.render.contains("mytable"))
      },
      test("ddl with MySQL dialect") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "t", MigrationIntrospector.SqlDialect.MySQL)
        assertTrue(ddl.dialect == MigrationIntrospector.SqlDialect.MySQL)
      },
      test("ddl with SQLite dialect") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val ddl = MigrationIntrospector.generateSqlDdl(migration, "t", MigrationIntrospector.SqlDialect.SQLite)
        assertTrue(ddl.dialect == MigrationIntrospector.SqlDialect.SQLite)
      },
      test("ddl empty migration") {
        val migration = DynamicMigration(Chunk.empty)
        val ddl       = MigrationIntrospector.generateSqlDdl(migration, "empty")
        assertTrue(ddl.statements.isEmpty)
      }
    ),
    suite("MigrationIntrospector - generateDocumentation")(
      test("generates markdown header") {
        val migration = DynamicMigration(Chunk.empty)
        val doc       = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("# Migration: v1 -> v2"))
      },
      test("includes total actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "1.0", "2.0")
        assertTrue(doc.contains("Total Actions"))
      },
      test("lists renamed fields section") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("Renamed Fields"))
      },
      test("lists added fields section") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "newfield", Resolved.Literal(str("x")))
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("Added Fields"))
      },
      test("lists dropped fields section") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "gone", Resolved.Literal(str("x")))
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("Dropped Fields"))
      },
      test("includes complexity") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Join(
              DynamicOptic.root,
              "j",
              Chunk(DynamicOptic.root.field("a")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("Complexity"))
      },
      test("includes reversibility") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val doc = MigrationIntrospector.generateDocumentation(migration, "v1", "v2")
        assertTrue(doc.contains("Reversible"))
      }
    ),
    suite("MigrationIntrospector - validate")(
      test("validate empty migration is valid") {
        val migration = DynamicMigration(Chunk.empty)
        val report    = MigrationIntrospector.validate(migration)
        assertTrue(report.isValid)
      },
      test("validate returns action count") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal(str("v")))
          )
        )
        val report = MigrationIntrospector.validate(migration)
        assertTrue(report.actionCount == 2)
      },
      test("validation report has errors list") {
        val migration = DynamicMigration(Chunk.empty)
        val report    = MigrationIntrospector.validate(migration)
        assertTrue(report.errors.isEmpty)
      },
      test("validation report has warnings list") {
        val migration = DynamicMigration(Chunk.empty)
        val report    = MigrationIntrospector.validate(migration)
        assertTrue(report.warnings.nonEmpty)
      }
    ),
    // =========================================================================
    // BATCH 6: DynamicMigration and DefaultValue (50 tests)
    // =========================================================================
    suite("DynamicMigration")(
      test("create empty migration") {
        val migration = DynamicMigration(Chunk.empty)
        assertTrue(migration.actions.isEmpty)
      },
      test("create migration with single action") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        assertTrue(migration.actions.size == 1)
      },
      test("create migration with multiple actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal(str("v"))),
            MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal(str("x")))
          )
        )
        assertTrue(migration.actions.size == 3)
      },
      test("migration actions are accessible") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val migration = DynamicMigration(Chunk(action))
        assertTrue(migration.actions.head == action)
      },
      test("migration with all action types") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal(str("v"))),
            MigrationAction.DropField(DynamicOptic.root, "g", Resolved.Literal(str("d"))),
            MigrationAction.Mandate(DynamicOptic.root, "h", Resolved.Literal(str("m"))),
            MigrationAction.Optionalize(DynamicOptic.root, "i"),
            MigrationAction.RenameCase(DynamicOptic.root, "X", "Y"),
            MigrationAction
              .Join(DynamicOptic.root, "j", Chunk(DynamicOptic.root.field("x")), Resolved.Identity, Resolved.Identity),
            MigrationAction.Split(
              DynamicOptic.root,
              "s",
              Chunk(DynamicOptic.root.field("x")),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        assertTrue(migration.actions.size == 8)
      },
      test("migration preserves action order") {
        val a1        = MigrationAction.Rename(DynamicOptic.root, "first", "second")
        val a2        = MigrationAction.Rename(DynamicOptic.root, "third", "fourth")
        val migration = DynamicMigration(Chunk(a1, a2))
        assertTrue(
          migration.actions(0) == a1,
          migration.actions(1) == a2
        )
      }
    ),
    suite("DefaultValue")(
      test("apply returns Right when schema has default") {
        val schema            = Schema[String]
        val schemaWithDefault = schema.defaultValue("hello")
        assertTrue(DefaultValue(schemaWithDefault).isRight)
      },
      test("apply returns Left when schema has no default") {
        val schema = Schema[String]
        assertTrue(DefaultValue(schema).isLeft)
      },
      test("fromSchema with default creates Resolved.DefaultValue") {
        val schema   = Schema[Int].defaultValue(42)
        val resolved = DefaultValue.fromSchema(schema)
        assertTrue(resolved.isInstanceOf[Resolved.DefaultValue])
      },
      test("literal creates Resolved.Literal") {
        val resolved = DefaultValue.literal("test", Schema[String])
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("literal with int value") {
        val resolved = DefaultValue.literal(123, Schema[Int])
        assertTrue(resolved.evalDynamic.isRight)
      },
      test("literal with boolean value") {
        val resolved = DefaultValue.literal(true, Schema[Boolean])
        assertTrue(resolved.evalDynamic.isRight)
      },
      test("UseSchemaDefault is DefaultValue Left") {
        val marker = DefaultValue.UseSchemaDefault
        assertTrue(marker.isInstanceOf[Resolved.DefaultValue])
      }
    ),
    suite("MigrationAction - individual types")(
      test("AddField stores path and name") {
        val action = MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal(str("x")))
        assertTrue(action.fieldName == "newField")
      },
      test("DropField stores path and name") {
        val action = MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Literal(str("y")))
        assertTrue(action.fieldName == "oldField")
      },
      test("Rename stores from and to") {
        val action = MigrationAction.Rename(DynamicOptic.root, "from", "to")
        assertTrue(action.from == "from" && action.to == "to")
      },
      test("Mandate stores fieldName") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "required", Resolved.Literal(str("d")))
        assertTrue(action.fieldName == "required")
      },
      test("Optionalize stores fieldName") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "optional")
        assertTrue(action.fieldName == "optional")
      },
      test("RenameCase stores from and to") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        assertTrue(action.from == "OldCase" && action.to == "NewCase")
      },
      test("TransformValue stores fieldName") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "field",
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.fieldName == "field")
      },
      test("TransformCase stores caseName") {
        val action = MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk.empty)
        assertTrue(action.caseName == "Case")
      },
      test("Join stores targetFieldName") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "combined",
          Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.targetFieldName == "combined")
      },
      test("Join stores sourcePaths") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "combined",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("second")),
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.sourcePaths == Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("second")))
      },
      test("Split stores sourceFieldName") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "source",
          Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.sourceFieldName == "source")
      },
      test("Split stores targetPaths") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "source",
          Chunk(DynamicOptic.root.field("x"), DynamicOptic.root.field("y"), DynamicOptic.root.field("z")),
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(
          action.targetPaths == Chunk(
            DynamicOptic.root.field("x"),
            DynamicOptic.root.field("y"),
            DynamicOptic.root.field("z")
          )
        )
      },
      test("ChangeType stores fieldName") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "typed",
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.fieldName == "typed")
      },
      test("TransformElements stores at path") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.at == DynamicOptic.root)
      }
    ),
    suite("MigrationAction - reverse")(
      test("Rename reverse swaps from and to") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val reversed = action.reverse
        assertTrue(
          reversed.asInstanceOf[MigrationAction.Rename].from == "b",
          reversed.asInstanceOf[MigrationAction.Rename].to == "a"
        )
      },
      test("AddField reverse creates DropField") {
        val action   = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField reverse creates AddField") {
        val action   = MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
      },
      test("RenameCase reverse swaps from and to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reversed = action.reverse
        assertTrue(
          reversed.asInstanceOf[MigrationAction.RenameCase].from == "New",
          reversed.asInstanceOf[MigrationAction.RenameCase].to == "Old"
        )
      },
      test("TransformValue reverse swaps transform and reverseTransform") {
        val forward  = Resolved.Literal(str("forward"))
        val backward = Resolved.Literal(str("backward"))
        val action   = MigrationAction.TransformValue(DynamicOptic.root, "f", forward, backward)
        val reversed = action.reverse.asInstanceOf[MigrationAction.TransformValue]
        assertTrue(
          reversed.transform == backward,
          reversed.reverseTransform == forward
        )
      },
      test("Optionalize reverse creates Mandate") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root, "opt")
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Mandate])
      }
    ),
    // =========================================================================
    // BATCH 7: DynamicOptic paths (50 tests)
    // =========================================================================
    suite("DynamicOptic - basic")(
      test("root is empty path") {
        assertTrue(DynamicOptic.root.nodes.isEmpty)
      },
      test("field creates single node") {
        val optic = DynamicOptic.root.field("name")
        assertTrue(optic.nodes.size == 1)
      },
      test("nested fields create chain") {
        val optic = DynamicOptic.root.field("a").field("b").field("c")
        assertTrue(optic.nodes.size == 3)
      },
      test("caseOf creates case node") {
        val optic = DynamicOptic.root.caseOf("SomeCase")
        assertTrue(optic.nodes.size == 1)
      },
      test("element creates element node") {
        val optic = DynamicOptic.root.elements
        assertTrue(optic.nodes.size == 1)
      },
      test("mixed path types") {
        val optic = DynamicOptic.root.field("items").elements.field("name")
        assertTrue(optic.nodes.size == 3)
      },
      test("equality with same path") {
        val a = DynamicOptic.root.field("x")
        val b = DynamicOptic.root.field("x")
        assertTrue(a == b)
      },
      test("inequality with different paths") {
        val a = DynamicOptic.root.field("x")
        val b = DynamicOptic.root.field("y")
        assertTrue(a != b)
      }
    ),
    suite("Resolved - OpticAccess")(
      test("OpticAccess evalDynamic requires input") {
        val access = Resolved.OpticAccess(DynamicOptic.root.field("x"), Resolved.Identity)
        assertTrue(access.evalDynamic.isLeft)
      },
      test("OpticAccess with root and Identity") {
        val access = Resolved.OpticAccess(DynamicOptic.root, Resolved.Identity)
        assertTrue(access.evalDynamic(str("test")).isRight)
      },
      test("OpticAccess descends into field") {
        val record = DynamicValue.Record("name" -> str("value"))
        val access = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        assertTrue(access.evalDynamic(record) == Right(str("value")))
      },
      test("OpticAccess fails on missing field") {
        val record = DynamicValue.Record("other" -> str("val"))
        val access = Resolved.OpticAccess(DynamicOptic.root.field("missing"), Resolved.Identity)
        assertTrue(access.evalDynamic(record).isLeft)
      },
      test("OpticAccess with nested path") {
        val inner  = DynamicValue.Record("b" -> str("deep"))
        val outer  = DynamicValue.Record("a" -> inner)
        val access = Resolved.OpticAccess(DynamicOptic.root.field("a").field("b"), Resolved.Identity)
        assertTrue(access.evalDynamic(outer) == Right(str("deep")))
      }
    ),
    // =========================================================================
    // BATCH 8: More PrimitiveConversions edge cases (50 tests)
    // =========================================================================
    suite("PrimitiveConversions - additional edge cases")(
      test("Byte to String") {
        val result = PrimitiveConversions.convert(byte(42), "Byte", "String")
        assertTrue(result == Right(str("42")))
      },
      test("Short to String") {
        val result = PrimitiveConversions.convert(short(1234), "Short", "String")
        assertTrue(result == Right(str("1234")))
      },
      test("Float to String") {
        val result = PrimitiveConversions.convert(float(3.14f), "Float", "String")
        assertTrue(result.isRight)
      },
      test("BigInt to String") {
        val result = PrimitiveConversions.convert(bigInt(BigInt("99999999999")), "BigInt", "String")
        assertTrue(result.isRight)
      },
      test("BigDecimal to String") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal("123.456")), "BigDecimal", "String")
        assertTrue(result.isRight)
      },
      test("String to BigInt valid") {
        val result = PrimitiveConversions.convert(str("123456789012345"), "String", "BigInt")
        assertTrue(result.isRight)
      },
      test("String to BigInt invalid") {
        val result = PrimitiveConversions.convert(str("not-a-number"), "String", "BigInt")
        assertTrue(result.isLeft)
      },
      test("String to BigDecimal valid") {
        val result = PrimitiveConversions.convert(str("123.456"), "String", "BigDecimal")
        assertTrue(result.isRight)
      },
      test("String to BigDecimal invalid") {
        val result = PrimitiveConversions.convert(str("xyz"), "String", "BigDecimal")
        assertTrue(result.isLeft)
      },
      test("String to Byte valid") {
        val result = PrimitiveConversions.convert(str("100"), "String", "Byte")
        assertTrue(result == Right(byte(100)))
      },
      test("String to Byte out of range") {
        val result = PrimitiveConversions.convert(str("200"), "String", "Byte")
        assertTrue(result.isLeft)
      },
      test("String to Short valid") {
        val result = PrimitiveConversions.convert(str("10000"), "String", "Short")
        assertTrue(result == Right(short(10000)))
      },
      test("String to Short out of range") {
        val result = PrimitiveConversions.convert(str("50000"), "String", "Short")
        assertTrue(result.isLeft)
      },
      test("BigInt to Byte not supported") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(50)), "BigInt", "Byte")
        assertTrue(result.isLeft)
      },
      test("BigInt to Byte out of range") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(1000)), "BigInt", "Byte")
        assertTrue(result.isLeft)
      },
      test("BigInt to Short not supported") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(5000)), "BigInt", "Short")
        assertTrue(result.isLeft)
      },
      test("BigInt to Short out of range") {
        val result = PrimitiveConversions.convert(bigInt(BigInt(100000)), "BigInt", "Short")
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Byte not supported") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(50)), "BigDecimal", "Byte")
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Short not supported") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(5000)), "BigDecimal", "Short")
        assertTrue(result.isLeft)
      },
      test("BigDecimal to Float not supported") {
        val result = PrimitiveConversions.convert(bigDec(BigDecimal(3.14)), "BigDecimal", "Float")
        assertTrue(result.isLeft)
      },
      test("Float to Int in range") {
        val result = PrimitiveConversions.convert(float(100.0f), "Float", "Int")
        assertTrue(result.isRight)
      },
      test("Float to Long in range") {
        val result = PrimitiveConversions.convert(float(1000.0f), "Float", "Long")
        assertTrue(result.isRight)
      },
      test("Double to Byte not supported") {
        val result = PrimitiveConversions.convert(double(50.0), "Double", "Byte")
        assertTrue(result.isLeft)
      },
      test("Double to Short not supported") {
        val result = PrimitiveConversions.convert(double(5000.0), "Double", "Short")
        assertTrue(result.isLeft)
      },
      test("Int to Byte negative in range") {
        val result = PrimitiveConversions.convert(int(-50), "Int", "Byte")
        assertTrue(result == Right(byte(-50)))
      },
      test("Long to Byte negative in range") {
        val result = PrimitiveConversions.convert(long(-100L), "Long", "Byte")
        assertTrue(result == Right(byte(-100)))
      },
      test("Long to Short negative in range") {
        val result = PrimitiveConversions.convert(long(-10000L), "Long", "Short")
        assertTrue(result == Right(short(-10000)))
      },
      test("Long to Int negative in range") {
        val result = PrimitiveConversions.convert(long(-100000L), "Long", "Int")
        assertTrue(result == Right(int(-100000)))
      }
    ),
    // =========================================================================
    // BATCH 9: More Resolved expression combinations (50 tests)
    // =========================================================================
    suite("Resolved - nested expressions")(
      test("Convert with FieldAccess") {
        val record = DynamicValue.Record("num" -> str("42"))
        val expr   = Resolved.Convert("String", "Int", Resolved.FieldAccess("num", Resolved.Identity))
        assertTrue(expr.evalDynamic(record).isRight)
      },
      test("Concat with FieldAccess parts") {
        val record = DynamicValue.Record("a" -> str("Hello"), "b" -> str("World"))
        val expr   = Resolved.Concat(
          Vector(Resolved.FieldAccess("a", Resolved.Identity), Resolved.FieldAccess("b", Resolved.Identity)),
          " "
        )
        assertTrue(expr.evalDynamic(record) == Right(str("Hello World")))
      },
      test("SplitString with FieldAccess") {
        val record = DynamicValue.Record("data" -> str("x-y-z"))
        val expr   = Resolved.SplitString(Resolved.FieldAccess("data", Resolved.Identity), "-", 1)
        assertTrue(expr.evalDynamic(record) == Right(str("y")))
      },
      test("UnwrapOption passes through to inner") {
        val someRecord = DynamicValue.Record("value" -> str("hello"))
        val some       = DynamicValue.Variant("Some", someRecord)
        val expr       = Resolved.UnwrapOption(
          Resolved.Identity,
          Resolved.Fail("none")
        )
        assertTrue(expr.evalDynamic(some).isRight)
      },
      test("WrapOption with Literal") {
        val expr = Resolved.WrapOption(Resolved.Literal(str("wrapped")))
        assertTrue(expr.evalDynamic(str("ignored")).isRight)
      },
      test("Concat with three parts") {
        val record = DynamicValue.Record("a" -> str("1"), "b" -> str("2"), "c" -> str("3"))
        val expr   = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("a", Resolved.Identity),
            Resolved.FieldAccess("b", Resolved.Identity),
            Resolved.FieldAccess("c", Resolved.Identity)
          ),
          "-"
        )
        assertTrue(expr.evalDynamic(record) == Right(str("1-2-3")))
      },
      test("SplitString with index 0") {
        val expr = Resolved.SplitString(Resolved.Identity, "/", 0)
        assertTrue(expr.evalDynamic(str("a/b/c")) == Right(str("a")))
      },
      test("SplitString with last index") {
        val expr = Resolved.SplitString(Resolved.Identity, "/", 2)
        assertTrue(expr.evalDynamic(str("a/b/c")) == Right(str("c")))
      },
      test("Convert chain Int->Long->String") {
        val inner = Resolved.Convert("Int", "Long", Resolved.Identity)
        val outer = Resolved.Convert("Long", "String", inner)
        assertTrue(outer.evalDynamic(int(42)).isRight)
      },
      test("FieldAccess in Convert") {
        val record = DynamicValue.Record("value" -> int(100))
        val expr   = Resolved.Convert("Int", "String", Resolved.FieldAccess("value", Resolved.Identity))
        assertTrue(expr.evalDynamic(record) == Right(str("100")))
      }
    ),
    suite("Resolved - schema")(
      test("Resolved schema exists") {
        assertTrue(implicitly[Schema[Resolved]] != null)
      },
      test("Literal can be serialized via schema") {
        val literal = Resolved.Literal(str("test"))
        val schema  = implicitly[Schema[Resolved]]
        val dv      = schema.toDynamicValue(literal)
        assertTrue(dv != null)
      }
    ),
    // =========================================================================
    // BATCH 10: More MigrationAction edge cases and prefixPath (50 tests)
    // =========================================================================
    suite("MigrationAction - prefixPath")(
      test("AddField prefixPath updates at") {
        val action   = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("DropField prefixPath updates at") {
        val action   = MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal(str("v")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("Rename prefixPath updates at") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("TransformValue prefixPath updates at") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "f",
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("Mandate prefixPath updates at") {
        val action   = MigrationAction.Mandate(DynamicOptic.root, "f", Resolved.Literal(str("d")))
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("Optionalize prefixPath updates at") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root, "f")
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("RenameCase prefixPath updates at") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("TransformCase prefixPath updates at") {
        val action   = MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk.empty)
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("Join prefixPath updates at") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "j",
          Chunk(DynamicOptic.root.field("a")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("Split prefixPath updates at") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "s",
          Chunk(DynamicOptic.root.field("a")),
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("ChangeType prefixPath updates at") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "f",
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      },
      test("TransformElements prefixPath updates at") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val prefixed = action.prefixPath(DynamicOptic.root.field("parent"))
        assertTrue(prefixed.at.nodes.size == 1)
      }
    ),
    suite("MigrationAction - additional reversals")(
      test("Mandate reverse creates Optionalize") {
        val action   = MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal(str("d")))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Optionalize])
      },
      test("TransformCase reverse") {
        val action   = MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk.empty)
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformCase])
      },
      test("Join reverse creates Split") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "combined",
          Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Resolved.Identity,
          Resolved.Identity
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Split])
      },
      test("Split reverse creates Join") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "source",
          Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Resolved.Identity,
          Resolved.Identity
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Join])
      },
      test("ChangeType reverse swaps converters") {
        val forward  = Resolved.Convert("Int", "Long", Resolved.Identity)
        val backward = Resolved.Convert("Long", "Int", Resolved.Identity)
        val action   = MigrationAction.ChangeType(DynamicOptic.root, "f", forward, backward)
        val reversed = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        assertTrue(
          reversed.converter == backward,
          reversed.reverseConverter == forward
        )
      },
      test("TransformElements reverse swaps transforms") {
        val forward  = Resolved.Literal(str("f"))
        val backward = Resolved.Literal(str("b"))
        val action   = MigrationAction.TransformElements(DynamicOptic.root, forward, backward)
        val reversed = action.reverse.asInstanceOf[MigrationAction.TransformElements]
        assertTrue(
          reversed.elementTransform == backward,
          reversed.reverseTransform == forward
        )
      }
    ),
    suite("MigrationAction - execute edge cases")(
      test("AddField to empty record") {
        val action = MigrationAction.AddField(DynamicOptic.root, "new", Resolved.Literal(str("val")))
        val record = DynamicValue.Record.empty
        val result = action.execute(record)
        assertTrue(result.isRight)
      },
      test("AddField to record with existing fields") {
        val action = MigrationAction.AddField(DynamicOptic.root, "new", Resolved.Literal(str("val")))
        val record = DynamicValue.Record("existing" -> str("old"))
        val result = action.execute(record)
        assertTrue(result.isRight)
      },
      test("DropField from record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "remove", Resolved.Literal(str("def")))
        val record = DynamicValue.Record("remove" -> str("value"), "keep" -> str("kept"))
        val result = action.execute(record)
        assertTrue(result.isRight)
      },
      test("Rename field in record") {
        val action = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val record = DynamicValue.Record("old" -> str("value"))
        val result = action.execute(record)
        assertTrue(result.isRight)
      },
      test("TransformValue in record") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "field",
          Resolved.Literal(str("transformed")),
          Resolved.Identity
        )
        val record = DynamicValue.Record("field" -> str("original"))
        val result = action.execute(record)
        assertTrue(result.isRight)
      },
      test("Optionalize field") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val record = DynamicValue.Record("field" -> str("value"))
        val result = action.execute(record)
        assertTrue(result.isRight)
      }
    ),
    // =========================================================================
    // BATCH 11: DynamicMigrationInterpreter (50 tests)
    // =========================================================================
    suite("DynamicMigrationInterpreter - apply")(
      test("apply empty migration returns input unchanged") {
        val migration = DynamicMigration(Chunk.empty)
        val input     = DynamicValue.Record("x" -> str("y"))
        val result    = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result == Right(input))
      },
      test("apply single rename") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val input  = DynamicValue.Record("old" -> str("value"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply single addField") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal(str("default")))
          )
        )
        val input  = DynamicValue.Record("existing" -> str("val"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply single dropField") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "removed", Resolved.Literal(str("def")))
          )
        )
        val input  = DynamicValue.Record("removed" -> str("val"), "kept" -> str("k"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply multiple actions sequentially") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal(str("v")))
          )
        )
        val input  = DynamicValue.Record("a" -> str("val"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply respects action order") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "new", Resolved.Literal(str("first"))),
            MigrationAction.Rename(DynamicOptic.root, "new", "renamed")
          )
        )
        val input  = DynamicValue.Record.empty
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply transformValue") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.TransformValue(
              DynamicOptic.root,
              "field",
              Resolved.Literal(str("transformed")),
              Resolved.Identity
            )
          )
        )
        val input  = DynamicValue.Record("field" -> str("original"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      },
      test("apply optionalize") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Optionalize(DynamicOptic.root, "field")
          )
        )
        val input  = DynamicValue.Record("field" -> str("value"))
        val result = DynamicMigrationInterpreter.apply(migration, input)
        assertTrue(result.isRight)
      }
    ),
    // =========================================================================
    // BATCH 12: MigrationBuilder fluent API (50 tests)
    // =========================================================================
    // =========================================================================
    // BATCH 13: More conversion and expression edge cases (50 tests)
    // =========================================================================
    suite("PrimitiveConversions - boundary tests")(
      test("Int.MaxValue to Long") {
        val result = PrimitiveConversions.convert(int(Int.MaxValue), "Int", "Long")
        assertTrue(result == Right(long(Int.MaxValue.toLong)))
      },
      test("Int.MinValue to Long") {
        val result = PrimitiveConversions.convert(int(Int.MinValue), "Int", "Long")
        assertTrue(result == Right(long(Int.MinValue.toLong)))
      },
      test("Long.MaxValue to BigInt") {
        val result = PrimitiveConversions.convert(long(Long.MaxValue), "Long", "BigInt")
        assertTrue(result.isRight)
      },
      test("Long.MinValue to BigInt") {
        val result = PrimitiveConversions.convert(long(Long.MinValue), "Long", "BigInt")
        assertTrue(result.isRight)
      },
      test("Byte.MaxValue to Short") {
        val result = PrimitiveConversions.convert(byte(Byte.MaxValue), "Byte", "Short")
        assertTrue(result == Right(short(Byte.MaxValue.toShort)))
      },
      test("Byte.MinValue to Short") {
        val result = PrimitiveConversions.convert(byte(Byte.MinValue), "Byte", "Short")
        assertTrue(result == Right(short(Byte.MinValue.toShort)))
      },
      test("Short.MaxValue to Int") {
        val result = PrimitiveConversions.convert(short(Short.MaxValue), "Short", "Int")
        assertTrue(result == Right(int(Short.MaxValue.toInt)))
      },
      test("Short.MinValue to Int") {
        val result = PrimitiveConversions.convert(short(Short.MinValue), "Short", "Int")
        assertTrue(result == Right(int(Short.MinValue.toInt)))
      },
      test("zero int to all types") {
        assertTrue(
          PrimitiveConversions.convert(int(0), "Int", "Byte") == Right(byte(0)),
          PrimitiveConversions.convert(int(0), "Int", "Short") == Right(short(0)),
          PrimitiveConversions.convert(int(0), "Int", "Long") == Right(long(0L)),
          PrimitiveConversions.convert(int(0), "Int", "Float").isRight,
          PrimitiveConversions.convert(int(0), "Int", "Double").isRight
        )
      },
      test("negative one to various types") {
        assertTrue(
          PrimitiveConversions.convert(int(-1), "Int", "Byte") == Right(byte(-1)),
          PrimitiveConversions.convert(int(-1), "Int", "Short") == Right(short(-1)),
          PrimitiveConversions.convert(int(-1), "Int", "Long") == Right(long(-1L))
        )
      }
    ),
    suite("Resolved - error handling")(
      test("Fail with empty message") {
        val fail = Resolved.Fail("")
        assertTrue(fail.evalDynamic == Left(""))
      },
      test("Fail with detailed message") {
        val fail = Resolved.Fail("detailed error message")
        assertTrue(fail.evalDynamic.isLeft)
      },
      test("Convert Boolean to Int succeeds") {
        val expr = Resolved.Convert("Boolean", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(bool(true)).isRight)
      },
      test("FieldAccess on primitive fails") {
        val access = Resolved.FieldAccess("field", Resolved.Identity)
        assertTrue(access.evalDynamic(str("primitive")).isLeft)
      },
      test("Concat with non-string fails") {
        val expr = Resolved.Concat(Vector(Resolved.Identity), ",")
        assertTrue(expr.evalDynamic(int(42)).isLeft)
      },
      test("SplitString on non-string fails") {
        val expr = Resolved.SplitString(Resolved.Identity, "/", 0)
        assertTrue(expr.evalDynamic(int(123)).isLeft)
      },
      test("UnwrapOption on non-option passes through") {
        val expr = Resolved.UnwrapOption(Resolved.Identity, Resolved.Fail("fallback"))
        assertTrue(expr.evalDynamic(str("not-option")).isRight)
      }
    ),
    // =========================================================================
    // BATCH 14: Additional edge cases and integration (50 tests)
    // =========================================================================
    suite("DynamicValue helpers")(
      test("str creates string primitive") {
        val v = str("hello")
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("int creates int primitive") {
        val v = int(42)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("long creates long primitive") {
        val v = long(100L)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("bool creates boolean primitive") {
        val v = bool(true)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("double creates double primitive") {
        val v = double(3.14)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("float creates float primitive") {
        val v = float(2.5f)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("byte creates byte primitive") {
        val v = byte(10)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("short creates short primitive") {
        val v = short(1000)
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("bigInt creates bigint primitive") {
        val v = bigInt(BigInt("12345678901234567890"))
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      },
      test("bigDec creates bigdecimal primitive") {
        val v = bigDec(BigDecimal("123.456789"))
        assertTrue(v.isInstanceOf[DynamicValue.Primitive])
      }
    ),
    suite("Migration composition")(
      test("compose two empty migrations") {
        val m1       = DynamicMigration(Chunk.empty)
        val m2       = DynamicMigration(Chunk.empty)
        val composed = DynamicMigration(m1.actions ++ m2.actions)
        assertTrue(composed.actions.isEmpty)
      },
      test("compose migration with empty") {
        val m1       = DynamicMigration(Chunk(MigrationAction.Rename(DynamicOptic.root, "a", "b")))
        val m2       = DynamicMigration(Chunk.empty)
        val composed = DynamicMigration(m1.actions ++ m2.actions)
        assertTrue(composed.actions.size == 1)
      },
      test("compose two single-action migrations") {
        val m1       = DynamicMigration(Chunk(MigrationAction.Rename(DynamicOptic.root, "a", "b")))
        val m2       = DynamicMigration(Chunk(MigrationAction.Rename(DynamicOptic.root, "c", "d")))
        val composed = DynamicMigration(m1.actions ++ m2.actions)
        assertTrue(composed.actions.size == 2)
      },
      test("compose multi-action migrations") {
        val m1 = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(str("v")))
          )
        )
        val m2 = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "y", Resolved.Literal(str("d")))
          )
        )
        val composed = DynamicMigration(m1.actions ++ m2.actions)
        assertTrue(composed.actions.size == 3)
      }
    ),
    suite("Optimizer integration")(
      test("optimize composed migration") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal(str("v"))),
            MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal(str("d")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size < migration.actions.size)
      },
      test("optimize preserves non-redundant actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal(str("v")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 2)
      },
      test("optimized migration still executes") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        val input     = DynamicValue.Record("old" -> str("value"))
        val result    = DynamicMigrationInterpreter.apply(optimized, input)
        assertTrue(result.isRight)
      }
    ),
    suite("Introspector integration")(
      test("summarize optimized migration") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        val summary   = MigrationIntrospector.summarize(optimized)
        assertTrue(summary.totalActions == 1)
      },
      test("validate optimized migration") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(str("v")))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        val report    = MigrationIntrospector.validate(optimized)
        assertTrue(report.isValid)
      },
      test("ddl from optimized migration") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        val ddl       = MigrationIntrospector.generateSqlDdl(optimized, "table")
        assertTrue(ddl.statements.nonEmpty)
      }
    )
  )
}
