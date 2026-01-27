package zio.blocks.schema

import zio.test._
import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding.Binding

/**
 * Coverage tests for ReflectPrinter to exercise more branches.
 */
object ReflectPrinterCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ReflectPrinterCoverageSpec")(
    printRecordTests,
    printVariantTests,
    printSequenceTests,
    printMapTests,
    printWrapperTests,
    printTermTests,
    sdlTypeNameTests,
    nestedStructureTests,
    advancedTypeReprTests
  )

  // Test data models
  case class Empty()
  object Empty {
    implicit val schema: Schema[Empty] = Schema.derived
  }

  case class SingleField(value: String)
  object SingleField {
    implicit val schema: Schema[SingleField] = Schema.derived
  }

  case class MultiField(name: String, age: Int, active: Boolean)
  object MultiField {
    implicit val schema: Schema[MultiField] = Schema.derived
  }

  case class Nested(inner: SingleField)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  case class DeepNested(level1: Nested)
  object DeepNested {
    implicit val schema: Schema[DeepNested] = Schema.derived
  }

  sealed trait SimpleVariant
  case class CaseA(x: Int) extends SimpleVariant
  case class CaseB(y: String) extends SimpleVariant
  case object CaseC extends SimpleVariant
  object SimpleVariant {
    implicit val schema: Schema[SimpleVariant] = Schema.derived
  }

  case class WithSequence(items: List[String])
  object WithSequence {
    implicit val schema: Schema[WithSequence] = Schema.derived
  }

  case class WithMap(data: Map[String, Int])
  object WithMap {
    implicit val schema: Schema[WithMap] = Schema.derived
  }

  // Print record tests
  val printRecordTests = suite("printRecord")(
    test("Print empty record") {
      val record = Schema[Empty].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(result.contains("record Empty"))
    },
    test("Print single field record") {
      val record = Schema[SingleField].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(
        result.contains("record SingleField"),
        result.contains("value")
      )
    },
    test("Print multi field record") {
      val record = Schema[MultiField].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(
        result.contains("name"),
        result.contains("age"),
        result.contains("active")
      )
    },
    test("Print nested record") {
      val record = Schema[Nested].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(result.contains("inner"))
    }
  )

  // Print variant tests
  val printVariantTests = suite("printVariant")(
    test("Print variant with cases") {
      val variant = Schema[SimpleVariant].reflect.asVariant.get
      val result = ReflectPrinter.printVariant(variant)
      assertTrue(
        result.contains("variant SimpleVariant"),
        result.contains("CaseA"),
        result.contains("CaseB"),
        result.contains("CaseC")
      )
    },
    test("Print Either variant") {
      val variant = Schema[Either[String, Int]].reflect.asVariant.get
      val result = ReflectPrinter.printVariant(variant)
      assertTrue(
        result.contains("variant"),
        result.contains("Left") || result.contains("Right")
      )
    },
    test("Print Option variant") {
      val variant = Schema[Option[String]].reflect.asVariant.get
      val result = ReflectPrinter.printVariant(variant)
      assertTrue(result.contains("variant"))
    }
  )

  // Print sequence tests
  val printSequenceTests = suite("printSequence")(
    test("Print List sequence") {
      val schema = Schema[List[String]]
      val seq = schema.reflect match {
        case s: Reflect.Sequence[_, _, _] => Some(s)
        case _ => None
      }
      assertTrue(seq.isDefined)
      val result = ReflectPrinter.printSequence(seq.get.asInstanceOf[Reflect.Sequence[Binding, String, List]])
      assertTrue(result.contains("sequence"))
    },
    test("Print Vector sequence") {
      val schema = Schema[Vector[Int]]
      val seq = schema.reflect match {
        case s: Reflect.Sequence[_, _, _] => Some(s)
        case _ => None
      }
      assertTrue(seq.isDefined)
    },
    test("Print Set sequence") {
      val schema = Schema[Set[String]]
      val seq = schema.reflect match {
        case s: Reflect.Sequence[_, _, _] => Some(s)
        case _ => None
      }
      assertTrue(seq.isDefined)
    }
  )

  // Print map tests
  val printMapTests = suite("printMap")(
    test("Print String to Int map") {
      val schema = Schema[Map[String, Int]]
      val map = schema.reflect match {
        case m: Reflect.Map[_, _, _, _] => Some(m)
        case _ => None
      }
      assertTrue(map.isDefined)
      val result = ReflectPrinter.printMap(map.get.asInstanceOf[Reflect.Map[Binding, String, Int, scala.collection.immutable.Map]])
      assertTrue(result.contains("map"))
    }
  )

  // Print wrapper tests
  val printWrapperTests = suite("printWrapper")(
    test("Print Option wrapper") {
      // Option is typically a variant, not a wrapper, but we test what's available
      val schema = Schema[Option[Int]]
      val printed = schema.reflect match {
        case v: Reflect.Variant[_, _] => ReflectPrinter.printVariant(v)
        case w: Reflect.Wrapper[_, _, _] => ReflectPrinter.printWrapper(w.asInstanceOf[Reflect.Wrapper[Binding, Option[Int], Int]])
        case _ => "other"
      }
      assertTrue(printed.nonEmpty)
    }
  )

  // Print term tests
  val printTermTests = suite("printTerm")(
    test("Print term for record field") {
      val record = Schema[SingleField].reflect.asRecord.get
      val field = record.fields(0)
      val result = ReflectPrinter.printTerm(field)
      assertTrue(result.contains("value"))
    },
    test("Print term for variant case") {
      val variant = Schema[SimpleVariant].reflect.asVariant.get
      val case_ = variant.cases(0)
      val result = ReflectPrinter.printTerm(case_)
      assertTrue(result.contains("CaseA"))
    }
  )

  // SDL type name tests
  val sdlTypeNameTests = suite("sdlTypeName")(
    test("SDL name for primitive") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[String])
      assertTrue(sdl == "String")
    },
    test("SDL name for Int") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[Int])
      assertTrue(sdl == "Int")
    },
    test("SDL name for custom type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[SingleField])
      assertTrue(sdl == "SingleField")
    },
    test("SDL name for generic type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[List[String]])
      assertTrue(sdl.contains("List"))
    },
    test("SDL name for java.time type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[java.time.Instant])
      assertTrue(sdl.contains("java.time"))
    },
    test("SDL name for java.util type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[java.util.UUID])
      assertTrue(sdl.contains("java.util"))
    }
  )

  // Nested structure tests
  val nestedStructureTests = suite("Nested structures")(
    test("Print deeply nested record") {
      val record = Schema[DeepNested].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(
        result.contains("DeepNested"),
        result.contains("level1")
      )
    },
    test("Print record with sequence field") {
      val record = Schema[WithSequence].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(
        result.contains("WithSequence"),
        result.contains("items")
      )
    },
    test("Print record with map field") {
      val record = Schema[WithMap].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(
        result.contains("WithMap"),
        result.contains("data")
      )
    }
  )

  // Advanced TypeRepr tests - exercises Union, Intersection, Tuple, AppliedType branches
  val advancedTypeReprTests = suite("Advanced TypeRepr coverage")(
    test("SDL name for Union type via Either") {
      // Either is rendered as a variant with Left/Right
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[Either[String, Int]])
      assertTrue(sdl.contains("Either") || sdl.contains("String") || sdl.contains("Int"))
    },
    test("SDL name for tuple type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[(String, Int)])
      assertTrue(sdl.contains("Tuple") || sdl.contains("String") || sdl.contains(","))
    },
    test("SDL name for triple tuple type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[(String, Int, Boolean)])
      assertTrue(sdl.nonEmpty)
    },
    test("SDL name for nested generic type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[List[Option[String]]])
      assertTrue(sdl.contains("List") && sdl.contains("Option"))
    },
    test("SDL name for Map generic type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[Map[String, List[Int]]])
      assertTrue(sdl.contains("Map"))
    },
    test("SDL name for deeply nested generic") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[List[Map[String, Vector[Int]]]])
      assertTrue(sdl.contains("List") && sdl.contains("Map"))
    },
    test("SDL for all primitive types") {
      val intSdl = ReflectPrinter.sdlTypeName(TypeId.of[Int])
      val stringSdl = ReflectPrinter.sdlTypeName(TypeId.of[String])
      val boolSdl = ReflectPrinter.sdlTypeName(TypeId.of[Boolean])
      assertTrue(intSdl == "Int" && stringSdl == "String" && boolSdl == "Boolean")
    },
    test("Print record via public API") {
      val record = Schema[SingleField].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(result.contains("SingleField"))
    },
    test("Print variant via public API") {
      val variant = Schema[SimpleVariant].reflect.asVariant.get
      val result = ReflectPrinter.printVariant(variant)
      assertTrue(result.contains("SimpleVariant"))
    },
    test("Print sequence via public API") {
      val schema = Schema[List[String]]
      val seq = schema.reflect match {
        case s: Reflect.Sequence[_, _, _] => 
          ReflectPrinter.printSequence(s.asInstanceOf[Reflect.Sequence[binding.Binding, String, List]])
        case _ => "not a sequence"
      }
      assertTrue(seq.contains("sequence"))
    },
    test("Print map via public API") {
      val schema = Schema[Map[String, Int]]
      val map = schema.reflect match {
        case m: Reflect.Map[_, _, _, _] => 
          ReflectPrinter.printMap(m.asInstanceOf[Reflect.Map[binding.Binding, String, Int, scala.collection.immutable.Map]])
        case _ => "not a map"
      }
      assertTrue(map.contains("map"))
    },
    test("Print Option variant via public API") {
      val variant = Schema[Option[SingleField]].reflect.asVariant.get
      val result = ReflectPrinter.printVariant(variant)
      assertTrue(result.nonEmpty)
    },
    test("Print deeply nested record via public API") {
      case class Level3(value: String)
      object Level3 { implicit val schema: Schema[Level3] = Schema.derived }
      case class Level2(l3: Level3)
      object Level2 { implicit val schema: Schema[Level2] = Schema.derived }
      case class Level1(l2: Level2)
      object Level1 { implicit val schema: Schema[Level1] = Schema.derived }
      
      val record = Schema[Level1].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(result.contains("Level1") && result.contains("l2"))
    },
    test("Print record with all primitive types via public API") {
      case class AllPrimitives(
        b: Boolean,
        c: Char,
        by: Byte,
        sh: Short,
        i: Int,
        l: Long,
        f: Float,
        d: Double,
        s: String
      )
      object AllPrimitives { implicit val schema: Schema[AllPrimitives] = Schema.derived }
      
      val record = Schema[AllPrimitives].reflect.asRecord.get
      val result = ReflectPrinter.printRecord(record)
      assertTrue(result.contains("AllPrimitives"))
    },
    test("SDL for Array type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[Array[Byte]])
      assertTrue(sdl.contains("Array") || sdl.contains("Byte"))
    },
    test("SDL for BigDecimal type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[BigDecimal])
      assertTrue(sdl.contains("BigDecimal"))
    },
    test("SDL for BigInt type") {
      val sdl = ReflectPrinter.sdlTypeName(TypeId.of[BigInt])
      assertTrue(sdl.contains("BigInt"))
    },
    test("Print nested sequence via public API") {
      val schema = Schema[List[List[Int]]]
      val seq = schema.reflect match {
        case s: Reflect.Sequence[_, _, _] => 
          ReflectPrinter.printSequence(s.asInstanceOf[Reflect.Sequence[binding.Binding, List[Int], List]])
        case _ => "not a sequence"
      }
      assertTrue(seq.contains("sequence"))
    },
    test("Print nested map via public API") {
      val schema = Schema[Map[String, Map[String, Int]]]
      val map = schema.reflect match {
        case m: Reflect.Map[_, _, _, _] => 
          ReflectPrinter.printMap(m.asInstanceOf[Reflect.Map[binding.Binding, String, Map[String, Int], scala.collection.immutable.Map]])
        case _ => "not a map"
      }
      assertTrue(map.contains("map"))
    }
  )
}
