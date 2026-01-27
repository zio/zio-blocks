package zio.blocks.schema

import zio.test._
import zio.blocks.typeid.TypeId

/**
 * Coverage tests for DerivedOptics.scala
 * Targets:
 * - buildCaseClassOptics for case classes
 * - buildSealedTraitOptics for sealed traits/enums
 * - NameTransformer.encode for special characters
 */
object DerivedOpticsCoverageSpec extends SchemaBaseSpec {

  // Case classes for buildCaseClassOptics - covers code paths for generating lenses
  case class SimplePerson(name: String, age: Int)
  object SimplePerson {
    given schema: Schema[SimplePerson] = Schema.derived
  }

  case class ComplexRecord(
    id: Long,
    active: Boolean,
    score: Double,
    label: Char,
    count: Short,
    data: Byte,
    ratio: Float,
    description: String
  )
  object ComplexRecord {
    given schema: Schema[ComplexRecord] = Schema.derived
  }

  // Sealed traits for buildSealedTraitOptics - covers code paths for generating prisms
  sealed trait SimpleStatus
  case object Active extends SimpleStatus
  case object Inactive extends SimpleStatus
  object SimpleStatus {
    given schema: Schema[SimpleStatus] = Schema.derived
  }

  sealed trait Shape
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double) extends Shape
  object Shape {
    given schema: Schema[Shape] = Schema.derived
  }

  enum Color {
    case Red, Green, Blue
  }
  object Color {
    given schema: Schema[Color] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsCoverageSpec")(
    caseClassOpticsTests,
    sealedTraitOpticsTests,
    nameTransformerTests,
    dynamicOpticTests
  )

  // Case class optics tests - exercises buildCaseClassOptics code paths
  val caseClassOpticsTests = suite("buildCaseClassOptics coverage")(
    test("Simple case class has lenses for all fields") {
      val record = SimplePerson.schema.reflect.asRecord.get
      assertTrue(
        record.fields.size == 2,
        record.lensByIndex[String](0).isDefined,
        record.lensByIndex[Int](1).isDefined
      )
    },
    test("Complex case class has lenses for all 8 fields covering all primitive types") {
      val record = ComplexRecord.schema.reflect.asRecord.get
      assertTrue(
        record.fields.size == 8,
        record.lensByIndex[Long](0).isDefined,
        record.lensByIndex[Boolean](1).isDefined,
        record.lensByIndex[Double](2).isDefined,
        record.lensByIndex[Char](3).isDefined,
        record.lensByIndex[Short](4).isDefined,
        record.lensByIndex[Byte](5).isDefined,
        record.lensByIndex[Float](6).isDefined,
        record.lensByIndex[String](7).isDefined
      )
    },
    test("Case class lens get works correctly") {
      val record = SimplePerson.schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val ageLens = record.lensByIndex[Int](1).get
      val person = SimplePerson("Alice", 30)
      assertTrue(
        nameLens.get(person) == "Alice",
        ageLens.get(person) == 30
      )
    },
    test("Case class lens replace works correctly") {
      val record = SimplePerson.schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val ageLens = record.lensByIndex[Int](1).get
      val person = SimplePerson("Alice", 30)
      val updated1 = nameLens.replace(person, "Bob")
      val updated2 = ageLens.replace(person, 25)
      assertTrue(
        updated1 == SimplePerson("Bob", 30),
        updated2 == SimplePerson("Alice", 25)
      )
    },
    test("Case class lens modify works correctly") {
      val record = SimplePerson.schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val person = SimplePerson("Alice", 30)
      val updated = nameLens.modify(person, _.toUpperCase)
      assertTrue(updated == SimplePerson("ALICE", 30))
    },
    test("Complex case class lenses work for all types") {
      val record = ComplexRecord.schema.reflect.asRecord.get
      val original = ComplexRecord(
        id = 1L,
        active = true,
        score = 99.5,
        label = 'X',
        count = 100.toShort,
        data = 42.toByte,
        ratio = 0.5f,
        description = "test"
      )
      
      val idLens = record.lensByIndex[Long](0).get
      val activeLens = record.lensByIndex[Boolean](1).get
      val scoreLens = record.lensByIndex[Double](2).get
      val labelLens = record.lensByIndex[Char](3).get
      val countLens = record.lensByIndex[Short](4).get
      val dataLens = record.lensByIndex[Byte](5).get
      val ratioLens = record.lensByIndex[Float](6).get
      val descLens = record.lensByIndex[String](7).get
      
      assertTrue(
        idLens.get(original) == 1L,
        activeLens.get(original) == true,
        scoreLens.get(original) == 99.5,
        labelLens.get(original) == 'X',
        countLens.get(original) == 100.toShort,
        dataLens.get(original) == 42.toByte,
        ratioLens.get(original) == 0.5f,
        descLens.get(original) == "test"
      )
    },
    test("Case class lenses support modifyOption") {
      val record = SimplePerson.schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val person = SimplePerson("Alice", 30)
      val result = nameLens.modifyOption(person, _.toLowerCase)
      assertTrue(result == Some(SimplePerson("alice", 30)))
    },
    test("Case class lenses support modifyOrFail") {
      val record = SimplePerson.schema.reflect.asRecord.get
      val ageLens = record.lensByIndex[Int](1).get
      val person = SimplePerson("Alice", 30)
      val result = ageLens.modifyOrFail(person, _ + 1)
      assertTrue(result == Right(SimplePerson("Alice", 31)))
    }
  )

  // Sealed trait optics tests - exercises buildSealedTraitOptics code paths
  val sealedTraitOpticsTests = suite("buildSealedTraitOptics coverage")(
    test("Simple sealed trait has prisms for all cases") {
      val variant = SimpleStatus.schema.reflect.asVariant.get
      assertTrue(
        variant.cases.size == 2,
        variant.prismByIndex[Active.type](0).isDefined,
        variant.prismByIndex[Inactive.type](1).isDefined
      )
    },
    test("Shape sealed trait has prisms for all cases") {
      val variant = Shape.schema.reflect.asVariant.get
      assertTrue(
        variant.cases.size == 3,
        variant.prismByIndex[Circle](0).isDefined,
        variant.prismByIndex[Rectangle](1).isDefined,
        variant.prismByIndex[Triangle](2).isDefined
      )
    },
    test("Enum has prisms for all cases") {
      val variant = Color.schema.reflect.asVariant.get
      assertTrue(
        variant.cases.size == 3,
        variant.prismByIndex[Color](0).isDefined,
        variant.prismByIndex[Color](1).isDefined,
        variant.prismByIndex[Color](2).isDefined
      )
    },
    test("Prism getOption works for matching case") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle: Shape = Circle(5.0)
      val rect: Shape = Rectangle(3.0, 4.0)
      assertTrue(
        circlePrism.getOption(circle).isDefined,
        circlePrism.getOption(rect).isEmpty
      )
    },
    test("Prism getOrFail works correctly") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle: Shape = Circle(5.0)
      val rect: Shape = Rectangle(3.0, 4.0)
      assertTrue(
        circlePrism.getOrFail(circle).isRight,
        circlePrism.getOrFail(rect).isLeft
      )
    },
    test("Prism reverseGet works correctly") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle = Circle(5.0)
      val result = circlePrism.reverseGet(circle)
      assertTrue(result == circle)
    },
    test("Prism replace works for matching case") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle: Shape = Circle(5.0)
      val newCircle = Circle(10.0)
      val replaced = circlePrism.replace(circle, newCircle)
      assertTrue(replaced == newCircle)
    },
    test("Prism replace returns original for non-matching case") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val rect: Shape = Rectangle(3.0, 4.0)
      val newCircle = Circle(10.0)
      val replaced = circlePrism.replace(rect, newCircle)
      assertTrue(replaced == rect)
    },
    test("Prism replaceOption works correctly") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle: Shape = Circle(5.0)
      val rect: Shape = Rectangle(3.0, 4.0)
      val newCircle = Circle(10.0)
      assertTrue(
        circlePrism.replaceOption(circle, newCircle) == Some(newCircle),
        circlePrism.replaceOption(rect, newCircle).isEmpty
      )
    },
    test("Prism check works correctly") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val circle: Shape = Circle(5.0)
      val rect: Shape = Rectangle(3.0, 4.0)
      assertTrue(
        circlePrism.check(circle).isEmpty,
        circlePrism.check(rect).isDefined
      )
    },
    test("Prism toDynamic returns correct path") {
      val variant = Shape.schema.reflect.asVariant.get
      val circlePrism = variant.prismByIndex[Circle](0).get
      val dynamic = circlePrism.toDynamic
      assertTrue(dynamic.nodes.nonEmpty)
    }
  )

  // NameTransformer tests - exercises encode for special character coverage
  val nameTransformerTests = suite("NameTransformer.encode coverage")(
    test("Plain identifier passes through unchanged") {
      val result = NameTransformer.encode("normalName")
      assertTrue(result == "normalName")
    },
    test("Tilde is encoded") {
      val result = NameTransformer.encode("~")
      assertTrue(result.contains("$tilde"))
    },
    test("Equals is encoded") {
      val result = NameTransformer.encode("=")
      assertTrue(result.contains("$eq"))
    },
    test("Less than is encoded") {
      val result = NameTransformer.encode("<")
      assertTrue(result.contains("$less"))
    },
    test("Greater than is encoded") {
      val result = NameTransformer.encode(">")
      assertTrue(result.contains("$greater"))
    },
    test("Bang is encoded") {
      val result = NameTransformer.encode("!")
      assertTrue(result.contains("$bang"))
    },
    test("Hash is encoded") {
      val result = NameTransformer.encode("#")
      assertTrue(result.contains("$hash"))
    },
    test("Percent is encoded") {
      val result = NameTransformer.encode("%")
      assertTrue(result.contains("$percent"))
    },
    test("Caret is encoded") {
      val result = NameTransformer.encode("^")
      assertTrue(result.contains("$up"))
    },
    test("Ampersand is encoded") {
      val result = NameTransformer.encode("&")
      assertTrue(result.contains("$amp"))
    },
    test("Pipe is encoded") {
      val result = NameTransformer.encode("|")
      assertTrue(result.contains("$bar"))
    },
    test("Star is encoded") {
      val result = NameTransformer.encode("*")
      assertTrue(result.contains("$times"))
    },
    test("Slash is encoded") {
      val result = NameTransformer.encode("/")
      assertTrue(result.contains("$div"))
    },
    test("Plus is encoded") {
      val result = NameTransformer.encode("+")
      assertTrue(result.contains("$plus"))
    },
    test("Minus is encoded") {
      val result = NameTransformer.encode("-")
      assertTrue(result.contains("$minus"))
    },
    test("Colon is encoded") {
      val result = NameTransformer.encode(":")
      assertTrue(result.contains("$colon"))
    },
    test("Backslash is encoded") {
      val result = NameTransformer.encode("\\")
      assertTrue(result.contains("$bslash"))
    },
    test("Question mark is encoded") {
      val result = NameTransformer.encode("?")
      assertTrue(result.contains("$qmark"))
    },
    test("At sign is encoded") {
      val result = NameTransformer.encode("@")
      assertTrue(result.contains("$at"))
    },
    test("Mixed identifier with operators") {
      val result = NameTransformer.encode("foo+bar")
      assertTrue(result.contains("$plus"))
    },
    test("Empty string returns empty") {
      val result = NameTransformer.encode("")
      assertTrue(result == "")
    },
    test("Multiple operators in sequence") {
      val result = NameTransformer.encode("++--")
      assertTrue(
        result.contains("$plus") && result.contains("$minus")
      )
    },
    test("Operator at end of identifier") {
      val result = NameTransformer.encode("name+")
      assertTrue(result.contains("$plus"))
    }
  )

  // DynamicOptic tests - exercises the Node variant prism matchers, constructors, deconstructors
  val dynamicOpticTests = suite("DynamicOptic.Node coverage")(
    test("DynamicOptic.Node schema has all 10 cases") {
      val nodeSchema = Schema[DynamicOptic.Node]
      val variant = nodeSchema.reflect.asVariant.get
      assertTrue(variant.cases.size == 10)
    },
    test("Field node round-trip through toDynamicValue/fromDynamicValue") {
      val field = DynamicOptic.Node.Field("testField")
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(field)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(field))
    },
    test("Case node round-trip through toDynamicValue/fromDynamicValue") {
      val caseNode = DynamicOptic.Node.Case("TestCase")
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(caseNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(caseNode))
    },
    test("AtIndex node round-trip through toDynamicValue/fromDynamicValue") {
      val atIndex = DynamicOptic.Node.AtIndex(42)
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(atIndex)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atIndex))
    },
    test("AtMapKey node round-trip through toDynamicValue/fromDynamicValue") {
      val key = DynamicValue.Primitive(PrimitiveValue.String("myKey"))
      val atMapKey = DynamicOptic.Node.AtMapKey(key)
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(atMapKey)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atMapKey))
    },
    test("AtIndices node round-trip through toDynamicValue/fromDynamicValue") {
      val atIndices = DynamicOptic.Node.AtIndices(Vector(1, 2, 3))
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(atIndices)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atIndices))
    },
    test("AtMapKeys node round-trip through toDynamicValue/fromDynamicValue") {
      val keys = Vector(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2))
      )
      val atMapKeys = DynamicOptic.Node.AtMapKeys(keys)
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(atMapKeys)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atMapKeys))
    },
    test("Elements node round-trip through toDynamicValue/fromDynamicValue") {
      val elements = DynamicOptic.Node.Elements
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(elements)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(elements))
    },
    test("MapKeys node round-trip through toDynamicValue/fromDynamicValue") {
      val mapKeys = DynamicOptic.Node.MapKeys
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(mapKeys)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(mapKeys))
    },
    test("MapValues node round-trip through toDynamicValue/fromDynamicValue") {
      val mapValues = DynamicOptic.Node.MapValues
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(mapValues)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(mapValues))
    },
    test("Wrapped node round-trip through toDynamicValue/fromDynamicValue") {
      val wrapped = DynamicOptic.Node.Wrapped
      val schema = Schema[DynamicOptic.Node]
      val dv = schema.reflect.toDynamicValue(wrapped)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(wrapped))
    },
    test("DynamicOptic round-trip through toDynamicValue/fromDynamicValue") {
      val optic = DynamicOptic(Vector(
        DynamicOptic.Node.Field("name"),
        DynamicOptic.Node.AtIndex(0),
        DynamicOptic.Node.Elements
      ))
      val schema = Schema[DynamicOptic]
      val dv = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("DynamicOptic.toString renders correctly for Field") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.Field("test")))
      assertTrue(optic.toString == ".test")
    },
    test("DynamicOptic.toString renders correctly for Case") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.Case("TestCase")))
      assertTrue(optic.toString.contains("TestCase"))
    },
    test("DynamicOptic.toString renders correctly for AtIndex") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.AtIndex(5)))
      assertTrue(optic.toString.contains("[5]"))
    },
    test("DynamicOptic.toScalaString renders correctly for Field") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.Field("test")))
      assertTrue(optic.toScalaString == ".test")
    },
    test("DynamicOptic.toScalaString renders correctly for Elements") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.Elements))
      assertTrue(optic.toScalaString.contains("each"))
    },
    test("DynamicOptic root is empty") {
      assertTrue(DynamicOptic.root.nodes.isEmpty)
    },
    test("Node prism for Field matches Field and rejects others") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val fieldPrism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      val field: DynamicOptic.Node = DynamicOptic.Node.Field("test")
      val caseNode: DynamicOptic.Node = DynamicOptic.Node.Case("test")
      assertTrue(
        fieldPrism.getOption(field).isDefined,
        fieldPrism.getOption(caseNode).isEmpty
      )
    },
    test("Node prism for Case matches Case and rejects others") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val casePrism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      val caseNode: DynamicOptic.Node = DynamicOptic.Node.Case("test")
      val atIndex: DynamicOptic.Node = DynamicOptic.Node.AtIndex(0)
      assertTrue(
        casePrism.getOption(caseNode).isDefined,
        casePrism.getOption(atIndex).isEmpty
      )
    },
    test("Node prism for AtIndex matches AtIndex and rejects others") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val atIndexPrism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      val atIndex: DynamicOptic.Node = DynamicOptic.Node.AtIndex(5)
      val elements: DynamicOptic.Node = DynamicOptic.Node.Elements
      assertTrue(
        atIndexPrism.getOption(atIndex).isDefined,
        atIndexPrism.getOption(elements).isEmpty
      )
    },
    test("Node prism for MapKeys matches MapKeys and rejects others") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val mapKeysPrism = variant.prismByIndex[DynamicOptic.Node.MapKeys.type](7).get
      val mapKeys: DynamicOptic.Node = DynamicOptic.Node.MapKeys
      val wrapped: DynamicOptic.Node = DynamicOptic.Node.Wrapped
      assertTrue(
        mapKeysPrism.getOption(mapKeys).isDefined,
        mapKeysPrism.getOption(wrapped).isEmpty
      )
    },
    test("Node prism for Wrapped matches Wrapped and rejects others") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val wrappedPrism = variant.prismByIndex[DynamicOptic.Node.Wrapped.type](9).get
      val wrapped: DynamicOptic.Node = DynamicOptic.Node.Wrapped
      val mapValues: DynamicOptic.Node = DynamicOptic.Node.MapValues
      assertTrue(
        wrappedPrism.getOption(wrapped).isDefined,
        wrappedPrism.getOption(mapValues).isEmpty
      )
    },
    // Exhaustive prism rejection tests for all 10 node types
    test("All prisms reject all non-matching types - AtMapKey") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      val target = DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k")))
      val allOthers: Seq[DynamicOptic.Node] = Seq(
        DynamicOptic.Node.Field("f"), DynamicOptic.Node.Case("c"),
        DynamicOptic.Node.AtIndex(0), DynamicOptic.Node.AtIndices(Seq(0)),
        DynamicOptic.Node.AtMapKeys(Seq()), DynamicOptic.Node.Elements,
        DynamicOptic.Node.MapKeys, DynamicOptic.Node.MapValues, DynamicOptic.Node.Wrapped
      )
      assertTrue(
        prism.getOption(target).isDefined,
        allOthers.forall(n => prism.getOption(n).isEmpty)
      )
    },
    test("All prisms reject all non-matching types - AtIndices") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndices](4).get
      val target = DynamicOptic.Node.AtIndices(Seq(0, 1))
      val allOthers: Seq[DynamicOptic.Node] = Seq(
        DynamicOptic.Node.Field("f"), DynamicOptic.Node.Case("c"),
        DynamicOptic.Node.AtIndex(0), DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
        DynamicOptic.Node.AtMapKeys(Seq()), DynamicOptic.Node.Elements,
        DynamicOptic.Node.MapKeys, DynamicOptic.Node.MapValues, DynamicOptic.Node.Wrapped
      )
      assertTrue(
        prism.getOption(target).isDefined,
        allOthers.forall(n => prism.getOption(n).isEmpty)
      )
    },
    test("All prisms reject all non-matching types - AtMapKeys") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKeys](5).get
      val target = DynamicOptic.Node.AtMapKeys(Seq(DynamicValue.Primitive(PrimitiveValue.String("k"))))
      val allOthers: Seq[DynamicOptic.Node] = Seq(
        DynamicOptic.Node.Field("f"), DynamicOptic.Node.Case("c"),
        DynamicOptic.Node.AtIndex(0), DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
        DynamicOptic.Node.AtIndices(Seq(0)), DynamicOptic.Node.Elements,
        DynamicOptic.Node.MapKeys, DynamicOptic.Node.MapValues, DynamicOptic.Node.Wrapped
      )
      assertTrue(
        prism.getOption(target).isDefined,
        allOthers.forall(n => prism.getOption(n).isEmpty)
      )
    },
    test("All prisms reject all non-matching types - Elements") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val prism = variant.prismByIndex[DynamicOptic.Node.Elements.type](6).get
      val target = DynamicOptic.Node.Elements
      val allOthers: Seq[DynamicOptic.Node] = Seq(
        DynamicOptic.Node.Field("f"), DynamicOptic.Node.Case("c"),
        DynamicOptic.Node.AtIndex(0), DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
        DynamicOptic.Node.AtIndices(Seq(0)), DynamicOptic.Node.AtMapKeys(Seq()),
        DynamicOptic.Node.MapKeys, DynamicOptic.Node.MapValues, DynamicOptic.Node.Wrapped
      )
      assertTrue(
        prism.getOption(target).isDefined,
        allOthers.forall(n => prism.getOption(n).isEmpty)
      )
    },
    test("All prisms reject all non-matching types - MapValues") {
      val variant = Schema[DynamicOptic.Node].reflect.asVariant.get
      val prism = variant.prismByIndex[DynamicOptic.Node.MapValues.type](8).get
      val target = DynamicOptic.Node.MapValues
      val allOthers: Seq[DynamicOptic.Node] = Seq(
        DynamicOptic.Node.Field("f"), DynamicOptic.Node.Case("c"),
        DynamicOptic.Node.AtIndex(0), DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
        DynamicOptic.Node.AtIndices(Seq(0)), DynamicOptic.Node.AtMapKeys(Seq()),
        DynamicOptic.Node.Elements, DynamicOptic.Node.MapKeys, DynamicOptic.Node.Wrapped
      )
      assertTrue(
        prism.getOption(target).isDefined,
        allOthers.forall(n => prism.getOption(n).isEmpty)
      )
    }
  )
}
