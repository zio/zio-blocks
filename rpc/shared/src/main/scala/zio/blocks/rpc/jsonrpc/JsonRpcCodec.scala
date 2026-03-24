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

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json

/**
 * A JSON-RPC 2.0 handler derived from an RPC service descriptor.
 * Transport-agnostic: operates on `String => ZIO[Any, Nothing, String]`.
 */
final class JsonRpcCodec[T](
  private val operationHandlers: Map[String, JsonRpcCodec.OperationHandler]
) {

  /**
   * Handles a JSON-RPC 2.0 request string and returns a response string. Note:
   * JSON-RPC 2.0 notifications (requests without an `id` field) are not
   * supported; they are treated as requests with `id: null`.
   */
  def handleRequest(request: String): ZIO[Any, Nothing, String] =
    Json.parse(request) match {
      case Left(_) =>
        ZIO.succeed(JsonRpcCodec.errorResponse(Json.Null, -32700, "Parse error"))
      case Right(parsed) =>
        val rawId  = parsed.get("id").values.flatMap(_.headOption).getOrElse(Json.Null)
        val idJson = rawId match {
          case _: Json.String | _: Json.Number | Json.Null => rawId
          case _                                           => Json.Null
        }
        parsed.get("method").values.flatMap(_.headOption) match {
          case None =>
            ZIO.succeed(
              JsonRpcCodec.errorResponse(idJson, -32600, "Invalid Request: missing 'method'")
            )
          case Some(methodJson) =>
            methodJson match {
              case str: Json.String =>
                val methodName = str.value
                operationHandlers.get(methodName) match {
                  case None =>
                    ZIO.succeed(
                      JsonRpcCodec.errorResponse(idJson, -32601, s"Method not found: $methodName")
                    )
                  case Some(handler) =>
                    val params = parsed.get("params").values.flatMap(_.headOption).getOrElse(Json.Null)
                    handler
                      .handle(params)
                      .map(result => JsonRpcCodec.successResponse(idJson, result))
                      .catchAll(error =>
                        ZIO.succeed(
                          JsonRpcCodec.errorResponse(
                            idJson,
                            -32603,
                            Option(error.getMessage).getOrElse("Internal error")
                          )
                        )
                      )
                }
              case _ =>
                ZIO.succeed(
                  JsonRpcCodec.errorResponse(idJson, -32600, "Invalid Request: 'method' must be a string")
                )
            }
        }
    }
}

object JsonRpcCodec {

  trait OperationHandler {
    def handle(params: Json): ZIO[Any, Throwable, Json]
  }

  def successResponse(id: Json, result: Json): String =
    Json
      .Object(
        Chunk(
          ("jsonrpc", Json.String("2.0")),
          ("result", result),
          ("id", id)
        )
      )
      .print

  def errorResponse(id: Json, code: Int, message: String): String =
    Json
      .Object(
        Chunk(
          ("jsonrpc", Json.String("2.0")),
          (
            "error",
            Json.Object(
              Chunk(
                ("code", Json.Number(code)),
                ("message", Json.String(message))
              )
            )
          ),
          ("id", id)
        )
      )
      .print
}
