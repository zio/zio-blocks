package zio.blocks.smithy

import zio.test._

object SmithyModelSpec extends ZIOSpecDefault {

  def spec = suite("SmithyModel")(
    suite("SmithyModel construction")(
      test("creates model with version, namespace, and basic fields") {
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = Nil
        )

        assertTrue(
          model.version == "2.0" &&
            model.namespace == "com.example" &&
            model.useStatements == Nil &&
            model.metadata == (Map.empty[String, NodeValue]) &&
            model.shapes == Nil
        )
      },
      test("creates model with use statements") {
        val useStmts = List(ShapeId("com.other", "Shape1"), ShapeId("com.other", "Shape2"))
        val model    = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = useStmts,
          metadata = Map.empty[String, NodeValue],
          shapes = Nil
        )

        assertTrue(model.useStatements == useStmts)
      },
      test("creates model with metadata") {
        val meta = Map(
          "key1" -> NodeValue.StringValue("value1"),
          "key2" -> NodeValue.NumberValue(BigDecimal(42))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = meta,
          shapes = Nil
        )

        assertTrue(model.metadata == meta)
      },
      test("creates model with shapes") {
        val shapes = List(
          ShapeDefinition("String", StringShape("String")),
          ShapeDefinition("Integer", IntegerShape("Integer"))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = shapes
        )

        assertTrue(model.shapes == shapes)
      }
    ),
    suite("ShapeDefinition")(
      test("creates ShapeDefinition with name and shape") {
        val shapeDef = ShapeDefinition("User", StructureShape("User"))

        assertTrue(
          shapeDef.name == "User" &&
            shapeDef.shape.isInstanceOf[StructureShape] &&
            shapeDef.shape.name == "User"
        )
      },
      test("wraps different shape types") {
        val stringDef = ShapeDefinition("MyString", StringShape("MyString"))
        val listDef   = ShapeDefinition(
          "MyList",
          ListShape("MyList", Nil, MemberDefinition("member", ShapeId("smithy.api", "String")))
        )

        assertTrue(
          stringDef.shape.isInstanceOf[StringShape] &&
            listDef.shape.isInstanceOf[ListShape]
        )
      }
    ),
    suite("findShape")(
      test("finds shape by name when present") {
        val stringDef = ShapeDefinition("User", StructureShape("User"))
        val intDef    = ShapeDefinition("Id", IntegerShape("Id"))
        val model     = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = List(stringDef, intDef)
        )

        val found = model.findShape("User")

        assertTrue(
          found.isDefined &&
            found.get.name == "User" &&
            found.get.shape.isInstanceOf[StructureShape]
        )
      },
      test("returns None when shape not found") {
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = List(ShapeDefinition("User", StructureShape("User")))
        )

        val notFound = model.findShape("NonExistent")

        assertTrue(notFound.isEmpty)
      },
      test("returns None when shapes list is empty") {
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = Nil
        )

        val notFound = model.findShape("Any")

        assertTrue(notFound.isEmpty)
      },
      test("finds first match when multiple shapes with same name") {
        val def1  = ShapeDefinition("Duplicate", StringShape("Duplicate"))
        val def2  = ShapeDefinition("Duplicate", IntegerShape("Duplicate"))
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = List(def1, def2)
        )

        val found = model.findShape("Duplicate")

        assertTrue(
          found.isDefined &&
            found.get.shape.isInstanceOf[StringShape]
        )
      }
    ),
    suite("allShapeIds")(
      test("generates ShapeIds from namespace and shape names") {
        val shapes = List(
          ShapeDefinition("User", StructureShape("User")),
          ShapeDefinition("Order", StructureShape("Order"))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = shapes
        )

        val ids = model.allShapeIds

        assertTrue(
          ids.length == 2 &&
            ids.contains(ShapeId("com.example", "User")) &&
            ids.contains(ShapeId("com.example", "Order"))
        )
      },
      test("returns empty list for model with no shapes") {
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = Nil
        )

        val ids = model.allShapeIds

        assertTrue(ids.isEmpty)
      },
      test("preserves order of shape definitions") {
        val shapes = List(
          ShapeDefinition("First", StringShape("First")),
          ShapeDefinition("Second", IntegerShape("Second")),
          ShapeDefinition("Third", BooleanShape("Third"))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = shapes
        )

        val ids = model.allShapeIds

        assertTrue(
          ids.length == 3 &&
            ids(0) == ShapeId("com.example", "First") &&
            ids(1) == ShapeId("com.example", "Second") &&
            ids(2) == ShapeId("com.example", "Third")
        )
      },
      test("uses namespace from model, not shape names") {
        val shapes = List(
          ShapeDefinition("MyShape", StringShape("MyShape"))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "my.custom.namespace",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = shapes
        )

        val ids = model.allShapeIds

        assertTrue(
          ids.length == 1 &&
            ids(0) == ShapeId("my.custom.namespace", "MyShape")
        )
      }
    ),
    suite("empty model")(
      test("creates valid empty model") {
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example",
          useStatements = Nil,
          metadata = Map.empty[String, NodeValue],
          shapes = Nil
        )

        assertTrue(
          model.version == "2.0" &&
            model.namespace == "com.example" &&
            model.shapes.isEmpty &&
            model.metadata.isEmpty &&
            model.allShapeIds.isEmpty
        )
      }
    ),
    suite("complex model")(
      test("handles model with all features") {
        val shapes = List(
          ShapeDefinition("User", StructureShape("User")),
          ShapeDefinition(
            "CreateUserRequest",
            StructureShape(
              "CreateUserRequest",
              Nil,
              List(MemberDefinition("user", ShapeId("com.example", "User")))
            )
          )
        )
        val useStmts = List(ShapeId("smithy.api", "String"))
        val metadata = Map(
          "version"   -> NodeValue.StringValue("1.0"),
          "timestamp" -> NodeValue.NumberValue(BigDecimal(1234567890))
        )
        val model = SmithyModel(
          version = "2.0",
          namespace = "com.example.api",
          useStatements = useStmts,
          metadata = metadata,
          shapes = shapes
        )

        val found  = model.findShape("User")
        val allIds = model.allShapeIds

        assertTrue(
          found.isDefined &&
            found.get.name == "User" &&
            allIds.length == 2 &&
            allIds.contains(ShapeId("com.example.api", "User")) &&
            allIds.contains(ShapeId("com.example.api", "CreateUserRequest")) &&
            model.useStatements.length == 1 &&
            model.metadata.size == 2
        )
      }
    )
  )
}
