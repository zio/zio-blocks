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

package zio.blocks.rpc

/**
 * Compile-time type class that decomposes a method return type into error and
 * success components.
 *
 * The RPC derivation macro summons this via implicit search to determine how to
 * extract error and success types from service trait method return types.
 *
 * Built-in instance: `Either[E, A]` decomposes to error=E, success=A.
 *
 * Plain return types (not wrapped in Either or an effect) do not require a
 * `ReturnTypeDecomposer` instance — the macro handles them directly as
 * success-only with `error = Nothing`.
 *
 * For effect types (ZIO, cats IO, Kyo), add a dependency on the corresponding
 * rpc integration module which provides the appropriate decomposer instance.
 */
trait ReturnTypeDecomposer[F] {
  type Error
  type Success
}

object ReturnTypeDecomposer {
  type Aux[F, E, S] = ReturnTypeDecomposer[F] { type Error = E; type Success = S }

  implicit def eitherDecomposer[E, A]: Aux[Either[E, A], E, A] =
    new ReturnTypeDecomposer[Either[E, A]] {
      type Error   = E
      type Success = A
    }
}
