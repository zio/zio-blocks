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

package zio.blocks.rpc.jsonrpc

import zio.blocks.rpc._

/**
 * RpcDeriver that produces transport-neutral JSON-RPC contracts from RPC
 * descriptors.
 */
object JsonRpcDeriver extends RpcDeriver[JsonRpcProtocol] {

  override def deriveService[T](rpc: RPC[T]): JsonRpcProtocol[T] =
    JsonRpcProtocol[T](
      serviceName = rpc.label,
      serviceTypeId = rpc.typeId,
      operations = rpc.operations.map { op =>
        JsonRpcProtocol.Operation(
          name = op.name,
          inputSchema = op.inputSchema,
          inputCodec = JsonRpcCodecs.derive(op.inputSchema),
          outputSchema = op.outputSchema,
          outputCodec = JsonRpcCodecs.derive(op.outputSchema),
          errorSchema = op.errorSchema,
          errorCodec = op.errorSchema.map(JsonRpcCodecs.derive),
          parameterNames = op.parameterNames,
          annotations = op.annotations,
          parameterAnnotations = op.parameterAnnotations
        )
      },
      metadata = rpc.metadata
    )
}
