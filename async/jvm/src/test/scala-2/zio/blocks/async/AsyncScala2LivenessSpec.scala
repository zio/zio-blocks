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

import zio.test._

/**
 * ADVERSARIAL PROBE (Category AA / J — Scala 2 macro state-machine liveness over
 * binding shapes the ANF transform must thread across await boundaries).
 *
 * Corners not yet exercised by the shape / sweep probes: a `val (a, b) =
 * pair.await` tuple-destructuring binding whose components are BOTH read after a
 * LATER await (the macro must keep both pattern bindings live across the
 * continuation, not drop one); an await in EACH argument of a `new Foo(_, _)`
 * constructor (two awaits in one application spine, left-to-right); and a nested
 * `Async.async` block inside an outer one, driven via its own `.await`. Each
 * asserts the full runtime value — a silent miscompile (a dropped binding, a
 * reordered await, an eagerly forced inner block) a compile-only check would
 * miss.
 */
object AsyncScala2LivenessSpec extends ZIOSpecDefault {

  final case class Pair2(a: Int, b: String)

  def spec = suite("AsyncScala2LivenessSpec")(
    test("val (a, b) = pair.await then both used after a later await") {
      val r = Async.async {
        val (a, b) = Async.succeed((1, "x")).await
        val c      = Async.succeed(100).await
        s"$a:$b:$c"
      }
      assertTrue(r.block == "1:x:100")
    },
    test("destructured tuple components survive across two await boundaries") {
      val r = Async.async {
        val (a, b) = Async.succeed((10, 20)).await
        val mid    = Async.succeed(1).await
        val (c, d) = Async.succeed((30, 40)).await
        val last   = Async.succeed(1000).await
        a + b + c + d + mid + last
      }
      assertTrue(r.block == 1101)
    },
    test("an await in each argument of a constructor evaluates left-to-right") {
      val order = scala.collection.mutable.ListBuffer.empty[String]
      val r = Async.async {
        val p = new Pair2(
          Async.succeed { order += "a"; 7 }.await,
          Async.succeed { order += "b"; "hi" }.await
        )
        s"${p.a}:${p.b}:${order.mkString(",")}"
      }
      assertTrue(r.block == "7:hi:a,b")
    },
    test("a nested Async.async block awaited inside an outer one runs independently") {
      val r = Async.async {
        val inner = Async.async {
          val x = Async.succeed(5).await
          x * 2
        }
        val outer = Async.succeed(3).await
        inner.await + outer
      }
      assertTrue(r.block == 13)
    },
    test("destructuring an awaited 3-tuple, components used out of declaration order") {
      val r = Async.async {
        val (a, b, c) = Async.succeed((1, 2, 3)).await
        val k         = Async.succeed(10).await
        c * 100 + b * 10 + a + k
      }
      assertTrue(r.block == 321 + 10)
    }
  )
}
