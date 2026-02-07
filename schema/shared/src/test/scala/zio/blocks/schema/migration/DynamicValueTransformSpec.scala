package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object DynamicValueTransformSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueTransform")(
    suite("basic transforms")(
      test("Identity transform returns the same value") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.identity(value)
        assertTrue(result == Right(value))
      },
      test("Constant transform returns the constant value") {
        val value    = DynamicValue.int(42)
        val constant = DynamicValue.string("hello")
        val result   = DynamicValueTransform.constant(constant)(value)
        assertTrue(result == Right(constant))
      },
      test("Sequence applies transforms in order") {
        val value     = DynamicValue.int(10)
        val transform = DynamicValueTransform.sequence(
          DynamicValueTransform.numericAdd(5),
          DynamicValueTransform.numericMultiply(2)
        )
        val result = transform(value)
        assertTrue(result == Right(DynamicValue.int(30)))
      }
    ),
    suite("string transforms")(
      test("StringAppend appends a suffix") {
        val value  = DynamicValue.string("hello")
        val result = DynamicValueTransform.stringAppend(" world")(value)
        assertTrue(result == Right(DynamicValue.string("hello world")))
      },
      test("StringPrepend prepends a prefix") {
        val value  = DynamicValue.string("world")
        val result = DynamicValueTransform.stringPrepend("hello ")(value)
        assertTrue(result == Right(DynamicValue.string("hello world")))
      },
      test("StringReplace replaces occurrences") {
        val value  = DynamicValue.string("hello world")
        val result = DynamicValueTransform.stringReplace("world", "universe")(value)
        assertTrue(result == Right(DynamicValue.string("hello universe")))
      },
      test("StringAppend fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringAppend(" suffix")(value)
        assertTrue(result.isLeft)
      },
      test("StringPrepend fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringPrepend("prefix ")(value)
        assertTrue(result.isLeft)
      },
      test("StringReplace fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringReplace("old", "new")(value)
        assertTrue(result.isLeft)
      },
      test("StringAppend fails on non-String") {
        val result = DynamicValueTransform.stringAppend("suffix")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringPrepend fails on non-String") {
        val result = DynamicValueTransform.stringPrepend("prefix")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringReplace fails on non-String") {
        val result = DynamicValueTransform.stringReplace("a", "b")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      }
    ),
    suite("StringJoinFields")(
      test("StringJoinFields fails for non-Record input") {
        val value  = DynamicValue.string("not a record")
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      },
      test("StringJoinFields fails when field is not String") {
        val value = DynamicValue.Record(
          "a" -> DynamicValue.string("hello"),
          "b" -> DynamicValue.int(42)
        )
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      },
      test("StringJoinFields reports all missing fields") {
        val value = DynamicValue.Record(
          "x" -> DynamicValue.string("only x")
        )
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b", "c"), "-")(value)
        result match {
          case Left(err) =>
            assertTrue(
              err.contains("'a'"),
              err.contains("'b'"),
              err.contains("'c'")
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("StringSplitToFields")(
      test("StringSplitToFields splits correctly") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("first", "last"), " ")
        val result    = transform(DynamicValue.string("John Doe"))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("first") == Some(DynamicValue.string("John")),
              fieldMap.get("last") == Some(DynamicValue.string("Doe"))
            )
          case _ => assertTrue(false)
        }
      },
      test("StringSplitToFields fails on non-string") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("a", "b"), " ")
        val result    = transform(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringSplitToFields pads with empty strings when fewer parts") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("a", "b", "c"), " ")
        val result    = transform(DynamicValue.string("only two"))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("a") == Some(DynamicValue.string("only")),
              fieldMap.get("b") == Some(DynamicValue.string("two")),
              fieldMap.get("c") == Some(DynamicValue.string(""))
            )
          case _ => assertTrue(false)
        }
      },
      test("StringSplitToFields fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringSplitToFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("numeric transforms")(
      test("NumericAdd adds to integer") {
        val value  = DynamicValue.int(10)
        val result = DynamicValueTransform.numericAdd(5)(value)
        assertTrue(result == Right(DynamicValue.int(15)))
      },
      test("NumericMultiply multiplies integer") {
        val value  = DynamicValue.int(10)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result == Right(DynamicValue.int(30)))
      },
      test("NumericAdd on Long values") {
        val transform = DynamicValueTransform.numericAdd(5)
        val result    = transform(DynamicValue.long(10L))
        assertTrue(result == Right(DynamicValue.long(15L)))
      },
      test("NumericAdd on Float values") {
        val transform = DynamicValueTransform.numericAdd(1)
        val result    = transform(DynamicValue.float(2.5f))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f == 3.5f)
          case _ => assertTrue(false)
        }
      },
      test("NumericAdd on BigDecimal values") {
        val transform = DynamicValueTransform.numericAdd(BigDecimal("0.5"))
        val result    = transform(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("1.5"))))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
            assertTrue(bd == BigDecimal("2.0"))
          case _ => assertTrue(false)
        }
      },
      test("NumericAdd on BigInt values") {
        val transform = DynamicValueTransform.numericAdd(50)
        val result    = transform(DynamicValue.bigInt(BigInt(100)))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
            assertTrue(bi == BigInt(150))
          case _ => assertTrue(false)
        }
      },
      test("NumericMultiply on Long values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.long(5L))
        assertTrue(result == Right(DynamicValue.long(10L)))
      },
      test("NumericMultiply on Float values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.float(2.5f))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f == 5.0f)
          case _ => assertTrue(false)
        }
      },
      test("NumericMultiply on non-numeric fails") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.string("not a number"))
        assertTrue(result.isLeft)
      },
      test("NumericMultiply on BigDecimal values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("3.14"))))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
            assertTrue(bd == BigDecimal("6.28"))
          case _ => assertTrue(false)
        }
      },
      test("NumericMultiply on BigInt values") {
        val transform = DynamicValueTransform.numericMultiply(3)
        val result    = transform(DynamicValue.bigInt(BigInt(100)))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
            assertTrue(bi == BigInt(300))
          case _ => assertTrue(false)
        }
      },
      test("NumericAdd fails for non-numeric value") {
        val transform = DynamicValueTransform.numericAdd(5)
        val result    = transform(DynamicValue.string("not a number"))
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails for non-numeric value") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.string("not a number"))
        assertTrue(result.isLeft)
      },
      test("NumericAdd on Double values") {
        val transform = DynamicValueTransform.numericAdd(2.5)
        val result    = transform(DynamicValue.double(3.5))
        assertTrue(result == Right(DynamicValue.double(6.0)))
      },
      test("NumericMultiply on Double values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.double(3.5))
        assertTrue(result == Right(DynamicValue.double(7.0)))
      }
    ),
    suite("numeric overflow")(
      test("NumericAdd fails when Int result exceeds Int.MaxValue") {
        val value  = DynamicValue.int(Int.MaxValue)
        val result = DynamicValueTransform.numericAdd(1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Int result is below Int.MinValue") {
        val value  = DynamicValue.int(Int.MinValue)
        val result = DynamicValueTransform.numericAdd(-1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Long result exceeds Long.MaxValue") {
        val value  = DynamicValue.long(Long.MaxValue)
        val result = DynamicValueTransform.numericAdd(1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Long result is below Long.MinValue") {
        val value  = DynamicValue.long(Long.MinValue)
        val result = DynamicValueTransform.numericAdd(-1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd on Long overflow") {
        val transform = DynamicValueTransform.numericAdd(BigDecimal(Long.MaxValue))
        val result    = transform(DynamicValue.long(1L))
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Int result exceeds Int.MaxValue") {
        val value  = DynamicValue.int(Int.MaxValue / 2 + 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Int result is below Int.MinValue") {
        val value  = DynamicValue.int(Int.MinValue / 2 - 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Long result exceeds Long.MaxValue") {
        val value  = DynamicValue.long(Long.MaxValue / 2 + 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Long result is below Long.MinValue") {
        val value  = DynamicValue.long(Long.MinValue / 2 - 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply on Long overflow") {
        val transform = DynamicValueTransform.numericMultiply(BigDecimal(Long.MaxValue))
        val result    = transform(DynamicValue.long(2L))
        assertTrue(result.isLeft)
      }
    ),
    suite("Option transforms")(
      test("WrapInSome wraps value in Some variant") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.wrapInSome(value)
        result match {
          case Right(DynamicValue.Variant("Some", _)) => assertTrue(true)
          case _                                      => assertTrue(false)
        }
      },
      test("UnwrapSome extracts value from Some variant") {
        val value = DynamicValue.Variant(
          "Some",
          DynamicValue.Record("value" -> DynamicValue.string("inner"))
        )
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result == Right(DynamicValue.string("inner")))
      },
      test("UnwrapSome returns default for None variant") {
        val value   = DynamicValue.Variant("None", DynamicValue.Record())
        val default = DynamicValue.string("default")
        val result  = DynamicValueTransform.unwrapSome(default)(value)
        assertTrue(result == Right(default))
      },
      test("UnwrapSome returns default for Null") {
        val default = DynamicValue.string("default")
        val result  = DynamicValueTransform.unwrapSome(default)(DynamicValue.Null)
        assertTrue(result == Right(default))
      },
      test("UnwrapSome fails for non-Option value") {
        val value  = DynamicValue.string("plain string")
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result.isLeft)
      },
      test("UnwrapSome fails for Some variant without 'value' field") {
        val value  = DynamicValue.Variant("Some", DynamicValue.Record("wrong" -> DynamicValue.int(42)))
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result.isLeft)
      }
    )
  )
}
