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

    val tpe = TypeRepr.of[A]

    if (!tpe.typeSymbol.flags.is(Flags.Opaque))
      report.errorAndAbort(
        s"DbCodec.derivedOpaque requires an opaque type, but ${Type.show[A]} is not opaque"
      )

    val underlying = tpe match {
      case tr: TypeRef if tr.isOpaqueAlias => tr.translucentSuperType.dealias
      case _ =>
        report.errorAndAbort(
          s"Cannot determine underlying type of opaque type ${Type.show[A]}"
        )
    }

    underlying.asType match {
      case '[u] =>
        Expr.summon[DbCodec[u]] match {
          case Some(baseCodec) =>
            // Safe: opaque types are erased to their underlying type at runtime.
            '{ $baseCodec.asInstanceOf[DbCodec[A]] }
          case None =>
            report.errorAndAbort(
              s"No DbCodec found for underlying type ${Type.show[u]} of opaque type ${Type.show[A]}"
            )
        }
    }
  }
}
