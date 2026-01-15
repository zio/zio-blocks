package zio.blocks.schema

import zio.test._

/**
 * Shared tests for ToStructural that don't require Schema.derived. Tests that
 * use Schema.derived are in version-specific test files.
 */
object StructuralSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("StructuralSpec")(
    typeNameNormalizationSuite
  )

  // ===========================================================================
  // Type Name Normalization Tests
  // ===========================================================================
  val typeNameNormalizationSuite: Spec[Any, Nothing] = suite("Type Name Normalization")(
    test("empty record type name is {}") {
      val fields   = Seq.empty[(String, TypeName[?])]
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName == "{}")
    },
    test("single field type name") {
      val fields   = Seq(("value", TypeName.string))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName == "{value:scala.String}")
    },
    test("simple fields are sorted alphabetically") {
      val fields   = Seq(("name", TypeName.string), ("age", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName == "{age:scala.Int,name:scala.String}")
    },
    test("type name has no whitespace") {
      val fields   = Seq(("name", TypeName.string), ("age", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(!typeName.contains(" ") && !typeName.contains("\n"))
    },
    test("same structure produces same type name") {
      val fields1 = Seq(("name", TypeName.string), ("age", TypeName.int))
      val fields2 = Seq(("name", TypeName.string), ("age", TypeName.int))
      assertTrue(ToStructural.structuralTypeName(fields1) == ToStructural.structuralTypeName(fields2))
    },
    test("different field order produces same type name") {
      val fields1 = Seq(("name", TypeName.string), ("age", TypeName.int))
      val fields2 = Seq(("age", TypeName.int), ("name", TypeName.string))
      assertTrue(ToStructural.structuralTypeName(fields1) == ToStructural.structuralTypeName(fields2))
    },
    test("Int type is fully qualified") {
      val fields   = Seq(("n", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Int"))
    },
    test("String type is fully qualified") {
      val fields   = Seq(("s", TypeName.string))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.String"))
    },
    test("Long type is fully qualified") {
      val fields   = Seq(("l", TypeName.long))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Long"))
    },
    test("Boolean type is fully qualified") {
      val fields   = Seq(("b", TypeName.boolean))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Boolean"))
    },
    test("Double type is fully qualified") {
      val fields   = Seq(("d", TypeName.double))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Double"))
    },
    test("Float type is fully qualified") {
      val fields   = Seq(("f", TypeName.float))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Float"))
    },
    test("Byte type is fully qualified") {
      val fields   = Seq(("b", TypeName.byte))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Byte"))
    },
    test("Short type is fully qualified") {
      val fields   = Seq(("s", TypeName.short))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Short"))
    },
    test("Char type is fully qualified") {
      val fields   = Seq(("c", TypeName.char))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.contains("scala.Char"))
    },
    test("underscore field names sorted correctly") {
      val fields   = Seq(("_2", TypeName.int), ("_1", TypeName.int), ("_3", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.indexOf("_1:") < typeName.indexOf("_2:") && typeName.indexOf("_2:") < typeName.indexOf("_3:"))
    },
    test("type name starts with { and ends with }") {
      val fields   = Seq(("a", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.startsWith("{") && typeName.endsWith("}"))
    },
    test("multiple fields with same first letter sorted correctly") {
      val fields   = Seq(("ab", TypeName.int), ("aa", TypeName.int), ("ac", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(typeName.indexOf("aa:") < typeName.indexOf("ab:") && typeName.indexOf("ab:") < typeName.indexOf("ac:"))
    },
    test("fields with numbers sorted correctly") {
      val fields   = Seq(("a2", TypeName.int), ("a1", TypeName.int), ("a10", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      // Lexicographic sort: a1 < a10 < a2
      assertTrue(
        typeName.indexOf("a1:") < typeName.indexOf("a10:") && typeName.indexOf("a10:") < typeName.indexOf("a2:")
      )
    },
    test("type name is deterministic") {
      val fields = Seq(("b", TypeName.int), ("a", TypeName.string))
      val names  = (1 to 5).map(_ => ToStructural.structuralTypeName(fields))
      assertTrue(names.distinct.size == 1)
    },
    test("many fields are sorted correctly") {
      val fields = Seq(
        ("z", TypeName.int),
        ("y", TypeName.int),
        ("x", TypeName.int),
        ("w", TypeName.int),
        ("v", TypeName.int)
      )
      val typeName = ToStructural.structuralTypeName(fields)
      assertTrue(
        typeName.indexOf("v:") < typeName.indexOf("w:") &&
          typeName.indexOf("w:") < typeName.indexOf("x:") &&
          typeName.indexOf("x:") < typeName.indexOf("y:") &&
          typeName.indexOf("y:") < typeName.indexOf("z:")
      )
    },
    test("case sensitivity in sorting") {
      val fields   = Seq(("B", TypeName.int), ("a", TypeName.int))
      val typeName = ToStructural.structuralTypeName(fields)
      // Capital letters come before lowercase in ASCII
      assertTrue(typeName.indexOf("B:") < typeName.indexOf("a:"))
    }
  )
}
