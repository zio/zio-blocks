package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object TypeDefinitionSpec extends ZIOSpecDefault {
  def spec =
    suite("TypeDefinition")(
      suite("CaseClass")(
        test("basic construction") {
          val cc = CaseClass("Person", fields = List(Field("name", TypeRef.String)))
          assert(cc.name)(equalTo("Person")) &&
          assert(cc.fields.length)(equalTo(1)) &&
          assert(cc.typeParams)(isEmpty) &&
          assert(cc.extendsTypes)(isEmpty) &&
          assert(cc.derives)(isEmpty) &&
          assert(cc.annotations)(isEmpty) &&
          assert(cc.companion)(isNone) &&
          assert(cc.doc)(isNone)
        },
        test("with multiple fields") {
          val cc = CaseClass(
            "Person",
            fields = List(
              Field("name", TypeRef.String),
              Field("age", TypeRef.Int),
              Field("email", TypeRef.optional(TypeRef.String))
            )
          )
          assert(cc.fields.length)(equalTo(3)) &&
          assert(cc.fields(0).name)(equalTo("name")) &&
          assert(cc.fields(1).name)(equalTo("age")) &&
          assert(cc.fields(2).typeRef.name)(equalTo("Option"))
        },
        test("with type parameters") {
          val cc = CaseClass(
            "Container",
            fields = List(Field("value", TypeRef("A"))),
            typeParams = List(TypeRef("A"))
          )
          assert(cc.typeParams.length)(equalTo(1)) &&
          assert(cc.typeParams(0).name)(equalTo("A"))
        },
        test("with extends types") {
          val cc = CaseClass(
            "Dog",
            fields = List(Field("name", TypeRef.String)),
            extendsTypes = List(TypeRef("Animal"), TypeRef("Serializable"))
          )
          assert(cc.extendsTypes.length)(equalTo(2)) &&
          assert(cc.extendsTypes(0).name)(equalTo("Animal")) &&
          assert(cc.extendsTypes(1).name)(equalTo("Serializable"))
        },
        test("with derives") {
          val cc = CaseClass(
            "User",
            fields = List(Field("id", TypeRef.Long)),
            derives = List("Schema", "Codec")
          )
          assert(cc.derives.length)(equalTo(2)) &&
          assert(cc.derives(0))(equalTo("Schema")) &&
          assert(cc.derives(1))(equalTo("Codec"))
        },
        test("with annotations") {
          val cc = CaseClass(
            "Config",
            fields = List(Field("key", TypeRef.String)),
            annotations = List(Annotation("deprecated", List(("message", "\"use v2\""))))
          )
          assert(cc.annotations.length)(equalTo(1)) &&
          assert(cc.annotations(0).name)(equalTo("deprecated"))
        },
        test("with companion object") {
          val companion = CompanionObject(
            members = List(
              ObjectMember.ValMember("default", TypeRef("Config"), "Config(\"\")")
            )
          )
          val cc = CaseClass(
            "Config",
            fields = List(Field("key", TypeRef.String)),
            companion = Some(companion)
          )
          assert(cc.companion)(isSome) &&
          assert(cc.companion.get.members.length)(equalTo(1))
        },
        test("with documentation") {
          val cc = CaseClass(
            "User",
            fields = List(Field("name", TypeRef.String)),
            doc = Some("Represents a user in the system")
          )
          assert(cc.doc)(isSome(equalTo("Represents a user in the system")))
        },
        test("is a TypeDefinition") {
          val cc: TypeDefinition = CaseClass("Foo", fields = List(Field("x", TypeRef.Int)))
          assert(cc.name)(equalTo("Foo")) &&
          assert(cc.annotations)(isEmpty) &&
          assert(cc.doc)(isNone)
        }
      ),
      suite("SealedTrait")(
        test("basic construction") {
          val st = SealedTrait("Shape")
          assert(st.name)(equalTo("Shape")) &&
          assert(st.typeParams)(isEmpty) &&
          assert(st.cases)(isEmpty) &&
          assert(st.annotations)(isEmpty) &&
          assert(st.companion)(isNone) &&
          assert(st.doc)(isNone)
        },
        test("with CaseClassCase") {
          val circleCC = CaseClass("Circle", fields = List(Field("radius", TypeRef.Double)))
          val st       = SealedTrait(
            "Shape",
            cases = List(SealedTraitCase.CaseClassCase(circleCC))
          )
          assert(st.cases.length)(equalTo(1)) &&
          assert(st.cases(0))(
            isSubtype[SealedTraitCase.CaseClassCase](
              hasField(
                "cc",
                (c: SealedTraitCase.CaseClassCase) => c.cc,
                hasField("name", (cc: CaseClass) => cc.name, equalTo("Circle"))
              )
            )
          )
        },
        test("with CaseObjectCase") {
          val st = SealedTrait(
            "Color",
            cases = List(SealedTraitCase.CaseObjectCase("Red"))
          )
          assert(st.cases.length)(equalTo(1)) &&
          assert(st.cases(0))(
            isSubtype[SealedTraitCase.CaseObjectCase](
              hasField("name", _.name, equalTo("Red"))
            )
          )
        },
        test("with mixed cases") {
          val circleCC = CaseClass("Circle", fields = List(Field("radius", TypeRef.Double)))
          val rectCC   = CaseClass(
            "Rectangle",
            fields = List(Field("width", TypeRef.Double), Field("height", TypeRef.Double))
          )
          val st = SealedTrait(
            "Shape",
            cases = List(
              SealedTraitCase.CaseClassCase(circleCC),
              SealedTraitCase.CaseClassCase(rectCC),
              SealedTraitCase.CaseObjectCase("Unknown")
            )
          )
          assert(st.cases.length)(equalTo(3))
        },
        test("with type parameters") {
          val st = SealedTrait(
            "Result",
            typeParams = List(TypeRef("A"), TypeRef("E"))
          )
          assert(st.typeParams.length)(equalTo(2)) &&
          assert(st.typeParams(0).name)(equalTo("A")) &&
          assert(st.typeParams(1).name)(equalTo("E"))
        },
        test("with companion and doc") {
          val companion = CompanionObject()
          val st        = SealedTrait(
            "Event",
            companion = Some(companion),
            doc = Some("Domain events")
          )
          assert(st.companion)(isSome) &&
          assert(st.doc)(isSome(equalTo("Domain events")))
        },
        test("is a TypeDefinition") {
          val st: TypeDefinition = SealedTrait("Shape")
          assert(st.name)(equalTo("Shape")) &&
          assert(st.annotations)(isEmpty) &&
          assert(st.doc)(isNone)
        }
      ),
      suite("Enum")(
        test("with simple cases") {
          val e = Enum(
            "Color",
            cases = List(
              EnumCase.SimpleCase("Red"),
              EnumCase.SimpleCase("Green"),
              EnumCase.SimpleCase("Blue")
            )
          )
          assert(e.name)(equalTo("Color")) &&
          assert(e.cases.length)(equalTo(3)) &&
          assert(e.extendsTypes)(isEmpty) &&
          assert(e.annotations)(isEmpty) &&
          assert(e.doc)(isNone)
        },
        test("with parameterized cases") {
          val e = Enum(
            "Expr",
            cases = List(
              EnumCase.ParameterizedCase("Lit", List(Field("value", TypeRef.Int))),
              EnumCase.ParameterizedCase(
                "Add",
                List(Field("left", TypeRef("Expr")), Field("right", TypeRef("Expr")))
              )
            )
          )
          assert(e.cases.length)(equalTo(2)) &&
          assert(e.cases(0))(
            isSubtype[EnumCase.ParameterizedCase](
              hasField("name", _.name, equalTo("Lit"))
            )
          ) &&
          assert(e.cases(1))(
            isSubtype[EnumCase.ParameterizedCase](
              hasField("fields", _.fields, hasSize(equalTo(2)))
            )
          )
        },
        test("with mixed cases") {
          val e = Enum(
            "Shape",
            cases = List(
              EnumCase.SimpleCase("Unknown"),
              EnumCase.ParameterizedCase("Circle", List(Field("radius", TypeRef.Double)))
            )
          )
          assert(e.cases.length)(equalTo(2))
        },
        test("with extends types") {
          val e = Enum(
            "Status",
            cases = List(EnumCase.SimpleCase("Active")),
            extendsTypes = List(TypeRef("Serializable"))
          )
          assert(e.extendsTypes.length)(equalTo(1)) &&
          assert(e.extendsTypes(0).name)(equalTo("Serializable"))
        },
        test("with annotations and doc") {
          val e = Enum(
            "Priority",
            cases = List(EnumCase.SimpleCase("High"), EnumCase.SimpleCase("Low")),
            annotations = List(Annotation("stable")),
            doc = Some("Task priority levels")
          )
          assert(e.annotations.length)(equalTo(1)) &&
          assert(e.doc)(isSome(equalTo("Task priority levels")))
        },
        test("is a TypeDefinition") {
          val e: TypeDefinition = Enum("Color", cases = List(EnumCase.SimpleCase("Red")))
          assert(e.name)(equalTo("Color")) &&
          assert(e.annotations)(isEmpty) &&
          assert(e.doc)(isNone)
        }
      ),
      suite("ObjectDef")(
        test("basic construction") {
          val obj = ObjectDef("Utils")
          assert(obj.name)(equalTo("Utils")) &&
          assert(obj.members)(isEmpty) &&
          assert(obj.extendsTypes)(isEmpty) &&
          assert(obj.annotations)(isEmpty) &&
          assert(obj.doc)(isNone)
        },
        test("with ValMember") {
          val obj = ObjectDef(
            "Constants",
            members = List(
              ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3"),
              ObjectMember.ValMember("Timeout", TypeRef.Long, "5000L")
            )
          )
          assert(obj.members.length)(equalTo(2)) &&
          assert(obj.members(0))(
            isSubtype[ObjectMember.ValMember](
              hasField("name", _.name, equalTo("MaxRetries"))
            )
          )
        },
        test("with DefMember") {
          val method = Method(
            "create",
            params = List(List(MethodParam("name", TypeRef.String))),
            returnType = TypeRef("User")
          )
          val obj = ObjectDef(
            "UserFactory",
            members = List(ObjectMember.DefMember(method))
          )
          assert(obj.members.length)(equalTo(1)) &&
          assert(obj.members(0))(
            isSubtype[ObjectMember.DefMember](
              hasField(
                "method",
                (d: ObjectMember.DefMember) => d.method,
                hasField("name", (m: Method) => m.name, equalTo("create"))
              )
            )
          )
        },
        test("with TypeAlias") {
          val obj = ObjectDef(
            "Types",
            members = List(
              ObjectMember.TypeAlias("UserId", TypeRef.Long),
              ObjectMember.TypeAlias("UserMap", TypeRef.map(TypeRef.String, TypeRef("User")))
            )
          )
          assert(obj.members.length)(equalTo(2)) &&
          assert(obj.members(0))(
            isSubtype[ObjectMember.TypeAlias](
              hasField("name", _.name, equalTo("UserId"))
            )
          )
        },
        test("with NestedType") {
          val nestedCC = CaseClass("Inner", fields = List(Field("value", TypeRef.Int)))
          val obj      = ObjectDef(
            "Outer",
            members = List(ObjectMember.NestedType(nestedCC))
          )
          assert(obj.members.length)(equalTo(1)) &&
          assert(obj.members(0))(
            isSubtype[ObjectMember.NestedType](
              hasField(
                "typeDef",
                (n: ObjectMember.NestedType) => n.typeDef,
                hasField("name", (td: TypeDefinition) => td.name, equalTo("Inner"))
              )
            )
          )
        },
        test("with extends types") {
          val obj = ObjectDef(
            "MyApp",
            extendsTypes = List(TypeRef("App"))
          )
          assert(obj.extendsTypes.length)(equalTo(1)) &&
          assert(obj.extendsTypes(0).name)(equalTo("App"))
        },
        test("with mixed members") {
          val method = Method("apply", returnType = TypeRef("Config"))
          val obj    = ObjectDef(
            "Config",
            members = List(
              ObjectMember.ValMember("default", TypeRef("Config"), "Config()"),
              ObjectMember.DefMember(method),
              ObjectMember.TypeAlias("Id", TypeRef.Long)
            )
          )
          assert(obj.members.length)(equalTo(3))
        },
        test("is a TypeDefinition") {
          val obj: TypeDefinition = ObjectDef("Utils")
          assert(obj.name)(equalTo("Utils")) &&
          assert(obj.annotations)(isEmpty) &&
          assert(obj.doc)(isNone)
        }
      ),
      suite("CompanionObject")(
        test("basic empty companion") {
          val comp = CompanionObject()
          assert(comp.members)(isEmpty)
        },
        test("with members") {
          val comp = CompanionObject(
            members = List(
              ObjectMember.ValMember("empty", TypeRef("MyType"), "MyType()"),
              ObjectMember.TypeAlias("Builder", TypeRef("MyTypeBuilder"))
            )
          )
          assert(comp.members.length)(equalTo(2))
        }
      ),
      suite("Newtype")(
        test("basic construction") {
          val nt = Newtype("UserId", wrappedType = TypeRef.Long)
          assert(nt.name)(equalTo("UserId")) &&
          assert(nt.wrappedType.name)(equalTo("Long")) &&
          assert(nt.annotations)(isEmpty) &&
          assert(nt.doc)(isNone)
        },
        test("with annotations") {
          val nt = Newtype(
            "Email",
            wrappedType = TypeRef.String,
            annotations = List(Annotation("validated"))
          )
          assert(nt.annotations.length)(equalTo(1)) &&
          assert(nt.annotations(0).name)(equalTo("validated"))
        },
        test("with documentation") {
          val nt = Newtype(
            "Temperature",
            wrappedType = TypeRef.Double,
            doc = Some("Temperature in Celsius")
          )
          assert(nt.doc)(isSome(equalTo("Temperature in Celsius")))
        },
        test("with complex wrapped type") {
          val nt = Newtype(
            "UserIds",
            wrappedType = TypeRef.list(TypeRef.Long)
          )
          assert(nt.wrappedType.name)(equalTo("List")) &&
          assert(nt.wrappedType.typeArgs(0).name)(equalTo("Long"))
        },
        test("is a TypeDefinition") {
          val nt: TypeDefinition = Newtype("UserId", wrappedType = TypeRef.Long)
          assert(nt.name)(equalTo("UserId")) &&
          assert(nt.annotations)(isEmpty) &&
          assert(nt.doc)(isNone)
        }
      ),
      suite("Method placeholder")(
        test("basic construction") {
          val m = Method("doSomething", returnType = TypeRef.Unit)
          assert(m.name)(equalTo("doSomething")) &&
          assert(m.typeParams)(isEmpty) &&
          assert(m.params)(isEmpty) &&
          assert(m.returnType.name)(equalTo("Unit")) &&
          assert(m.body)(isNone) &&
          assert(m.annotations)(isEmpty) &&
          assert(m.isOverride)(isFalse) &&
          assert(m.doc)(isNone)
        },
        test("with parameters") {
          val m = Method(
            "greet",
            params = List(
              List(
                MethodParam("name", TypeRef.String),
                MethodParam("greeting", TypeRef.String, Some("\"Hello\""))
              )
            ),
            returnType = TypeRef.String
          )
          assert(m.params.length)(equalTo(1)) &&
          assert(m.params(0).length)(equalTo(2)) &&
          assert(m.params(0)(0).name)(equalTo("name")) &&
          assert(m.params(0)(1).defaultValue)(isSome(equalTo("\"Hello\"")))
        },
        test("with type parameters and body") {
          val m = Method(
            "identity",
            typeParams = List(TypeRef("A")),
            params = List(List(MethodParam("a", TypeRef("A")))),
            returnType = TypeRef("A"),
            body = Some("a")
          )
          assert(m.typeParams.length)(equalTo(1)) &&
          assert(m.body)(isSome(equalTo("a")))
        },
        test("with override and annotations") {
          val m = Method(
            "toString",
            returnType = TypeRef.String,
            body = Some("\"Custom\""),
            annotations = List(Annotation("deprecated")),
            isOverride = true,
            doc = Some("Custom toString")
          )
          assert(m.isOverride)(isTrue) &&
          assert(m.annotations.length)(equalTo(1)) &&
          assert(m.doc)(isSome(equalTo("Custom toString")))
        }
      )
    )
}
