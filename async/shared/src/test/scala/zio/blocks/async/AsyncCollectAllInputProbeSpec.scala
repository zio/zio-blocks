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
import zio.test.Assertion._

/**
 * ADVERSARIAL PROBE (Category 1 — over-tight input constraint, Possible /
 * contract proposal).
 *
 * `Async.collectAll[A](as: Iterable[Async[A]]): Async[List[A]]` only ever
 * consumes its argument through `as.iterator` (see `Async.collectAll` ->
 * `drainCollectAll`). It therefore needs only `IterableOnce[Async[A]]`. By
 * requiring the tighter `Iterable`, it rejects legitimate single-pass sources
 * such as `Iterator` (and `View`) that the operation's sequential-drain
 * semantics fully support.
 *
 * Oracle: implementation only uses `.iterator` => `IterableOnce` suffices;
 * requiring `Iterable` is strictly tighter than necessary.
 *
 * The FIRST test below SHOULD compile (an `Iterator` is a valid sequential
 * source) but currently does NOT — it asserts `isRight` and therefore FAILS on
 * the current code, documenting the over-tight constraint. The second is a
 * convergence probe: an `Iterable` source (`LazyList`) is accepted, confirming
 * the only blocker is the `Iterable` (vs `IterableOnce`) bound.
 *
 * Fix direction: widen the parameter to `IterableOnce[Async[A]]` (the body is
 * unchanged; `IterableOnce` already provides `.iterator`).
 */
object AsyncCollectAllInputProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncCollectAllInputProbeSpec")(
    test("collectAll should accept a single-pass Iterator source (only .iterator is used)") {
      typeCheck("""
        import zio.blocks.async._
        val it: Iterator[Async[Int]] = Iterator(Async.succeed(1), Async.succeed(2))
        val r: Async[List[Int]] = Async.collectAll(it)
        r
      """).map(r => assert(r)(isRight))
    },
    test("CONVERGENCE: collectAll accepts an Iterable source (LazyList)") {
      typeCheck("""
        import zio.blocks.async._
        val ll: LazyList[Async[Int]] = LazyList(Async.succeed(1))
        val r: Async[List[Int]] = Async.collectAll(ll)
        r
      """).map(r => assert(r)(isRight))
    }
  )
}
