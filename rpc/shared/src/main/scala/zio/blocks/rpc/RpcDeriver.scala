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
 * Trait for protocol-specific derivation from an RPC descriptor. Analogous to
 * Schema's `Deriver[TC[_]]`, but operates at service level.
 *
 * @tparam Protocol
 *   The protocol-specific type class to derive
 */
trait RpcDeriver[Protocol[_]] {

  /**
   * Derives a protocol-specific implementation from an RPC service descriptor.
   *
   * @tparam T
   *   The service trait type
   * @param rpc
   *   The RPC descriptor for the service
   * @return
   *   A protocol-specific implementation for the service
   */
  def deriveService[T](rpc: RPC[T]): Protocol[T]
}
