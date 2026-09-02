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
 * ADVERSARIAL PROBE (Category AA / I — Scala 2 macro var-boxing interactions).
 *
 * The macro boxes every block-local `var` into a `*Ref` cell so generated
 * `flatMap` closures capture only immutable vals. We attack the corners that
 * the existing while-loop tests do NOT cover: a NON-awaiting loop (`do/while`,
 * `while`) whose body mutates a boxed `var`, sitting alongside an `.await`
 * elsewhere in the block (so the block as a whole is rewritten), and a boxed
 * `var` mutated repeatedly ACROSS await boundaries. All of these are legitimate
 * direct-style code and must evaluate exactly as the equivalent straight-line
 * Scala. We assert the full runtime value.
 */
object AsyncScala2VarLoopBoxingSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2VarLoopBoxingSpec")(
    test("a non-awaiting do/while mutating a boxed var, alongside an await") {
      val r = Async.async {
        var i = 0
        do { i += 1 } while (i < 3)
        val x = Async.succeed(10).await
        i + x
      }
      assertTrue(r.block == 13)
    },
    test("a non-awaiting while mutating a boxed var, alongside an await") {
      val r = Async.async {
        var i   = 0
        var sum = 0
        while (i < 4) { sum += i; i += 1 }
        val x = Async.succeed(100).await
        sum + x
      }
      assertTrue(r.block == 106)
    },
    test("a boxed var mutated across multiple await boundaries threads correctly") {
      val r = Async.async {
        var acc = 0
        val x   = Async.succeed(5).await
        acc = acc + x
        val y = Async.succeed(10).await
        acc = acc + y
        val z = Async.succeed(20).await
        acc = acc + z
        acc
      }
      assertTrue(r.block == 35)
    },
    test("a boxed var read inside an awaited HOF closure sees the latest value") {
      val r = Async.async {
        var base = 0
        base = Async.succeed(100).await
        List(1, 2, 3).map(n => Async.succeed(base + n).await)
      }
      assertTrue(r.block == List(101, 102, 103))
    }
  )
}
