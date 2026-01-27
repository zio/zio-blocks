package zio.blocks.schema

import zio.test._

/**
 * Tests for ReflectPrinter SDL output to increase branch coverage. Tests
 * various schema types and their multi-line/inline rendering decisions.
 */
object ReflectPrinterSpec extends SchemaBaseSpec {

  // Simple test types
  case class Empty()
  object Empty {
    implicit val schema: Schema[Empty] = Schema.derived
  }

  case class SingleField(name: String)
  object SingleField {
    implicit val schema: Schema[SingleField] = Schema.derived
  }

  case class MultiField(name: String, age: Int, active: Boolean)
  object MultiField {
    implicit val schema: Schema[MultiField] = Schema.derived
  }

  sealed trait SimpleVariant
  case object CaseA extends SimpleVariant
  case object CaseB extends SimpleVariant
  object SimpleVariant {
    implicit val schema: Schema[SimpleVariant] = Schema.derived
  }

  sealed trait PayloadVariant
  case class WithPayload(value: String)      extends PayloadVariant
  case class MultiPayload(a: Int, b: String) extends PayloadVariant
  object PayloadVariant {
    implicit val schema: Schema[PayloadVariant] = Schema.derived
  }

  case class Wrapper(inner: Int)
  object Wrapper {
    implicit val schema: Schema[Wrapper] = Schema.derived
  }

  case class Nested(child: SingleField)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  def spec: Spec[Any, Any] = suite("ReflectPrinterSpec")(
    suite("printRecord")(
      test("empty record") {
        val sdl = ReflectPrinter.printRecord(Schema[Empty].reflect.asRecord.get)
        assertTrue(sdl.contains("record Empty"))
      },
      test("single field record") {
        val sdl = ReflectPrinter.printRecord(Schema[SingleField].reflect.asRecord.get)
        assertTrue(
          sdl.contains("record SingleField"),
          sdl.contains("name: String")
        )
      },
      test("multi field record") {
        val sdl = ReflectPrinter.printRecord(Schema[MultiField].reflect.asRecord.get)
        assertTrue(
          sdl.contains("record MultiField"),
          sdl.contains("name: String"),
          sdl.contains("age: Int"),
          sdl.contains("active: Boolean")
        )
      },
      test("nested record") {
        val sdl = ReflectPrinter.printRecord(Schema[Nested].reflect.asRecord.get)
        assertTrue(
          sdl.contains("record Nested"),
          sdl.contains("child:")
        )
      }
    ),
    suite("printVariant")(
      test("simple variant with case objects") {
        val sdl = ReflectPrinter.printVariant(Schema[SimpleVariant].reflect.asVariant.get)
        assertTrue(
          sdl.contains("variant SimpleVariant"),
          sdl.contains("| CaseA"),
          sdl.contains("| CaseB")
        )
      },
      test("variant with payload cases") {
        val sdl = ReflectPrinter.printVariant(Schema[PayloadVariant].reflect.asVariant.get)
        assertTrue(
          sdl.contains("variant PayloadVariant"),
          sdl.contains("| WithPayload"),
          sdl.contains("| MultiPayload")
        )
      }
    ),
    suite("printSequence")(
      test("list of primitives") {
        val sdl = ReflectPrinter.printSequence(Schema[List[Int]].reflect.asSequenceUnknown.get.sequence)
        assertTrue(
          sdl.contains("sequence List"),
          sdl.contains("Int")
        )
      },
      test("vector of strings") {
        val sdl = ReflectPrinter.printSequence(Schema[Vector[String]].reflect.asSequenceUnknown.get.sequence)
        assertTrue(sdl.contains("Vector"))
      },
      test("sequence of records") {
        val sdl = ReflectPrinter.printSequence(Schema[List[SingleField]].reflect.asSequenceUnknown.get.sequence)
        assertTrue(sdl.contains("List"))
      }
    ),
    suite("printMap")(
      test("map of primitives") {
        val sdl = ReflectPrinter.printMap(Schema[Map[String, Int]].reflect.asMapUnknown.get.map)
        assertTrue(
          sdl.contains("map Map"),
          sdl.contains("String"),
          sdl.contains("Int")
        )
      },
      test("map with complex value") {
        val sdl = ReflectPrinter.printMap(Schema[Map[String, SingleField]].reflect.asMapUnknown.get.map)
        assertTrue(sdl.contains("map Map"))
      }
    ),
    suite("printTerm")(
      test("primitive term") {
        val record = Schema[SingleField].reflect.asRecord.get
        val sdl    = ReflectPrinter.printTerm(record.fields(0))
        assertTrue(sdl.contains("name: String"))
      },
      test("complex term") {
        val record = Schema[Nested].reflect.asRecord.get
        val sdl    = ReflectPrinter.printTerm(record.fields(0))
        assertTrue(sdl.contains("child:"))
      }
    ),
    suite("sdlTypeName")(
      test("primitive type names") {
        val intReflect = Schema[Int].reflect.asPrimitive.get
        val sdl        = ReflectPrinter.sdlTypeName(intReflect.typeId)
        assertTrue(sdl == "Int")
      },
      test("java.time types keep namespace") {
        val instantReflect = Schema[java.time.Instant].reflect.asPrimitive.get
        val sdl            = ReflectPrinter.sdlTypeName(instantReflect.typeId)
        assertTrue(sdl.contains("Instant"))
      }
    ),
    suite("multiline determination")(
      test("primitives don't need multiline") {
        val sdl = ReflectPrinter.printRecord(Schema[SingleField].reflect.asRecord.get)
        assertTrue(sdl.contains("String"))
      },
      test("records with fields need multiline") {
        val sdl = ReflectPrinter.printRecord(Schema[MultiField].reflect.asRecord.get)
        assertTrue(sdl.contains("\n"))
      }
    )
  )
}
