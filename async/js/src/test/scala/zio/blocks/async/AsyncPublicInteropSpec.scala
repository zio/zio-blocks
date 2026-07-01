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

package zio.blocks.async

import zio._
import zio.test._

import scala.scalajs.js

import AsyncJsTestSupport._

/**
 * Verifies the Scala.js public interop surface — `Async.fromFuture` /
 * `Async.fromJsPromise` on the companion and the `fa.toFuture` /
 * `fa.toJsPromise` extension methods — that replaced the old `AsyncInterop`
 * object. The exhaustive edge-case coverage lives in [[AsyncInteropSpec]]
 * (against the internal implementation); this spec proves the public forwarders
 * and extension-method resolution work end-to-end.
 */
object AsyncPublicInteropSpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom

  def spec = suite("AsyncPublicInteropSpec")(
    test("Async.fromJsPromise round-trips a resolved value") {
      val p  = js.Promise.resolve[Int](7)
      val fa = Async.fromJsPromise(p).map(_ + 1)
      ZIO.fromFuture(_ => fa.toFuture).map(v => assertTrue(v == 8))
    },
    test("fa.toFuture completes with the value") {
      ZIO.fromFuture(_ => Async.succeed(123).map(_ + 1).toFuture).map(v => assertTrue(v == 124))
    },
    test("fa.toFuture propagates failure") {
      ZIO.fromFuture(_ => Async.fail(boom).toFuture).either.map(e => assertTrue(e == Left(boom)))
    },
    test("fa.toJsPromise resolves with the value") {
      val jp = Async.succeed(123).map(_ + 1).toJsPromise
      ZIO.fromFuture(_ => jp.toFuture).map(v => assertTrue(v == 124))
    },
    test("round-trip Async -> js.Promise -> Async") {
      val jp = Async.succeed(5).map(_ * 2).toJsPromise
      val fa = Async.fromJsPromise(jp)
      ZIO.fromFuture(_ => fa.toFuture).map(v => assertTrue(v == 10))
    }
  )
}
