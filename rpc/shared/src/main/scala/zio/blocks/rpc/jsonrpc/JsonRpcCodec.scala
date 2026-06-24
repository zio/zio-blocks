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

import scala.util.control.NonFatal
import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError
import zio.blocks.schema.json.Json

/**
 * A low-level JSON-RPC 2.0 dispatcher over internal JSON operation handlers.
 *
 * This type is transport-agnostic and executable, but intentionally unaware of
 * RPC descriptors or dependency injection. Higher-level runtimes can construct
 * it from a derived protocol contract plus concrete typed bindings while raw
 * JSON remains confined to the wire format boundary.
 */
final class JsonRpcCodec(
  private val operationHandlers: Map[String, JsonRpcCodec.OperationHandler]
) {

  /**
   * Handles a JSON-RPC 2.0 request string and returns an optional response
   * string. Returns `None` for notifications (requests without an `id` field),
   * as the JSON-RPC 2.0 spec requires no response for notifications.
   */
  def handleRequest(request: String): Option[String] =
    Json.parse(request) match {
      case Left(_) =>
        Some(JsonRpcCodec.errorResponse(Json.Null, -32700, "Parse error"))
      case Right(parsed) =>
        parsed match {
          case obj: Json.Object =>
            val jsonrpcField    = obj.get("jsonrpc").values.flatMap(_.headOption)
            val hasValidJsonrpc = jsonrpcField.exists {
              case s: Json.String => s.value == "2.0"
              case _              => false
            }
            if (!hasValidJsonrpc) {
              Some(
                JsonRpcCodec
                  .errorResponse(Json.Null, -32600, "Invalid Request: missing or invalid 'jsonrpc' field")
              )
            } else {
              val rawId          = obj.get("id").values.flatMap(_.headOption)
              val isNotification = rawId.isEmpty
              val idJson         = rawId.map {
                case s: Json.String => s
                case n: Json.Number => n
                case _              => Json.Null
              }
                .getOrElse(Json.Null)

              val response = obj.get("method").values.flatMap(_.headOption) match {
                case None =>
                  JsonRpcCodec.errorResponse(idJson, -32600, "Invalid Request: missing 'method'")
                case Some(methodJson) =>
                  methodJson match {
                    case str: Json.String =>
                      val methodName = str.value
                      operationHandlers.get(methodName) match {
                        case None =>
                          JsonRpcCodec.errorResponse(idJson, -32601, s"Method not found: $methodName")
                        case Some(handler) =>
                          val params = obj.get("params").values.flatMap(_.headOption).getOrElse(Json.Null)
                          try {
                            handler.handle(params) match {
                              case Right(result) => JsonRpcCodec.successResponse(idJson, result)
                              case Left(error)   =>
                                JsonRpcCodec.errorResponse(
                                  idJson,
                                  -32603,
                                  JsonRpcCodec.message(error)
                                )
                            }
                          } catch {
                            case NonFatal(e) =>
                              JsonRpcCodec.errorResponse(
                                idJson,
                                -32603,
                                Option(e.getMessage).getOrElse("Internal error")
                              )
                          }
                      }
                    case _ =>
                      JsonRpcCodec.errorResponse(idJson, -32600, "Invalid Request: 'method' must be a string")
                  }
              }
              if (isNotification) None else Some(response)
            }
          case _ =>
            Some(JsonRpcCodec.errorResponse(Json.Null, -32600, "Invalid Request: expected JSON object"))
        }
    }
}

object JsonRpcCodec {

  /**
   * Handles a single JSON-RPC operation at the internal wire boundary.
   *
   * Public bindings should remain typed; this low-level trait exists only for
   * the executable JSON-RPC dispatcher after typed protocol binding.
   *
   * Returns `Right(result)` on success (serialized as the `"result"` field) or
   * `Left(error)` on failure (serialized as a `-32603` internal error).
   * Non-fatal exceptions thrown during handling are caught and treated as
   * `Left`.
   */
  trait OperationHandler {
    def handle(params: Json): Either[SchemaError, Json]
  }

  private[jsonrpc] def message(error: SchemaError): String = {
    val rendered = error.toString
    if (rendered == null || rendered.isEmpty) "Internal error" else rendered
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
