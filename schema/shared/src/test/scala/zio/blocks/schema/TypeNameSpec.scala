package zio.blocks.schema

import zio.test._

object TypeNameSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("TypeNameSpec")(
    suite("TypeName.structural")(
      test("same fields in different order produce same TypeName") {
        val fields1 = Seq("name" -> TypeName.string, "age" -> TypeName.int)
        val fields2 = Seq("age" -> TypeName.int, "name" -> TypeName.string)

        val typeName1 = TypeName.structural[Any](fields1)
        val typeName2 = TypeName.structural[Any](fields2)

        assertTrue(typeName1 == typeName2) &&
        assertTrue(typeName1.hashCode == typeName2.hashCode)
      },
      test("generates normalized name with fields sorted alphabetically") {
        val fields = Seq("name" -> TypeName.string, "age" -> TypeName.int)

        val typeName = TypeName.structural[Any](fields)

        assertTrue(typeName.name == "{age: Int, name: String}")
      },
      test("uses empty namespace for structural types") {
        val fields   = Seq("x" -> TypeName.int)
        val typeName = TypeName.structural[Any](fields)

        assertTrue(typeName.namespace == Namespace.empty)
      },
      test("different structures produce different TypeNames") {
        val fields1 = Seq("name" -> TypeName.string, "age" -> TypeName.int)
        val fields2 = Seq("name" -> TypeName.string, "id" -> TypeName.long)

        val typeName1 = TypeName.structural[Any](fields1)
        val typeName2 = TypeName.structural[Any](fields2)

        assertTrue(typeName1 != typeName2)
      },
      test("handles nested structural types") {
        val innerFields = Seq("x" -> TypeName.int, "y" -> TypeName.int)
        val innerType   = TypeName.structural[Any](innerFields)

        val outerFields = Seq("point" -> innerType, "label" -> TypeName.string)
        val outerType   = TypeName.structural[Any](outerFields)

        assertTrue(outerType.name == "{label: String, point: {x: Int, y: Int}}")
      },
      test("handles generic types in fields") {
        val fields = Seq(
          "names" -> TypeName.list(TypeName.string),
          "age"   -> TypeName.int
        )

        val typeName = TypeName.structural[Any](fields)

        assertTrue(typeName.name == "{age: Int, names: List[String]}")
      },
      test("handles nested generic types") {
        val fields = Seq(
          "data" -> TypeName.map(TypeName.string, TypeName.list(TypeName.int))
        )

        val typeName = TypeName.structural[Any](fields)

        assertTrue(typeName.name == "{data: Map[String, List[Int]]}")
      },
      // Ajay fix later
      // test("stores field types as params") {
      // val fields   = Seq("name" -> TypeName.string, "age" -> TypeName.int)
      // val typeName = TypeName.structural[Any](fields)

      // Params should be sorted by field name (age before name)
      // assertTrue(typeName.params == Seq(TypeName.int, TypeName.string))
      // },
      test("empty fields produce empty structural type") {
        val typeName = TypeName.structural[Any](Seq.empty)

        assertTrue(typeName.name == "{}") &&
        assertTrue(typeName.params.isEmpty)
      }
    )
  )
}
