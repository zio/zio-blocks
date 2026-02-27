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
          val params = List(
            MethodParam("x", TypeRef.Int),
            MethodParam("y", TypeRef.Int)
          )
          val method = Method(
            "add",
            params = List(params),
            returnType = TypeRef.Int
          )
          assert(method.name)(equalTo("add")) &&
          assert(method.params.length)(equalTo(1)) &&
          assert(method.params(0).length)(equalTo(2))
        },
        test("creates method with return type") {
          val method = Method("getName", returnType = TypeRef.String)
          assert(method.returnType.name)(equalTo("String"))
        },
        test("creates method with single type parameter") {
          val typeParams = List(TypeRef("T"))
          val method     = Method(
            "get",
            typeParams = typeParams,
            returnType = TypeRef("T")
          )
          assert(method.typeParams.length)(equalTo(1)) &&
          assert(method.typeParams(0).name)(equalTo("T"))
        },
        test("creates method with multiple type parameters") {
          val typeParams = List(TypeRef("A"), TypeRef("B"), TypeRef("C"))
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
          val params = List(MethodParam("name", TypeRef.String))
          val method =
            Method("greet", params = List(params), returnType = TypeRef.Unit)
          assert(method.params.length)(equalTo(1)) &&
          assert(method.params(0).length)(equalTo(1))
        },
        test("creates method with curried parameters") {
          val params1 = List(MethodParam("x", TypeRef.Int))
          val params2 = List(MethodParam("y", TypeRef.Int))
          val method  = Method(
            "curry",
            params = List(params1, params2),
            returnType = TypeRef.Int
          )
          assert(method.params.length)(equalTo(2)) &&
          assert(method.params(0).length)(equalTo(1)) &&
          assert(method.params(1).length)(equalTo(1))
        },
        test("creates method with multiple parameters in single list") {
          val params = List(
            MethodParam("a", TypeRef.Int),
            MethodParam("b", TypeRef.String),
            MethodParam("c", TypeRef.Boolean)
          )
          val method =
            Method("process", params = List(params), returnType = TypeRef.String)
          assert(method.params(0).length)(equalTo(3))
        },
        test("creates method with parameters with default values") {
          val params = List(
            MethodParam("x", TypeRef.Int, Some("0")),
            MethodParam("y", TypeRef.Int, Some("1"))
          )
          val method =
            Method("calculate", params = List(params), returnType = TypeRef.Int)
          assert(method.params(0)(0).defaultValue)(isSome(equalTo("0"))) &&
          assert(method.params(0)(1).defaultValue)(isSome(equalTo("1")))
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
            params = List(List(MethodParam("x", TypeRef.Int))),
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
            params = List(List(MethodParam("obj", TypeRef.Any))),
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
            typeParams = List(TypeRef("T")),
            params = List(List(MethodParam("value", TypeRef("T")))),
            returnType = TypeRef("T")
          )
          assert(method.typeParams.length)(equalTo(1)) &&
          assert(method.params(0)(0).typeRef.name)(equalTo("T")) &&
          assert(method.returnType.name)(equalTo("T"))
        },
        test("creates complex method with all features") {
          val method = Method(
            "transform",
            typeParams = List(TypeRef("A"), TypeRef("B")),
            params = List(
              List(
                MethodParam("input", TypeRef("A")),
                MethodParam("options", TypeRef.String, Some("\"default\""))
              ),
              List(MethodParam("callback", TypeRef.of("Function1", TypeRef("A"), TypeRef("B"))))
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
          val typeParams = (1 to 10).map(i => TypeRef(s"T$i")).toList
          val method     = Method(
            "complex",
            typeParams = typeParams,
            returnType = TypeRef("Unit")
          )
          assert(method.typeParams.length)(equalTo(10))
        },
        test("method with many parameter lists (curried)") {
          val paramLists = (1 to 5).map { i =>
            List(MethodParam(s"p$i", TypeRef.Int))
          }.toList
          val method = Method(
            "curried",
            params = paramLists,
            returnType = TypeRef.Int
          )
          assert(method.params.length)(equalTo(5))
        }
      )
    )
}
