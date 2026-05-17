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

trait Concat[L, R] {
  type Out

  def isIdentityLike: Boolean

  def left(l: L): Out

  def right(r: R): Out
}

object Concat extends ConcatCompanionPlatform {
  type WithOut[L, R, O] = _root_.zio.blocks.combinators.Concat[L, R] { type Out = O }

  implicit val bothNothing: WithOut[Nothing, Nothing, Nothing] =
    new _root_.zio.blocks.combinators.Concat[Nothing, Nothing] {
      type Out = Nothing
      def isIdentityLike: Boolean    = true
      def left(l: Nothing): Nothing  = l
      def right(r: Nothing): Nothing = r
    }

  implicit def leftNothing[R]: WithOut[Nothing, R, R] =
    new _root_.zio.blocks.combinators.Concat[Nothing, R] {
      type Out = R
      def isIdentityLike: Boolean = true
      def left(l: Nothing): R     = l
      def right(r: R): R          = r
    }

  implicit def rightNothing[L]: WithOut[L, Nothing, L] =
    new _root_.zio.blocks.combinators.Concat[L, Nothing] {
      type Out = L
      def isIdentityLike: Boolean = true
      def left(l: L): L           = l
      def right(r: Nothing): L    = r
    }
}
