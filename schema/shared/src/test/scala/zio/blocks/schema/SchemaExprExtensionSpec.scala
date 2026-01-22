package zio.blocks.schema

import zio.test._

object SchemaExprExtensionSpec extends ZIOSpecDefault {

  def spec = suite("SchemaExprExtensionSpec")(
    suite("Convert - PrimitiveConverter")(
      suite("StringToInt")(
        test("should convert valid string to int") {
          val expr: SchemaExpr[Unit, Int] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("42", Schema.string),
            PrimitiveConverter.StringToInt
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        },
        test("should fail to convert invalid string to int") {
          val expr: SchemaExpr[Unit, Int] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("abc", Schema.string),
            PrimitiveConverter.StringToInt
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isLeft)
        }
      ),
      suite("IntToString")(
        test("should convert int to string") {
          val expr: SchemaExpr[Unit, String] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Int](42, Schema.int),
            PrimitiveConverter.IntToString
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.String("42"))
          )
        }
      ),
      suite("StringToLong")(
        test("should convert valid string to long") {
          val expr: SchemaExpr[Unit, Long] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("9223372036854775807", Schema.string),
            PrimitiveConverter.StringToLong
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))
          )
        },
        test("should fail to convert invalid string to long") {
          val expr: SchemaExpr[Unit, Long] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("not-a-number", Schema.string),
            PrimitiveConverter.StringToLong
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isLeft)
        }
      ),
      suite("LongToString")(
        test("should convert long to string") {
          val expr: SchemaExpr[Unit, String] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Long](9223372036854775807L, Schema.long),
            PrimitiveConverter.LongToString
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.String("9223372036854775807"))
          )
        }
      ),
      suite("StringToDouble")(
        test("should convert valid string to double") {
          val expr: SchemaExpr[Unit, Double] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("3.14159", Schema.string),
            PrimitiveConverter.StringToDouble
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Double(3.14159))
          )
        },
        test("should fail to convert invalid string to double") {
          val expr: SchemaExpr[Unit, Double] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, String]("not-a-double", Schema.string),
            PrimitiveConverter.StringToDouble
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isLeft)
        }
      ),
      suite("DoubleToString")(
        test("should convert double to string") {
          val expr: SchemaExpr[Unit, String] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Double](3.14159, Schema.double),
            PrimitiveConverter.DoubleToString
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.String("3.14159"))
          )
        }
      ),
      suite("IntToLong")(
        test("should convert int to long") {
          val expr: SchemaExpr[Unit, Long] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Int](42, Schema.int),
            PrimitiveConverter.IntToLong
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Long(42L))
          )
        }
      ),
      suite("LongToInt")(
        test("should convert long to int when in range") {
          val expr: SchemaExpr[Unit, Int] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Long](42L, Schema.long),
            PrimitiveConverter.LongToInt
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        },
        test("should fail to convert long to int when out of range") {
          val expr: SchemaExpr[Unit, Int] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Long](9223372036854775807L, Schema.long),
            PrimitiveConverter.LongToInt
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isLeft)
        }
      ),
      suite("DoubleToInt")(
        test("should convert double to int (truncating)") {
          val expr: SchemaExpr[Unit, Int] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Double](42.7, Schema.double),
            PrimitiveConverter.DoubleToInt
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        }
      ),
      suite("IntToDouble")(
        test("should convert int to double") {
          val expr: SchemaExpr[Unit, Double] = SchemaExpr.Convert(
            SchemaExpr.Literal[Unit, Int](42, Schema.int),
            PrimitiveConverter.IntToDouble
          )
          val result = expr.evalDynamic(())

          assertTrue(result.isRight) &&
          assertTrue(
            result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.Double(42.0))
          )
        }
      )
    ),
    suite("Convert - Reverse")(
      test("should reverse StringToInt to IntToString") {
        val converter = PrimitiveConverter.StringToInt
        assertTrue(converter.reverse == PrimitiveConverter.IntToString)
      },
      test("should reverse IntToLong to LongToInt") {
        val converter = PrimitiveConverter.IntToLong
        assertTrue(converter.reverse == PrimitiveConverter.LongToInt)
      }
    ),
    suite("StringSplit")(
      test("should split string by single space delimiter") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("John Doe", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("John", "Doe"))
      },
      test("should split string and return DynamicValue sequence") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("John Doe", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")
        val result     = splitExpr.evalDynamic(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.length == 2) &&
        assertTrue(result.toOption.get.head == DynamicValue.Primitive(PrimitiveValue.String("John"))) &&
        assertTrue(result.toOption.get(1) == DynamicValue.Primitive(PrimitiveValue.String("Doe")))
      },
      test("should split string by comma delimiter") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("apple,orange,banana", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, ",")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("apple", "orange", "banana"))
      },
      test("should handle string with no delimiter present") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("NoDelimiterHere", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("NoDelimiterHere"))
      },
      test("should handle empty string") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq(""))
      },
      test("should handle string with multiple consecutive delimiters") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("a  b", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("a", "", "b"))
      },
      test("should handle string with trailing delimiter") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("a,b,", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, ",")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("a", "b", ""))
      },
      test("should handle string with leading delimiter") {
        val stringExpr = SchemaExpr.Literal[Unit, String](",a,b", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, ",")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("", "a", "b"))
      },
      test("should split by multi-character delimiter") {
        val stringExpr = SchemaExpr.Literal[Unit, String]("one::two::three", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, "::")
        val result     = splitExpr.eval(())

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == Seq("one", "two", "three"))
      },
      test("should work with different input strings") {
        // Test with different input strings
        val stringExpr1 = SchemaExpr.Literal[Unit, String]("John Doe", Schema.string)
        val stringExpr2 = SchemaExpr.Literal[Unit, String]("Jane Smith", Schema.string)

        val splitExpr1 = SchemaExpr.StringSplit(stringExpr1, " ")
        val result1    = splitExpr1.eval(())

        val splitExpr2 = SchemaExpr.StringSplit(stringExpr2, " ")
        val result2    = splitExpr2.eval(())

        assertTrue(result1.isRight) &&
        assertTrue(result1.toOption.get == Seq("John", "Doe")) &&
        assertTrue(result2.isRight) &&
        assertTrue(result2.toOption.get == Seq("Jane", "Smith"))
      }
    )
  )
}
