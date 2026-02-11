package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object DynamicSchemaExprSpec extends ZIOSpecDefault {

  private val dummyInput = DynamicValue.Primitive(PrimitiveValue.Unit)

  private def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val dv       = schema.toDynamicValue(value)
    val restored = schema.fromDynamicValue(dv)
    assertTrue(restored == Right(value))
  }

  def spec = suite("DynamicSchemaExprSpec")(
    suite("Literal")(
      test("should return the stored value") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr = DynamicSchemaExpr.Literal(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("DefaultValue")(
      test("should return the stored value regardless of input") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("should return string DynamicValue") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.String("default"))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("should return record DynamicValue") {
        val dv   = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("inverse should be Some(this)") {
        val expr = DynamicSchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        assertTrue(expr.inverse == Some(expr))
      }
    ),
    suite("Dynamic")(
      test("should read a field from a record") {
        val record = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("name"))
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
      },
      test("should read nested field") {
        val inner  = DynamicValue.Record(Chunk("city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))))
        val record = DynamicValue.Record(Chunk("address" -> inner))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("address").field("city"))
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("NYC")))))
      },
      test("should fail when field not found") {
        val record = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("age"))
        assertTrue(expr.eval(record).isLeft)
      },
      test("inverse should be Some(this)") {
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
        assertTrue(expr.inverse == Some(expr))
      },
      test("should extract value from a Variant via Case node") {
        val variant = DynamicValue.Variant("Admin", DynamicValue.Primitive(PrimitiveValue.String("root")))
        val expr    = DynamicSchemaExpr.Dynamic(DynamicOptic.root.caseOf("Admin"))
        assertTrue(expr.eval(variant) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("root")))))
      },
      test("should fail when Case name does not match") {
        val variant = DynamicValue.Variant("User", DynamicValue.Primitive(PrimitiveValue.String("bob")))
        val expr    = DynamicSchemaExpr.Dynamic(DynamicOptic.root.caseOf("Admin"))
        assertTrue(expr.eval(variant).isLeft)
      },
      test("should fail when Case is applied to non-Variant") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.caseOf("Admin"))
        assertTrue(expr.eval(record).isLeft)
      },
      test("should traverse Sequence via Elements node") {
        val seq = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.elements)
        assertTrue(
          expr.eval(seq) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2)),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
        )
      },
      test("should fail on empty Sequence via Elements node") {
        val seq  = DynamicValue.Sequence(Chunk.empty)
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.elements)
        assertTrue(expr.eval(seq).isLeft)
      },
      test("should fail when Elements is applied to non-Sequence") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.elements)
        assertTrue(expr.eval(record).isLeft)
      },
      test("should extract map keys via MapKeys node") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapKeys)
        assertTrue(
          expr.eval(map) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("b"))
            )
          )
        )
      },
      test("should fail on empty Map via MapKeys node") {
        val map  = DynamicValue.Map(Chunk.empty)
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapKeys)
        assertTrue(expr.eval(map).isLeft)
      },
      test("should fail when MapKeys is applied to non-Map") {
        val seq  = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapKeys)
        assertTrue(expr.eval(seq).isLeft)
      },
      test("should extract map values via MapValues node") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapValues)
        assertTrue(
          expr.eval(map) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
      },
      test("should fail on empty Map via MapValues node") {
        val map  = DynamicValue.Map(Chunk.empty)
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapValues)
        assertTrue(expr.eval(map).isLeft)
      },
      test("should fail when MapValues is applied to non-Map") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.mapValues)
        assertTrue(expr.eval(record).isLeft)
      },
      test("should unwrap Some via Wrapped node") {
        val some = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.wrapped)
        assertTrue(expr.eval(some) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should skip None via Wrapped node") {
        val none   = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.wrapped)
        val result = expr.eval(none)
        assertTrue(result == Right(Seq.empty))
      },
      test("should fail when Wrapped is applied to non-Variant") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.wrapped)
        assertTrue(expr.eval(record).isLeft)
      },
      test("should access element by index via AtIndex node") {
        val seq = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")),
            DynamicValue.Primitive(PrimitiveValue.String("b")),
            DynamicValue.Primitive(PrimitiveValue.String("c"))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.at(1))
        assertTrue(expr.eval(seq) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("b")))))
      },
      test("should fail when AtIndex is out of bounds") {
        val seq  = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.at(5))
        assertTrue(expr.eval(seq).isLeft)
      },
      test("should fail when AtIndex is applied to non-Sequence") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.at(0))
        assertTrue(expr.eval(record).isLeft)
      },
      test("should access multiple elements by indices via AtIndices node") {
        val seq = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")),
            DynamicValue.Primitive(PrimitiveValue.String("b")),
            DynamicValue.Primitive(PrimitiveValue.String("c")),
            DynamicValue.Primitive(PrimitiveValue.String("d"))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atIndices(0, 2, 3))
        assertTrue(
          expr.eval(seq) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("c")),
              DynamicValue.Primitive(PrimitiveValue.String("d"))
            )
          )
        )
      },
      test("should fail when AtIndices has out of bounds index") {
        val seq  = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atIndices(0, 5))
        assertTrue(expr.eval(seq).isLeft)
      },
      test("should fail when AtIndices is applied to non-Sequence") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atIndices(0))
        assertTrue(expr.eval(record).isLeft)
      },
      test("should look up value by map key via AtMapKey node") {
        val key = DynamicValue.Primitive(PrimitiveValue.String("name"))
        val map = DynamicValue.Map(
          Chunk(
            key                                                  -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            DynamicValue.Primitive(PrimitiveValue.String("age")) -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKey("name"))
        assertTrue(expr.eval(map) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
      },
      test("should fail when AtMapKey key is not found") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKey("missing"))
        assertTrue(expr.eval(map).isLeft)
      },
      test("should fail when AtMapKey is applied to non-Map") {
        val seq  = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKey("x"))
        assertTrue(expr.eval(seq).isLeft)
      },
      test("should look up values by multiple map keys via AtMapKeys node") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.String("c")) -> DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKeys("a", "c"))
        assertTrue(
          expr.eval(map) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
        )
      },
      test("should return empty when AtMapKeys finds no matching keys") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKeys("x", "y"))
        assertTrue(expr.eval(map) == Right(Seq.empty))
      },
      test("should fail when AtMapKeys is applied to non-Map") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.atKeys("x"))
        assertTrue(expr.eval(record).isLeft)
      },
      test("should chain Field then Elements for nested traversal") {
        val record = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(10)),
                DynamicValue.Primitive(PrimitiveValue.Int(20))
              )
            )
          )
        )
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("items").elements)
        assertTrue(
          expr.eval(record) == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.Int(10)),
              DynamicValue.Primitive(PrimitiveValue.Int(20))
            )
          )
        )
      }
    ),
    suite("Arithmetic")(
      test("should add two ints") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(10)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(5))
          )
        )
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("a")),
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("b")),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(15)))))
      },
      test("should subtract two ints") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(7)))))
      },
      test("should multiply two doubles") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply,
          DynamicSchemaExpr.NumericType.DoubleType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(10.0)))))
      },
      test("should divide two ints (integer division)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(7))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(3)))))
      },
      test("should fail on type mismatch") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not a number"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should add two longs") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(200L))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.LongType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Long(300L)))))
      },
      test("inverse of Add should be Subtract") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Arithmetic]
        assertTrue(inv.operator == DynamicSchemaExpr.ArithmeticOperator.Subtract)
      },
      test("inverse of Subtract should be Add") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.NumericType.IntType
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Arithmetic]
        assertTrue(inv.operator == DynamicSchemaExpr.ArithmeticOperator.Add)
      },
      test("inverse of Multiply should be Divide") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply,
          DynamicSchemaExpr.NumericType.IntType
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Arithmetic]
        assertTrue(inv.operator == DynamicSchemaExpr.ArithmeticOperator.Divide)
      },
      test("inverse of Divide should be Multiply") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.IntType
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Arithmetic]
        assertTrue(inv.operator == DynamicSchemaExpr.ArithmeticOperator.Multiply)
      }
    ),
    suite("StringConcat")(
      test("should concatenate two strings") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" World")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Hello World")))))
      },
      test("should concatenate from record fields") {
        val record = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("first")),
          DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("last"))
          )
        )
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("John Doe")))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("StringSplit")(
      test("should split string by single space delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("John Doe"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("John")),
              DynamicValue.Primitive(PrimitiveValue.String("Doe"))
            )
          )
        )
      },
      test("should split by comma delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("apple,orange,banana"))),
          ","
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("apple")),
              DynamicValue.Primitive(PrimitiveValue.String("orange")),
              DynamicValue.Primitive(PrimitiveValue.String("banana"))
            )
          )
        )
      },
      test("should handle string with no delimiter present") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("NoDelimiterHere"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("NoDelimiterHere")))))
      },
      test("should handle empty string") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("")))))
      },
      test("should handle multiple consecutive delimiters") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a  b"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("")),
              DynamicValue.Primitive(PrimitiveValue.String("b"))
            )
          )
        )
      },
      test("should handle trailing delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a,b,"))),
          ","
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("b")),
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
        )
      },
      test("should split by multi-character delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("one::two::three"))),
          "::"
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("one")),
              DynamicValue.Primitive(PrimitiveValue.String("two")),
              DynamicValue.Primitive(PrimitiveValue.String("three"))
            )
          )
        )
      }
    ),
    suite("StringUppercase")(
      test("should convert string to uppercase") {
        val expr = DynamicSchemaExpr.StringUppercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("HELLO")))))
      },
      test("inverse should be StringLowercase") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val expr  = DynamicSchemaExpr.StringUppercase(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.StringLowercase(inner)))
      }
    ),
    suite("StringLowercase")(
      test("should convert string to lowercase") {
        val expr = DynamicSchemaExpr.StringLowercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("HELLO")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello")))))
      },
      test("inverse should be StringUppercase") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val expr  = DynamicSchemaExpr.StringLowercase(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.StringUppercase(inner)))
      }
    ),
    suite("StringLength")(
      test("should return length of string") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(5)))))
      },
      test("should return 0 for empty string") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(0)))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("StringRegexMatch")(
      test("should return true for matching regex") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello123"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(".*\\d+")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("should return false for non-matching regex") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("^\\d+$")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      }
    ),
    suite("Not")(
      test("should negate true to false") {
        val expr = DynamicSchemaExpr.Not(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("should negate false to true") {
        val expr = DynamicSchemaExpr.Not(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("inverse of Not should be Not") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val expr  = DynamicSchemaExpr.Not(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.Not(inner)))
      }
    ),
    suite("Relational")(
      test("Equal should compare DynamicValues") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("NotEqual should detect different values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.NotEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("LessThan should compare ordered values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.LessThan
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("GreaterThanOrEqual should compare ordered values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("GreaterThan should detect strictly greater") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.GreaterThan
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("GreaterThan should return false when equal") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.GreaterThan
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("LessThanOrEqual should return true when equal") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("LessThanOrEqual should return true when less") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("Logical")(
      test("And should return true when both are true") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("And should return false when one is false") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("Or should return true when one is true") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("Or should return false when both are false") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("Convert")(
      test("should convert string to int") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))),
          PrimitiveConverter.StringToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should fail to convert invalid string to int") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("abc"))),
          PrimitiveConverter.StringToInt
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert int to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToString
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("42")))))
      },
      test("should convert string to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("9223372036854775807"))),
          PrimitiveConverter.StringToLong
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))))
        )
      },
      test("should fail to convert invalid string to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))),
          PrimitiveConverter.StringToLong
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert long to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))),
          PrimitiveConverter.LongToString
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("9223372036854775807"))))
        )
      },
      test("should convert string to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("3.14159"))),
          PrimitiveConverter.StringToDouble
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(3.14159)))))
      },
      test("should fail to convert invalid string to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not-a-double"))),
          PrimitiveConverter.StringToDouble
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert double to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14159))),
          PrimitiveConverter.DoubleToString
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("3.14159")))))
      },
      test("should convert int to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToLong
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Long(42L)))))
      },
      test("should convert long to int when in range") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(42L))),
          PrimitiveConverter.LongToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should fail to convert long to int when out of range") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))),
          PrimitiveConverter.LongToInt
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert double to int (truncating)") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(42.7))),
          PrimitiveConverter.DoubleToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should convert int to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToDouble
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(42.0)))))
      },
      test("inverse should use reverse converter") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))),
          PrimitiveConverter.StringToInt
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Convert]
        assertTrue(inv.converter == PrimitiveConverter.IntToString)
      }
    ),
    suite("PrimitiveConverter reverse")(
      test("StringToInt reverse is IntToString") {
        assertTrue(PrimitiveConverter.StringToInt.reverse == PrimitiveConverter.IntToString)
      },
      test("IntToString reverse is StringToInt") {
        assertTrue(PrimitiveConverter.IntToString.reverse == PrimitiveConverter.StringToInt)
      },
      test("StringToLong reverse is LongToString") {
        assertTrue(PrimitiveConverter.StringToLong.reverse == PrimitiveConverter.LongToString)
      },
      test("LongToString reverse is StringToLong") {
        assertTrue(PrimitiveConverter.LongToString.reverse == PrimitiveConverter.StringToLong)
      },
      test("StringToDouble reverse is DoubleToString") {
        assertTrue(PrimitiveConverter.StringToDouble.reverse == PrimitiveConverter.DoubleToString)
      },
      test("DoubleToString reverse is StringToDouble") {
        assertTrue(PrimitiveConverter.DoubleToString.reverse == PrimitiveConverter.StringToDouble)
      },
      test("IntToLong reverse is LongToInt") {
        assertTrue(PrimitiveConverter.IntToLong.reverse == PrimitiveConverter.LongToInt)
      },
      test("LongToInt reverse is IntToLong") {
        assertTrue(PrimitiveConverter.LongToInt.reverse == PrimitiveConverter.IntToLong)
      },
      test("IntToDouble reverse is DoubleToInt") {
        assertTrue(PrimitiveConverter.IntToDouble.reverse == PrimitiveConverter.DoubleToInt)
      },
      test("DoubleToInt reverse is IntToDouble") {
        assertTrue(PrimitiveConverter.DoubleToInt.reverse == PrimitiveConverter.IntToDouble)
      }
    ),
    suite("StringSplit inverse")(
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a b"))),
          " "
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("StringRegexMatch inverse")(
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("test"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(".*")))
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("Not error paths")(
      test("should fail when input is not Boolean") {
        val expr = DynamicSchemaExpr.Not(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("Logical error paths")(
      test("should fail when left input is not Boolean") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should fail when right input is not Boolean") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not bool"))),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("Arithmetic error paths")(
      test("should fail on integer division by zero") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should fail on long division by zero") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(10L))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0L))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.LongType
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should divide two doubles (Fractional path)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(10.0))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.0))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.DoubleType
        )
        val result = expr.eval(dummyInput)
        assertTrue(result.isRight)
      },
      test("should divide two floats (Fractional path)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.FloatType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Float(2.5f)))))
      },
      test("should fail when y value cannot be extracted") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("nope"))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("Arithmetic with all numeric types")(
      test("ByteType add") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(1.toByte))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(2.toByte))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.ByteType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte)))))
      },
      test("ShortType subtract") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(10.toShort))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(3.toShort))),
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.NumericType.ShortType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Short(7.toShort)))))
      },
      test("FloatType multiply") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply,
          DynamicSchemaExpr.NumericType.FloatType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Float(10.0f)))))
      },
      test("BigIntType add") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100)))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(200)))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.BigIntType
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(300)))))
        )
      },
      test("BigDecimalType divide") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(10)))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(4)))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.BigDecimalType
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2.5)))))
        )
      },
      test("ByteType divide (Integral path)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(2.toByte))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.ByteType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte)))))
      },
      test("ShortType divide (Integral path)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(10.toShort))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(3.toShort))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.ShortType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Short(3.toShort)))))
      },
      test("BigIntType divide (Integral path)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100)))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(7)))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.BigIntType
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(14)))))
        )
      }
    ),
    suite("PrimitiveConverter wrong type errors")(
      test("IntToString should fail on non-Int") {
        val result = PrimitiveConverter.IntToString.convert(DynamicValue.Primitive(PrimitiveValue.String("hi")))
        assertTrue(result.isLeft)
      },
      test("StringToInt should fail on non-String") {
        val result = PrimitiveConverter.StringToInt.convert(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result.isLeft)
      },
      test("LongToString should fail on non-Long") {
        val result = PrimitiveConverter.LongToString.convert(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result.isLeft)
      },
      test("StringToLong should fail on non-String") {
        val result = PrimitiveConverter.StringToLong.convert(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
        assertTrue(result.isLeft)
      },
      test("DoubleToString should fail on non-Double") {
        val result = PrimitiveConverter.DoubleToString.convert(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result.isLeft)
      },
      test("StringToDouble should fail on non-String") {
        val result = PrimitiveConverter.StringToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Double(3.14)))
        assertTrue(result.isLeft)
      },
      test("IntToLong should fail on non-Int") {
        val result = PrimitiveConverter.IntToLong.convert(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
        assertTrue(result.isLeft)
      },
      test("LongToInt should fail on non-Long") {
        val result = PrimitiveConverter.LongToInt.convert(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result.isLeft)
      },
      test("IntToDouble should fail on non-Int") {
        val result = PrimitiveConverter.IntToDouble.convert(DynamicValue.Primitive(PrimitiveValue.Double(3.14)))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt should fail on non-Double") {
        val result = PrimitiveConverter.DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(result.isLeft)
      }
    ),
    suite("StringConcat error paths")(
      test("should fail when left is not a string") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should fail when right is not a string") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("StringSplit error paths")(
      test("should fail when input is not a string") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          ","
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("StringUppercase error paths")(
      test("should fail when input is not a string") {
        val expr = DynamicSchemaExpr.StringUppercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("StringLowercase error paths")(
      test("should fail when input is not a string") {
        val expr = DynamicSchemaExpr.StringLowercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("StringLength error paths")(
      test("should fail when input is not a string") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("StringRegexMatch error paths")(
      test("should fail when regex input is not a string") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(".*")))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should fail when string input is not a string") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("Dynamic error path - field on non-record")(
      test("should fail when accessing field on non-record") {
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(42))).isLeft)
      }
    ),
    suite("Convert error path")(
      test("should propagate converter error") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          PrimitiveConverter.StringToInt
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      }
    ),
    suite("SchemaExpr typed wrapper")(
      test("eval should return typed result") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(0) == Right(Seq(42)))
      },
      test("eval should return error for fromDynamicValue failure") {
        // Provide an expression that returns a String but output schema expects Int
        val expr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(0).isLeft)
      },
      test("eval should return error for expression failure") {
        // Dynamic field access on non-record input
        val expr = SchemaExpr(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("missing")),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("evalDynamic should return DynamicValue results") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99))),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.evalDynamic(0) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(99)))))
      },
      test("evalDynamic should propagate expression error") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("missing")),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.evalDynamic(42).isLeft)
      },
      test("&& combinator should AND boolean expressions") {
        val trueExpr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          Schema.boolean,
          Schema.boolean
        )
        val combined = trueExpr && trueExpr
        assertTrue(combined.eval(true) == Right(Seq(true)))
      },
      test("|| combinator should OR boolean expressions") {
        val falseExpr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          Schema.boolean,
          Schema.boolean
        )
        val trueExpr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          Schema.boolean,
          Schema.boolean
        )
        val combined = falseExpr || trueExpr
        assertTrue(combined.eval(true) == Right(Seq(true)))
      }
    ),
    suite("DynamicOptic serialization")(
      test("round-trip AtIndices node") {
        val optic = DynamicOptic.root.atIndices(0, 2, 4)
        roundTrip(optic)
      },
      test("round-trip AtMapKeys node") {
        val optic = DynamicOptic.root.atKeys("a", "b")
        roundTrip(optic)
      },
      test("round-trip AtIndex node") {
        val optic = DynamicOptic.root.at(3)
        roundTrip(optic)
      },
      test("round-trip AtMapKey node") {
        val optic = DynamicOptic.root.atKey("test")
        roundTrip(optic)
      },
      test("round-trip Elements node") {
        val optic = DynamicOptic.root.elements
        roundTrip(optic)
      },
      test("round-trip MapKeys node") {
        val optic = DynamicOptic.root.mapKeys
        roundTrip(optic)
      },
      test("round-trip MapValues node") {
        val optic = DynamicOptic.root.mapValues
        roundTrip(optic)
      },
      test("round-trip Wrapped node") {
        val optic = DynamicOptic.root.wrapped
        roundTrip(optic)
      },
      test("round-trip complex multi-node path") {
        val optic = DynamicOptic.root.field("data").elements.field("name")
        roundTrip(optic)
      },
      test("round-trip Case node") {
        val optic = DynamicOptic.root.caseOf("Admin")
        roundTrip(optic)
      }
    ),
    suite("DynamicOptic toString with various key types")(
      test("atKey with int key") {
        val optic = DynamicOptic.root.atKey(42)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with long key") {
        val optic = DynamicOptic.root.atKey(123L)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with float key") {
        val optic = DynamicOptic.root.atKey(1.5f)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with double key") {
        val optic = DynamicOptic.root.atKey(3.14)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with bigint key") {
        val optic = DynamicOptic.root.atKey(BigInt(999))
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with bigdecimal key") {
        val optic = DynamicOptic.root.atKey(BigDecimal("1.23"))
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with boolean key") {
        val optic = DynamicOptic.root.atKey(true)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with byte key") {
        val optic = DynamicOptic.root.atKey(7.toByte)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with short key") {
        val optic = DynamicOptic.root.atKey(42.toShort)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("atKey with char key") {
        val optic = DynamicOptic.root.atKey('x')
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("toString with AtIndices") {
        val optic = DynamicOptic.root.atIndices(0, 1, 2)
        val str   = optic.toString
        assertTrue(str.nonEmpty)
      },
      test("toString with multiple nodes") {
        val optic = DynamicOptic.root.field("x").elements.field("y")
        val str   = optic.toString
        assertTrue(str.contains("x") && str.contains("y"))
      }
    ),
    suite("SchemaExpr.findOptic coverage")(
      test("findOptic traverses Logical branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Logical(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
            DynamicSchemaExpr.LogicalOperator.And
          ),
          Schema.int,
          Schema.boolean
        )
        // Eval triggers toOpticCheck -> findOptic which traverses Logical branch
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses Arithmetic branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          ),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringConcat branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
          ),
          Schema.int,
          Schema.string
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringLength branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringLength(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
          ),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringRegexMatch branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringRegexMatch(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(".*"))),
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
          ),
          Schema.int,
          Schema.boolean
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses Not branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Not(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
          ),
          Schema.int,
          Schema.boolean
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses Convert branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Convert(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            PrimitiveConverter.StringToInt
          ),
          Schema.int,
          Schema.int
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringUppercase branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringUppercase(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
          ),
          Schema.int,
          Schema.string
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringLowercase branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringLowercase(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
          ),
          Schema.int,
          Schema.string
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses StringSplit branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.StringSplit(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            " "
          ),
          Schema.int,
          Schema.string
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic traverses Relational branch") {
        val expr = SchemaExpr(
          DynamicSchemaExpr.Relational(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
            DynamicSchemaExpr.RelationalOperator.Equal
          ),
          Schema.int,
          Schema.boolean
        )
        assertTrue(expr.eval(42).isLeft)
      },
      test("findOptic returns None for Literal (fallthrough)") {
        // Literal has no optic -> findOptic returns None -> uses DynamicOptic.root
        val expr = SchemaExpr(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          Schema.int,
          Schema.int
        )
        // This will fail during fromDynamicValue (String->Int mismatch)
        assertTrue(expr.eval(42).isLeft)
      }
    ),
    suite("NumericType.fromIsNumeric")(
      test("IsByte") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsByte) == DynamicSchemaExpr.NumericType.ByteType
        )
      },
      test("IsShort") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsShort) == DynamicSchemaExpr.NumericType.ShortType
        )
      },
      test("IsInt") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsInt) == DynamicSchemaExpr.NumericType.IntType
        )
      },
      test("IsLong") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsLong) == DynamicSchemaExpr.NumericType.LongType
        )
      },
      test("IsFloat") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsFloat) == DynamicSchemaExpr.NumericType.FloatType
        )
      },
      test("IsDouble") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsDouble) == DynamicSchemaExpr.NumericType.DoubleType
        )
      },
      test("IsBigInt") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(IsNumeric.IsBigInt) == DynamicSchemaExpr.NumericType.BigIntType
        )
      },
      test("IsBigDecimal") {
        assertTrue(
          DynamicSchemaExpr.NumericType.fromIsNumeric(
            IsNumeric.IsBigDecimal
          ) == DynamicSchemaExpr.NumericType.BigDecimalType
        )
      }
    )
  )
}
