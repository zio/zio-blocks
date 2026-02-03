package zio.blocks.schema.binding

import scala.quoted.*

/**
 * Version-specific macro support for structural type bindings.
 *
 * This version (Scala 3.5+) provides full support for deriving Binding
 * instances for structural types using `Symbol.newClass` which is marked as @experimental
 * and requires Scala 3.5+.
 */
private[binding] object StructuralBindingMacros {

  case class StructuralFieldInfo(name: String, memberTpe: Any, kind: String, index: Int)

  /**
   * Generates a factory function that creates anonymous class instances for
   * structural types.
   *
   * Uses `Symbol.newClass` (experimental API, Scala 3.5+) to create a class at
   * compile time with real methods that satisfy the structural type
   * requirements.
   *
   * @param fields
   *   field metadata including name, type, and register kind
   * @param fail
   *   error reporting function
   * @return
   *   an expression that evaluates to `Array[Any] => A`
   */
  def generateAnonymousClassFactory[A: Type](
    fields: Seq[StructuralFieldInfo],
    fail: String => Nothing
  )(using Quotes): Expr[Array[Any] => A] = {
    import quotes.reflect.*

    val fieldsTyped = fields.map { f =>
      (f.name, f.memberTpe.asInstanceOf[TypeRepr], f.kind, f.index)
    }

    val parents        = List(TypeRepr.of[Object])
    val className      = Symbol.freshName("Structural")
    val arrayOfAnyType = TypeRepr.of[Array[Any]]

    val lambdaSym = Symbol.newMethod(
      Symbol.spliceOwner,
      "factory",
      MethodType(List("values"))(_ => List(arrayOfAnyType), _ => TypeRepr.of[A])
    )

    val classSymbol = Symbol.newClass(
      lambdaSym,
      className,
      parents,
      decls = cls => {
        fieldsTyped.map { case (name, memberTpe, _, _) =>
          Symbol.newMethod(cls, name, ByNameType(memberTpe))
        }.toList
      },
      selfType = None
    )

    val lambdaDef = DefDef(
      lambdaSym,
      {
        case List(List(valuesParam: Term)) =>
          val methodDefs = fieldsTyped.zipWithIndex.map { case ((name, memberTpe, _, _), idx) =>
            val methodSym = classSymbol.declaredMethod(name).head
            DefDef(
              methodSym,
              { _ =>
                val indexExpr = Literal(IntConstant(idx))
                val arrayGet  = Apply(Select.unique(valuesParam, "apply"), List(indexExpr))
                val casted    = memberTpe.asType match {
                  case '[t] =>
                    Some(TypeApply(Select.unique(arrayGet, "asInstanceOf"), List(TypeTree.of[t])))
                }
                casted
              }
            )
          }.toList

          val classDef = ClassDef(
            classSymbol,
            parents.map(Inferred(_)),
            body = methodDefs
          )

          val newInstance = New(TypeIdent(classSymbol))
            .select(classSymbol.primaryConstructor)
            .appliedToNone

          val castInstance = Typed(newInstance, TypeTree.of[A])

          Some(Block(List(classDef), castInstance))

        case _ =>
          fail("Unexpected parameter list shape in lambda definition")
      }
    )

    val closure = Closure(Ref(lambdaSym), None)
    Block(List(lambdaDef), closure).asExprOf[Array[Any] => A]
  }
}
