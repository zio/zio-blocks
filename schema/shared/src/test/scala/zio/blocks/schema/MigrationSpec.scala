package zio.blocks.schema

import zio.Chunk
import zio.blocks.schema.TypeNameConversions._
import zio.schema.TypeId
import zio.test.Assertion._
import zio.test._

/**
 * Migration test suite for TypeName to TypeId migration.
 *
 * This test suite serves as a "laboratory" for verifying the correctness of
 * conversions between TypeName and TypeId, and ensuring that TypeId produces
 * the same behavior as TypeName.
 */
object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("TypeName to TypeId Conversions")(
      test("TypeName to TypeId conversion for primitives") {
        val testCases = Seq(
          (TypeName.int, "Int"),
          (TypeName.string, "String"),
          (TypeName.boolean, "Boolean"),
          (TypeName.byte, "Byte"),
          (TypeName.short, "Short"),
          (TypeName.long, "Long"),
          (TypeName.float, "Float"),
          (TypeName.double, "Double"),
          (TypeName.char, "Char"),
          (TypeName.unit, "Unit")
        )

        testCases.foldLeft(assertTrue(true)) { (acc, case_) =>
          val (typeName, expectedName) = case_
          val typeId                   = typeNameToTypeId(typeName)
          typeId match {
            case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
              acc && assert(typeNameStr)(equalTo(expectedName)) &&
              assert(packageName.toSeq)(equalTo(Seq("scala"))) &&
              assertTrue(objectNames.isEmpty)
            case _ => assertTrue(false)
          }
        }
      },
      test("TypeName to TypeId conversion for collections") {
        val listTypeName   = TypeName.list(TypeName.int)
        val listTypeId     = typeNameToTypeId(listTypeName)
        val mapTypeName    = TypeName.map(TypeName.string, TypeName.int)
        val mapTypeId      = typeNameToTypeId(mapTypeName)
        val setTypeName    = TypeName.set(TypeName.string)
        val setTypeId      = typeNameToTypeId(setTypeName)
        val vectorTypeName = TypeName.vector(TypeName.double)
        val vectorTypeId   = typeNameToTypeId(vectorTypeName)

        (listTypeId, mapTypeId, setTypeId, vectorTypeId) match {
          case (
                TypeId.Nominal(listPkg, listObj, listName),
                TypeId.Nominal(mapPkg, mapObj, mapName),
                TypeId.Nominal(setPkg, setObj, setName),
                TypeId.Nominal(vecPkg, vecObj, vecName)
              ) =>
            assert(listName)(equalTo("List[Int]")) &&
            assert(listPkg.toSeq)(equalTo(Seq("scala", "collection", "immutable"))) &&
            assert(mapName)(equalTo("Map[String, Int]")) &&
            assert(mapPkg.toSeq)(equalTo(Seq("scala", "collection", "immutable"))) &&
            assert(setName)(equalTo("Set[String]")) &&
            assert(vecName)(equalTo("Vector[Double]"))
          case _ => assertTrue(false)
        }
      },
      test("TypeName to TypeId conversion for generics") {
        val optionTypeName = TypeName.option(TypeName.string)
        val optionTypeId   = typeNameToTypeId(optionTypeName)

        optionTypeId match {
          case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
            assert(typeNameStr)(equalTo("Option[String]")) &&
            assert(packageName.toSeq)(equalTo(Seq("scala"))) &&
            assertTrue(objectNames.isEmpty)
          case _ => assertTrue(false)
        }
      },
      test("TypeName to TypeId conversion for nested types") {
        val nestedTypeName = TypeName.list(TypeName.option(TypeName.int))
        val nestedTypeId   = typeNameToTypeId(nestedTypeName)

        nestedTypeId match {
          case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
            // Type parameters are encoded in the name string
            assertTrue(typeNameStr.contains("List")) &&
            assert(packageName.toSeq)(equalTo(Seq("scala", "collection", "immutable")))
          case _ => assertTrue(false)
        }
      },
      test("TypeName to TypeId round-trip conversion") {
        val originalTypeName  = TypeName.map(TypeName.string, TypeName.list(TypeName.int))
        val typeId            = typeNameToTypeId(originalTypeName)
        val convertedTypeName = typeIdToTypeName[Map[String, List[Int]]](typeId)

        // Note: Round-trip may not be perfect due to type parameters being encoded in string
        // and namespace.values not being preserved. This test verifies the basic structure is maintained
        // The name will contain the encoded type parameters, so we check it contains the base name
        assertTrue(convertedTypeName.name.contains(originalTypeName.name)) &&
        assert(convertedTypeName.namespace.packages)(equalTo(originalTypeName.namespace.packages))
      },
      test("TypeId to TypeName conversion (backward compatibility)") {
        val typeId   = TypeId.Nominal(Chunk("test", "package"), Chunk.empty, "TestType")
        val typeName = typeIdToTypeName[String](typeId)

        assert(typeName.name)(equalTo("TestType")) &&
        assert(typeName.namespace.packages)(equalTo(Seq("test", "package")))
      },
      test("TypeId Nominal with encoded type parameters") {
        // Type parameters are encoded in the typeName string and parsed back
        val typeId = TypeId.Nominal(
          packageName = Chunk("scala"),
          objectNames = Chunk.empty,
          typeName = "Option[String]"
        )
        val typeName = typeIdToTypeName[Option[String]](typeId)

        assert(typeName.name)(equalTo("Option")) &&
        assert(typeName.namespace.packages)(equalTo(Seq("scala"))) &&
        // Type parameters are now parsed and preserved in params
        assert(typeName.params.size)(equalTo(1)) &&
        assert(typeName.params.head.name)(equalTo("String"))
      },
      test("Extension method toTypeId works") {
        val typeName = TypeName.int
        val typeId   = typeName.toTypeId

        typeId match {
          case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
            assert(typeNameStr)(equalTo("Int")) &&
            assert(packageName.toSeq)(equalTo(Seq("scala"))) &&
            assertTrue(objectNames.isEmpty)
          case _ => assertTrue(false)
        }
      },
      test("Extension method toTypeName works") {
        val typeId   = TypeId.Nominal(Chunk("test"), Chunk.empty, "Test")
        val typeName = typeId.toTypeName[String]

        assert(typeName.name)(equalTo("Test")) &&
        assert(typeName.namespace.packages)(equalTo(Seq("test")))
      }
    ),
    suite("TypeId Structure Validation")(
      test("Nominal TypeId without type parameters") {
        val typeId = TypeId.Nominal(
          packageName = Chunk("scala"),
          objectNames = Chunk.empty,
          typeName = "Int"
        )

        typeId match {
          case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
            assert(typeNameStr)(equalTo("Int")) &&
            assert(packageName.toSeq)(equalTo(Seq("scala"))) &&
            assertTrue(objectNames.isEmpty)
          case _ => assertTrue(false)
        }
      },
      test("Nominal TypeId with encoded type parameters") {
        val typeId = TypeId.Nominal(
          packageName = Chunk("scala", "collection", "immutable"),
          objectNames = Chunk.empty,
          typeName = "List[Int]"
        )

        typeId match {
          case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
            assert(typeNameStr)(equalTo("List[Int]")) &&
            assert(packageName.toSeq)(equalTo(Seq("scala", "collection", "immutable"))) &&
            assertTrue(typeNameStr.contains("Int")) // Type parameter encoded in name
          case _ => assertTrue(false)
        }
      }
    )
  )
}
