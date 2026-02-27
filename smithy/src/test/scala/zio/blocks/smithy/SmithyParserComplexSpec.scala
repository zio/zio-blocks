package zio.blocks.smithy

import zio.test._

object SmithyParserComplexSpec extends ZIOSpecDefault {

  private val header = "$version: \"2\"\nnamespace com.example\n"

  private def parse(body: String): Either[SmithyError, SmithyModel] =
    SmithyParser.parse(header + body)

  private def parseOk(body: String): SmithyModel =
    parse(body) match {
      case Right(m)  => m
      case Left(err) => throw new AssertionError("Parse failed: " + err.formatMessage)
    }

  private def shapeId(name: String): ShapeId = ShapeId("", name)

  def spec = suite("SmithyParser - complex shapes")(
    suite("structure")(
      test("parses empty structure") {
        val m = parseOk("structure Foo {}")
        assertTrue(
          m.shapes.length == 1,
          m.shapes.head.name == "Foo",
          m.shapes.head.shape.isInstanceOf[StructureShape],
          m.shapes.head.shape.asInstanceOf[StructureShape].members.isEmpty
        )
      },
      test("parses structure with members") {
        val m = parseOk(
          """structure Foo {
            |    bar: String
            |    baz: Integer
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[StructureShape]
        assertTrue(
          s.members.length == 2,
          s.members(0).name == "bar",
          s.members(0).target == shapeId("String"),
          s.members(1).name == "baz",
          s.members(1).target == shapeId("Integer")
        )
      },
      test("parses structure with member traits") {
        val m = parseOk(
          """structure Foo {
            |    @required
            |    bar: String
            |    baz: Integer
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[StructureShape]
        assertTrue(
          s.members(0).traits == List(TraitApplication.required),
          s.members(1).traits.isEmpty
        )
      },
      test("parses structure with commas between members") {
        val m = parseOk(
          """structure Foo {
            |    bar: String,
            |    baz: Integer,
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[StructureShape]
        assertTrue(s.members.length == 2)
      },
      test("parses structure with traits on the shape itself") {
        val m = parseOk(
          """@documentation("A foo shape")
            |structure Foo {
            |    bar: String
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[StructureShape]
        assertTrue(
          s.traits == List(TraitApplication.documentation("A foo shape")),
          s.members.length == 1
        )
      }
    ),
    suite("union")(
      test("parses union with members") {
        val m = parseOk(
          """union MyUnion {
            |    i32: Integer
            |    str: String
            |}""".stripMargin
        )
        val u = m.shapes.head.shape.asInstanceOf[UnionShape]
        assertTrue(
          u.members.length == 2,
          u.members(0).name == "i32",
          u.members(0).target == shapeId("Integer"),
          u.members(1).name == "str",
          u.members(1).target == shapeId("String")
        )
      },
      test("parses empty union") {
        val m = parseOk("union MyUnion {}")
        val u = m.shapes.head.shape.asInstanceOf[UnionShape]
        assertTrue(u.members.isEmpty)
      }
    ),
    suite("list")(
      test("parses list shape") {
        val m = parseOk(
          """list MyList {
            |    member: String
            |}""".stripMargin
        )
        val l = m.shapes.head.shape.asInstanceOf[ListShape]
        assertTrue(
          l.member.name == "member",
          l.member.target == shapeId("String")
        )
      }
    ),
    suite("map")(
      test("parses map shape") {
        val m = parseOk(
          """map MyMap {
            |    key: String
            |    value: Integer
            |}""".stripMargin
        )
        val mp = m.shapes.head.shape.asInstanceOf[MapShape]
        assertTrue(
          mp.key.name == "key",
          mp.key.target == shapeId("String"),
          mp.value.name == "value",
          mp.value.target == shapeId("Integer")
        )
      }
    ),
    suite("enum")(
      test("parses enum without values") {
        val m = parseOk(
          """enum Suit {
            |    SPADES
            |    HEARTS
            |    DIAMONDS
            |    CLUBS
            |}""".stripMargin
        )
        val e = m.shapes.head.shape.asInstanceOf[EnumShape]
        assertTrue(
          e.members.length == 4,
          e.members(0).name == "SPADES",
          e.members(0).value.isEmpty,
          e.members(1).name == "HEARTS",
          e.members(2).name == "DIAMONDS",
          e.members(3).name == "CLUBS"
        )
      },
      test("parses enum with string values") {
        val m = parseOk(
          """enum Suit {
            |    SPADES = "spades"
            |    HEARTS = "hearts"
            |}""".stripMargin
        )
        val e = m.shapes.head.shape.asInstanceOf[EnumShape]
        assertTrue(
          e.members(0).value == Some("spades"),
          e.members(1).value == Some("hearts")
        )
      },
      test("parses enum member with traits") {
        val m = parseOk(
          """enum Suit {
            |    @documentation("The spades suit")
            |    SPADES
            |    HEARTS
            |}""".stripMargin
        )
        val e = m.shapes.head.shape.asInstanceOf[EnumShape]
        assertTrue(
          e.members(0).traits == List(TraitApplication.documentation("The spades suit")),
          e.members(1).traits.isEmpty
        )
      }
    ),
    suite("intEnum")(
      test("parses intEnum") {
        val m = parseOk(
          """intEnum FaceCard {
            |    JACK = 11
            |    QUEEN = 12
            |    KING = 13
            |}""".stripMargin
        )
        val e = m.shapes.head.shape.asInstanceOf[IntEnumShape]
        assertTrue(
          e.members.length == 3,
          e.members(0).name == "JACK",
          e.members(0).value == 11,
          e.members(1).name == "QUEEN",
          e.members(1).value == 12,
          e.members(2).name == "KING",
          e.members(2).value == 13
        )
      },
      test("parses intEnum with member traits") {
        val m = parseOk(
          """intEnum FaceCard {
            |    @documentation("Jack card")
            |    JACK = 11
            |    QUEEN = 12
            |}""".stripMargin
        )
        val e = m.shapes.head.shape.asInstanceOf[IntEnumShape]
        assertTrue(
          e.members(0).traits == List(TraitApplication.documentation("Jack card")),
          e.members(1).traits.isEmpty
        )
      }
    ),
    suite("service")(
      test("parses service with version") {
        val m = parseOk(
          """service MyService {
            |    version: "1.0"
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[ServiceShape]
        assertTrue(
          s.name == "MyService",
          s.version == Some("1.0")
        )
      },
      test("parses service with operations and resources") {
        val m = parseOk(
          """service MyService {
            |    version: "1.0"
            |    operations: [GetFoo, PutFoo]
            |    resources: [FooResource]
            |    errors: [NotFound]
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[ServiceShape]
        assertTrue(
          s.operations == List(shapeId("GetFoo"), shapeId("PutFoo")),
          s.resources == List(shapeId("FooResource")),
          s.errors == List(shapeId("NotFound"))
        )
      },
      test("parses empty service") {
        val m = parseOk("service MyService {}")
        val s = m.shapes.head.shape.asInstanceOf[ServiceShape]
        assertTrue(
          s.version.isEmpty,
          s.operations.isEmpty,
          s.resources.isEmpty,
          s.errors.isEmpty
        )
      }
    ),
    suite("operation")(
      test("parses operation with input, output, errors") {
        val m = parseOk(
          """operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |    errors: [NotFound, BadRequest]
            |}""".stripMargin
        )
        val op = m.shapes.head.shape.asInstanceOf[OperationShape]
        assertTrue(
          op.input == Some(shapeId("GetFooInput")),
          op.output == Some(shapeId("GetFooOutput")),
          op.errors == List(shapeId("NotFound"), shapeId("BadRequest"))
        )
      },
      test("parses empty operation") {
        val m  = parseOk("operation GetFoo {}")
        val op = m.shapes.head.shape.asInstanceOf[OperationShape]
        assertTrue(
          op.input.isEmpty,
          op.output.isEmpty,
          op.errors.isEmpty
        )
      }
    ),
    suite("resource")(
      test("parses resource with identifiers and lifecycle ops") {
        val m = parseOk(
          """resource FooResource {
            |    identifiers: { id: FooId }
            |    create: CreateFoo
            |    read: GetFoo
            |    update: UpdateFoo
            |    delete: DeleteFoo
            |    list: ListFoos
            |    operations: [SpecialOp]
            |    collectionOperations: [BatchOp]
            |    resources: [SubResource]
            |}""".stripMargin
        )
        val r = m.shapes.head.shape.asInstanceOf[ResourceShape]
        assertTrue(
          r.identifiers == Map("id" -> shapeId("FooId")),
          r.create == Some(shapeId("CreateFoo")),
          r.read == Some(shapeId("GetFoo")),
          r.update == Some(shapeId("UpdateFoo")),
          r.delete == Some(shapeId("DeleteFoo")),
          r.list == Some(shapeId("ListFoos")),
          r.operations == List(shapeId("SpecialOp")),
          r.collectionOperations == List(shapeId("BatchOp")),
          r.resources == List(shapeId("SubResource"))
        )
      },
      test("parses empty resource") {
        val m = parseOk("resource FooResource {}")
        val r = m.shapes.head.shape.asInstanceOf[ResourceShape]
        assertTrue(
          r.identifiers.isEmpty,
          r.create.isEmpty,
          r.operations.isEmpty
        )
      }
    ),
    suite("apply statements")(
      test("parses apply statement with single trait") {
        val m = parseOk("apply MyShape @deprecated")
        assertTrue(
          m.applyStatements.length == 1,
          m.applyStatements.head.target == "MyShape",
          m.applyStatements.head.traits.length == 1,
          m.applyStatements.head.traits.head.id == ShapeId("smithy.api", "deprecated")
        )
      },
      test("parses apply statement with trait value") {
        val m = parseOk("""apply MyShape @tags(["foo", "bar"])""")
        assertTrue(
          m.applyStatements.length == 1,
          m.applyStatements.head.target == "MyShape"
        )
      },
      test("parses multiple apply statements") {
        val m = parseOk(
          """apply MyShape @deprecated
            |apply MyShape @tags(["foo"])""".stripMargin
        )
        assertTrue(m.applyStatements.length == 2)
      }
    ),
    suite("mixed complex model")(
      test("parses file with multiple shape types") {
        val m = parseOk(
          """string Name
            |
            |structure User {
            |    @required
            |    name: Name
            |    age: Integer
            |}
            |
            |union Result {
            |    success: User
            |    error: String
            |}
            |
            |list Users {
            |    member: User
            |}
            |
            |map UserNames {
            |    key: String
            |    value: Name
            |}
            |
            |enum Status {
            |    ACTIVE
            |    INACTIVE
            |}
            |
            |service UserService {
            |    version: "1.0"
            |    operations: [GetUser]
            |}
            |
            |operation GetUser {
            |    input: GetUserInput
            |    output: User
            |}
            |
            |apply Name @documentation("A user name")""".stripMargin
        )
        assertTrue(
          m.shapes.length == 8,
          m.shapes(0).shape.isInstanceOf[StringShape],
          m.shapes(1).shape.isInstanceOf[StructureShape],
          m.shapes(2).shape.isInstanceOf[UnionShape],
          m.shapes(3).shape.isInstanceOf[ListShape],
          m.shapes(4).shape.isInstanceOf[MapShape],
          m.shapes(5).shape.isInstanceOf[EnumShape],
          m.shapes(6).shape.isInstanceOf[ServiceShape],
          m.shapes(7).shape.isInstanceOf[OperationShape],
          m.applyStatements.length == 1
        )
      }
    ),
    suite("member target with namespace")(
      test("parses structure member with fully qualified target") {
        val m = parseOk(
          """structure Foo {
            |    bar: smithy.api#String
            |}""".stripMargin
        )
        val s = m.shapes.head.shape.asInstanceOf[StructureShape]
        assertTrue(
          s.members(0).target == ShapeId("smithy.api", "String")
        )
      }
    ),
    suite("resource with multiple identifiers")(
      test("parses resource identifiers map") {
        val m = parseOk(
          """resource Foo {
            |    identifiers: { id: FooId, name: String }
            |}""".stripMargin
        )
        val r = m.shapes.head.shape.asInstanceOf[ResourceShape]
        assertTrue(
          r.identifiers.size == 2,
          r.identifiers("id") == shapeId("FooId"),
          r.identifiers("name") == shapeId("String")
        )
      }
    )
  )
}
