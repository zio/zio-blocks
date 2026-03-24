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

package zio.blocks.otel

import zio.test._

object SpanStatusSpec extends ZIOSpecDefault {

  def spec = suite("SpanStatus")(
    suite("Unset")(
      test("is accessible as object") {
        val _: SpanStatus = SpanStatus.Unset
        assertTrue(true)
      },
      test("equals itself") {
        assertTrue(SpanStatus.Unset == SpanStatus.Unset)
      }
    ),
    suite("Ok")(
      test("is accessible as object") {
        val _: SpanStatus = SpanStatus.Ok
        assertTrue(true)
      },
      test("equals itself") {
        assertTrue(SpanStatus.Ok == SpanStatus.Ok)
      },
      test("is not equal to Unset") {
        val ok: SpanStatus    = SpanStatus.Ok
        val unset: SpanStatus = SpanStatus.Unset
        assertTrue(ok != unset)
      }
    ),
    suite("Error")(
      test("can be created with description") {
        val error = SpanStatus.Error("test error")
        assertTrue(error.description == "test error")
      },
      test("stores description") {
        val desc  = "connection timeout"
        val error = SpanStatus.Error(desc)
        assertTrue(error.description == desc)
      },
      test("equals with same description") {
        val err1 = SpanStatus.Error("same message")
        val err2 = SpanStatus.Error("same message")
        assertTrue(err1 == err2)
      },
      test("not equal with different description") {
        val err1 = SpanStatus.Error("error 1")
        val err2 = SpanStatus.Error("error 2")
        assertTrue(err1 != err2)
      },
      test("is not equal to Ok") {
        val error: SpanStatus = SpanStatus.Error("msg")
        val ok: SpanStatus    = SpanStatus.Ok
        assertTrue(error != ok)
      },
      test("is not equal to Unset") {
        val error: SpanStatus = SpanStatus.Error("msg")
        val unset: SpanStatus = SpanStatus.Unset
        assertTrue(error != unset)
      }
    ),
    suite("pattern matching")(
      test("exhaustive match on Unset") {
        val status: SpanStatus = SpanStatus.Unset
        val result             = status match {
          case SpanStatus.Unset      => "unset"
          case SpanStatus.Ok         => "ok"
          case SpanStatus.Error(msg) => s"error: $msg"
        }
        assertTrue(result == "unset")
      },
      test("exhaustive match on Ok") {
        val status: SpanStatus = SpanStatus.Ok
        val result             = status match {
          case SpanStatus.Unset      => "unset"
          case SpanStatus.Ok         => "ok"
          case SpanStatus.Error(msg) => s"error: $msg"
        }
        assertTrue(result == "ok")
      },
      test("exhaustive match on Error with extraction") {
        val status: SpanStatus = SpanStatus.Error("test failure")
        val result             = status match {
          case SpanStatus.Unset      => "unset"
          case SpanStatus.Ok         => "ok"
          case SpanStatus.Error(msg) => s"error: $msg"
        }
        assertTrue(result == "error: test failure")
      }
    )
  )
}
