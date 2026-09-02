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
 * ADVERSARIAL PROBE (Category AA / I — Scala 2 macro var-boxing across every
 * primitive `*Ref` family).
 *
 * BUG-046 fixed a `var` whose initializer awaits for the `IntRef` case. The
 * `boxVars` pre-pass rewrites each block-local mutable `var` into a
 * type-specific `scala.runtime.*Ref` cell — `IntRef`, `LongRef`, `DoubleRef`,
 * `FloatRef`, `ShortRef`, `CharRef`, `ByteRef`, `BooleanRef`, `ObjectRef` — and
 * each family is a SEPARATE code path. A miscompile that survives for, say,
 * `LongRef` or `CharRef` (wrong boxing/unboxing, value truncation, default
 * initializer) would not be caught by the single `Int` regression test. We
 * exercise EVERY primitive Ref family plus an `Object`/`String`/`null` carrier:
 * a `var` initialized from an await, mutated after the init, and read after a
 * LATER await — asserting the full runtime value against straight-line Scala.
 */
object AsyncScala2VarInitAwaitSpec extends ZIOSpecDefault {

  def spec = suite("AsyncScala2VarInitAwaitSpec")(
    test("var: Int initialized from await, mutated, read after a later await") {
      val r = Async.async {
        var x: Int = Async.succeed(1).await
        x = x + 10
        val y = Async.succeed(100).await
        x + y
      }
      assertTrue(r.block == 111)
    },
    test("var: Long initialized from await truncates nothing") {
      val r = Async.async {
        var x: Long = Async.succeed(9000000000L).await
        x = x + 1L
        val y = Async.succeed(2L).await
        x + y
      }
      assertTrue(r.block == 9000000003L)
    },
    test("var: Double initialized from await preserves precision") {
      val r = Async.async {
        var x: Double = Async.succeed(1.5).await
        x = x * 2.0
        val y = Async.succeed(0.25).await
        x + y
      }
      assertTrue(r.block == 3.25)
    },
    test("var: Float initialized from await") {
      val r = Async.async {
        var x: Float = Async.succeed(1.5f).await
        x = x + 0.5f
        val y = Async.succeed(1.0f).await
        x + y
      }
      assertTrue(r.block == 3.0f)
    },
    test("var: Short initialized from await") {
      val r = Async.async {
        var x: Short = Async.succeed(5.toShort).await
        x = (x + 2).toShort
        val y = Async.succeed(3.toShort).await
        (x + y)
      }
      assertTrue(r.block == 10)
    },
    test("var: Char initialized from await") {
      val r = Async.async {
        var x: Char = Async.succeed('a').await
        x = (x + 1).toChar
        val y = Async.succeed('z').await
        s"$x$y"
      }
      assertTrue(r.block == "bz")
    },
    test("var: Byte initialized from await") {
      val r = Async.async {
        var x: Byte = Async.succeed(3.toByte).await
        x = (x + 4).toByte
        val y = Async.succeed(1.toByte).await
        (x + y)
      }
      assertTrue(r.block == 8)
    },
    test("var: Boolean initialized from await, flipped after a later await") {
      val r = Async.async {
        var x: Boolean = Async.succeed(true).await
        x = !x
        val y = Async.succeed(7).await
        if (x) y else -y
      }
      assertTrue(r.block == -7)
    },
    test("var: String (ObjectRef) initialized from await, appended across awaits") {
      val r = Async.async {
        var s: String = Async.succeed("a").await
        s = s + "b"
        val y = Async.succeed("c").await
        s + y
      }
      assertTrue(r.block == "abc")
    },
    test("var: nullable Object initialized from await(null), reassigned after a later await") {
      val r = Async.async {
        var s: String = Async.succeed[String](null).await
        val first     = s == null
        s = Async.succeed("now").await
        s + ":" + first
      }
      assertTrue(r.block == "now:true")
    },
    test("two awaiting-init vars in one block thread independently") {
      val r = Async.async {
        var a: Int  = Async.succeed(1).await
        var b: Long = Async.succeed(2L).await
        a = a + 5
        b = b + 10L
        val c = Async.succeed(100).await
        a + b.toInt + c
      }
      assertTrue(r.block == 118)
    },
    test("an awaiting-init var captured by a later HOF closure reads its latest value") {
      val r = Async.async {
        var base: Int = Async.succeed(10).await
        base = base + 5
        List(1, 2, 3).map(n => Async.succeed(base + n).await)
      }
      assertTrue(r.block == List(16, 17, 18))
    },
    test("an awaiting-init var reassigned from a later await inside a loop") {
      val r = Async.async {
        var acc: Int = Async.succeed(0).await
        var i        = 0
        while (i < 3) {
          acc = acc + Async.succeed(i + 1).await
          i += 1
        }
        acc
      }
      assertTrue(r.block == 6)
    }
  )
}
