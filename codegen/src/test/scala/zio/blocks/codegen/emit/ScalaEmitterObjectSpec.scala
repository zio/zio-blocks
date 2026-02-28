package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterObjectSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter")(
      suite("emitObjectDef")(
        test("empty object") {
          val obj    = ObjectDef("Utils")
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result == "object Utils")
        },
        test("object with val member") {
          val obj = ObjectDef(
            "Constants",
            members = List(
              ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3")
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Constants {
                 |  val MaxRetries: Int = 3
                 |}""".stripMargin
          )
        },
        test("object with def member") {
          val obj = ObjectDef(
            "Utils",
            members = List(
              ObjectMember.DefMember(
                Method(
                  "helper",
                  params = List(List(MethodParam("x", TypeRef.Int))),
                  returnType = TypeRef.String,
                  body = Some("x.toString")
                )
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Utils {
                 |  def helper(x: Int): String = x.toString
                 |}""".stripMargin
          )
        },
        test("object with type alias") {
          val obj = ObjectDef(
            "Types",
            members = List(
              ObjectMember.TypeAlias("Id", TypeRef.Long)
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Types {
                 |  type Id = Long
                 |}""".stripMargin
          )
        },
        test("object with multiple members") {
          val obj = ObjectDef(
            "Utils",
            members = List(
              ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3"),
              ObjectMember.TypeAlias("Id", TypeRef.Long),
              ObjectMember.DefMember(
                Method(
                  "helper",
                  params = List(List(MethodParam("x", TypeRef.Int))),
                  returnType = TypeRef.String,
                  body = Some("x.toString")
                )
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Utils {
                 |  val MaxRetries: Int = 3
                 |  type Id = Long
                 |  def helper(x: Int): String = x.toString
                 |}""".stripMargin
          )
        },
        test("object with extends") {
          val obj = ObjectDef(
            "MyApp",
            members = List(ObjectMember.ValMember("version", TypeRef.String, "\"1.0\"")),
            extendsTypes = List(TypeRef("App"))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object MyApp extends App {
                 |  val version: String = "1.0"
                 |}""".stripMargin
          )
        },
        test("case object with extends and no members") {
          val obj = ObjectDef(
            "Unknown",
            extendsTypes = List(TypeRef("Shape")),
            isCaseObject = true
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result == "case object Unknown extends Shape")
        },
        test("object with nested type") {
          val obj = ObjectDef(
            "Outer",
            members = List(
              ObjectMember.NestedType(
                CaseClass("Inner", List(Field("value", TypeRef.Int)))
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Outer {
                 |  case class Inner(
                 |    value: Int,
                 |  )
                 |}""".stripMargin
          )
        },
        test("object with doc and annotations") {
          val obj = ObjectDef(
            "Utils",
            doc = Some("Utility functions"),
            annotations = List(Annotation("deprecated"))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|/** Utility functions */
                 |@deprecated
                 |object Utils""".stripMargin
          )
        }
      ),
      suite("emitMethod")(
        test("simple method with body") {
          val method = Method(
            "greet",
            params = List(List(MethodParam("name", TypeRef.String))),
            returnType = TypeRef.String,
            body = Some("s\"Hello, $name\"")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def greet(name: String): String = s\"Hello, $name\"")
        },
        test("method with type params") {
          val method = Method(
            "identity",
            typeParams = List(TypeRef("A")),
            params = List(List(MethodParam("value", TypeRef("A")))),
            returnType = TypeRef("A"),
            body = Some("value")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def identity[A](value: A): A = value")
        },
        test("curried method") {
          val method = Method(
            "fold",
            typeParams = List(TypeRef("B")),
            params = List(
              List(MethodParam("z", TypeRef("B"))),
              List(MethodParam("f", TypeRef("(B, A) => B")))
            ),
            returnType = TypeRef("B")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def fold[B](z: B)(f: (B, A) => B): B")
        },
        test("abstract method (no body)") {
          val method = Method(
            "process",
            params = List(List(MethodParam("input", TypeRef.String))),
            returnType = TypeRef.Int
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def process(input: String): Int")
        },
        test("override method") {
          val method = Method(
            "toString",
            returnType = TypeRef.String,
            body = Some("\"custom\""),
            isOverride = true
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "override def toString: String = \"custom\"")
        },
        test("method with default params") {
          val method = Method(
            "connect",
            params = List(
              List(
                MethodParam("host", TypeRef.String),
                MethodParam("port", TypeRef.Int, Some("8080"))
              )
            ),
            returnType = TypeRef.Unit,
            body = Some("()")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def connect(host: String, port: Int = 8080): Unit = ()")
        },
        test("method with doc and annotations") {
          val method = Method(
            "run",
            returnType = TypeRef.Unit,
            body = Some("()"),
            doc = Some("Runs the process"),
            annotations = List(Annotation("inline"))
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(
            result ==
              """|/** Runs the process */
                 |@inline
                 |def run: Unit = ()""".stripMargin
          )
        },
        test("no-param method") {
          val method = Method(
            "now",
            returnType = TypeRef.Long,
            body = Some("System.currentTimeMillis()")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def now: Long = System.currentTimeMillis()")
        }
      ),
      suite("emitNewtype")(
        test("simple newtype") {
          val nt     = Newtype("UserId", wrappedType = TypeRef.String)
          val result = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          assertTrue(
            result ==
              """|object UserId extends Newtype[String]
                 |type UserId = UserId.Type""".stripMargin
          )
        },
        test("newtype with generic wrapped type") {
          val nt     = Newtype("Ids", wrappedType = TypeRef.list(TypeRef.Long))
          val result = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Ids extends Newtype[List[Long]]
                 |type Ids = Ids.Type""".stripMargin
          )
        },
        test("newtype with doc and annotations") {
          val nt = Newtype(
            "UserId",
            wrappedType = TypeRef.String,
            doc = Some("Strong typed user ID"),
            annotations = List(Annotation("opaque"))
          )
          val result = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          assertTrue(
            result ==
              """|/** Strong typed user ID */
                 |@opaque
                 |object UserId extends Newtype[String]
                 |type UserId = UserId.Type""".stripMargin
          )
        }
      ),
      suite("emitCompanionObject")(
        test("empty companion") {
          val result = ScalaEmitter.emitCompanionObject("Foo", CompanionObject(), EmitterConfig.default)
          assertTrue(result == "\nobject Foo")
        },
        test("companion with members") {
          val comp = CompanionObject(
            members = List(
              ObjectMember.ValMember("empty", TypeRef("Foo"), "Foo()")
            )
          )
          val result = ScalaEmitter.emitCompanionObject("Foo", comp, EmitterConfig.default)
          assertTrue(
            result ==
              """|
                 |object Foo {
                 |  val empty: Foo = Foo()
                 |}""".stripMargin
          )
        }
      ),
      suite("emitTypeDefinition dispatches")(
        test("dispatches CaseClass") {
          val cc     = CaseClass("X", fields = Nil)
          val direct = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
          val via    = ScalaEmitter.emitTypeDefinition(cc, EmitterConfig.default)
          assertTrue(direct == via)
        },
        test("dispatches ObjectDef") {
          val od     = ObjectDef("X")
          val direct = ScalaEmitter.emitObjectDef(od, EmitterConfig.default)
          val via    = ScalaEmitter.emitTypeDefinition(od, EmitterConfig.default)
          assertTrue(direct == via)
        },
        test("dispatches Newtype") {
          val nt     = Newtype("X", TypeRef.Int)
          val direct = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          val via    = ScalaEmitter.emitTypeDefinition(nt, EmitterConfig.default)
          assertTrue(direct == via)
        }
      )
    )
}
