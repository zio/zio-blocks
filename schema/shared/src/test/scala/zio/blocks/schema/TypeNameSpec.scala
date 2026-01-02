package zio.blocks.schema

import zio.test._

object TypeNameSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Any] = suite("TypeNameSpec")(
    suite("toSimpleName")(
      test("primitive type returns simple name") {
        assertTrue(
          TypeName.int.toSimpleName == "Int",
          TypeName.string.toSimpleName == "String",
          TypeName.boolean.toSimpleName == "Boolean",
          TypeName.double.toSimpleName == "Double",
          TypeName.long.toSimpleName == "Long"
        )
      },
      test("parameterized type includes parameters") {
        val listInt    = TypeName.list(TypeName.int)
        val optString  = TypeName.option(TypeName.string)
        val mapIntStr  = TypeName.map(TypeName.int, TypeName.string)
        val setDouble  = TypeName.set(TypeName.double)
        val vectorLong = TypeName.vector(TypeName.long)
        assertTrue(
          listInt.toSimpleName == "List[Int]",
          optString.toSimpleName == "Option[String]",
          mapIntStr.toSimpleName == "Map[Int,String]",
          setDouble.toSimpleName == "Set[Double]",
          vectorLong.toSimpleName == "Vector[Long]"
        )
      },
      test("nested parameterized type") {
        val listOptInt = TypeName.list(TypeName.option(TypeName.int))
        val mapStrList = TypeName.map(TypeName.string, TypeName.list(TypeName.int))
        assertTrue(
          listOptInt.toSimpleName == "List[Option[Int]]",
          mapStrList.toSimpleName == "Map[String,List[Int]]"
        )
      }
    ),
    suite("structural")(
      test("creates normalized structural type name with alphabetical field order") {
        val tn1 = TypeName.structural[Any](Seq("name" -> "String", "age" -> "Int"))
        val tn2 = TypeName.structural[Any](Seq("age" -> "Int", "name" -> "String"))
        assertTrue(
          tn1.name == "{age:Int,name:String}",
          tn2.name == "{age:Int,name:String}",
          tn1.name == tn2.name // Same structure = same name
        )
      },
      test("single field structural type") {
        val tn = TypeName.structural[Any](Seq("value" -> "String"))
        assertTrue(tn.name == "{value:String}")
      },
      test("empty structural type") {
        val tn = TypeName.structural[Any](Seq.empty)
        assertTrue(tn.name == "{}")
      },
      test("structural type with many fields") {
        val fields = (1 to 10).map(i => s"f$i" -> "Int")
        val tn     = TypeName.structural[Any](fields)
        assertTrue(
          tn.name == "{f1:Int,f10:Int,f2:Int,f3:Int,f4:Int,f5:Int,f6:Int,f7:Int,f8:Int,f9:Int}"
        )
      },
      test("structural type with collection types") {
        val tn = TypeName.structural[Any](Seq("items" -> "List[Int]", "count" -> "Int"))
        assertTrue(tn.name == "{count:Int,items:List[Int]}")
      },
      test("uses empty namespace") {
        val tn = TypeName.structural[Any](Seq("x" -> "Int"))
        assertTrue(tn.namespace == Namespace.empty)
      },
      test("has no type parameters") {
        val tn = TypeName.structural[Any](Seq("x" -> "Int"))
        assertTrue(tn.params.isEmpty)
      }
    ),
    suite("structuralFromTypeNames")(
      test("creates structural type from TypeName instances") {
        val tn = TypeName.structuralFromTypeNames[Any](Seq("name" -> TypeName.string, "age" -> TypeName.int))
        assertTrue(tn.name == "{age:Int,name:String}")
      },
      test("handles parameterized types") {
        val listInt = TypeName.list(TypeName.int)
        val tn      = TypeName.structuralFromTypeNames[Any](Seq("items" -> listInt, "count" -> TypeName.int))
        assertTrue(tn.name == "{count:Int,items:List[Int]}")
      },
      test("handles nested parameterized types") {
        val mapStrListInt = TypeName.map(TypeName.string, TypeName.list(TypeName.int))
        val tn            = TypeName.structuralFromTypeNames[Any](Seq("data" -> mapStrListInt))
        assertTrue(tn.name == "{data:Map[String,List[Int]]}")
      }
    ),
    suite("variant")(
      test("creates variant type name from case names") {
        val tn = TypeName.variant[Any](Seq("Success", "Failure"))
        assertTrue(tn.name == "({Tag:Failure}|{Tag:Success})")
      },
      test("sorts cases alphabetically") {
        val tn1 = TypeName.variant[Any](Seq("C", "A", "B"))
        val tn2 = TypeName.variant[Any](Seq("A", "B", "C"))
        assertTrue(
          tn1.name == "({Tag:A}|{Tag:B}|{Tag:C})",
          tn2.name == "({Tag:A}|{Tag:B}|{Tag:C})",
          tn1.name == tn2.name
        )
      },
      test("single case variant") {
        val tn = TypeName.variant[Any](Seq("Only"))
        assertTrue(tn.name == "({Tag:Only})")
      },
      test("uses empty namespace") {
        val tn = TypeName.variant[Any](Seq("A", "B"))
        assertTrue(tn.namespace == Namespace.empty)
      }
    ),
    suite("taggedCase")(
      test("creates tagged case type name") {
        val tn = TypeName.taggedCase[Any]("Success")
        assertTrue(tn.name == "{Tag:Success}")
      },
      test("uses empty namespace") {
        val tn = TypeName.taggedCase[Any]("Test")
        assertTrue(tn.namespace == Namespace.empty)
      },
      test("has no type parameters") {
        val tn = TypeName.taggedCase[Any]("Test")
        assertTrue(tn.params.isEmpty)
      }
    ),
    suite("emptyStructural")(
      test("creates empty structural type name") {
        val tn = TypeName.emptyStructural[Any]
        assertTrue(tn.name == "{}")
      },
      test("uses empty namespace") {
        val tn = TypeName.emptyStructural[Any]
        assertTrue(tn.namespace == Namespace.empty)
      }
    ),
    suite("determinism")(
      test("same structure always produces same name") {
        val fields = Seq("z" -> "Int", "a" -> "String", "m" -> "Boolean")
        val tn1    = TypeName.structural[Any](fields)
        val tn2    = TypeName.structural[Any](fields)
        val tn3    = TypeName.structural[Any](fields.reverse)
        assertTrue(
          tn1.name == tn2.name,
          tn2.name == tn3.name,
          tn1 == tn2,
          tn2 == tn3
        )
      },
      test("same variant cases produce same name regardless of order") {
        val cases1 = Seq("Z", "A", "M")
        val cases2 = Seq("A", "M", "Z")
        val tn1    = TypeName.variant[Any](cases1)
        val tn2    = TypeName.variant[Any](cases2)
        assertTrue(
          tn1.name == "({Tag:A}|{Tag:M}|{Tag:Z})",
          tn2.name == "({Tag:A}|{Tag:M}|{Tag:Z})",
          tn1.name == tn2.name,
          tn1 == tn2
        )
      }
    ),
    suite("predefined TypeNames")(
      test("primitives have correct names") {
        assertTrue(
          TypeName.unit.name == "Unit",
          TypeName.boolean.name == "Boolean",
          TypeName.byte.name == "Byte",
          TypeName.short.name == "Short",
          TypeName.int.name == "Int",
          TypeName.long.name == "Long",
          TypeName.float.name == "Float",
          TypeName.double.name == "Double",
          TypeName.char.name == "Char",
          TypeName.string.name == "String",
          TypeName.bigInt.name == "BigInt",
          TypeName.bigDecimal.name == "BigDecimal"
        )
      },
      test("java.time types have correct namespace and names") {
        assertTrue(
          TypeName.instant.name == "Instant",
          TypeName.instant.namespace == Namespace.javaTime,
          TypeName.localDate.name == "LocalDate",
          TypeName.localDateTime.name == "LocalDateTime",
          TypeName.zonedDateTime.name == "ZonedDateTime"
        )
      },
      test("java.util types have correct namespace and names") {
        assertTrue(
          TypeName.uuid.name == "UUID",
          TypeName.uuid.namespace == Namespace.javaUtil,
          TypeName.currency.name == "Currency"
        )
      },
      test("collection builders create correct TypeNames") {
        val listInt   = TypeName.list(TypeName.int)
        val setStr    = TypeName.set(TypeName.string)
        val vectorDbl = TypeName.vector(TypeName.double)
        assertTrue(
          listInt.name == "List",
          listInt.params == Seq(TypeName.int),
          setStr.name == "Set",
          vectorDbl.name == "Vector"
        )
      }
    ),
    suite("Namespace.empty")(
      test("empty namespace has no packages or values") {
        assertTrue(
          Namespace.empty.packages == Nil,
          Namespace.empty.values == Nil,
          Namespace.empty.elements.isEmpty
        )
      }
    )
  )
}
