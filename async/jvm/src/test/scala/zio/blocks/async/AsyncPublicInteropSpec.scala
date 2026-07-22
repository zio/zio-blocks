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

import zio.ZIO
import zio.test._

import java.util.concurrent.CompletableFuture

import scala.concurrent.{ExecutionContext, Future}

/**
 * Verifies the JVM public interop surface — `Async.fromFuture` /
 * `Async.fromCompletionStage` on the companion and the `fa.toFuture` /
 * `fa.toCompletableFuture` extension methods — that replaced the old
 * `AsyncInterop` object. The exhaustive edge-case coverage lives in
 * [[AsyncInteropSpec]] (against the internal implementation); this spec proves
 * the public forwarders and extension-method resolution work end-to-end.
 */
object AsyncPublicInteropSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val boom = AsyncTestSupport.boom

  def spec = suite("AsyncPublicInteropSpec")(
    test("Async.fromFuture round-trips a value") {
      assertTrue(Async.fromFuture(Future.successful(7)).map(_ + 1).block == 8)
    },
    test("Async.fromCompletionStage round-trips a value") {
      val cf: CompletableFuture[Int] = CompletableFuture.completedFuture(41)
      assertTrue(Async.fromCompletionStage(cf).map(_ + 1).block == 42)
    },
    test("fa.toFuture completes with the value") {
      val fut = Async.succeed(123).map(_ + 1).toFuture
      ZIO.fromFuture(_ => fut).map(v => assertTrue(v == 124))
    },
    test("fa.toFuture propagates failure") {
      val fut = Async.fail(boom).toFuture
      ZIO.fromFuture(_ => fut).either.map(e => assertTrue(e == Left(boom)))
    },
    test("fa.toCompletableFuture completes with the value") {
      val cf = Async.succeed(9).toCompletableFuture
      assertTrue(cf.get() == 9)
    },
    test("round-trip Async -> Future -> Async") {
      val back: Async[Int] = Async.fromFuture(Async.succeed(5).map(_ * 2).toFuture)
      ZIO.fromFuture(_ => back.toFuture).map(v => assertTrue(v == 10))
    }
  )
}
