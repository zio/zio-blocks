package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object StructuralValueSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("StructuralValueSpec")(
    suite("StructuralValue.Product")(
      test("creates product with correct type name") {
        val product = StructuralValue.product(
          ("name", "Alice", "String"),
          ("age", 30, "Int")
        )
        assertTrue(product.typeName == "{age:Int,name:String}")
      },
      test("type name sorts fields alphabetically") {
        val product = StructuralValue.product(
          ("zebra", "z", "String"),
          ("alpha", "a", "String"),
          ("middle", "m", "String")
        )
        assertTrue(product.typeName == "{alpha:String,middle:String,zebra:String}")
      },
      test("selectDynamic returns field value") {
        val product = StructuralValue.product(
          ("name", "Bob", "String"),
          ("age", 25, "Int")
        )
        assertTrue(
          product.selectDynamic("name") == Right("Bob") &&
            product.selectDynamic("age") == Right(25)
        )
      },
      test("selectDynamic returns error for missing field") {
        val product = StructuralValue.product(("name", "Alice", "String"))
        assertTrue(product.selectDynamic("missing").isLeft)
      },
      test("toDynamicValue converts to Record") {
        val product = StructuralValue.product(
          ("name", "Alice", "String"),
          ("age", 30, "Int")
        )
        val dv = product.toDynamicValue
        assertTrue(dv.isInstanceOf[DynamicValue.Record])
      },
      test("handles nested structural values") {
        val inner = StructuralValue.product(("x", 1, "Int"), ("y", 2, "Int"))
        val outer = StructuralValue.Product(
          Vector(("point", inner, "Record")),
          "{point:Record}"
        )
        assertTrue(outer.selectDynamic("point") == Right(inner))
      }
    ),
    suite("StructuralValue.Sum")(
      test("creates sum with correct type name") {
        val inner = StructuralValue.product(("value", 42, "Int"))
        val sum   = StructuralValue.Sum("SomeCase", inner, "CaseA | CaseB | SomeCase")
        assertTrue(sum.typeName == "CaseA | CaseB | SomeCase")
      },
      test("selectDynamic returns value for matching case") {
        val inner = StructuralValue.product(("value", "test", "String"))
        val sum   = StructuralValue.Sum("MyCase", inner, "MyCase | OtherCase")
        assertTrue(sum.selectDynamic("MyCase") == Right(inner))
      },
      test("selectDynamic returns error for non-matching case") {
        val inner = StructuralValue.product(("value", "test", "String"))
        val sum   = StructuralValue.Sum("MyCase", inner, "MyCase | OtherCase")
        assertTrue(sum.selectDynamic("OtherCase").isLeft)
      },
      test("toDynamicValue converts to Variant") {
        val inner = StructuralValue.product(("x", 1, "Int"))
        val sum   = StructuralValue.Sum("TestCase", inner, "TestCase")
        val dv    = sum.toDynamicValue
        assertTrue(dv.isInstanceOf[DynamicValue.Variant])
      }
    ),
    suite("Type name generation")(
      test("productTypeName generates correct format") {
        val fields   = Seq(("b", "Int"), ("a", "String"), ("c", "Boolean"))
        val typeName = StructuralValue.productTypeName(fields)
        assertTrue(typeName == "{a:String,b:Int,c:Boolean}")
      },
      test("productTypeName handles empty fields") {
        val typeName = StructuralValue.productTypeName(Seq.empty)
        assertTrue(typeName == "{}")
      },
      test("productTypeName handles single field") {
        val typeName = StructuralValue.productTypeName(Seq(("only", "Int")))
        assertTrue(typeName == "{only:Int}")
      },
      test("sumTypeName generates correct format") {
        val cases    = Seq("C", "A", "B")
        val typeName = StructuralValue.sumTypeName(cases)
        assertTrue(typeName == "A | B | C")
      },
      test("sumTypeName handles single case") {
        val typeName = StructuralValue.sumTypeName(Seq("OnlyCase"))
        assertTrue(typeName == "OnlyCase")
      }
    ),
    suite("DynamicValue conversion")(
      test("String converts correctly") {
        val product = StructuralValue.product(("s", "hello", "String"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("Int converts correctly") {
        val product = StructuralValue.product(("i", 42, "Int"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Int(42)))
      },
      test("Boolean converts correctly") {
        val product = StructuralValue.product(("b", true, "Boolean"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("Long converts correctly") {
        val product = StructuralValue.product(("l", 123456789L, "Long"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Long(123456789L)))
      },
      test("Double converts correctly") {
        val product = StructuralValue.product(("d", 3.14, "Double"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Double(3.14)))
      },
      test("Unit converts correctly") {
        val product = StructuralValue.product(("u", (), "Unit"))
        val dv      = product.toDynamicValue.asInstanceOf[DynamicValue.Record]
        assertTrue(dv.fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Unit))
      }
    )
  )
}
