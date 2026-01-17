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

package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

final case class BindingInstance[TC[_], T, A](binding: Binding[T, A], instance: Lazy[TC[A]])

object BindingInstance {
  implicit def hasBinding[TC[_]]: HasBinding[({ type F[A, B] = BindingInstance[TC, A, B] })#F] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasBinding[F] {
      def binding[A, B](fa: F[A, B]): Binding[A, B] = fa.binding

      def updateBinding[A, B](fa: F[A, B], f: Binding[A, B] => Binding[A, B]): F[A, B] =
        fa.copy(binding = f(fa.binding))
    }
  }

  implicit def hasInstance[TC[_]]: HasInstance[({ type F[A, B] = BindingInstance[TC, A, B] })#F, TC] = {
    type F[A, B] = BindingInstance[TC, A, B]
    new HasInstance[F, TC] {
      def instance[A, B](fa: F[A, B]): Lazy[TC[B]] = fa.instance
    }
  }
}
