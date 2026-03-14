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
import zio.test._
import zio.blocks.rpc._
import zio.blocks.rpc.fixtures._
import zio.blocks.schema.json.Json

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

  private val greeterCodec = new JsonRpcCodec[GreeterService](
    Map(
      "greet" -> new JsonRpcCodec.OperationHandler {
        def handle(params: Json): ZIO[Any, Throwable, Json] = {
          val name = extractString(params, "name").getOrElse("unknown")
          ZIO.succeed(Json.String(s"Hello, $name!"))
        }
      }
    )
  )

  private val failingCodec = new JsonRpcCodec[GreeterService](
    Map(
      "greet" -> new JsonRpcCodec.OperationHandler {
        def handle(params: Json): ZIO[Any, Throwable, Json] =
          ZIO.fail(new RuntimeException("Something went wrong"))
      }
    )
  )

  def spec = suite("JsonRpcIntegrationSpec")(
    suite("successful request-response round-trip")(
      test("returns correct result with string param") {
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"world"},"id":1}"""
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
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
        }
      },
      test("preserves request id in response") {
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":42}"""
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val id       = extractNumber(response, "id")
          assertTrue(id.contains(BigDecimal(42)))
        }
      }
    ),
    suite("error handling")(
      test("method not found returns -32601") {
        val request = """{"jsonrpc":"2.0","method":"nonexistent","params":{},"id":1}"""
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          assertTrue(code.contains(BigDecimal(-32601)))
        }
      },
      test("parse error returns -32700") {
        val request = "not valid json{{"
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          assertTrue(code.contains(BigDecimal(-32700)))
        }
      },
      test("missing method returns -32600") {
        val request = """{"jsonrpc":"2.0","params":{},"id":1}"""
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          val message  = error.flatMap(e => extractString(e, "message"))
          assertTrue(
            code.contains(BigDecimal(-32600)),
            message.exists(_.contains("missing 'method'"))
          )
        }
      },
      test("handler error returns -32603") {
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":1}"""
        for {
          raw <- failingCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          val message  = error.flatMap(e => extractString(e, "message"))
          assertTrue(
            code.contains(BigDecimal(-32603)),
            message.contains("Something went wrong")
          )
        }
      },
      test("non-string method returns -32600") {
        val request = """{"jsonrpc":"2.0","method":123,"params":{},"id":1}"""
        for {
          raw <- greeterCodec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          assertTrue(code.contains(BigDecimal(-32600)))
        }
      }
    ),
    suite("JsonRpcDeriver integration")(
      test("deriveService creates codec from RPC descriptor") {
        val rpc     = RPC.derived[GreeterService]
        val codec   = JsonRpcDeriver.deriveService(rpc)
        val request = """{"jsonrpc":"2.0","method":"unknown","params":{},"id":1}"""
        for {
          raw <- codec.handleRequest(request)
        } yield assertTrue(raw.contains("-32601"))
      },
      test("derived codec returns internal error for unbound operations") {
        val rpc     = RPC.derived[GreeterService]
        val codec   = JsonRpcDeriver.deriveService(rpc)
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":1}"""
        for {
          raw <- codec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          val code     = error.flatMap(e => extractNumber(e, "code"))
          assertTrue(code.contains(BigDecimal(-32603)))
        }
      }
    ),
    suite("JsonRpcFormat integration")(
      test("deriver returns JsonRpcDeriver") {
        assertTrue(JsonRpcFormat.deriver == JsonRpcDeriver)
      },
      test("RPC.derive with JsonRpcFormat produces codec") {
        val rpc     = RPC.derived[GreeterService]
        val codec   = rpc.derive(JsonRpcFormat.deriver)
        val request = """{"jsonrpc":"2.0","method":"greet","params":{"name":"test"},"id":1}"""
        for {
          raw <- codec.handleRequest(request)
        } yield {
          val response = parseResponse(raw)
          val error    = extractField(response, "error")
          assertTrue(error.isDefined)
        }
      }
    )
  )
}
