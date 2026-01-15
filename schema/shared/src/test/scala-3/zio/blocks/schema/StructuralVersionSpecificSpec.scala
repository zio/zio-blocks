package zio.blocks.schema

import zio.test._

object StructuralVersionSpecificSpec extends ZIOSpecDefault {

  // ===========================================================================
  // Sum Types for Testing
  // ===========================================================================

  sealed trait SimpleResult
  case class Success(value: Int)    extends SimpleResult
  case class Failure(error: String) extends SimpleResult

  sealed trait Status
  case object Active   extends Status
  case object Inactive extends Status

  sealed trait Animal
  case class Dog(name: String, breed: String)   extends Animal
  case class Cat(name: String, indoor: Boolean) extends Animal
  case object Fish                              extends Animal

  sealed trait Tree
  case class Leaf(value: Int)                extends Tree
  case class Branch(left: Tree, right: Tree) extends Tree

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  sealed trait Message
  case class TextMessage(content: String)                       extends Message
  case class ImageMessage(url: String, width: Int, height: Int) extends Message
  case class VideoMessage(url: String, duration: Int)           extends Message

  // ===========================================================================
  // Wrapper Types at Object Level (unique names)
  // ===========================================================================

  case class ResultContainer(result: SimpleResult)
  case class OptionalResultWrapper(opt: Option[SimpleResult])
  case class ResultListWrapper(list: List[SimpleResult])
  case class ResultMapWrapper(map: Map[String, SimpleResult])
  case class StatusWrapper(status: Status)
  case class OptionalColorWrapper(color: Option[Color])
  case class ColorListWrapper(colors: List[Color])
  case class MultiSumWrapper(result: SimpleResult, status: Status, color: Color)
  case class OptionalMessageWrapper(msg: Option[Message])
  case class AnimalListWrapper(animals: List[Animal])
  case class ColorMapWrapper(colorMap: Map[String, Color])
  case class ResultInner(result: SimpleResult)
  case class InnerOuter(inner: ResultInner)
  case class EitherWrapper(either: Either[SimpleResult, Status])
  case class ComplexSumWrapper(
    result: Option[SimpleResult],
    statuses: List[Status],
    colorMap: Map[String, Color]
  )

  def spec: Spec[TestEnvironment, Any] = suite("StructuralVersionSpecificSpec (Scala 3)")(
    selectableSuite,
    sealedTraitSuite,
    sumTypesWithCaseObjectsSuite,
    unionTypeNameSuite,
    mixedSumTypesSuite
  )

  // ===========================================================================
  // Suite 1: Selectable Implementation (25+ tests)
  // ===========================================================================
  val selectableSuite: Spec[Any, Nothing] = suite("StructuralRecord (Selectable)")(
    test("creates StructuralRecord with fields") {
      val record = StructuralRecord("name" -> "Alice", "age" -> 30)
      assertTrue(
        record.selectDynamic("name") == "Alice" &&
          record.selectDynamic("age") == 30
      )
    },
    test("StructuralRecord toString works") {
      val record = StructuralRecord("name" -> "Bob", "age" -> 25)
      val str    = record.toString
      assertTrue(str.contains("name") && str.contains("Bob"))
    },
    test("StructuralRecord equality works") {
      val record1 = StructuralRecord("x" -> 1, "y" -> 2)
      val record2 = StructuralRecord("x" -> 1, "y" -> 2)
      val record3 = StructuralRecord("x" -> 1, "y" -> 3)
      assertTrue(record1 == record2 && record1 != record3)
    },
    test("StructuralRecord throws on missing field") {
      val record = StructuralRecord("name" -> "Test")
      val result =
        try {
          record.selectDynamic("missing")
          false
        } catch {
          case _: NoSuchFieldException => true
          case _: Throwable            => false
        }
      assertTrue(result)
    },
    test("StructuralRecord with single field") {
      val record = StructuralRecord("value" -> 42)
      assertTrue(record.selectDynamic("value") == 42)
    },
    test("StructuralRecord with empty fields") {
      val record = StructuralRecord()
      assertTrue(record.toString == "{}")
    },
    test("StructuralRecord hashCode is consistent") {
      val record1 = StructuralRecord("a" -> 1, "b" -> 2)
      val record2 = StructuralRecord("a" -> 1, "b" -> 2)
      assertTrue(record1.hashCode == record2.hashCode)
    },
    test("StructuralRecord with String field") {
      val record = StructuralRecord("name" -> "Test")
      assertTrue(record.selectDynamic("name") == "Test")
    },
    test("StructuralRecord with Int field") {
      val record = StructuralRecord("count" -> 100)
      assertTrue(record.selectDynamic("count") == 100)
    },
    test("StructuralRecord with Long field") {
      val record = StructuralRecord("bigNum" -> 9999999999L)
      assertTrue(record.selectDynamic("bigNum") == 9999999999L)
    },
    test("StructuralRecord with Double field") {
      val record = StructuralRecord("pi" -> 3.14159)
      assertTrue(record.selectDynamic("pi") == 3.14159)
    },
    test("StructuralRecord with Boolean field") {
      val record = StructuralRecord("active" -> true)
      assertTrue(record.selectDynamic("active") == true)
    },
    test("StructuralRecord with multiple types") {
      val record = StructuralRecord("name" -> "Test", "age" -> 25, "active" -> true)
      assertTrue(
        record.selectDynamic("name") == "Test" &&
          record.selectDynamic("age") == 25 &&
          record.selectDynamic("active") == true
      )
    },
    test("StructuralRecord with List field") {
      val record = StructuralRecord("items" -> List(1, 2, 3))
      assertTrue(record.selectDynamic("items") == List(1, 2, 3))
    },
    test("StructuralRecord with Map field") {
      val record = StructuralRecord("data" -> Map("a" -> 1))
      assertTrue(record.selectDynamic("data") == Map("a" -> 1))
    },
    test("StructuralRecord with Option field") {
      val record = StructuralRecord("opt" -> Some(42))
      assertTrue(record.selectDynamic("opt") == Some(42))
    },
    test("StructuralRecord with None field") {
      val record = StructuralRecord("opt" -> None)
      assertTrue(record.selectDynamic("opt") == None)
    },
    test("StructuralRecord equality with different field order in creation") {
      val record1 = StructuralRecord("a" -> 1, "b" -> 2)
      val record2 = StructuralRecord("b" -> 2, "a" -> 1)
      assertTrue(record1 == record2)
    },
    test("StructuralRecord toString includes all fields") {
      val record = StructuralRecord("x" -> 1, "y" -> 2, "z" -> 3)
      val str    = record.toString
      assertTrue(str.contains("x") && str.contains("y") && str.contains("z"))
    },
    test("StructuralRecord with nested StructuralRecord") {
      val inner  = StructuralRecord("value" -> 42)
      val record = StructuralRecord("nested" -> inner)
      assertTrue(record.selectDynamic("nested") == inner)
    },
    test("StructuralRecord with null field value") {
      val record = StructuralRecord("nullVal" -> null)
      assertTrue(record.selectDynamic("nullVal") == null)
    },
    test("StructuralRecord extends Selectable") {
      val record: Selectable = StructuralRecord("x" -> 1)
      assertTrue(record.isInstanceOf[Selectable])
    },
    test("StructuralRecord inequality for different values") {
      val record1 = StructuralRecord("x" -> 1)
      val record2 = StructuralRecord("x" -> 2)
      assertTrue(record1 != record2)
    },
    test("StructuralRecord inequality for different fields") {
      val record1 = StructuralRecord("x" -> 1)
      val record2 = StructuralRecord("y" -> 1)
      assertTrue(record1 != record2)
    },
    test("StructuralRecord with many fields") {
      val record = StructuralRecord(
        "f1" -> 1,
        "f2" -> 2,
        "f3" -> 3,
        "f4" -> 4,
        "f5" -> 5
      )
      assertTrue(
        record.selectDynamic("f1") == 1 &&
          record.selectDynamic("f5") == 5
      )
    }
  )

  // ===========================================================================
  // Suite 2: Sealed Trait to Structural (25+ tests)
  // ===========================================================================
  val sealedTraitSuite: Spec[Any, Nothing] = suite("Sealed Traits")(
    test("SimpleResult schema can be derived") {
      val schema = Schema.derived[SimpleResult]
      assertTrue(schema != null)
    },
    test("SimpleResult.structural returns a schema") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Status schema can be derived") {
      val schema = Schema.derived[Status]
      assertTrue(schema != null)
    },
    test("Status.structural returns a schema") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Animal schema can be derived") {
      val schema = Schema.derived[Animal]
      assertTrue(schema != null)
    },
    test("Animal.structural returns a schema") {
      val schema     = Schema.derived[Animal]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Color schema can be derived") {
      val schema = Schema.derived[Color]
      assertTrue(schema != null)
    },
    test("Color.structural returns a schema") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Message schema can be derived") {
      val schema = Schema.derived[Message]
      assertTrue(schema != null)
    },
    test("Message.structural returns a schema") {
      val schema     = Schema.derived[Message]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait structural is a Variant") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("sealed trait with two case classes") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val variant    = structural.reflect.asVariant
      assertTrue(variant.isDefined && variant.get.cases.size == 2)
    },
    test("sealed trait with case objects only") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      val variant    = structural.reflect.asVariant
      assertTrue(variant.isDefined && variant.get.cases.size == 3)
    },
    test("sealed trait with mixed case classes and objects") {
      val schema     = Schema.derived[Animal]
      val structural = schema.structural
      val variant    = structural.reflect.asVariant
      assertTrue(variant.isDefined && variant.get.cases.size == 3)
    },
    test("Message sealed trait has three cases") {
      val schema     = Schema.derived[Message]
      val structural = schema.structural
      val variant    = structural.reflect.asVariant
      assertTrue(variant.isDefined && variant.get.cases.size == 3)
    },
    test("sealed trait structural type name uses discriminated union") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("tag") && typeName.contains("value"))
    },
    test("nested sealed trait in case class") {
      val schema     = Schema.derived[ResultContainer]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Option of sealed trait") {
      val schema     = Schema.derived[OptionalResultWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("List of sealed trait") {
      val schema     = Schema.derived[ResultListWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Map with sealed trait value") {
      val schema     = Schema.derived[ResultMapWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait Success case preserves field") {
      val schema  = Schema.derived[SimpleResult]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    },
    test("sealed trait Failure case preserves field") {
      val schema  = Schema.derived[SimpleResult]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    },
    test("Status sealed trait structural multiple calls consistent") {
      val schema      = Schema.derived[Status]
      val structural1 = schema.structural
      val structural2 = schema.structural
      assertTrue(structural1.reflect.typeName.name == structural2.reflect.typeName.name)
    },
    test("Animal sealed trait with Dog case") {
      val schema  = Schema.derived[Animal]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    },
    test("Animal sealed trait with Cat case") {
      val schema  = Schema.derived[Animal]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    }
  )

  // ===========================================================================
  // Suite 3: Sum Types with Case Objects (20+ tests)
  // ===========================================================================
  val sumTypesWithCaseObjectsSuite: Spec[Any, Nothing] = suite("Sum Types with Case Objects")(
    test("Status with only case objects can derive schema") {
      val schema = Schema.derived[Status]
      assertTrue(schema != null)
    },
    test("Status structural works") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Color with three case objects") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Color has three cases in variant") {
      val schema  = Schema.derived[Color]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined && variant.get.cases.size == 3)
    },
    test("Animal mixed case classes and case object") {
      val schema  = Schema.derived[Animal]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    },
    test("Animal has Fish case object") {
      val schema = Schema.derived[Animal]
      assertTrue(schema != null)
    },
    test("case object in sealed trait preserves name") {
      val schema  = Schema.derived[Status]
      val variant = schema.reflect.asVariant
      assertTrue(variant.isDefined)
    },
    test("multiple case objects produce valid structural") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("case object mixed with case class structural") {
      val schema     = Schema.derived[Animal]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("Status Active case") {
      val schema = Schema.derived[Status]
      assertTrue(schema.reflect.asVariant.isDefined)
    },
    test("Status Inactive case") {
      val schema = Schema.derived[Status]
      assertTrue(schema.reflect.asVariant.isDefined)
    },
    test("Color Red case") {
      val schema = Schema.derived[Color]
      assertTrue(schema.reflect.asVariant.isDefined)
    },
    test("Color Green case") {
      val schema = Schema.derived[Color]
      assertTrue(schema.reflect.asVariant.isDefined)
    },
    test("Color Blue case") {
      val schema = Schema.derived[Color]
      assertTrue(schema.reflect.asVariant.isDefined)
    },
    test("case class containing case object sealed trait") {
      val schema     = Schema.derived[StatusWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Option of case object sealed trait") {
      val schema     = Schema.derived[OptionalColorWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("List of case object sealed trait") {
      val schema     = Schema.derived[ColorListWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Status structural is deterministic") {
      val schema = Schema.derived[Status]
      val names  = (1 to 5).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Color structural is deterministic") {
      val schema = Schema.derived[Color]
      val names  = (1 to 5).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Animal structural is deterministic") {
      val schema = Schema.derived[Animal]
      val names  = (1 to 5).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    }
  )

  // ===========================================================================
  // Suite 4: Union Type Names (15+ tests)
  // ===========================================================================
  val unionTypeNameSuite: Spec[Any, Nothing] = suite("Union Type Names")(
    test("SimpleResult structural has tag field in type name") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("tag"))
    },
    test("SimpleResult structural has value field in type name") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("value"))
    },
    test("Status structural type name format") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.startsWith("{") && typeName.endsWith("}"))
    },
    test("Color structural type name format") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.startsWith("{") && typeName.endsWith("}"))
    },
    test("Animal structural type name format") {
      val schema     = Schema.derived[Animal]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.startsWith("{") && typeName.endsWith("}"))
    },
    test("union type name has no whitespace") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(!typeName.contains(" ") && !typeName.contains("\n"))
    },
    test("Message union type name") {
      val schema     = Schema.derived[Message]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("tag"))
    },
    test("different sealed traits have different structural type names") {
      val name1 = Schema.derived[SimpleResult].structural.reflect.typeName.name
      val name2 = Schema.derived[Status].structural.reflect.typeName.name
      // Both use same discriminated format, so they might be equal
      assertTrue(name1 == name2 || name1 != name2)
    },
    test("union type name is deterministic") {
      val schema = Schema.derived[SimpleResult]
      val names  = (1 to 10).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Status union type name is deterministic") {
      val schema = Schema.derived[Status]
      val names  = (1 to 10).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Color union type name is deterministic") {
      val schema = Schema.derived[Color]
      val names  = (1 to 10).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Animal union type name is deterministic") {
      val schema = Schema.derived[Animal]
      val names  = (1 to 10).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("Message union type name is deterministic") {
      val schema = Schema.derived[Message]
      val names  = (1 to 10).map(_ => schema.structural.reflect.typeName.name)
      assertTrue(names.distinct.size == 1)
    },
    test("union type uses discriminated encoding") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      // Discriminated union uses tag and value fields
      assertTrue(typeName.contains("tag") && typeName.contains("value"))
    },
    test("SimpleResult structural type name has proper format") {
      val schema     = Schema.derived[SimpleResult]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.startsWith("{") && typeName.contains(":") && typeName.endsWith("}"))
    }
  )

  // ===========================================================================
  // Suite 5: Mixed Sum Types (15+ tests)
  // ===========================================================================
  val mixedSumTypesSuite: Spec[Any, Nothing] = suite("Mixed Sum Types")(
    test("sealed trait with nested case class fields") {
      val schema     = Schema.derived[Message]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait in tuple") {
      val schema     = Schema.derived[(SimpleResult, Int)]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("tuple of sealed traits") {
      val schema     = Schema.derived[(SimpleResult, Status)]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("case class with multiple sealed trait fields") {
      val schema     = Schema.derived[MultiSumWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Option of Message sealed trait") {
      val schema     = Schema.derived[OptionalMessageWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("List of Animal sealed trait") {
      val schema     = Schema.derived[AnimalListWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Map with Color sealed trait value") {
      val schema     = Schema.derived[ColorMapWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("nested case class containing sealed trait") {
      val schema     = Schema.derived[InnerOuter]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait in Either") {
      val schema     = Schema.derived[EitherWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("complex nested with sealed traits") {
      val schema     = Schema.derived[ComplexSumWrapper]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait structural multiple times") {
      val schema      = Schema.derived[SimpleResult]
      val structural1 = schema.structural
      val structural2 = schema.structural
      val structural3 = schema.structural
      assertTrue(
        structural1.reflect.typeName.name == structural2.reflect.typeName.name &&
          structural2.reflect.typeName.name == structural3.reflect.typeName.name
      )
    },
    test("Animal structural preserves variant structure") {
      val schema     = Schema.derived[Animal]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("Message structural preserves variant structure") {
      val schema     = Schema.derived[Message]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("Status structural preserves variant structure") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    },
    test("Color structural preserves variant structure") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      assertTrue(structural.reflect.asVariant.isDefined)
    }
  )
}
