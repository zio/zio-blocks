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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

private[typeid] trait IsNominalIntersectionVersionSpecific {
  implicit def derived[A]: IsNominalIntersection[A] = macro IsNominalIntersectionMacros.deriveImpl[A]
}

private[typeid] object IsNominalIntersectionMacros {
  def deriveImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[IsNominalIntersection[A]] = {
    import c.universe._

    def flattenIntersection(tpe: Type): List[Type] = tpe.dealias match {
      case RefinedType(parents, scope) if scope.isEmpty =>
        parents.flatMap(flattenIntersection)
      case other =>
        List(other)
    }

    def isNominal(t: Type): Boolean = t match {
      case RefinedType(_, scope) if scope.nonEmpty     => false
      case RefinedType(parents, _) if parents.size > 1 => false
      case ExistentialType(_, _)                       => false
      case PolyType(_, _)                              => false
      case TypeBounds(_, _)                            => false
      case ConstantType(_)                             => false
      case t if t.takesTypeArgs                        => true
      case t if t.typeSymbol.isClass                   => true
      case t if t.typeSymbol.isModuleClass             => true
      case _                                           => false
    }

    val tpe     = weakTypeOf[A]
    val members = flattenIntersection(tpe)

    members.foreach { member =>
      if (!isNominal(member)) {
        c.abort(
          c.enclosingPosition,
          s"Cannot derive IsNominalIntersection: member type $member is not a nominal type. " +
            "All intersection members must be classes, traits, objects, or applied types."
        )
      }
    }

    val typeIdTrees = members.map { member =>
      q"_root_.zio.blocks.typeid.TypeId.of[$member].erased"
    }

    val chunkTree = q"_root_.zio.blocks.chunk.Chunk(..$typeIdTrees)"

    c.Expr[IsNominalIntersection[A]](q"""
      new _root_.zio.blocks.typeid.IsNominalIntersection[$tpe] {
        val typeIdsErased: _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.typeid.TypeId.Erased] = $chunkTree
      }
    """)
  }
}
