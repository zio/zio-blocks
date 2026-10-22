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

import zio.blocks.chunk.Chunk
import scala.quoted.*

private[typeid] trait IsNominalIntersectionVersionSpecific {
  inline implicit def derived[A]: IsNominalIntersection[A] = ${ IsNominalIntersectionMacros.deriveImpl[A] }
}

private[typeid] object IsNominalIntersectionMacros {
  def deriveImpl[A: Type](using Quotes): Expr[IsNominalIntersection[A]] = {
    import quotes.reflect.*

    def flattenIntersection(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
      case AndType(left, right) =>
        flattenIntersection(left) ++ flattenIntersection(right)
      case other => List(other)
    }

    val tpe     = TypeRepr.of[A]
    val members = flattenIntersection(tpe)

    members.foreach { member =>
      if (!IsNominalTypeMacros.isNominal(member)) {
        report.errorAndAbort(
          s"Cannot derive IsNominalIntersection: member type ${member.show} is not a nominal type. " +
            "All intersection members must be classes, traits, objects, enums, or applied types."
        )
      }
    }

    val typeIdExprs: List[Expr[TypeId.Erased]] = members.map { member =>
      member.asType match {
        case '[t] =>
          '{ TypeId.of[t].erased }
      }
    }

    val listExpr = Expr.ofList(typeIdExprs)

    '{
      new IsNominalIntersection[A] {
        val typeIdsErased: Chunk[TypeId.Erased] = Chunk.fromIterable($listExpr)
      }
    }
  }
}
