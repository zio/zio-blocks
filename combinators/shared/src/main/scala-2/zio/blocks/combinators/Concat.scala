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

object Concat {

  trait Concat[L, R] {
    type Out

    def isIdentityLike: Boolean

    def left(l: L): Out

    def right(r: R): Out
  }

  object Concat {
    type WithOut[L, R, O] = Concat[L, R] { type Out = O }

    implicit val bothNothing: WithOut[Nothing, Nothing, Nothing] =
      new Concat[Nothing, Nothing] {
        type Out = Nothing
        def isIdentityLike: Boolean     = true
        def left(l: Nothing): Nothing  = l
        def right(r: Nothing): Nothing = r
      }

    implicit def leftNothing[R]: WithOut[Nothing, R, R] =
      new Concat[Nothing, R] {
        type Out = R
        def isIdentityLike: Boolean = true
        def left(l: Nothing): R = l
        def right(r: R): R      = r
      }

    implicit def rightNothing[L]: WithOut[L, Nothing, L] =
      new Concat[L, Nothing] {
        type Out = L
        def isIdentityLike: Boolean = true
        def left(l: L): L       = l
        def right(r: Nothing): L = r
      }

    implicit def derive[L, R]: Concat[L, R] = macro ConcatMacros.concatImpl[L, R]
  }

  private[combinators] object ConcatMacros {

    def concatImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType = weakTypeOf[L].dealias
      val rType = weakTypeOf[R].dealias

      if (lType =:= rType) {
        q"""
          new _root_.zio.blocks.combinators.Concat.Concat[$lType, $rType] {
            type Out = $lType
            def isIdentityLike: _root_.scala.Boolean = true
            def left(l: $lType): $lType = l
            def right(r: $rType): $lType = r
          }
        """
      } else if (lType <:< rType) {
        q"""
          new _root_.zio.blocks.combinators.Concat.Concat[$lType, $rType] {
            type Out = $rType
            def isIdentityLike: _root_.scala.Boolean = true
            def left(l: $lType): $rType = l: $rType
            def right(r: $rType): $rType = r
          }
        """
      } else if (rType <:< lType) {
        q"""
          new _root_.zio.blocks.combinators.Concat.Concat[$lType, $rType] {
            type Out = $lType
            def isIdentityLike: _root_.scala.Boolean = true
            def left(l: $lType): $lType = l
            def right(r: $rType): $lType = r: $lType
          }
        """
      } else {
        val outType = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))
        q"""
          new _root_.zio.blocks.combinators.Concat.Concat[$lType, $rType] {
            type Out = $outType
            def isIdentityLike: _root_.scala.Boolean = false
            def left(l: $lType): $outType = _root_.scala.Left(l)
            def right(r: $rType): $outType = _root_.scala.Right(r)
          }
        """
      }
    }
  }
}
