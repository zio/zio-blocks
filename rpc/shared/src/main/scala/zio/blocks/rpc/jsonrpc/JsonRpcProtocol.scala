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
import zio.blocks.rpc.{MetaAnnotation, RPC}
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.{Json, JsonCodec}
import zio.blocks.typeid.TypeId

/**
 * A transport-neutral JSON-RPC 2.0 contract derived from an RPC descriptor.
 *
 * External runtimes can use this contract to build concrete Netty, Node, Wasm,
 * browser, or other server/client implementations without storing executable
 * logic in the descriptor itself.
 */
final case class JsonRpcProtocol[T](
  serviceName: String,
  serviceTypeId: TypeId[T],
  operations: Chunk[JsonRpcProtocol.Operation],
  metadata: RPC.ServiceMetadata
) {

  private[jsonrpc] def bindHandlers(handlers: Chunk[JsonRpcProtocol.BoundOperation]): Either[String, JsonRpcCodec] = {
    val expectedNames = operations.map(_.name)
    val boundNames    = handlers.map(_.name)

    val missing = expectedNames.filterNot(expected => boundNames.contains(expected))
    val extra   = boundNames.filterNot(bound => expectedNames.contains(bound))

    val errors = Chunk.empty[String] ++
      (if (missing.nonEmpty) Chunk(s"Missing JSON-RPC handlers for operations: ${missing.mkString(", ")}")
       else Chunk.empty) ++
      (if (extra.nonEmpty) Chunk(s"Unknown JSON-RPC handlers supplied for operations: ${extra.mkString(", ")}")
       else Chunk.empty)

    if (errors.nonEmpty) Left(errors.mkString("; "))
    else {
      val handlerMap = handlers.foldLeft(Map.empty[String, JsonRpcCodec.OperationHandler]) { (acc, handler) =>
        acc + (handler.name -> new JsonRpcCodec.OperationHandler {
          def handle(params: Json): Either[SchemaError, Json] = handler.handle(params)
        })
      }

      Right(new JsonRpcCodec(handlerMap))
    }
  }

  /**
   * Binds a single typed operation handler and produces an executable codec.
   *
   * This reference binding keeps the public API in terms of typed values plus
   * the derived operation schemas/codecs, while raw JSON remains internal to the
   * wire format boundary.
   */
  def bind[Input, Output](
    name: String
  )(handler: Input => Either[SchemaError, Output])(implicit
    inputSchema: Schema[Input],
    outputSchema: Schema[Output]
  ): Either[String, JsonRpcCodec] = {
    val operationOpt = operations.find(_.name == name)

    operationOpt match {
      case None => Left(s"Unknown JSON-RPC operation: $name")
      case Some(operation) =>
        validateSchema(name, "input", operation.inputSchema, inputSchema)
          .flatMap(_ => validateSchema(name, "output", operation.outputSchema, outputSchema))
          .flatMap(_ =>
            bindHandlers(
              Chunk(
                JsonRpcProtocol.BoundOperation(
                  name = name,
                  handle = params => {
                    val typedInput = operation.inputCodec.asInstanceOf[JsonCodec[Input]].decodeValue(params)
                    try {
                      handler(typedInput).map(output => operation.outputCodec.asInstanceOf[JsonCodec[Output]].encodeValue(output))
                    } catch {
                      case NonFatal(error) => Left(SchemaError(error.getMessage))
                    }
                  }
                )
              )
            )
          )
    }
  }

  private def validateSchema[A](
    operationName: String,
    label: String,
    actual: Schema[?],
    expected: Schema[A]
  ): Either[String, Unit] =
    if (actual.reflect.typeId == expected.reflect.typeId) Right(())
    else
      Left(
        s"JSON-RPC operation '$operationName' has $label schema ${actual.reflect.typeId}, but binding expects ${expected.reflect.typeId}"
      )
}

object JsonRpcProtocol {

  private[jsonrpc] /** Internal raw JSON handler after typed binding. */
  final case class BoundOperation(
    name: String,
    handle: Json => Either[SchemaError, Json]
  )

  /**
   * JSON-RPC-specific information for a single RPC operation.
   *
   * `inputCodec` encodes and decodes the combined input value described by
   * `inputSchema`. Concrete runtimes are responsible for mapping this combined
   * value to and from the JSON-RPC `params` representation they choose to
   * support.
   */
  final case class Operation(
    name: String,
    inputSchema: Schema[?],
    inputCodec: JsonCodec[?],
    outputSchema: Schema[?],
    outputCodec: JsonCodec[?],
    errorSchema: Option[Schema[?]],
    errorCodec: Option[JsonCodec[?]],
    parameterNames: Chunk[String],
    annotations: Chunk[MetaAnnotation],
    parameterAnnotations: Chunk[Chunk[MetaAnnotation]]
  )
}
