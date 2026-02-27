package zio.blocks.smithy

import zio.test._

object ShapeSpec extends ZIOSpecDefault {

  private val stringId      = ShapeId("smithy.api", "String")
  private val integerId     = ShapeId("smithy.api", "Integer")
  private val docTrait      = TraitApplication.documentation("A test shape")
  private val requiredTrait = TraitApplication.required

  def spec = suite("Shape")(
    suite("simple shapes")(
      test("BlobShape has name and empty traits") {
        val s = BlobShape("MyBlob")
        assertTrue(
          s.name == "MyBlob",
          s.traits == Nil
        )
      },
      test("BooleanShape with traits") {
        val s = BooleanShape("MyBool", List(docTrait))
        assertTrue(
          s.name == "MyBool",
          s.traits == List(docTrait)
        )
      },
      test("StringShape construction") {
        val s = StringShape("MyString", List(docTrait, requiredTrait))
        assertTrue(
          s.name == "MyString",
          s.traits.length == 2
        )
      },
      test("ByteShape construction") {
        val s = ByteShape("MyByte")
        assertTrue(s.name == "MyByte", s.traits.isEmpty)
      },
      test("ShortShape construction") {
        val s = ShortShape("MyShort")
        assertTrue(s.name == "MyShort", s.traits.isEmpty)
      },
      test("IntegerShape construction") {
        val s = IntegerShape("MyInt")
        assertTrue(s.name == "MyInt", s.traits.isEmpty)
      },
      test("LongShape construction") {
        val s = LongShape("MyLong")
        assertTrue(s.name == "MyLong", s.traits.isEmpty)
      },
      test("FloatShape construction") {
        val s = FloatShape("MyFloat")
        assertTrue(s.name == "MyFloat", s.traits.isEmpty)
      },
      test("DoubleShape construction") {
        val s = DoubleShape("MyDouble")
        assertTrue(s.name == "MyDouble", s.traits.isEmpty)
      },
      test("BigIntegerShape construction") {
        val s = BigIntegerShape("MyBigInt")
        assertTrue(s.name == "MyBigInt", s.traits.isEmpty)
      },
      test("BigDecimalShape construction") {
        val s = BigDecimalShape("MyBigDec")
        assertTrue(s.name == "MyBigDec", s.traits.isEmpty)
      },
      test("TimestampShape construction") {
        val s = TimestampShape("MyTimestamp")
        assertTrue(s.name == "MyTimestamp", s.traits.isEmpty)
      },
      test("DocumentShape construction") {
        val s = DocumentShape("MyDoc")
        assertTrue(s.name == "MyDoc", s.traits.isEmpty)
      },
      test("all simple shapes extend Shape") {
        val shapes: List[Shape] = List(
          BlobShape("a"),
          BooleanShape("b"),
          StringShape("c"),
          ByteShape("d"),
          ShortShape("e"),
          IntegerShape("f"),
          LongShape("g"),
          FloatShape("h"),
          DoubleShape("i"),
          BigIntegerShape("j"),
          BigDecimalShape("k"),
          TimestampShape("l"),
          DocumentShape("m")
        )
        assertTrue(shapes.length == 13)
      }
    ),
    suite("enum shapes")(
      test("EnumShape with members") {
        val members = List(
          EnumMember("ACTIVE", Some("active"), List(docTrait)),
          EnumMember("INACTIVE", None, Nil)
        )
        val s = EnumShape("Status", List(docTrait), members)
        assertTrue(
          s.name == "Status",
          s.traits == List(docTrait),
          s.members.length == 2,
          s.members.head.name == "ACTIVE",
          s.members.head.value == Some("active"),
          s.members.head.traits == List(docTrait),
          s.members(1).name == "INACTIVE",
          s.members(1).value.isEmpty
        )
      },
      test("IntEnumShape with members") {
        val members = List(
          IntEnumMember("OK", 200, Nil),
          IntEnumMember("NOT_FOUND", 404, List(docTrait))
        )
        val s = IntEnumShape("StatusCode", Nil, members)
        assertTrue(
          s.name == "StatusCode",
          s.traits.isEmpty,
          s.members.length == 2,
          s.members.head.name == "OK",
          s.members.head.value == 200,
          s.members(1).name == "NOT_FOUND",
          s.members(1).value == 404,
          s.members(1).traits == List(docTrait)
        )
      },
      test("EnumShape and IntEnumShape extend Shape") {
        val shapes: List[Shape] = List(
          EnumShape("E1", Nil, Nil),
          IntEnumShape("E2", Nil, Nil)
        )
        assertTrue(shapes.length == 2)
      }
    ),
    suite("aggregate shapes")(
      test("ListShape with member") {
        val member = MemberDefinition("member", stringId, Nil)
        val s      = ListShape("Names", Nil, member)
        assertTrue(
          s.name == "Names",
          s.member.name == "member",
          s.member.target == stringId,
          s.member.traits.isEmpty
        )
      },
      test("MapShape with key and value") {
        val key   = MemberDefinition("key", stringId, Nil)
        val value = MemberDefinition("value", integerId, List(requiredTrait))
        val s     = MapShape("Scores", List(docTrait), key, value)
        assertTrue(
          s.name == "Scores",
          s.traits == List(docTrait),
          s.key.name == "key",
          s.key.target == stringId,
          s.value.name == "value",
          s.value.target == integerId,
          s.value.traits == List(requiredTrait)
        )
      },
      test("StructureShape with members") {
        val members = List(
          MemberDefinition("name", stringId, List(requiredTrait)),
          MemberDefinition("age", integerId, Nil)
        )
        val s = StructureShape("Person", List(docTrait), members)
        assertTrue(
          s.name == "Person",
          s.traits == List(docTrait),
          s.members.length == 2,
          s.members.head.name == "name",
          s.members.head.target == stringId,
          s.members.head.traits == List(requiredTrait),
          s.members(1).name == "age",
          s.members(1).target == integerId
        )
      },
      test("UnionShape with members") {
        val members = List(
          MemberDefinition("s", stringId, Nil),
          MemberDefinition("i", integerId, Nil)
        )
        val s = UnionShape("StringOrInt", Nil, members)
        assertTrue(
          s.name == "StringOrInt",
          s.members.length == 2,
          s.members.head.name == "s",
          s.members(1).name == "i"
        )
      },
      test("aggregate shapes extend Shape") {
        val shapes: List[Shape] = List(
          ListShape("L", Nil, MemberDefinition("member", stringId, Nil)),
          MapShape("M", Nil, MemberDefinition("key", stringId, Nil), MemberDefinition("value", stringId, Nil)),
          StructureShape("S", Nil, Nil),
          UnionShape("U", Nil, Nil)
        )
        assertTrue(shapes.length == 4)
      }
    ),
    suite("service shapes")(
      test("ServiceShape with all fields") {
        val opId  = ShapeId("com.example", "GetUser")
        val resId = ShapeId("com.example", "UserResource")
        val errId = ShapeId("com.example", "NotFound")
        val s     = ServiceShape(
          name = "MyService",
          traits = List(docTrait),
          version = Some("2024-01-01"),
          operations = List(opId),
          resources = List(resId),
          errors = List(errId)
        )
        assertTrue(
          s.name == "MyService",
          s.traits == List(docTrait),
          s.version == Some("2024-01-01"),
          s.operations == List(opId),
          s.resources == List(resId),
          s.errors == List(errId)
        )
      },
      test("ServiceShape with minimal fields") {
        val s = ServiceShape("MinimalService")
        assertTrue(
          s.name == "MinimalService",
          s.traits.isEmpty,
          s.version.isEmpty,
          s.operations.isEmpty,
          s.resources.isEmpty,
          s.errors.isEmpty
        )
      },
      test("OperationShape with all fields") {
        val inputId  = ShapeId("com.example", "GetUserInput")
        val outputId = ShapeId("com.example", "GetUserOutput")
        val errId    = ShapeId("com.example", "NotFound")
        val s        = OperationShape(
          name = "GetUser",
          traits = Nil,
          input = Some(inputId),
          output = Some(outputId),
          errors = List(errId)
        )
        assertTrue(
          s.name == "GetUser",
          s.input == Some(inputId),
          s.output == Some(outputId),
          s.errors == List(errId)
        )
      },
      test("OperationShape with minimal fields") {
        val s = OperationShape("NoOp")
        assertTrue(
          s.name == "NoOp",
          s.traits.isEmpty,
          s.input.isEmpty,
          s.output.isEmpty,
          s.errors.isEmpty
        )
      },
      test("ResourceShape with all fields") {
        val userId    = ShapeId("com.example", "UserId")
        val createId  = ShapeId("com.example", "CreateUser")
        val readId    = ShapeId("com.example", "GetUser")
        val updateId  = ShapeId("com.example", "UpdateUser")
        val deleteId  = ShapeId("com.example", "DeleteUser")
        val listId    = ShapeId("com.example", "ListUsers")
        val extraOpId = ShapeId("com.example", "ActivateUser")
        val collOpId  = ShapeId("com.example", "BatchDelete")
        val subResId  = ShapeId("com.example", "UserAddress")
        val s         = ResourceShape(
          name = "UserResource",
          traits = List(docTrait),
          identifiers = Map("userId" -> userId),
          create = Some(createId),
          read = Some(readId),
          update = Some(updateId),
          delete = Some(deleteId),
          list = Some(listId),
          operations = List(extraOpId),
          collectionOperations = List(collOpId),
          resources = List(subResId)
        )
        assertTrue(
          s.name == "UserResource",
          s.traits == List(docTrait),
          s.identifiers == Map("userId" -> userId),
          s.create == Some(createId),
          s.read == Some(readId),
          s.update == Some(updateId),
          s.delete == Some(deleteId),
          s.list == Some(listId),
          s.operations == List(extraOpId),
          s.collectionOperations == List(collOpId),
          s.resources == List(subResId)
        )
      },
      test("ResourceShape with minimal fields") {
        val s = ResourceShape("EmptyResource")
        assertTrue(
          s.name == "EmptyResource",
          s.traits.isEmpty,
          s.identifiers.isEmpty,
          s.create.isEmpty,
          s.read.isEmpty,
          s.update.isEmpty,
          s.delete.isEmpty,
          s.list.isEmpty,
          s.operations.isEmpty,
          s.collectionOperations.isEmpty,
          s.resources.isEmpty
        )
      },
      test("service shapes extend Shape") {
        val shapes: List[Shape] = List(
          ServiceShape("S"),
          OperationShape("O"),
          ResourceShape("R")
        )
        assertTrue(shapes.length == 3)
      }
    ),
    suite("supporting types")(
      test("MemberDefinition construction") {
        val m = MemberDefinition("field", stringId, List(requiredTrait))
        assertTrue(
          m.name == "field",
          m.target == stringId,
          m.traits == List(requiredTrait)
        )
      },
      test("MemberDefinition with default empty traits") {
        val m = MemberDefinition("field", stringId)
        assertTrue(m.traits.isEmpty)
      },
      test("EnumMember construction") {
        val m = EnumMember("VALUE", Some("val"), List(docTrait))
        assertTrue(
          m.name == "VALUE",
          m.value == Some("val"),
          m.traits == List(docTrait)
        )
      },
      test("EnumMember with defaults") {
        val m = EnumMember("VALUE")
        assertTrue(
          m.name == "VALUE",
          m.value.isEmpty,
          m.traits.isEmpty
        )
      },
      test("IntEnumMember construction") {
        val m = IntEnumMember("CODE", 42, List(docTrait))
        assertTrue(
          m.name == "CODE",
          m.value == 42,
          m.traits == List(docTrait)
        )
      },
      test("IntEnumMember with default traits") {
        val m = IntEnumMember("CODE", 0)
        assertTrue(
          m.name == "CODE",
          m.value == 0,
          m.traits.isEmpty
        )
      }
    ),
    suite("equality")(
      test("identical simple shapes are equal") {
        val a = StringShape("Foo", List(docTrait))
        val b = StringShape("Foo", List(docTrait))
        assertTrue(a == b)
      },
      test("different simple shape types are not equal") {
        val a: Shape = StringShape("Foo")
        val b: Shape = IntegerShape("Foo")
        assertTrue(a != b)
      },
      test("identical structure shapes are equal") {
        val members = List(MemberDefinition("x", stringId, Nil))
        val a       = StructureShape("S", Nil, members)
        val b       = StructureShape("S", Nil, members)
        assertTrue(a == b)
      },
      test("structure shapes with different members are not equal") {
        val a = StructureShape("S", Nil, List(MemberDefinition("x", stringId, Nil)))
        val b = StructureShape("S", Nil, List(MemberDefinition("y", stringId, Nil)))
        assertTrue(a != b)
      }
    )
  )
}
