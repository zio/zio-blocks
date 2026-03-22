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

package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object MethodSpec extends ZIOSpecDefault {
  def spec =
    suite("Method and MethodParam")(
      suite("MethodParam")(
        test("creates parameter with name and type") {
          val param = MethodParam("count", TypeRef.Int)
          assert(param.name)(equalTo("count")) &&
          assert(param.typeRef.name)(equalTo("Int")) &&
          assert(param.defaultValue)(isNone)
        },
        test("creates parameter with default value") {
          val param =
            MethodParam("count", TypeRef.Int, Some("10"))
          assert(param.name)(equalTo("count")) &&
          assert(param.defaultValue)(isSome(equalTo("10")))
        },
        test("handles complex type references") {
          val param = MethodParam(
            "items",
            TypeRef("List", List(TypeRef.String))
          )
          assert(param.typeRef.name)(equalTo("List")) &&
          assert(param.typeRef.typeArgs.length)(equalTo(1))
        },
        test("copy preserves values") {
          val param1 = MethodParam("x", TypeRef.Int)
          val param2 = param1.copy(name = "y")
          assert(param2.name)(equalTo("y")) &&
          assert(param2.typeRef.name)(equalTo("Int"))
        },
        test("equality works correctly") {
          val param1 = MethodParam("x", TypeRef.Int)
          val param2 = MethodParam("x", TypeRef.Int)
          assert(param1)(equalTo(param2))
        },
        test("inequality when name differs") {
          val param1 = MethodParam("x", TypeRef.Int)
          val param2 = MethodParam("y", TypeRef.Int)
          assert(param1)(not(equalTo(param2)))
        },
        test("inequality when type differs") {
          val param1 = MethodParam("x", TypeRef.Int)
          val param2 = MethodParam("x", TypeRef.String)
          assert(param1)(not(equalTo(param2)))
        },
        test("handles optional type") {
          val param =
            MethodParam("maybeValue", TypeRef.optional(TypeRef.String))
          assert(param.typeRef.name)(equalTo("Option"))
        }
      ),
      suite("Method - basic construction")(
        test("creates method with name and return type") {
          val method = Method("getValue", returnType = TypeRef.String)
          assert(method.name)(equalTo("getValue")) &&
          assert(method.returnType.name)(equalTo("String")) &&
          assert(method.typeParams)(isEmpty) &&
          assert(method.params)(isEmpty) &&
          assert(method.body)(isNone)
        },
        test("creates method with parameters") {
          val params = ParamList(
            List(
              MethodParam("x", TypeRef.Int),
              MethodParam("y", TypeRef.Int)
            )
          )
          val method = Method(
            "add",
            params = List(params),
            returnType = TypeRef.Int
          )
          assert(method.name)(equalTo("add")) &&
          assert(method.params.length)(equalTo(1)) &&
          assert(method.params(0).params.length)(equalTo(2))
        },
        test("creates method with return type") {
          val method = Method("getName", returnType = TypeRef.String)
          assert(method.returnType.name)(equalTo("String"))
        },
        test("creates method with single type parameter") {
          val typeParams = List(TypeParam("T"))
          val method     = Method(
            "get",
            typeParams = typeParams,
            returnType = TypeRef("T")
          )
          assert(method.typeParams.length)(equalTo(1)) &&
          assert(method.typeParams(0).name)(equalTo("T"))
        },
        test("creates method with multiple type parameters") {
          val typeParams = List(TypeParam("A"), TypeParam("B"), TypeParam("C"))
          val method     = Method(
            "convert",
            typeParams = typeParams,
            returnType = TypeRef("C")
          )
          assert(method.typeParams.length)(equalTo(3))
        }
      ),
      suite("Method - parameters")(
        test("creates method with single parameter list") {
          val params = ParamList(List(MethodParam("name", TypeRef.String)))
          val method =
            Method("greet", params = List(params), returnType = TypeRef.Unit)
          assert(method.params.length)(equalTo(1)) &&
          assert(method.params(0).params.length)(equalTo(1))
        },
        test("creates method with curried parameters") {
          val params1 = ParamList(List(MethodParam("x", TypeRef.Int)))
          val params2 = ParamList(List(MethodParam("y", TypeRef.Int)))
          val method  = Method(
            "curry",
            params = List(params1, params2),
            returnType = TypeRef.Int
          )
          assert(method.params.length)(equalTo(2)) &&
          assert(method.params(0).params.length)(equalTo(1)) &&
          assert(method.params(1).params.length)(equalTo(1))
        },
        test("creates method with multiple parameters in single list") {
          val params = ParamList(
            List(
              MethodParam("a", TypeRef.Int),
              MethodParam("b", TypeRef.String),
              MethodParam("c", TypeRef.Boolean)
            )
          )
          val method =
            Method("process", params = List(params), returnType = TypeRef.String)
          assert(method.params(0).params.length)(equalTo(3))
        },
        test("creates method with parameters with default values") {
          val params = ParamList(
            List(
              MethodParam("x", TypeRef.Int, Some("0")),
              MethodParam("y", TypeRef.Int, Some("1"))
            )
          )
          val method =
            Method("calculate", params = List(params), returnType = TypeRef.Int)
          assert(method.params(0).params(0).defaultValue)(isSome(equalTo("0"))) &&
          assert(method.params(0).params(1).defaultValue)(isSome(equalTo("1")))
        }
      ),
      suite("Method - body and documentation")(
        test("creates method with body") {
          val method = Method(
            "getValue",
            returnType = TypeRef.String,
            body = Some("\"hello world\"")
          )
          assert(method.body)(isSome(equalTo("\"hello world\"")))
        },
        test("creates method with complex body") {
          val body =
            """if (x > 0) x else -x"""
          val method = Method(
            "abs",
            params = List(ParamList(List(MethodParam("x", TypeRef.Int)))),
            returnType = TypeRef.Int,
            body = Some(body)
          )
          assert(method.body)(isSome(equalTo(body)))
        },
        test("creates method with documentation") {
          val doc    = "Returns the absolute value"
          val method =
            Method("abs", returnType = TypeRef.Int, doc = Some(doc))
          assert(method.doc)(isSome(equalTo(doc)))
        },
        test("creates method with both body and documentation") {
          val method = Method(
            "getValue",
            returnType = TypeRef.String,
            body = Some("\"value\""),
            doc = Some("Gets the value")
          )
          assert(method.body)(isSome(equalTo("\"value\""))) &&
          assert(method.doc)(isSome(equalTo("Gets the value")))
        }
      ),
      suite("Method - annotations and override")(
        test("creates method with single annotation") {
          val annotation = Annotation("Override")
          val method     = Method(
            "toString",
            returnType = TypeRef.String,
            annotations = List(annotation)
          )
          assert(method.annotations.length)(equalTo(1)) &&
          assert(method.annotations(0).name)(equalTo("Override"))
        },
        test("creates method with multiple annotations") {
          val annotations = List(
            Annotation("Override"),
            Annotation("Deprecated")
          )
          val method = Method(
            "oldMethod",
            returnType = TypeRef.String,
            annotations = annotations
          )
          assert(method.annotations.length)(equalTo(2))
        },
        test("creates method with override flag") {
          val method =
            Method("toString", returnType = TypeRef.String, isOverride = true)
          assert(method.isOverride)(isTrue)
        },
        test("creates method with override false by default") {
          val method = Method("newMethod", returnType = TypeRef.String)
          assert(method.isOverride)(isFalse)
        },
        test("creates method with annotations and override") {
          val method = Method(
            "equals",
            params = List(ParamList(List(MethodParam("obj", TypeRef.Any)))),
            returnType = TypeRef.Boolean,
            annotations = List(Annotation("Override")),
            isOverride = true
          )
          assert(method.annotations.length)(equalTo(1)) &&
          assert(method.isOverride)(isTrue)
        }
      ),
      suite("Method - complex examples")(
        test("creates generic method") {
          val method = Method(
            "identity",
            typeParams = List(TypeParam("T")),
            params = List(ParamList(List(MethodParam("value", TypeRef("T"))))),
            returnType = TypeRef("T")
          )
          assert(method.typeParams.length)(equalTo(1)) &&
          assert(method.params(0).params(0).typeRef.name)(equalTo("T")) &&
          assert(method.returnType.name)(equalTo("T"))
        },
        test("creates complex method with all features") {
          val method = Method(
            "transform",
            typeParams = List(TypeParam("A"), TypeParam("B")),
            params = List(
              ParamList(
                List(
                  MethodParam("input", TypeRef("A")),
                  MethodParam("options", TypeRef.String, Some("\"default\""))
                )
              ),
              ParamList(List(MethodParam("callback", TypeRef.of("Function1", TypeRef("A"), TypeRef("B")))))
            ),
            returnType = TypeRef("B"),
            body = Some("callback(input)"),
            annotations = List(Annotation("Override")),
            isOverride = true,
            doc = Some("Transforms input using callback")
          )
          assert(method.typeParams.length)(equalTo(2)) &&
          assert(method.params.length)(equalTo(2)) &&
          assert(method.body)(isSome(equalTo("callback(input)"))) &&
          assert(method.isOverride)(isTrue) &&
          assert(method.annotations.length)(equalTo(1)) &&
          assert(method.doc)(isSome)
        }
      ),
      suite("Method - copy and equality")(
        test("copy preserves all fields") {
          val method1 = Method(
            "getValue",
            returnType = TypeRef.String
          )
          val method2 = method1.copy(name = "setValue")
          assert(method2.name)(equalTo("setValue")) &&
          assert(method2.returnType.name)(equalTo("String"))
        },
        test("equality works correctly") {
          val method1 = Method("getValue", returnType = TypeRef.String)
          val method2 = Method("getValue", returnType = TypeRef.String)
          assert(method1)(equalTo(method2))
        },
        test("inequality when name differs") {
          val method1 = Method("getValue", returnType = TypeRef.String)
          val method2 = Method("setValue", returnType = TypeRef.String)
          assert(method1)(not(equalTo(method2)))
        },
        test("inequality when return type differs") {
          val method1 = Method("getValue", returnType = TypeRef.String)
          val method2 = Method("getValue", returnType = TypeRef.Int)
          assert(method1)(not(equalTo(method2)))
        }
      ),
      suite("edge cases")(
        test("method with no parameters and void return") {
          val method = Method("doSomething", returnType = TypeRef.Unit)
          assert(method.params)(isEmpty) &&
          assert(method.returnType.name)(equalTo("Unit"))
        },
        test("method with complex generic return type") {
          val returnType = TypeRef.of(
            "Either",
            TypeRef.String,
            TypeRef.of("Option", TypeRef.Int)
          )
          val method = Method("process", returnType = returnType)
          assert(method.returnType.name)(equalTo("Either")) &&
          assert(method.returnType.typeArgs.length)(equalTo(2))
        },
        test("method with many type parameters") {
          val typeParams = (1 to 10).map(i => TypeParam(s"T$i")).toList
          val method     = Method(
            "complex",
            typeParams = typeParams,
            returnType = TypeRef("Unit")
          )
          assert(method.typeParams.length)(equalTo(10))
        },
        test("method with many parameter lists (curried)") {
          val paramLists = (1 to 5).map { i =>
            ParamList(List(MethodParam(s"p$i", TypeRef.Int)))
          }.toList
          val method = Method(
            "curried",
            params = paramLists,
            returnType = TypeRef.Int
          )
          assert(method.params.length)(equalTo(5))
        }
      ),
      suite("MethodParam - by-name and varargs")(
        test("creates by-name parameter") {
          val param = MethodParam("x", TypeRef.Int, isByName = true)
          assert(param.isByName)(isTrue) &&
          assert(param.isVarargs)(isFalse)
        },
        test("creates varargs parameter") {
          val param = MethodParam("xs", TypeRef.String, isVarargs = true)
          assert(param.isVarargs)(isTrue) &&
          assert(param.isByName)(isFalse)
        },
        test("isByName defaults to false") {
          val param = MethodParam("x", TypeRef.Int)
          assert(param.isByName)(isFalse)
        },
        test("isVarargs defaults to false") {
          val param = MethodParam("x", TypeRef.Int)
          assert(param.isVarargs)(isFalse)
        }
      ),
      suite("Method - implicit")(
        test("creates implicit method") {
          val method = Method("codec", returnType = TypeRef("Codec"), isImplicit = true)
          assert(method.isImplicit)(isTrue)
        },
        test("isImplicit defaults to false") {
          val method = Method("codec", returnType = TypeRef("Codec"))
          assert(method.isImplicit)(isFalse)
        }
      ),
      suite("ParamList")(
        test("creates normal param list") {
          val pl = ParamList(List(MethodParam("x", TypeRef.Int)))
          assert(pl.params.length)(equalTo(1)) &&
          assert(pl.modifier)(equalTo(ParamListModifier.Normal))
        },
        test("creates implicit param list") {
          val pl = ParamList(List(MethodParam("ev", TypeRef("Ordering"))), ParamListModifier.Implicit)
          assert(pl.modifier)(equalTo(ParamListModifier.Implicit))
        },
        test("creates using param list") {
          val pl = ParamList(List(MethodParam("ctx", TypeRef("Context"))), ParamListModifier.Using)
          assert(pl.modifier)(equalTo(ParamListModifier.Using))
        },
        test("modifier defaults to Normal") {
          val pl = ParamList(List(MethodParam("x", TypeRef.Int)))
          assert(pl.modifier)(equalTo(ParamListModifier.Normal))
        },
        test("empty param list") {
          val pl = ParamList(Nil)
          assert(pl.params)(isEmpty)
        },
        test("equality works correctly") {
          val pl1 = ParamList(List(MethodParam("x", TypeRef.Int)), ParamListModifier.Using)
          val pl2 = ParamList(List(MethodParam("x", TypeRef.Int)), ParamListModifier.Using)
          assert(pl1)(equalTo(pl2))
        },
        test("inequality when modifier differs") {
          val pl1 = ParamList(List(MethodParam("x", TypeRef.Int)), ParamListModifier.Using)
          val pl2 = ParamList(List(MethodParam("x", TypeRef.Int)), ParamListModifier.Implicit)
          assert(pl1)(not(equalTo(pl2)))
        }
      ),
      suite("Method with ParamList modifiers")(
        test("method with using param list") {
          val method = Method(
            "run",
            params = List(
              ParamList(List(MethodParam("x", TypeRef.Int))),
              ParamList(List(MethodParam("ctx", TypeRef("Context"))), ParamListModifier.Using)
            ),
            returnType = TypeRef.Unit
          )
          assert(method.params.length)(equalTo(2)) &&
          assert(method.params(0).modifier)(equalTo(ParamListModifier.Normal)) &&
          assert(method.params(1).modifier)(equalTo(ParamListModifier.Using))
        },
        test("method with all modifier types") {
          val method = Method(
            "complex",
            params = List(
              ParamList(List(MethodParam("x", TypeRef.Int))),
              ParamList(List(MethodParam("y", TypeRef.String)), ParamListModifier.Using),
              ParamList(List(MethodParam("z", TypeRef.Boolean)), ParamListModifier.Implicit)
            ),
            returnType = TypeRef.Unit
          )
          assert(method.params.length)(equalTo(3)) &&
          assert(method.params(0).modifier)(equalTo(ParamListModifier.Normal)) &&
          assert(method.params(1).modifier)(equalTo(ParamListModifier.Using)) &&
          assert(method.params(2).modifier)(equalTo(ParamListModifier.Implicit))
        }
      )
    )
}
