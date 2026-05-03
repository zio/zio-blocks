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

import zio.test._
import zio.blocks.rpc._
import zio.blocks.rpc.fixtures._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.{Json, JsonCodec}

object JsonRpcIntegrationSpec extends ZIOSpecDefault {

  private def extractField(json: Json, key: String): Option[Json] =
    json.get(key).values.flatMap(_.headOption)

  private def extractString(json: Json, key: String): Option[String] =
    extractField(json, key).collect { case s: Json.String => s.value }

  private def extractNumber(json: Json, key: String): Option[BigDecimal] =
    extractField(json, key).collect { case n: Json.Number => n.value }

  private def parseResponse(raw: String): Json =
    Json.parse(raw) match {
      case Right(j) => j
      case Left(e)  => throw e
    }

  private val greeterCodec = new JsonRpcCodec(
    Map(
      "greet" -> new JsonRpcCodec.OperationHandler {
        def handle(params: Json): Either[SchemaError, Json] = {
          val name = extractString(params, "name").getOrElse("unknown")
          Right(Json.String(s"Hello, $name!"))
        }
      }
    )
  )

  private val failingCodec = new JsonRpcCodec(
    Map(
      "greet" -> new JsonRpcCodec.OperationHandler {
        def handle(params: Json): Either[SchemaError, Json] =
          Left(SchemaError("Something went wrong"))
      }
    )
  )

  def spec = suite("JsonRpcIntegrationSpec")(
    suite("successful request-response round-trip")(
      test("returns correct result with string param") {
        val request  = """{"jsonrpc":"2.0","method":"greet","params":{"name":"world"},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val result   = extractField(response, "result")
        val jsonrpc  = extractString(response, "jsonrpc")
        assertTrue(
          jsonrpc.contains("2.0"),
          result.exists {
            case s: Json.String => s.value == "Hello, world!"
            case _              => false
          }
        )
      },
      test("preserves request id in response") {
        val request  = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":42}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val id       = extractNumber(response, "id")
        assertTrue(id.contains(BigDecimal(42)))
      }
    ),
    suite("error handling")(
      test("method not found returns -32601") {
        val request  = """{"jsonrpc":"2.0","method":"nonexistent","params":{},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        assertTrue(code.contains(BigDecimal(-32601)))
      },
      test("parse error returns -32700") {
        val request  = "not valid json{{"
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        assertTrue(code.contains(BigDecimal(-32700)))
      },
      test("missing method returns -32600") {
        val request  = """{"jsonrpc":"2.0","params":{},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        val message  = error.flatMap(e => extractString(e, "message"))
        assertTrue(
          code.contains(BigDecimal(-32600)),
          message.exists(_.contains("missing 'method'"))
        )
      },
      test("handler error returns -32603") {
        val request  = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":1}"""
        val raw      = failingCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        val message  = error.flatMap(e => extractString(e, "message"))
        assertTrue(
          code.contains(BigDecimal(-32603)),
          message.exists(_.contains("Something went wrong"))
        )
      },
      test("non-string method returns -32600") {
        val request  = """{"jsonrpc":"2.0","method":123,"params":{},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        assertTrue(code.contains(BigDecimal(-32600)))
      },
      test("missing jsonrpc field returns -32600") {
        val request  = """{"method":"greet","params":{"name":"test"},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        val message  = error.flatMap(e => extractString(e, "message"))
        assertTrue(
          code.contains(BigDecimal(-32600)),
          message.exists(_.contains("jsonrpc"))
        )
      },
      test("wrong jsonrpc version returns -32600") {
        val request  = """{"jsonrpc":"1.0","method":"greet","params":{"name":"test"},"id":1}"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        assertTrue(code.contains(BigDecimal(-32600)))
      },
      test("non-object request returns -32600") {
        val request  = """[1, 2, 3]"""
        val raw      = greeterCodec.handleRequest(request).get
        val response = parseResponse(raw)
        val error    = extractField(response, "error")
        val code     = error.flatMap(e => extractNumber(e, "code"))
        assertTrue(code.contains(BigDecimal(-32600)))
      }
    ),
    suite("notification handling")(
      test("notification without id returns None") {
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"world"}}"""
        val result  = greeterCodec.handleRequest(request)
        assertTrue(result.isEmpty)
      }
    ),
    suite("JsonRpcDeriver integration")(
      test("deriveService creates protocol contract from RPC descriptor") {
        val rpc      = RPC.derived[GreeterService]
        val protocol = JsonRpcDeriver.deriveService(rpc)
        val op       = protocol.operations(0)

        assertTrue(
          protocol.serviceName == "GreeterService",
          protocol.serviceTypeId == rpc.typeId,
          protocol.metadata == rpc.metadata,
          protocol.operations.length == 1,
          op.name == "greet",
          op.parameterNames == Chunk("name")
        )
      },
      test("derived contract can be bound once into an executable codec") {
        val protocol = JsonRpcDeriver.deriveService(RPC.derived[GreeterService])
        val codec    = protocol
          .bind[String, String]("greet") { name => Right(s"Hello, $name!") }
          .fold(message => throw new RuntimeException(message), identity)

        val request  = """{"jsonrpc":"2.0","method":"greet","params":"bound","id":1}"""
        val raw      = codec.handleRequest(request).get
        val response = parseResponse(raw)
        val result   = extractField(response, "result")

        assertTrue(
          result.exists {
            case s: Json.String => s.value == "Hello, bound!"
            case _              => false
          }
        )
      },
      test("binding rejects missing handlers") {
        val protocol = JsonRpcDeriver.deriveService(RPC.derived[GreeterService])
        val result   = protocol.bind[Int, String]("missing")(_ => Right("ignored"))(Schema.int, Schema.string)

        assertTrue(result.left.exists(_.contains("Unknown JSON-RPC operation")))
      },
      test("binding rejects input schema mismatches") {
        val protocol = JsonRpcDeriver.deriveService(RPC.derived[GreeterService])
        val result   = protocol.bind[Int, String]("greet")(_ => Right("ignored"))(Schema.int, Schema.string)

        assertTrue(result.left.exists(_.contains("input schema")))
      },
      test("binding rejects output schema mismatches") {
        val protocol = JsonRpcDeriver.deriveService(RPC.derived[GreeterService])
        val result   = protocol.bind[String, Int]("greet")(_ => Right(1))(Schema.string, Schema.int)

        assertTrue(result.left.exists(_.contains("output schema")))
      },
      test("derived contract includes JSON codecs for output and errors") {
        val protocol = JsonRpcDeriver.deriveService(RPC.derived[TodoService])
        val getTodo  = protocol.operations.find(_.name == "getTodo").get
        val output   = getTodo.outputCodec.asInstanceOf[JsonCodec[Todo]].encodeValue(Todo(1, "write tests"))
        val error    = getTodo.errorCodec.get.asInstanceOf[JsonCodec[ServiceError]].encodeValue(ServiceError(500, "boom"))

        assertTrue(
          getTodo.errorCodec.isDefined,
          output.isInstanceOf[Json.Object],
          error.isInstanceOf[Json.Object]
        )
      }
    ),
    suite("JsonRpcFormat integration")(
      test("deriver returns JsonRpcDeriver") {
        assertTrue(JsonRpcFormat.deriver == JsonRpcDeriver)
      },
      test("RPC.derive with JsonRpcFormat produces protocol contract") {
        val rpc      = RPC.derived[GreeterService]
        val protocol = rpc.derive(JsonRpcFormat.deriver)
        assertTrue(
          protocol.serviceName == "GreeterService",
          protocol.operations.map(_.name) == Chunk("greet")
        )
      }
    )
  )
}
