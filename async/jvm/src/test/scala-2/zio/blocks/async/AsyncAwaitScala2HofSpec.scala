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
 * Scala-2-only: `internal.AsyncMacros` HOF-await capabilities that exceed what
 * dotty-cps-async supports on Scala 3, and so cannot live in the cross-version
 * `AsyncAwaitBlockSpec`.
 *
 * Specifically, the Scala 2 macro materializes for-comprehension guards
 * (`xs.withFilter(g)`) into a strict `xs.filter(g)` before the HOF rewrite,
 * which means *multiple* chained guards (`if ... if ...`, i.e.
 * `withFilter(a).withFilter(b)`) just work. dotty-cps-async has no
 * `AsyncShift[WithFilter]` for a nested `withFilter`, so the same source is a
 * compile error on Scala 3. The two backends therefore diverge here by design
 * (Scala 2 is a strict superset); single guards remain identical across both
 * and are covered cross-version.
 *
 * `Option` for-comprehension guards are an additional Scala-2-only superset:
 * dotty-cps-async has no `AsyncShift[Option#WithFilter]` at all (so even a
 * *single* guard over an `Option` is a compile error on Scala 3), whereas the
 * Scala 2 macro materializes it to `Option.filter` and rewrites cleanly.
 */
object AsyncAwaitScala2HofSpec extends ZIOSpecDefault {

  def spec = suite("AsyncAwaitScala2HofSpec")(
    test("multiple guards compose (chained withFilter)") {
      val r = Async.async {
        for {
          i <- List(1, 2, 3, 4, 5, 6)
          if i % 2 == 0
          if i > 2
        } yield Async.succeed(i).await
      }.block
      assertTrue(r == List(4, 6))
    },
    test("multiple guards with a multi-generator and await") {
      val r = Async.async {
        for {
          i <- List(1, 2, 3, 4)
          if i % 2 == 0
          if i < 4
          j <- List(10, 20)
        } yield Async.succeed(i + j).await
      }.block
      assertTrue(r == List(12, 22))
    },
    test("Option for-comprehension with a single guard (kept) — Scala 2 only") {
      val r = Async.async {
        for {
          i <- Option(4)
          if i % 2 == 0
        } yield Async.succeed(i * 10).await
      }.block
      assertTrue(r == Some(40))
    },
    test("Option for-comprehension with a guard that fails (filtered to None) — Scala 2 only") {
      val r = Async.async {
        for {
          i <- Option(3)
          if i % 2 == 0
        } yield Async.succeed(i * 10).await
      }.block
      assertTrue(r == None)
    },
    // `Map.filter` / `Map.filterNot` with `.await` in the predicate is a
    // Scala-2-only superset: dotty-cps-async has no working
    // `MapOpsAsyncShift.filter` and crashes the macro on Scala 3, whereas the
    // Scala 2 macro drains the map's entries and rebuilds via `mapFactory`.
    // (`List`/`Vector`/`Set`/`Option` `filter` work on all six cells and are
    // covered cross-version/cross-platform.)
    test("Map.filter over entries preserves the Map — Scala 2 only") {
      val r = Async.async {
        Map(1 -> 10, 2 -> 20, 3 -> 30).filter { case (_, v) => Async.succeed(v > 10).await }
      }.block
      assertTrue(r == Map(2 -> 20, 3 -> 30))
    },
    test("Map.filterNot over entries preserves the Map — Scala 2 only") {
      val r = Async.async {
        Map(1 -> 10, 2 -> 20, 3 -> 30).filterNot { case (_, v) => Async.succeed(v > 10).await }
      }.block
      assertTrue(r == Map(1 -> 10))
    },
    // The Scala 2 macro matches `foldLeft` syntactically by method name, so it
    // validates the receiver kind in the typed pass and rejects an awaiting
    // `foldLeft` over a non-whitelisted receiver (here `Iterator`, a one-shot
    // collection) rather than silently rewriting it into an `.iterator` drain.
    // The macro aborts during the typer (unlike the `@compileTimeOnly` `.await`
    // marker), so `typeCheck` observes the failure.
    test("foldLeft over a non-whitelisted receiver (Iterator) is rejected — Scala 2 only") {
      typeCheck {
        """
        import zio.blocks.async._
        Async.async {
          Iterator(1, 2, 3).foldLeft(0)((acc, x) => acc + Async.succeed(x).await)
        }
        """
      }.map(r => assertTrue(r.isLeft))
    },
    // `takeWhile` / `dropWhile` are prefix-ordered, so the typed pass restricts
    // them to ordered `Seq` receivers (`List` / `Vector`). An awaiting
    // `takeWhile` over an unordered `Set` (a whitelisted receiver for other
    // HOFs) is rejected — a leading-prefix predicate is ill-defined there.
    test("takeWhile over an unordered Set is rejected — Scala 2 only") {
      typeCheck {
        """
        import zio.blocks.async._
        Async.async {
          Set(1, 2, 3).takeWhile(i => Async.succeed(i < 3).await)
        }
        """
      }.map(r => assertTrue(r.isLeft))
    },
    test("dropWhile over an unordered Map is rejected — Scala 2 only") {
      typeCheck {
        """
        import zio.blocks.async._
        Async.async {
          Map(1 -> 10, 2 -> 20).dropWhile { case (_, v) => Async.succeed(v < 20).await }
        }
        """
      }.map(r => assertTrue(r.isLeft))
    },
    // `reduce` is matched syntactically by method name and validated in the
    // typed pass (it drains via `.iterator`), so an awaiting `reduce` over a
    // non-whitelisted receiver (here `Iterator`, one-shot) is rejected.
    test("reduce over a non-whitelisted receiver (Iterator) is rejected — Scala 2 only") {
      typeCheck {
        """
        import zio.blocks.async._
        Async.async {
          Iterator(1, 2, 3).reduce((acc, x) => acc + Async.succeed(x).await)
        }
        """
      }.map(r => assertTrue(r.isLeft))
    },
    // `foldRight` is matched syntactically by method name and validated in the
    // typed pass (it materializes via `.toVector`), so an awaiting `foldRight`
    // over a non-whitelisted receiver (here `Iterator`, one-shot) is rejected.
    test("foldRight over a non-whitelisted receiver (Iterator) is rejected — Scala 2 only") {
      typeCheck {
        """
        import zio.blocks.async._
        Async.async {
          Iterator(1, 2, 3).foldRight(0)((x, acc) => x + Async.succeed(acc).await)
        }
        """
      }.map(r => assertTrue(r.isLeft))
    }
  )
}
