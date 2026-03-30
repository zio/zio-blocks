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
import zio.blocks.schema.json.Json

/**
 * RpcDeriver that produces JsonRpcCodec instances from RPC descriptors.
 */
object JsonRpcDeriver extends RpcDeriver[JsonRpcCodec] {

  override def deriveService[T](rpc: RPC[T]): JsonRpcCodec[T] = {
    val handlers = rpc.operations.foldLeft(Map.empty[String, JsonRpcCodec.OperationHandler]) { (acc, op) =>
      acc + (op.name -> createHandler(op))
    }
    new JsonRpcCodec[T](handlers)
  }

  private def createHandler(op: RPC.Operation[?, ?]): JsonRpcCodec.OperationHandler =
    new JsonRpcCodec.OperationHandler {
      def handle(params: Json): Either[Throwable, Json] =
        Left(
          new UnsupportedOperationException(
            s"Operation '${op.name}' handler not bound to a service implementation"
          )
        )
    }
}
