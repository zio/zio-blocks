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

package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

private[combinators] sealed trait LowerPriorityConcat {
  implicit def disjoint[L, R]: Concat.WithOut[L, R, Either[L, R]] = macro ConcatMacros.disjointImpl[L, R]
}

private[combinators] trait ConcatCompanionPlatform extends LowerPriorityConcat {
  implicit def derive[L, R]: Concat[L, R] = macro ConcatMacros.concatImpl[L, R]
}

private[combinators] object ConcatMacros {

  def disjointImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._

    val lType = weakTypeOf[L].dealias
    val rType = weakTypeOf[R].dealias

    if (lType =:= rType || lType <:< rType || rType <:< lType)
      c.abort(c.enclosingPosition, s"Concat.disjoint requires disjoint types but $lType and $rType are related")

    val outType = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))

    q"""
      new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
        type Out = $outType
        def isIdentityLike: _root_.scala.Boolean = false
        def left(l: $lType): $outType = _root_.scala.Left(l)
        def right(r: $rType): $outType = _root_.scala.Right(r)
      }
    """
  }

  def concatImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._

    val lType = weakTypeOf[L].dealias
    val rType = weakTypeOf[R].dealias

    if (lType =:= rType) {
      q"""
        new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
          type Out = $lType
          def isIdentityLike: _root_.scala.Boolean = true
          def left(l: $lType): $lType = l
          def right(r: $rType): $lType = r
        }
      """
    } else if (lType <:< rType) {
      q"""
        new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
          type Out = $rType
          def isIdentityLike: _root_.scala.Boolean = true
          def left(l: $lType): $rType = l: $rType
          def right(r: $rType): $rType = r
        }
      """
    } else if (rType <:< lType) {
      q"""
        new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
          type Out = $lType
          def isIdentityLike: _root_.scala.Boolean = true
          def left(l: $lType): $lType = l
          def right(r: $rType): $lType = r: $lType
        }
      """
    } else {
      c.abort(c.enclosingPosition, s"Cannot derive Concat for disjoint types $lType and $rType; use Concat.disjoint")
    }
  }

}
