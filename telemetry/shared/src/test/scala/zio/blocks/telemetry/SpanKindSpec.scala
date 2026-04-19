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

package zio.blocks.telemetry

import zio.test._

object SpanKindSpec extends ZIOSpecDefault {

  def spec = suite("SpanKind")(
    suite("sealed trait exhaustiveness")(
      test("Internal is accessible") {
        val _: SpanKind = SpanKind.Internal
        assertTrue(true)
      },
      test("Server is accessible") {
        val _: SpanKind = SpanKind.Server
        assertTrue(true)
      },
      test("Client is accessible") {
        val _: SpanKind = SpanKind.Client
        assertTrue(true)
      },
      test("Producer is accessible") {
        val _: SpanKind = SpanKind.Producer
        assertTrue(true)
      },
      test("Consumer is accessible") {
        val _: SpanKind = SpanKind.Consumer
        assertTrue(true)
      }
    ),
    suite("pattern matching")(
      test("exhaustive match on all kinds") {
        def spanKindToString(kind: SpanKind): String = kind match {
          case SpanKind.Internal => "Internal"
          case SpanKind.Server   => "Server"
          case SpanKind.Client   => "Client"
          case SpanKind.Producer => "Producer"
          case SpanKind.Consumer => "Consumer"
        }
        assertTrue(
          spanKindToString(SpanKind.Internal) == "Internal" &&
            spanKindToString(SpanKind.Server) == "Server" &&
            spanKindToString(SpanKind.Client) == "Client" &&
            spanKindToString(SpanKind.Producer) == "Producer" &&
            spanKindToString(SpanKind.Consumer) == "Consumer"
        )
      }
    ),
    suite("equality")(
      test("same kind equals itself") {
        val internal: SpanKind = SpanKind.Internal
        assertTrue(internal == internal)
      },
      test("different kinds are not equal") {
        val internal: SpanKind = SpanKind.Internal
        val server: SpanKind   = SpanKind.Server
        assertTrue(internal != server)
      }
    )
  )
}
