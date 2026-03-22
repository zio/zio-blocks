/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                  params = List(ParamList(List(MethodParam("x", TypeRef.Int)))),
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
                  params = List(ParamList(List(MethodParam("x", TypeRef.Int)))),
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
            params = List(ParamList(List(MethodParam("name", TypeRef.String)))),
            returnType = TypeRef.String,
            body = Some("s\"Hello, $name\"")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def greet(name: String): String = s\"Hello, $name\"")
        },
        test("method with type params") {
          val method = Method(
            "identity",
            typeParams = List(TypeParam("A")),
            params = List(ParamList(List(MethodParam("value", TypeRef("A"))))),
            returnType = TypeRef("A"),
            body = Some("value")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def identity[A](value: A): A = value")
        },
        test("curried method") {
          val method = Method(
            "fold",
            typeParams = List(TypeParam("B")),
            params = List(
              ParamList(List(MethodParam("z", TypeRef("B")))),
              ParamList(List(MethodParam("f", TypeRef("(B, A) => B"))))
            ),
            returnType = TypeRef("B")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def fold[B](z: B)(f: (B, A) => B): B")
        },
        test("abstract method (no body)") {
          val method = Method(
            "process",
            params = List(ParamList(List(MethodParam("input", TypeRef.String)))),
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
              ParamList(
                List(
                  MethodParam("host", TypeRef.String),
                  MethodParam("port", TypeRef.Int, Some("8080"))
                )
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
      ),
      suite("emitMethod - ParamList modifiers")(
        test("method with using parameter list (Scala 3)") {
          val method = Method(
            "greet",
            params = List(
              ParamList(List(MethodParam("name", TypeRef.String))),
              ParamList(List(MethodParam("ctx", TypeRef("Context"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def greet(name: String)(using ctx: Context): Unit")
        },
        test("method with using parameter list (Scala 2)") {
          val method = Method(
            "greet",
            params = List(
              ParamList(List(MethodParam("name", TypeRef.String))),
              ParamList(List(MethodParam("ctx", TypeRef("Context"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.scala2)
          assertTrue(result == "def greet(name: String)(implicit ctx: Context): Unit")
        },
        test("method with implicit parameter list") {
          val method = Method(
            "show",
            params = List(
              ParamList(List(MethodParam("value", TypeRef("A")))),
              ParamList(List(MethodParam("ev", TypeRef("Show[A]"))), ParamListModifier.Implicit)
            ),
            returnType = TypeRef.String
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def show(value: A)(using ev: Show[A]): String")
        }
      ),
      suite("emitObjectMember - ExtensionBlock")(
        test("extension block with single method (Scala 3)") {
          val eb = ObjectMember.ExtensionBlock(
            on = MethodParam("s", TypeRef.String),
            methods = List(
              Method("greet", returnType = TypeRef.String, body = Some("s\"Hello, $s\""))
            )
          )
          val result = ScalaEmitter.emitObjectMember(eb, EmitterConfig.default)
          assertTrue(result.contains("extension (s: String)"))
          assertTrue(result.contains("def greet: String = s\"Hello, $s\""))
        },
        test("extension block with multiple methods (Scala 3)") {
          val eb = ObjectMember.ExtensionBlock(
            on = MethodParam("n", TypeRef.Int),
            methods = List(
              Method("double", returnType = TypeRef.Int, body = Some("n * 2")),
              Method("triple", returnType = TypeRef.Int, body = Some("n * 3"))
            )
          )
          val result = ScalaEmitter.emitObjectMember(eb, EmitterConfig.default)
          assertTrue(result.contains("extension (n: Int) {"))
          assertTrue(result.contains("def double: Int = n * 2"))
          assertTrue(result.contains("def triple: Int = n * 3"))
        },
        test("extension block emits implicit class in Scala 2") {
          val eb = ObjectMember.ExtensionBlock(
            on = MethodParam("s", TypeRef.String),
            methods = List(
              Method("greet", returnType = TypeRef.String, body = Some("s\"Hello, $s\""))
            )
          )
          val result = ScalaEmitter.emitObjectMember(eb, EmitterConfig.scala2)
          assertTrue(result.contains("implicit class StringOps"))
          assertTrue(result.contains("extends AnyVal"))
          assertTrue(result.contains("def greet: String"))
        }
      ),
      suite("emitMethodParam modifiers")(
        test("by-name parameter") {
          val method = Method(
            "foo",
            params = List(ParamList(List(MethodParam("x", TypeRef.Int, isByName = true)))),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def foo(x: => Int): Unit")
        },
        test("varargs parameter") {
          val method = Method(
            "bar",
            params = List(ParamList(List(MethodParam("xs", TypeRef.String, isVarargs = true)))),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def bar(xs: String*): Unit")
        },
        test("by-name with default value") {
          val method = Method(
            "baz",
            params = List(ParamList(List(MethodParam("x", TypeRef.Int, defaultValue = Some("0"), isByName = true)))),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def baz(x: => Int = 0): Unit")
        },
        test("mixed regular, by-name, and varargs params") {
          val method = Method(
            "mixed",
            params = List(
              ParamList(
                List(
                  MethodParam("a", TypeRef.Int),
                  MethodParam("b", TypeRef.String, isByName = true),
                  MethodParam("cs", TypeRef.Double, isVarargs = true)
                )
              )
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def mixed(a: Int, b: => String, cs: Double*): Unit")
        }
      ),
      suite("emitMethod implicit/given")(
        test("implicit method in Scala 3 emits given") {
          val method = Method(
            "codec",
            typeParams = List(TypeParam("A")),
            returnType = TypeRef("Codec", List(TypeRef("A"))),
            isImplicit = true
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "given def codec[A]: Codec[A]")
        },
        test("implicit method in Scala 2 emits implicit") {
          val method = Method(
            "codec",
            typeParams = List(TypeParam("A")),
            returnType = TypeRef("Codec", List(TypeRef("A"))),
            isImplicit = true
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.scala2)
          assertTrue(result == "implicit def codec[A]: Codec[A]")
        },
        test("non-implicit method has no modifier") {
          val method = Method("foo", returnType = TypeRef.Unit)
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def foo: Unit")
        }
      ),
      suite("emitObjectMember ValMember modifiers")(
        test("lazy val") {
          val obj = ObjectDef(
            "Consts",
            members = List(ObjectMember.ValMember("x", TypeRef.Int, "42", isLazy = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Consts {
                 |  lazy val x: Int = 42
                 |}""".stripMargin
          )
        },
        test("override val") {
          val obj = ObjectDef(
            "Impl",
            members = List(ObjectMember.ValMember("name", TypeRef.String, "\"default\"", isOverride = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Impl {
                 |  override val name: String = "default"
                 |}""".stripMargin
          )
        },
        test("implicit val in Scala 3 emits given") {
          val obj = ObjectDef(
            "Implicits",
            members = List(ObjectMember.ValMember("codec", TypeRef("Codec"), "derived", isImplicit = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Implicits {
                 |  given val codec: Codec = derived
                 |}""".stripMargin
          )
        },
        test("implicit val in Scala 2 emits implicit") {
          val obj = ObjectDef(
            "Implicits",
            members = List(ObjectMember.ValMember("codec", TypeRef("Codec"), "derived", isImplicit = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.scala2)
          assertTrue(
            result ==
              """|object Implicits {
                 |  implicit val codec: Codec = derived
                 |}""".stripMargin
          )
        },
        test("override lazy val") {
          val obj = ObjectDef(
            "Impl",
            members =
              List(ObjectMember.ValMember("data", TypeRef.String, "compute()", isLazy = true, isOverride = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result ==
              """|object Impl {
                 |  override lazy val data: String = compute()
                 |}""".stripMargin
          )
        },
        test("implicit lazy val in Scala 2") {
          val obj = ObjectDef(
            "Implicits",
            members =
              List(ObjectMember.ValMember("codec", TypeRef("Codec"), "derived", isLazy = true, isImplicit = true))
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.scala2)
          assertTrue(
            result ==
              """|object Implicits {
                 |  implicit lazy val codec: Codec = derived
                 |}""".stripMargin
          )
        }
      ),
      suite("emitMethod - ParamList additional tests")(
        test("method with normal param list") {
          val method = Method(
            "add",
            params = List(ParamList(List(MethodParam("x", TypeRef.Int), MethodParam("y", TypeRef.Int)))),
            returnType = TypeRef.Int,
            body = Some("x + y")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def add(x: Int, y: Int): Int = x + y")
        },
        test("method with multiple param lists with mixed modifiers") {
          val method = Method(
            "compute",
            params = List(
              ParamList(List(MethodParam("x", TypeRef.Int))),
              ParamList(List(MethodParam("y", TypeRef.String))),
              ParamList(List(MethodParam("ctx", TypeRef("Context"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def compute(x: Int)(y: String)(using ctx: Context): Unit")
        },
        test("method with multiple using param lists (Scala 3)") {
          val method = Method(
            "run",
            params = List(
              ParamList(List(MethodParam("a", TypeRef.Int))),
              ParamList(List(MethodParam("ec", TypeRef("ExecutionContext"))), ParamListModifier.Using),
              ParamList(List(MethodParam("logger", TypeRef("Logger"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def run(a: Int)(using ec: ExecutionContext)(using logger: Logger): Unit")
        },
        test("method with using in Scala 2 mode emits implicit") {
          val method = Method(
            "run",
            params = List(
              ParamList(List(MethodParam("a", TypeRef.Int))),
              ParamList(List(MethodParam("ec", TypeRef("ExecutionContext"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.scala2)
          assertTrue(result == "def run(a: Int)(implicit ec: ExecutionContext): Unit")
        },
        test("empty param list") {
          val method = Method(
            "noop",
            params = List(ParamList(Nil)),
            returnType = TypeRef.Unit,
            body = Some("()")
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def noop(): Unit = ()")
        }
      ),
      suite("object with nested types")(
        test("object with nested sealed trait") {
          val obj = ObjectDef(
            "Outer",
            members = List(
              ObjectMember.NestedType(
                SealedTrait(
                  "Color",
                  cases = List(
                    SealedTraitCase.CaseObjectCase("Red"),
                    SealedTraitCase.CaseObjectCase("Blue")
                  )
                )
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result.contains("object Outer {"),
            result.contains("sealed trait Color"),
            result.contains("case object Red extends Color")
          )
        },
        test("object with nested enum") {
          val obj = ObjectDef(
            "Outer",
            members = List(
              ObjectMember.NestedType(
                Enum(
                  "Priority",
                  cases = List(
                    EnumCase.SimpleCase("Low"),
                    EnumCase.SimpleCase("High")
                  )
                )
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result.contains("object Outer {"),
            result.contains("enum Priority {"),
            result.contains("case Low, High")
          )
        },
        test("object with nested abstract class") {
          val obj = ObjectDef(
            "Outer",
            members = List(
              ObjectMember.NestedType(
                AbstractClass("Base", fields = List(Field("id", TypeRef.Long)))
              )
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(
            result.contains("object Outer {"),
            result.contains("abstract class Base")
          )
        }
      )
    )
}
