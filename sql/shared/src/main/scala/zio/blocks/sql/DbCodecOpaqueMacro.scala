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

package zio.blocks.sql

import scala.quoted._

private[sql] object DbCodecOpaqueMacro {

  def derivedOpaqueImpl[A: Type](using Quotes): Expr[DbCodec[A]] = {
    import quotes.reflect._

    def companionModule(tpe: TypeRepr): Symbol = {
      val typeSym = tpe.typeSymbol
      val direct  = typeSym.companionModule
      if (direct != Symbol.noSymbol) direct
      else {
        val byOwner =
          typeSym.owner.declarations.find(symbol => symbol.name == typeSym.name && symbol.flags.is(Flags.Module))

        val byFullName =
          try Some(Symbol.requiredModule(typeSym.fullName))
          catch { case _: Throwable => None }

        byOwner.orElse(byFullName).getOrElse {
          report.errorAndAbort(s"DbCodec opaque derivation requires ${tpe.show} to define a companion object")
        }
      }
    }

    def sameType(a: TypeRepr, b: TypeRepr): Boolean =
      a =:= b || a.dealias =:= b.dealias

    def singleArgumentMethod(
      companion: Symbol,
      name: String,
      parameterType: TypeRepr,
      resultType: TypeRepr,
      alternativeResultType: Option[TypeRepr],
      error: String
    ): Symbol =
      companion.moduleClass.methodMembers.filter(_.name == name).find { methodSymbol =>
        companion.moduleClass.typeRef.memberType(methodSymbol) match {
          case MethodType(_, paramTypes, returnType) if paramTypes.size == 1 =>
            sameType(paramTypes.head, parameterType) &&
            (sameType(returnType, resultType) || alternativeResultType.exists(sameType(returnType, _)))
          case _ => false
        }
      } match {
        case Some(methodSymbol) => methodSymbol
        case None               =>
          val methods = companion.moduleClass.methodMembers
            .filter(_.name == name)
            .map { methodSymbol =>
              companion.moduleClass.typeRef.memberType(methodSymbol) match {
                case MethodType(_, paramTypes, returnType) =>
                  s"${methodSymbol.name}${paramTypes.map(_.show).mkString("(", ",", ")")}: ${returnType.show}"
                case other => s"${methodSymbol.name}: ${other.show}"
              }
            }
            .mkString("; ")
          val allNames = companion.moduleClass.methodMembers.map(_.name).take(30).mkString(", ")
          report.errorAndAbort(s"$error. Companion=${companion.fullName}; Found ${
              if methods.isEmpty then s"no candidates; method names: $allNames" else methods
            }")
      }

    val tpe = TypeRepr.of[A]

    if (!tpe.typeSymbol.flags.is(Flags.Opaque))
      report.errorAndAbort(
        s"DbCodec.derivedOpaque requires an opaque type, but ${Type.show[A]} is not opaque"
      )

    val underlying = tpe match {
      case tr: TypeRef if tr.isOpaqueAlias => tr.translucentSuperType.dealias
      case _                               =>
        report.errorAndAbort(
          s"Cannot determine underlying type of opaque type ${Type.show[A]}"
        )
    }

    underlying.asType match {
      case '[u] =>
        Expr.summon[DbCodec[u]] match {
          case Some(baseCodec) =>
            val companion = companionModule(tpe)
            val moduleRef = Ref(companion)
            val apply     = singleArgumentMethod(
              companion,
              "apply",
              underlying,
              tpe,
              Some(underlying),
              s"DbCodec opaque derivation for ${Type.show[A]} requires a public apply(${Type.show[u]}): ${Type.show[A]} method on the opaque type companion"
            )

            val read = Lambda(
              Symbol.spliceOwner,
              MethodType(List("value"))(_ => List(underlying), _ => tpe),
              (_, params) => Apply(Select(moduleRef, apply), params.map(_.asInstanceOf[Term]))
            ).asExprOf[u => A]

            val write =
              if (tpe <:< underlying)
                Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("value"))(_ => List(tpe), _ => underlying),
                  (_, params) => params.head
                )
                  .asExprOf[A => u]
              else {
                val unwrap = singleArgumentMethod(
                  companion,
                  "unwrap",
                  tpe,
                  underlying,
                  None,
                  s"DbCodec opaque derivation for ${Type.show[A]} requires either ${Type.show[A]} <: ${Type.show[u]} or a public unwrap(${Type.show[A]}): ${Type.show[u]} method on the opaque type companion"
                )
                Lambda(
                  Symbol.spliceOwner,
                  MethodType(List("value"))(_ => List(tpe), _ => underlying),
                  (_, params) => Apply(Select(moduleRef, unwrap), params.map(_.asInstanceOf[Term]))
                ).asExprOf[A => u]
              }

            '{ $baseCodec.transform($read, $write) }
          case None =>
            report.errorAndAbort(
              s"No DbCodec found for underlying type ${Type.show[u]} of opaque type ${Type.show[A]}"
            )
        }
    }
  }
}
