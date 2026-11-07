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

import zio.blocks.schema.Lazy

/**
 * Reads the instance of the type class `TC` out of schema node metadata of type
 * `F`. Each `derive*` method of a [[Deriver]] receives one as its implicit `D`;
 * `Deriver#instance` calls it.
 */
trait HasInstance[F[_, _], TC[_]] {

  /**
   * Returns the instance stored in `fa`. It is lazy: force it on first use, not
   * while deriving, so that recursive types work.
   */
  def instance[T, A](fa: F[T, A]): Lazy[TC[A]]
}
