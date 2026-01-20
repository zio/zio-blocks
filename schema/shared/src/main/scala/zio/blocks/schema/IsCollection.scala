/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema

trait IsCollection[A] {
  type Collection[_]
  type Elem

  def proof: Collection[Elem] =:= A
}

object IsCollection {
  type Typed[A, C0[_], Elem0] = IsCollection[A] {
    type Collection[X] = C0[X]
    type Elem          = Elem0
  }

  implicit def isCollection[C[_], A]: IsCollection.Typed[C[A], C, A] =
    new IsCollection[C[A]] {
      type Collection[X] = C[X]
      type Elem          = A

      def proof: Collection[Elem] =:= C[A] = implicitly[Collection[Elem] =:= C[A]]
    }
}
