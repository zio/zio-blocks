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

import zio.blocks.schema.Schema
import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

/**
 * A `RPC` is a data type that contains reified information on the structure of
 * a service trait, together with schemas for its operations' input and output
 * types.
 *
 * @tparam T
 *   The service trait type
 */
final case class RPC[T](
  label: String,
  typeId: TypeId[T],
  operations: Chunk[RPC.Operation[?, ?]],
  metadata: RPC.ServiceMetadata
) {

  /**
   * Derives a protocol-specific implementation from this RPC descriptor.
   *
   * @tparam P
   *   The protocol type constructor
   * @param deriver
   *   The protocol-specific deriver
   * @return
   *   A protocol-specific implementation for this service
   */
  def derive[P[_]](deriver: RpcDeriver[P]): P[T] = deriver.deriveService(this)
}

object RPC extends RPCCompanionVersionSpecific {

  /**
   * Describes a single operation (method) of a service trait.
   *
   * @tparam Input
   *   The combined input type (product of all parameters)
   * @tparam Output
   *   The success type
   */
  final case class Operation[Input, Output](
    name: String,
    inputSchema: Schema[Input],
    outputSchema: Schema[Output],
    errorSchema: Option[Schema[?]],
    parameterNames: Chunk[String],
    annotations: Chunk[MetaAnnotation],
    parameterAnnotations: Chunk[Chunk[MetaAnnotation]]
  )

  /**
   * Service-level metadata extracted from trait annotations.
   */
  final case class ServiceMetadata(
    annotations: Chunk[MetaAnnotation]
  )
}
