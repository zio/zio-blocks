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

package zio.blocks.typeid

import scala.quoted.*

private[typeid] trait IsNominalTypeVersionSpecific {
  inline implicit def derived[A]: IsNominalType[A] = ${ IsNominalTypeMacros.deriveImpl[A] }
}

private[typeid] object IsNominalTypeMacros {
  /**
   * Whether `t` is a nominal type: a class, trait, object, enum or opaque type,
   * a singleton of a value of one, or such a type applied to arguments. A type parameter or
   * an abstract type member is not nominal: its `TypeRef` names no concrete
   * type, so a `TypeId` derived for it would describe the parameter, not the
   * type the caller passes (#1172). Aliases are followed. This matches the
   * Scala 2 macro, which accepts a type only when its symbol is a class.
   */
  def isNominal(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    t match {
      case AndType(_, _)                => false
      case OrType(_, _)                 => false
      case Refinement(_, _, _)          => false
      case TypeLambda(_, _, _)          => false
      case ByNameType(_)                => false
      case TypeBounds(_, _)             => false
      case NoPrefix()                   => false
      case MatchType(_, _, _)           => false
      case AppliedType(tycon, _)        => isNominal(tycon)
      case ref @ TermRef(_, _)          => isNominal(ref.widen) // `x.type` is nominal when `x`'s type is
      case ref @ TypeRef(_, _)          =>
        val sym = ref.typeSymbol
        if (sym.isClassDef || sym.flags.is(Flags.Opaque)) true
        else if (sym.isAliasType) {
          val underlying = ref.dealias
          underlying != ref && isNominal(underlying)
        } else false // a type parameter or an abstract type member
      case ConstantType(_)              => false
      case ParamRef(_, _)               => false
      case ThisType(_)                  => true
      case RecursiveThis(_)             => false
      case RecursiveType(underlying)    => isNominal(underlying)
      case AnnotatedType(underlying, _) => isNominal(underlying)
      case _                            => false
    }
  }

  def deriveImpl[A: Type](using Quotes): Expr[IsNominalType[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A].dealias

    if (!isNominal(tpe)) {
      val typeName = tpe.show
      report.errorAndAbort(
        s"Cannot derive IsNominalType for non-nominal type: $typeName. " +
          "Only classes, traits, objects, enums, and applied types are supported. " +
          "Intersection types (A & B), union types (A | B), structural types, and type lambdas are not allowed."
      )
    }

    '{
      new IsNominalType[A] {
        val typeId: TypeId[A]           = TypeId.of[A]
        val typeIdErased: TypeId.Erased = typeId.erased
      }
    }
  }
}
