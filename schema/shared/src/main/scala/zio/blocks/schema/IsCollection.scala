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

package zio.blocks.schema

/**
 * Evidence that `A` has the shape `C[E]`, a type constructor with one type
 * argument, with `C` as `Collection` and `E` as `Elem`. `Reflect#asSequence`
 * takes it to type the sequence node it returns. The compiler provides it for
 * any such type through `isCollection`, collection or not: for a type such as
 * `Option[Int]`, `asSequence` returns `None`.
 */
trait IsCollection[A] {

  /** The type constructor of `A`, such as `List` for `List[Int]`. */
  type Collection[_]

  /** The element type of `A`, such as `Int` for `List[Int]`. */
  type Elem

  /** Proves that `Collection[Elem]` is `A`. */
  def proof: Collection[Elem] =:= A
}

/** The instance of [[IsCollection]] for every type of the form `C[A]`. */
object IsCollection {

  /**
   * An `IsCollection[A]` whose `Collection` is `C0` and whose `Elem` is
   * `Elem0`.
   */
  type Typed[A, C0[_], Elem0] = IsCollection[A] {
    type Collection[X] = C0[X]
    type Elem          = Elem0
  }

  /**
   * Provides `IsCollection` for `C[A]`, with `C` and `A` as its type members.
   */
  implicit def isCollection[C[_], A]: IsCollection.Typed[C[A], C, A] =
    new IsCollection[C[A]] {
      type Collection[X] = C[X]
      type Elem          = A

      def proof: Collection[Elem] =:= C[A] = implicitly[Collection[Elem] =:= C[A]]
    }
}
