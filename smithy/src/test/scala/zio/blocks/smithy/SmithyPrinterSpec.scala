package zio.blocks.smithy

import zio.test._

object SmithyPrinterSpec extends ZIOSpecDefault {

  private def minimalModel(
    version: String = "2.0",
    namespace: String = "com.example",
    useStatements: List[ShapeId] = Nil,
    metadata: Map[String, NodeValue] = Map.empty,
    shapes: List[ShapeDefinition] = Nil
  ): SmithyModel = SmithyModel(version, namespace, useStatements, metadata, shapes)

  def spec = suite("SmithyPrinter")(
    suite("minimal model")(
      test("prints version and namespace only") {
        val model    = minimalModel()
        val expected = "$version: \"2.0\"\n\nnamespace com.example\n"
        assertTrue(SmithyPrinter.print(model) == expected)
      }
    ),
    suite("use statements")(
      test("prints use statements after namespace") {
        val model = minimalModel(
          useStatements = List(
            ShapeId("smithy.api", "String"),
            ShapeId("com.other", "Foo")
          )
        )
        val expected =
          "$version: \"2.0\"\n\nnamespace com.example\n\nuse smithy.api#String\nuse com.other#Foo\n"
        assertTrue(SmithyPrinter.print(model) == expected)
      }
    ),
    suite("metadata")(
      test("prints metadata entries") {
        val model = minimalModel(
          metadata = Map(
            "key1" -> NodeValue.StringValue("value1"),
            "key2" -> NodeValue.NumberValue(BigDecimal(42))
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("metadata key1 = \"value1\"") &&
            result.contains("metadata key2 = 42")
        )
      }
    ),
    suite("simple shapes")(
      test("prints string shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyString", StringShape("MyString")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("string MyString"))
      },
      test("prints integer shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyInt", IntegerShape("MyInt")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("integer MyInt"))
      },
      test("prints boolean shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyBool", BooleanShape("MyBool")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("boolean MyBool"))
      },
      test("prints blob shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyBlob", BlobShape("MyBlob")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("blob MyBlob"))
      },
      test("prints byte shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyByte", ByteShape("MyByte")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("byte MyByte"))
      },
      test("prints short shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyShort", ShortShape("MyShort")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("short MyShort"))
      },
      test("prints long shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyLong", LongShape("MyLong")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("long MyLong"))
      },
      test("prints float shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyFloat", FloatShape("MyFloat")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("float MyFloat"))
      },
      test("prints double shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyDouble", DoubleShape("MyDouble")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("double MyDouble"))
      },
      test("prints bigInteger shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyBigInt", BigIntegerShape("MyBigInt")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("bigInteger MyBigInt"))
      },
      test("prints bigDecimal shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyBigDec", BigDecimalShape("MyBigDec")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("bigDecimal MyBigDec"))
      },
      test("prints timestamp shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyTime", TimestampShape("MyTime")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("timestamp MyTime"))
      },
      test("prints document shape") {
        val model = minimalModel(
          shapes = List(ShapeDefinition("MyDoc", DocumentShape("MyDoc")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("document MyDoc"))
      },
      test("prints simple shape with traits") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyString",
              StringShape(
                "MyString",
                List(TraitApplication(ShapeId("smithy.api", "sensitive"), None))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("@sensitive") &&
            result.contains("string MyString")
        )
      }
    ),
    suite("structure shape")(
      test("prints structure with members and traits") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition(
                    "bar",
                    ShapeId("smithy.api", "String"),
                    List(TraitApplication.required)
                  ),
                  MemberDefinition("baz", ShapeId("smithy.api", "Integer"))
                )
              )
            )
          )
        )
        val result   = SmithyPrinter.print(model)
        val expected =
          "structure Foo {\n    @required\n    bar: String\n    baz: Integer\n}"
        assertTrue(result.contains(expected))
      },
      test("prints empty structure") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition("Empty", StructureShape("Empty"))
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("structure Empty {}"))
      }
    ),
    suite("union shape")(
      test("prints union with members") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyUnion",
              UnionShape(
                "MyUnion",
                Nil,
                List(
                  MemberDefinition("i32", ShapeId("smithy.api", "Integer")),
                  MemberDefinition("str", ShapeId("smithy.api", "String"))
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("union MyUnion {\n    i32: Integer\n    str: String\n}")
        )
      }
    ),
    suite("list shape")(
      test("prints list with member") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyList",
              ListShape(
                "MyList",
                Nil,
                MemberDefinition("member", ShapeId("smithy.api", "String"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("list MyList {\n    member: String\n}")
        )
      }
    ),
    suite("map shape")(
      test("prints map with key and value") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyMap",
              MapShape(
                "MyMap",
                Nil,
                MemberDefinition("key", ShapeId("smithy.api", "String")),
                MemberDefinition("value", ShapeId("smithy.api", "Integer"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("map MyMap {\n    key: String\n    value: Integer\n}")
        )
      }
    ),
    suite("enum shape")(
      test("prints enum with members") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Suit",
              EnumShape(
                "Suit",
                Nil,
                List(
                  EnumMember("SPADES"),
                  EnumMember("HEARTS")
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("enum Suit {\n    SPADES\n    HEARTS\n}")
        )
      },
      test("prints enum member with explicit value") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Color",
              EnumShape(
                "Color",
                Nil,
                List(
                  EnumMember("RED", Some("red")),
                  EnumMember("BLUE", Some("blue"))
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("RED = \"red\"") &&
            result.contains("BLUE = \"blue\"")
        )
      }
    ),
    suite("intEnum shape")(
      test("prints intEnum with members") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "FaceCard",
              IntEnumShape(
                "FaceCard",
                Nil,
                List(
                  IntEnumMember("JACK", 11),
                  IntEnumMember("QUEEN", 12)
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("intEnum FaceCard {\n    JACK = 11\n    QUEEN = 12\n}")
        )
      }
    ),
    suite("service shape")(
      test("prints service with version and operations") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyService",
              ServiceShape(
                "MyService",
                Nil,
                version = Some("1.0"),
                operations = List(ShapeId("com.example", "GetFoo"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("service MyService {") &&
            result.contains("    version: \"1.0\"") &&
            result.contains("    operations: [GetFoo]")
        )
      },
      test("prints service with resources and errors") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyService",
              ServiceShape(
                "MyService",
                Nil,
                version = Some("1.0"),
                operations = Nil,
                resources = List(ShapeId("com.example", "FooResource")),
                errors = List(ShapeId("com.example", "BadRequest"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    resources: [FooResource]") &&
            result.contains("    errors: [BadRequest]")
        )
      }
    ),
    suite("operation shape")(
      test("prints operation with input and output") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "GetFoo",
              OperationShape(
                "GetFoo",
                Nil,
                input = Some(ShapeId("com.example", "GetFooInput")),
                output = Some(ShapeId("com.example", "GetFooOutput"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("operation GetFoo {") &&
            result.contains("    input: GetFooInput") &&
            result.contains("    output: GetFooOutput")
        )
      },
      test("prints operation with errors") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "GetFoo",
              OperationShape(
                "GetFoo",
                Nil,
                input = Some(ShapeId("com.example", "GetFooInput")),
                output = Some(ShapeId("com.example", "GetFooOutput")),
                errors = List(
                  ShapeId("com.example", "NotFound"),
                  ShapeId("com.example", "BadRequest")
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    errors: [NotFound, BadRequest]")
        )
      }
    ),
    suite("resource shape")(
      test("prints resource with identifiers and lifecycle operations") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "FooResource",
              ResourceShape(
                "FooResource",
                Nil,
                identifiers = Map("id" -> ShapeId("com.example", "FooId")),
                read = Some(ShapeId("com.example", "GetFoo")),
                list = Some(ShapeId("com.example", "ListFoo"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("resource FooResource {") &&
            result.contains("    identifiers: {id: FooId}") &&
            result.contains("    read: GetFoo") &&
            result.contains("    list: ListFoo")
        )
      },
      test("prints resource with create, update, delete") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "FooResource",
              ResourceShape(
                "FooResource",
                Nil,
                identifiers = Map("id" -> ShapeId("com.example", "FooId")),
                create = Some(ShapeId("com.example", "CreateFoo")),
                update = Some(ShapeId("com.example", "UpdateFoo")),
                delete = Some(ShapeId("com.example", "DeleteFoo"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    create: CreateFoo") &&
            result.contains("    update: UpdateFoo") &&
            result.contains("    delete: DeleteFoo")
        )
      },
      test("prints resource with operations, collectionOperations, resources") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "FooResource",
              ResourceShape(
                "FooResource",
                Nil,
                identifiers = Map("id" -> ShapeId("com.example", "FooId")),
                operations = List(ShapeId("com.example", "CustomOp")),
                collectionOperations = List(ShapeId("com.example", "BatchOp")),
                resources = List(ShapeId("com.example", "ChildResource"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    operations: [CustomOp]") &&
            result.contains("    collectionOperations: [BatchOp]") &&
            result.contains("    resources: [ChildResource]")
        )
      }
    ),
    suite("documentation comment")(
      test("prints @documentation trait as /// comment") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyString",
              StringShape(
                "MyString",
                List(TraitApplication.documentation("This is a documented string"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("/// This is a documented string") &&
            !result.contains("@documentation")
        )
      },
      test("prints multi-line documentation") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "MyString",
              StringShape(
                "MyString",
                List(TraitApplication.documentation("Line one\nLine two\nLine three"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("/// Line one\n/// Line two\n/// Line three")
        )
      }
    ),
    suite("node values")(
      test("prints string value with escapes") {
        val model = minimalModel(
          metadata = Map("key" -> NodeValue.StringValue("hello \"world\""))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("metadata key = \"hello \\\"world\\\"\""))
      },
      test("prints number value") {
        val model = minimalModel(
          metadata = Map("num" -> NodeValue.NumberValue(BigDecimal("3.14")))
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("metadata num = 3.14"))
      },
      test("prints boolean values") {
        val model = minimalModel(
          metadata = Map(
            "yes" -> NodeValue.BooleanValue(true),
            "no"  -> NodeValue.BooleanValue(false)
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("metadata yes = true") &&
            result.contains("metadata no = false")
        )
      },
      test("prints null value") {
        val model = minimalModel(
          metadata = Map("nothing" -> NodeValue.NullValue)
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("metadata nothing = null"))
      },
      test("prints array value") {
        val model = minimalModel(
          metadata = Map(
            "arr" -> NodeValue.ArrayValue(
              List(
                NodeValue.StringValue("a"),
                NodeValue.NumberValue(BigDecimal(1)),
                NodeValue.BooleanValue(true)
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("metadata arr = [\"a\", 1, true]"))
      },
      test("prints object value") {
        val model = minimalModel(
          metadata = Map(
            "obj" -> NodeValue.ObjectValue(
              List(
                "name"  -> NodeValue.StringValue("test"),
                "count" -> NodeValue.NumberValue(BigDecimal(5))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("metadata obj = {name: \"test\", count: 5}"))
      }
    ),
    suite("complex trait values")(
      test("prints trait with string value") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "ErrorShape",
              StructureShape(
                "ErrorShape",
                List(TraitApplication.error("client"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("@error(\"client\")"))
      },
      test("prints trait with object value") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "GetFoo",
              OperationShape(
                "GetFoo",
                List(TraitApplication.http("GET", "/foo"))
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("@http(method: \"GET\", uri: \"/foo\")"))
      },
      test("prints trait without value") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition(
                    "bar",
                    ShapeId("smithy.api", "String"),
                    List(TraitApplication.required)
                  )
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("@required"))
      }
    ),
    suite("custom indentation")(
      test("uses 2-space indentation") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition("bar", ShapeId("smithy.api", "String"))
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model, indent = 2)
        assertTrue(result.contains("structure Foo {\n  bar: String\n}"))
      }
    ),
    suite("shape separation")(
      test("separates shapes with empty lines") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition("A", StringShape("A")),
            ShapeDefinition("B", IntegerShape("B")),
            ShapeDefinition("C", BooleanShape("C"))
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("string A\n\ninteger B\n\nboolean C")
        )
      }
    ),
    suite("ShapeId rendering in members")(
      test("uses just name when namespace is empty") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition("bar", ShapeId("", "String"))
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("bar: String"))
      },
      test("uses full qualified name when namespace is non-empty") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition("bar", ShapeId("com.other", "Widget"))
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(result.contains("bar: com.other#Widget"))
      }
    ),
    suite("no trailing whitespace")(
      test("no lines have trailing whitespace") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                List(TraitApplication.documentation("A structure")),
                List(
                  MemberDefinition(
                    "bar",
                    ShapeId("smithy.api", "String"),
                    List(TraitApplication.required)
                  )
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        val lines  = result.split("\n", -1)
        assertTrue(lines.forall(line => line == line.stripTrailing()))
      }
    ),
    suite("member traits on structure members")(
      test("prints documentation trait on member as /// comment") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Foo",
              StructureShape(
                "Foo",
                Nil,
                List(
                  MemberDefinition(
                    "bar",
                    ShapeId("smithy.api", "String"),
                    List(TraitApplication.documentation("The bar field"))
                  )
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    /// The bar field\n    bar: String")
        )
      }
    ),
    suite("enum member traits")(
      test("prints traits on enum members") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "Color",
              EnumShape(
                "Color",
                Nil,
                List(
                  EnumMember(
                    "RED",
                    None,
                    List(TraitApplication.documentation("The color red"))
                  ),
                  EnumMember("BLUE")
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    /// The color red\n    RED")
        )
      }
    ),
    suite("intEnum member traits")(
      test("prints traits on intEnum members") {
        val model = minimalModel(
          shapes = List(
            ShapeDefinition(
              "FaceCard",
              IntEnumShape(
                "FaceCard",
                Nil,
                List(
                  IntEnumMember(
                    "JACK",
                    11,
                    List(TraitApplication.documentation("The Jack card"))
                  ),
                  IntEnumMember("QUEEN", 12)
                )
              )
            )
          )
        )
        val result = SmithyPrinter.print(model)
        assertTrue(
          result.contains("    /// The Jack card\n    JACK = 11")
        )
      }
    )
  )
}
