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

package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

/**
 * The metadata of a schema node during derivation: the node's
 * [[zio.blocks.schema.binding.Binding Binding]] and its instance of the type
 * class `TC`. `DerivationBuilder#derive` turns a schema's `Reflect[Binding, A]`
 * into a `Reflect` that carries this metadata, so a [[Deriver]] reads a child's
 * instance with `Deriver#instance` and its binding with `Deriver#binding`.
 */
final case class BindingInstance[TC[_], T, A](binding: Binding[T, A], instance: Lazy[TC[A]])

/** The `HasBinding` and `HasInstance` instances of [[BindingInstance]]. */
object BindingInstance {

  /** Reads and replaces the binding of a [[BindingInstance]]. */
  implicit def hasBinding[TC[_]]: HasBinding[({ type F[A, B] = BindingInstance[TC, A, B] })#F] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasBinding[F] {
      def binding[A, B](fa: F[A, B]): Binding[A, B] = fa.binding

      def updateBinding[A, B](fa: F[A, B], f: Binding[A, B] => Binding[A, B]): F[A, B] =
        fa.copy(binding = f(fa.binding))
    }
  }

  /** Reads the instance of a [[BindingInstance]]. */
  implicit def hasInstance[TC[_]]: HasInstance[({ type F[A, B] = BindingInstance[TC, A, B] })#F, TC] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasInstance[F, TC] {
      def instance[A, B](fa: F[A, B]): Lazy[TC[B]] = fa.instance
    }
  }
}
