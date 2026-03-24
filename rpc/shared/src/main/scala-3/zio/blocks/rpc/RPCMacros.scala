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

package zio.blocks.rpc

import scala.quoted.*

object RPCMacros {

  def derived[T: Type](using Quotes): Expr[RPC[T]] = {
    import quotes.reflect.*

    val tpe    = TypeRepr.of[T]
    val tpeSym = tpe.typeSymbol

    if (!tpeSym.isClassDef || !tpeSym.flags.is(Flags.Trait))
      report.errorAndAbort(s"RPC.derived requires a trait, got: ${tpe.show}")

    val abstractMethods = tpeSym.declaredMethods.filter(_.flags.is(Flags.Deferred))

    abstractMethods.foreach { method =>
      if (tpeSym.declaredMethods.count(_.name == method.name) > 1)
        report.errorAndAbort(
          s"Overloaded method '${method.name}' is not supported in RPC traits"
        )

      method.paramSymss match {
        case tps :: _ if tps.exists(_.isTypeParam) =>
          report.errorAndAbort(
            s"Generic/polymorphic method '${method.name}' is not supported in RPC traits"
          )
        case _ => ()
      }

      val termParamLists = method.paramSymss.filterNot(_.exists(_.isTypeParam))
      if (termParamLists.size > 1)
        report.errorAndAbort(
          s"Curried method '${method.name}' is not supported in RPC traits"
        )
    }

    val schemaTpe        = TypeRepr.of[zio.blocks.schema.Schema]
    val metaAnnotationTR = TypeRepr.of[MetaAnnotation]
    val decomposerTR     = TypeRepr.of[ReturnTypeDecomposer]

    def summonSchema(paramType: TypeRepr): Term =
      Implicits.search(schemaTpe.appliedTo(paramType)) match {
        case s: ImplicitSearchSuccess => s.tree
        case _                        =>
          report.errorAndAbort(
            s"No Schema instance found for ${paramType.show}. Add 'derives Schema' to the type."
          )
      }

    val traitAnnotations: List[Term] =
      tpeSym.annotations.filter(ann => ann.tpe <:< metaAnnotationTR)

    val traitAnnotationsExpr: Expr[zio.blocks.chunk.Chunk[MetaAnnotation]] = {
      val annExprs = traitAnnotations.map(_.asExpr.asInstanceOf[Expr[MetaAnnotation]])
      if (annExprs.isEmpty) '{ zio.blocks.chunk.Chunk.empty[MetaAnnotation] }
      else '{ zio.blocks.chunk.Chunk(${ Varargs(annExprs) }*) }
    }

    val metadataExpr: Expr[RPC.ServiceMetadata] =
      '{ RPC.ServiceMetadata($traitAnnotationsExpr) }

    /**
     * Decompose a return type into (successType, errorType).
     *   1. Try `Implicits.search(ReturnTypeDecomposer[returnType])` — if found,
     *      extract Error/Success type members
     *   2. Otherwise, treat as plain type: success = returnType, error =
     *      Nothing
     */
    def decomposeReturnType(returnType: TypeRepr): (TypeRepr, TypeRepr) = {
      val dealiased = returnType.dealias

      // Fast path: check if it's Either directly via type symbol
      if (dealiased.typeSymbol.fullName == "scala.util.Either") {
        val typeArgs = dealiased.typeArgs
        if (typeArgs.length == 2) {
          return (typeArgs(1), typeArgs(0)) // (success, error)
        }
      }

      // General path: try to find a ReturnTypeDecomposer instance
      val searchType = decomposerTR.appliedTo(returnType)
      Implicits.search(searchType) match {
        case s: ImplicitSearchSuccess =>
          val decompTpe = s.tree.tpe.dealias
          // Extract the Error and Success type members
          val errorMember   = decompTpe.typeSymbol.typeMember("Error")
          val successMember = decompTpe.typeSymbol.typeMember("Success")
          if (errorMember != Symbol.noSymbol && successMember != Symbol.noSymbol) {
            val errorType   = decompTpe.memberType(errorMember).dealias
            val successType = decompTpe.memberType(successMember).dealias
            (successType, errorType)
          } else {
            // Decomposer found but can't extract members — fall back to plain
            (returnType, TypeRepr.of[Nothing])
          }
        case _ =>
          // No decomposer found — treat as plain type
          (returnType, TypeRepr.of[Nothing])
      }
    }

    val operationExprs: List[Expr[RPC.Operation[?, ?]]] = abstractMethods.map { method =>
      val methodName = method.name

      val termParams: List[Symbol] = {
        val lists = method.paramSymss.filterNot(_.exists(_.isTypeParam))
        if (lists.isEmpty) Nil else lists.head
      }

      val methodType = tpe.memberType(method)

      val returnType: TypeRepr = methodType match {
        case MethodType(_, _, rt) => rt
        case ByNameType(rt)       => rt
        case other                => other
      }

      val (aType, eType) = decomposeReturnType(returnType)

      val paramNames: List[String]   = termParams.map(_.name)
      val paramTypes: List[TypeRepr] = termParams.map { p =>
        tpe.memberType(p) match {
          case MethodType(_, pts, _) => pts.head
          case other                 => other
        }
      }

      val methodAnnotations: List[Expr[MetaAnnotation]] =
        method.annotations
          .filter(ann => ann.tpe <:< metaAnnotationTR)
          .map(_.asExpr.asInstanceOf[Expr[MetaAnnotation]])

      val methodAnnotationsExpr: Expr[zio.blocks.chunk.Chunk[MetaAnnotation]] =
        if (methodAnnotations.isEmpty) '{ zio.blocks.chunk.Chunk.empty[MetaAnnotation] }
        else '{ zio.blocks.chunk.Chunk(${ Varargs(methodAnnotations) }*) }

      val paramAnnotationsExpr: Expr[zio.blocks.chunk.Chunk[zio.blocks.chunk.Chunk[MetaAnnotation]]] = {
        val perParam = termParams.map { p =>
          val anns = p.annotations
            .filter(ann => ann.tpe <:< metaAnnotationTR)
            .map(_.asExpr.asInstanceOf[Expr[MetaAnnotation]])
          if (anns.isEmpty) '{ zio.blocks.chunk.Chunk.empty[MetaAnnotation] }
          else '{ zio.blocks.chunk.Chunk(${ Varargs(anns) }*) }
        }
        if (perParam.isEmpty) '{ zio.blocks.chunk.Chunk.empty[zio.blocks.chunk.Chunk[MetaAnnotation]] }
        else '{ zio.blocks.chunk.Chunk(${ Varargs(perParam) }*) }
      }

      val paramNamesExpr: Expr[zio.blocks.chunk.Chunk[String]] = {
        val nameExprs = paramNames.map(n => Expr(n))
        if (nameExprs.isEmpty) '{ zio.blocks.chunk.Chunk.empty[String] }
        else '{ zio.blocks.chunk.Chunk(${ Varargs(nameExprs) }*) }
      }

      val opErrorSchemaExpr: Expr[Option[zio.blocks.schema.Schema[?]]] =
        if (eType =:= TypeRepr.of[Nothing]) '{ None }
        else {
          val schemaTree = summonSchema(eType)
          eType.asType match {
            case '[e] =>
              val se = schemaTree.asExpr.asInstanceOf[Expr[zio.blocks.schema.Schema[e]]]
              '{ Some($se) }
          }
        }

      val outputSchemaTree = summonSchema(aType)

      aType.asType match {
        case '[a] =>
          val outputSchemaExpr =
            outputSchemaTree.asExpr.asInstanceOf[Expr[zio.blocks.schema.Schema[a]]]

          buildOperation[a](
            paramTypes,
            methodName,
            outputSchemaExpr,
            opErrorSchemaExpr,
            paramNamesExpr,
            methodAnnotationsExpr,
            paramAnnotationsExpr,
            schemaTpe
          )
      }
    }

    val operationsExpr: Expr[zio.blocks.chunk.Chunk[RPC.Operation[?, ?]]] =
      if (operationExprs.isEmpty) '{ zio.blocks.chunk.Chunk.empty[RPC.Operation[?, ?]] }
      else '{ zio.blocks.chunk.Chunk(${ Varargs(operationExprs) }*) }

    val labelExpr  = Expr(tpeSym.name)
    val typeIdExpr = '{ zio.blocks.typeid.TypeId.of[T] }

    '{ RPC[T]($labelExpr, $typeIdExpr, $operationsExpr, $metadataExpr) }
  }

  private def buildOperation[A: Type](using
    Quotes
  )(
    paramTypes: List[quotes.reflect.TypeRepr],
    methodName: String,
    outputSchemaExpr: Expr[zio.blocks.schema.Schema[A]],
    errorSchemaExpr: Expr[Option[zio.blocks.schema.Schema[?]]],
    paramNamesExpr: Expr[zio.blocks.chunk.Chunk[String]],
    methodAnnotationsExpr: Expr[zio.blocks.chunk.Chunk[MetaAnnotation]],
    paramAnnotationsExpr: Expr[zio.blocks.chunk.Chunk[zio.blocks.chunk.Chunk[MetaAnnotation]]],
    schemaTpe: quotes.reflect.TypeRepr
  ): Expr[RPC.Operation[?, ?]] = {
    import quotes.reflect.*

    def summonSchemaLocal(paramType: TypeRepr): Term =
      Implicits.search(schemaTpe.appliedTo(paramType)) match {
        case s: ImplicitSearchSuccess => s.tree
        case _                        =>
          report.errorAndAbort(
            s"No Schema instance found for ${paramType.show}. Add 'derives Schema' to the type."
          )
      }

    paramTypes.size match {
      case 0 =>
        val inputSchemaExpr = '{ zio.blocks.schema.Schema.unit }
        '{
          RPC.Operation[Unit, A](
            ${ Expr(methodName) },
            $inputSchemaExpr,
            $outputSchemaExpr,
            $errorSchemaExpr,
            $paramNamesExpr,
            $methodAnnotationsExpr,
            $paramAnnotationsExpr
          )
        }

      case 1 =>
        val pt              = paramTypes.head
        val inputSchemaTree = summonSchemaLocal(pt)
        pt.asType match {
          case '[p] =>
            val inputSchemaExpr =
              inputSchemaTree.asExpr.asInstanceOf[Expr[zio.blocks.schema.Schema[p]]]
            '{
              RPC.Operation[p, A](
                ${ Expr(methodName) },
                $inputSchemaExpr,
                $outputSchemaExpr,
                $errorSchemaExpr,
                $paramNamesExpr,
                $methodAnnotationsExpr,
                $paramAnnotationsExpr
              )
            }
        }

      case n =>
        val tupleTypeRepr = defn.TupleClass(n).typeRef.appliedTo(paramTypes)
        tupleTypeRepr.asType match {
          case '[t] =>
            val inputSchemaExpr: Expr[zio.blocks.schema.Schema[t]] =
              Implicits.search(schemaTpe.appliedTo(tupleTypeRepr)) match {
                case s: ImplicitSearchSuccess =>
                  s.tree.asExpr.asInstanceOf[Expr[zio.blocks.schema.Schema[t]]]
                case _ =>
                  '{ zio.blocks.schema.Schema.derived[t] }
              }
            '{
              RPC.Operation[t, A](
                ${ Expr(methodName) },
                $inputSchemaExpr,
                $outputSchemaExpr,
                $errorSchemaExpr,
                $paramNamesExpr,
                $methodAnnotationsExpr,
                $paramAnnotationsExpr
              )
            }
        }
    }
  }
}
