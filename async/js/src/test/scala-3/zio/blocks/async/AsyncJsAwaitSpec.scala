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
import zio.durationInt
import zio.test._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext
import scala.scalajs.js

/**
 * Scala 3 / Scala.js direct-style `Async.async { ... .await ... }` semantics.
 *
 * JavaScript cannot block, so unlike the JVM `AsyncAwaitBlockSpec` (which
 * drives the result with `.block`), these assertions drive the resulting
 * `Async` through a `Future` and run as effectful ZIO tests. This single spec
 * covers both JS Scala 3 cells:
 *   - Scala 3.3.7 → dotty-cps-async `flatMap`-chain rewrite;
 *   - Scala 3.8+ → native `js.async` / `js.await`.
 *
 * The same source compiling and passing on both cells is what keeps the JS
 * direct-style API honest across the DCA and js-native implementations.
 */
object AsyncJsAwaitSpec extends ZIOSpecDefault {

  private val Boom: Throwable = new RuntimeException("boom")

  /**
   * A user EXTENSION named `await` on `Async` itself: invoked in explicit
   * application form (`userAwaitOps.await(fa)`) it elaborates to the same tree
   * shape as our rewritten extension, differing only by symbol owner — a shape-
   * or name-based matcher would hijack it.
   */
  private object userAwaitOps {
    var hits: Int                            = 0
    extension [A](fa: Async[A]) def await: A = { hits += 1; fa.block }
  }

  private def run[A](fa: Async[A]): Future[A] =
    AsyncInterop.toFuture(fa)(JSExecutionContext.queue)

  def spec = suite("AsyncJsAwaitSpec")(
    test("`.await` of a succeeded Async returns the value") {
      ZIO.fromFuture(_ => run(Async.async(Async.succeed(7).await + 1))).map(r => assertTrue(r == 8))
    },
    test("`.await` of a succeeded pollable-as-value returns the pollable identity") {
      val inner: Pollable[Int] = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
      }
      val prog = Async.async(Async.succeed(inner).await)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.asInstanceOf[AnyRef] eq inner.asInstanceOf[AnyRef]))
    },
    test("sequential awaits compose in order") {
      val prog = Async.async {
        val a = Async.succeed(2).await
        val b = Async.succeed(3).await
        a * b
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("if/else with await in branches") {
      val prog = Async.async(if (true) Async.succeed(1).await else 0)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 1))
    },
    test("while loop with await in body") {
      val prog = Async.async {
        var i   = 0
        var sum = 0
        while (i < 5) {
          sum += Async.succeed(i + 1).await
          i += 1
        }
        sum
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 15))
    },
    test("try/catch over a failed await recovers") {
      val prog = Async.async {
        try Async.fail(Boom).await
        catch { case _: Throwable => 42 }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42))
    },
    test("a failed await propagates as a failed Future") {
      val prog = Async.async(Async.fail(Boom).await)
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("a body that throws propagates the throwable") {
      val prog = Async.async[Int]((throw Boom): Int)
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("List.map with ready awaits in the closure") {
      val prog = Async.async(List(1, 2, 3).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 20, 30)))
    },
    test("List.map closure applies eagerly, then awaits sequence fail-fast") {
      // Strict `List.map` applies the closure to every element (so `seen`
      // observes 1,2,3), producing `List[Async[B]]`; the awaits are then
      // sequenced left-to-right and the i==2 failure short-circuits the result.
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).map { i =>
          seen = i :: seen
          if (i == 2) Async.fail(Boom).await else Async.succeed(i).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(3, 2, 1)))
    },
    test("List.foreach runs ready awaits in order") {
      var acc  = 0
      val prog = Async.async {
        List(1, 2, 3).foreach(i => acc += Async.succeed(i).await)
        acc
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("List.foreach is lazy: a failing await short-circuits the remaining elements") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).foreach { i =>
          seen = i :: seen
          if (i == 2) Async.fail(Boom).await else { val _ = Async.succeed(i).await; () }
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("List.flatMap accumulates ready awaits in order") {
      val prog = Async.async {
        List(1, 2, 3).flatMap { i =>
          val x = Async.succeed(i).await
          List(x, x * 10)
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 10, 2, 20, 3, 30)))
    },
    test("multi-generator for-comprehension desugars to nested flatMap/map") {
      val prog = Async.async {
        for {
          i <- List(1, 2)
          j <- List(10, 20)
        } yield Async.succeed(i + j).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(11, 21, 12, 22)))
    },
    test("for-comprehension guard (withFilter) is honored before the await") {
      val prog = Async.async {
        for {
          i <- List(1, 2, 3, 4)
          if i % 2 == 0
        } yield Async.succeed(i * 10).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(20, 40)))
    },
    test("Option.map over a Some runs the closure and rewraps") {
      val prog = Async.async(Option(5).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(50)))
    },
    test("Option.map over a None short-circuits") {
      val prog = Async.async((None: Option[Int]).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
    },
    test("Option.map propagates a failing await") {
      val prog = Async.async(Option(5).map(_ => Async.fail(Boom).await: Int))
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("Option.flatMap over a Some accumulates the inner Option") {
      val prog = Async.async(Option(5).flatMap(i => Option(Async.succeed(i * 10).await)))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(50)))
    },
    test("Option.foreach runs the await for its effect over a Some") {
      val prog = Async.async {
        var acc = 0
        Option(5).foreach(i => acc += Async.succeed(i).await)
        acc
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 5))
    },
    test("multi-generator for-comprehension over Options") {
      val prog = Async.async {
        for {
          i <- Option(2)
          j <- Option(3)
        } yield Async.succeed(i + j).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(5)))
    },
    test("for-comprehension over Options short-circuits on a None generator") {
      val prog = Async.async {
        for {
          i <- Option(2)
          j <- (None: Option[Int])
        } yield Async.succeed(i + j).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
    },
    test("Vector.map preserves order and result type") {
      val prog = Async.async(Vector(1, 2, 3).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(10, 20, 30)))
    },
    test("Vector.map is lazy: a failing await short-circuits the rest") {
      var seen = List.empty[Int]
      val prog = Async.async {
        Vector(1, 2, 3).map { i =>
          seen = i :: seen
          if (i == 2) Async.fail(Boom).await else Async.succeed(i).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("Vector.flatMap accumulates, preserving Vector") {
      val prog = Async.async(Vector(1, 2, 3).flatMap(i => Vector(Async.succeed(i).await, i * 10)))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 10, 2, 20, 3, 30)))
    },
    test("for-comprehension over Vectors") {
      val prog = Async.async {
        for {
          i <- Vector(1, 2)
          j <- Vector(10, 20)
        } yield Async.succeed(i + j).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(11, 21, 12, 22)))
    },
    test("Set.map deduplicates awaited results") {
      val prog = Async.async(Set(1, 2, 3, 4).map(i => Async.succeed(i % 2).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(0, 1)))
    },
    test("Set.flatMap accumulates into a Set") {
      val prog = Async.async(Set(1, 2).flatMap(i => Set(Async.succeed(i).await, i + 10)))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(1, 11, 2, 12)))
    },
    test("Map.map over a pair-returning closure awaits values, preserving Map") {
      val prog = Async.async {
        Map(1 -> 1, 2 -> 2, 3 -> 3).map { case (k, v) => (k, Async.succeed(v * 10).await) }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Map(1 -> 10, 2 -> 20, 3 -> 30)))
    },
    test("Map.flatMap accumulates into a Map") {
      val prog = Async.async {
        Map(1 -> 1, 2 -> 2).flatMap { case (k, v) => Map(k -> Async.succeed(v).await, (k + 10) -> v) }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Map(1 -> 1, 11 -> 1, 2 -> 2, 12 -> 2)))
    },
    test("Map.map propagates a failing await") {
      val prog = Async.async(Map(1 -> 1).map { case (k, v) => (k, Async.fail(Boom).await) })
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("Map.map over a non-pair closure widens to an Iterable") {
      val prog = Async.async {
        Map(1 -> 10, 2 -> 20, 3 -> 30).map { case (k, v) => Async.succeed(k + v).await }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toSet == Set(11, 22, 33)))
    },
    test("List.exists short-circuits on the first match") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3, 4).exists { i => seen = i :: seen; Async.succeed(i == 2).await }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r, seen == List(2, 1)))
    },
    test("List.forall short-circuits on the first false") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3, 4).forall { i => seen = i :: seen; Async.succeed(i < 3).await }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(!r, seen == List(3, 2, 1)))
    },
    test("List.find returns the first match") {
      val prog = Async.async(List(1, 2, 3, 4).find(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(2)))
    },
    test("exists propagates a failing await") {
      val prog = Async.async(List(1, 2, 3).exists(i => Async.fail(Boom).await))
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("Vector.exists over a generic receiver") {
      val prog = Async.async(Vector(1, 2, 3).exists(i => Async.succeed(i == 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("Option.exists over a Some") {
      val prog = Async.async(Option(4).exists(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("Map.find over entries returns the matching pair") {
      val prog = Async.async(Map(1 -> 10, 2 -> 20).find { case (_, v) => Async.succeed(v == 20).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some((2, 20))))
    },
    test("Option.find returns the value when the predicate matches") {
      val prog = Async.async(Option(4).find(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(4)))
    },
    test("Option.find returns None when the predicate fails") {
      val prog = Async.async(Option(3).find(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
    },
    test("foldLeft threads the accumulator over awaits") {
      val prog = Async.async(List(1, 2, 3, 4).foldLeft(0)((acc, x) => acc + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
    },
    test("foldLeft supports a result type that differs from the element type") {
      val prog = Async.async(List(1, 2, 3).foldLeft(List.empty[Int])((acc, x) => Async.succeed(x * 10).await :: acc))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(30, 20, 10)))
    },
    test("foldLeft is lazy: a failing await short-circuits the remaining elements") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).foldLeft(0) { (acc, x) =>
          seen = x :: seen
          if (x == 2) acc + (Async.fail(Boom).await: Int) else acc + Async.succeed(x).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("foldLeft over a Vector receiver") {
      val prog = Async.async(Vector(1, 2, 3).foldLeft(0)((acc, x) => acc + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("List.filter keeps matching elements") {
      val prog = Async.async(List(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(2, 4)))
    },
    test("List.filterNot keeps non-matching elements") {
      val prog = Async.async(List(1, 2, 3, 4).filterNot(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 3)))
    },
    test("filter is lazy: a failing await short-circuits the remaining elements") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).filter { i =>
          seen = i :: seen
          if (i == 2) (Async.fail(Boom).await: Boolean) else Async.succeed(i % 2 == 1).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("Vector.filter preserves the Vector type") {
      val prog = Async.async(Vector(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(2, 4)))
    },
    test("Set.filter preserves the Set type") {
      val prog = Async.async(Set(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(2, 4)))
    },
    test("Option.filter over a Some that matches keeps it") {
      val prog = Async.async(Option(4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(4)))
    },
    test("Option.filter over a Some that fails the predicate yields None") {
      val prog = Async.async(Option(3).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
    },
    test("List.takeWhile keeps the leading run, stopping at the first failure") {
      val prog = Async.async(List(1, 2, 3, 4, 1).takeWhile(i => Async.succeed(i < 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 2)))
    },
    test("List.dropWhile drops the leading run, keeping the rest unconditionally") {
      val prog = Async.async(List(1, 2, 3, 4, 1).dropWhile(i => Async.succeed(i < 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(3, 4, 1)))
    },
    test("takeWhile is lazy: it stops evaluating the predicate after the first failure") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3, 4).takeWhile { i =>
          seen = i :: seen
          Async.succeed(i < 3).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 2), seen == List(3, 2, 1)))
    },
    test("takeWhile is lazy: a failing await short-circuits the remaining elements") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).takeWhile { i =>
          seen = i :: seen
          if (i == 2) (Async.fail(Boom).await: Boolean) else Async.succeed(i < 3).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("Vector.takeWhile preserves the Vector type") {
      val prog = Async.async(Vector(1, 2, 3, 4).takeWhile(i => Async.succeed(i < 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 2)))
    },
    test("Vector.dropWhile preserves the Vector type") {
      val prog = Async.async(Vector(1, 2, 3, 4).dropWhile(i => Async.succeed(i < 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(3, 4)))
    },
    test("List.reduce folds left-to-right over ready awaits") {
      val prog = Async.async(List(1, 2, 3, 4).reduce((acc, x) => acc + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 10))
    },
    test("List.reduceLeft behaves identically to reduce") {
      val prog = Async.async(List(2, 3, 4).reduceLeft((acc, x) => acc * Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 24))
    },
    test("Vector.reduce folds over a Vector receiver") {
      val prog = Async.async(Vector(1, 2, 3).reduce((acc, x) => acc + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("reduce over an empty receiver fails with UnsupportedOperationException") {
      val prog = Async.async(List.empty[Int].reduce((acc, x) => acc + Async.succeed(x).await))
      ZIO
        .fromFuture(_ => run(prog))
        .either
        .map(e => assertTrue(e.left.exists(_.isInstanceOf[UnsupportedOperationException])))
    },
    test("reduce: a failing await short-circuits the remaining elements") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3, 4).reduce { (acc, x) =>
          seen = x :: seen
          if (x == 3) (Async.fail(Boom).await: Int) else acc + Async.succeed(x).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(3, 2)))
    },
    test("List.foldRight is right-associative over ready awaits") {
      val prog = Async.async(List(1, 2, 3).foldRight("z")((x, acc) => s"($x+${Async.succeed(acc).await})"))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == "(1+(2+(3+z)))"))
    },
    test("foldRight runs the op right-to-left") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).foldRight(0) { (x, acc) =>
          seen = x :: seen
          x + Async.succeed(acc).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6, seen == List(1, 2, 3)))
    },
    test("foldRight supports a result type that differs from the element type") {
      val prog = Async.async(List(1, 2, 3).foldRight(List.empty[Int])((x, acc) => Async.succeed(x).await :: acc))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(1, 2, 3)))
    },
    test("Vector.foldRight folds over a Vector receiver") {
      val prog = Async.async(Vector(1, 2, 3).foldRight(0)((x, acc) => x + Async.succeed(acc).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("foldRight: a failing await short-circuits the remaining elements (right-to-left)") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3, 4).foldRight(0) { (x, acc) =>
          seen = x :: seen
          if (x == 2) (Async.fail(Boom).await: Int) else x + Async.succeed(acc).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 3, 4)))
    },
    test("List.collect keeps matching elements, mapping them through an awaited body") {
      val prog = Async.async(List(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i * 10).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List(10, 30)))
    },
    test("collect tries multiple cases in order") {
      val prog = Async.async {
        List(1, 2, 3, 4, 5).collect {
          case i if i % 2 == 0 => Async.succeed(s"even$i").await
          case i if i == 5 => Async.succeed("five").await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == List("even2", "even4", "five")))
    },
    test("Vector.collect preserves the Vector type") {
      val prog = Async.async(Vector(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Vector(1, 3)))
    },
    test("Set.collect preserves the Set type") {
      val prog = Async.async(Set(1, 2, 3, 4).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Set(20, 40)))
    },
    test("collect: a failing await in a matched body short-circuits the rest") {
      var seen = List.empty[Int]
      val prog = Async.async {
        List(1, 2, 3).collect { case i =>
          seen = i :: seen
          if (i == 2) (Async.fail(Boom).await: Int) else Async.succeed(i).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom), seen == List(2, 1)))
    },
    test("Option.collect keeps a matching Some") {
      val prog = Async.async(Option(2).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Some(20)))
    },
    test("Option.collect returns None when the Some does not match") {
      val prog = Async.async(Option(3).collect { case i if i % 2 == 0 => Async.succeed(i * 10).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == None))
    },
    test("Map.collect with a non-pair body widens to an Iterable") {
      val prog = Async.async(Map(1 -> 10, 2 -> 20).collect { case (k, v) => Async.succeed(k + v).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toSet == Set(11, 22)))
    },
    // Strict immutable `Seq` receivers (`Queue` / `ArraySeq`) also support
    // `.await` in their HOF closures with the collection family preserved.
    test("Queue.map preserves the Queue type") {
      val prog = Async.async(scala.collection.immutable.Queue(1, 2, 3).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(10, 20, 30)))
    },
    test("Queue.filter preserves the Queue type") {
      val prog = Async.async(scala.collection.immutable.Queue(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(2, 4)))
    },
    test("Queue.foldLeft folds over a Queue receiver") {
      val prog =
        Async.async(scala.collection.immutable.Queue(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("Queue.collect preserves the Queue type") {
      val prog = Async.async {
        scala.collection.immutable.Queue(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.Queue(1, 3)))
    },
    test("ArraySeq.map preserves the ArraySeq type") {
      val prog = Async.async(scala.collection.immutable.ArraySeq(1, 2, 3).map(i => Async.succeed(i * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(10, 20, 30)))
    },
    test("ArraySeq.filter preserves the ArraySeq type") {
      val prog =
        Async.async(scala.collection.immutable.ArraySeq(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(2, 4)))
    },
    test("ArraySeq.foldLeft folds over an ArraySeq receiver") {
      val prog =
        Async.async(scala.collection.immutable.ArraySeq(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("ArraySeq.collect preserves the ArraySeq type") {
      val prog = Async.async {
        scala.collection.immutable.ArraySeq(1, 2, 3, 4).collect {
          case i if i % 2 == 1 => Async.succeed(i * 10).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == scala.collection.immutable.ArraySeq(10, 30)))
    },
    // `Array`: `map` eager, `flatMap` lazy / sequential, result always `Array[B]`.
    test("Array.map preserves the (primitive) element type") {
      val prog = Async.async(Array(1, 2, 3).map(i => Async.succeed(i.toLong * 10).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(10L, 20L, 30L)))
    },
    test("Array.flatMap concatenates and preserves the Array type") {
      val prog = Async.async(Array(1, 2, 3).flatMap(i => Array(Async.succeed(i).await, i * 10)))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 10, 2, 20, 3, 30)))
    },
    test("Array.foldLeft folds over an Array receiver") {
      val prog = Async.async(Array(1, 2, 3).foldLeft(0)((a, x) => a + Async.succeed(x).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6))
    },
    test("Array.filter preserves the Array type") {
      val prog = Async.async(Array(1, 2, 3, 4).filter(i => Async.succeed(i % 2 == 0).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(2, 4)))
    },
    test("Array.takeWhile preserves the Array type") {
      val prog = Async.async(Array(1, 2, 3, 1).takeWhile(i => Async.succeed(i < 3).await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 2)))
    },
    test("Array.collect preserves the Array type") {
      val prog = Async.async(Array(1, 2, 3, 4).collect { case i if i % 2 == 1 => Async.succeed(i).await })
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r.toList == List(1, 3)))
    },
    // Backend-agreement semantics: readiness, delivered-value identity, error
    // identity, and execution order must agree between the DCA rewrite
    // (Scala 3 < 3.8) and the native `js.async`/`js.await` backend (3.8+) —
    // this spec compiles and runs on both cells, which is what keeps them honest.
    test("an async block whose awaits are all ready is itself ready (`.block` returns the value)") {
      // Every other cell (JVM, JS DCA, Scala 2) rewrites ready awaits into a
      // ready flatMap chain; the value must be observable synchronously.
      val prog = Async.async(Async.succeed(7).await + 1)
      val r    = scala.util.Try(prog.block)
      ZIO.succeed(assertTrue(r == scala.util.Success(8)))
    },
    test("an async block whose await is an already-failed Async is a ready failure (`.block` throws the cause)") {
      // Docs: `Async.async { Async.fail(t).await }` is equivalent to
      // `Async.fail(t)` — a READY failure on every cell (JVM, JS DCA, Scala 2
      // all short-circuit the flatMap chain synchronously), so `.block` must
      // throw the original cause rather than observe a pending value.
      val prog = Async.async(Async.fail(Boom).await)
      val r    = scala.util.Try(prog.block)
      ZIO.succeed(assertTrue(r == scala.util.Failure(Boom)))
    },
    test("a try/catch that recovers an already-failed await is itself ready") {
      // Same readiness contract as above, observed through the recovery arm:
      // nothing in this block can suspend, so the result must be ready.
      val prog = Async.async {
        try Async.fail(Boom).await
        catch { case _: Throwable => 42 }
      }
      val r = scala.util.Try(prog.block)
      ZIO.succeed(assertTrue(r == scala.util.Success(42)))
    },
    test("try/finally over a ready-failed await runs the finally and stays a ready failure") {
      // The synchronous ready-failure channel must still honor the finally
      // block before the failure escapes — and nothing here can suspend, so
      // the result must be a READY failure with the original cause.
      var fin                = 0
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async[Int] {
        try failed.await
        finally fin += 1
      }
      val r = scala.util.Try(prog.block)
      ZIO.succeed(assertTrue(r == scala.util.Failure(Boom), fin == 1))
    },
    test("a ready-failed first await short-circuits the rest of the body without losing readiness") {
      // The statements after the failing await — including a genuinely pending
      // await — must never run; the block settles as a ready failure.
      var ran                 = false
      def failed: Async[Int]  = Async.fail(Boom)
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async[Int] {
        val x = failed.await
        ran = true
        x + pending.await
      }
      val r = scala.util.Try(prog.block)
      ZIO.succeed(assertTrue(r == scala.util.Failure(Boom), !ran))
    },
    test("a null-cause ready-failed await is a ready failure with the null cause decoded") {
      // A null cause travels the thrown channel as the marker and must be
      // decoded back to the logical null by the time the block settles —
      // `.block` exposes it through the same channel as `Async.fail(null)`.
      def failedNull: Async[Int] = Async.fail(null)
      val prog                   = Async.async[Int](failedNull.await)
      ZIO.succeed(assertTrue(AsyncTestSupport.blockAsNullCause(prog) == Left(null)))
    },
    test("a catch arm that recovers a ready failure by awaiting a pending value completes") {
      // Mixed-readiness recovery: the scrutinee fails synchronously (cause
      // identity intact at the handler) and the recovery itself suspends.
      def failed: Async[Int]  = Async.fail(Boom)
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(40)); ()
      }
      val prog = Async.async {
        try failed.await
        catch { case t: Throwable => if (t eq Boom) pending.await + 2 else -1 }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42))
    },
    test("a ready-failure block does not leave an unhandled promise rejection behind") {
      // The 3.8+ native arm reports a ready failure through the synchronous
      // channel; the rejection of the underlying js.async promise must be
      // marked handled so the JS engine raises no unhandledRejection event.
      val proc                                  = js.Dynamic.global.process
      val canObserve                            = js.typeOf(proc) != "undefined" && js.typeOf(proc.on) == "function"
      var strayRejections                       = 0
      val handler: js.Function2[Any, Any, Unit] = (_, _) => strayRejections += 1
      if (canObserve) proc.on("unhandledRejection", handler)
      def failed: Async[Int] = Async.fail(Boom)
      val ready              =
        (1 to 20).forall(_ => AsyncTestSupport.blockAsLeftCause(Async.async(failed.await + 1)) == Some(Boom))
      Live
        .live(ZIO.sleep(100.millis))
        .as {
          if (canObserve) { proc.removeListener("unhandledRejection", handler); () }
          assertTrue(ready, strayRejections == 0)
        }
    },
    test("a succeed carrier holding a Failure is delivered by the await as data, not rethrown") {
      // `Async.succeed` stores an effect value as DATA (one wrap layer); an
      // await of the carrier must deliver the bare failed Async by identity —
      // the ready-failure fast path must not fire on the carrier encoding.
      val failedValue: Async[Int] = Async.fail(Boom)
      val prog                    = Async.async {
        val v = Async.succeed(failedValue).await
        v.asInstanceOf[AnyRef] eq failedValue.asInstanceOf[AnyRef]
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("a user catch over a null-cause failed await observes the logical null cause (backend parity)") {
      // Every other cell (JVM DCA, JS DCA, the Scala 2 macro on both
      // platforms) applies the catch handler to the LOGICAL cause: a null
      // cause does not match `case t: Throwable`, so the handler must not run
      // and the failure propagates as Failure(null). The transport marker the
      // thrown channel uses internally must never be observable to a user
      // catch inside the block — on either the ready or the pending path.
      def failedNull: Async[Int] = Async.fail(null)
      val readyProg              = Async.async[Int] {
        try failedNull.await
        catch { case t: Throwable => if (t == null) -1 else -2 }
      }
      val pendingNull: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(null)); ()
      }
      val pendingProg = Async.async[Int] {
        try pendingNull.await
        catch { case t: Throwable => if (t == null) -1 else -2 }
      }
      ZIO
        .fromFuture(_ => run(pendingProg))
        .either
        .map { pendingOut =>
          assertTrue(
            AsyncTestSupport.blockAsNullCause(readyProg) == Left(null),
            AsyncTestSupport.unwindFutureEither(pendingOut) == Left(null)
          )
        }
    },
    test("a catch guard decides recovery against the awaited failure (matching recovers, non-matching propagates)") {
      // The guard must observe the logical cause: a matching guard recovers, a
      // non-matching guard falls through and the original failure propagates —
      // and nothing here can suspend, so both outcomes must stay ready.
      def failed: Async[Int] = Async.fail(Boom)
      val recovered          = Async.async {
        try failed.await
        catch { case t: Throwable if t eq Boom => 42 }
      }
      val unmatched = Async.async {
        try failed.await
        catch { case t: Throwable if t ne Boom => -1 }
      }
      ZIO.succeed(
        assertTrue(
          scala.util.Try(recovered.block) == scala.util.Success(42),
          AsyncTestSupport.blockAsLeftCause(unmatched) == Some(Boom)
        )
      )
    },
    test("a guarded catch arm recovers a pending awaited failure too") {
      val pendingFail: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(Boom)); ()
      }
      val prog = Async.async {
        try pendingFail.await
        catch { case t: Throwable if t eq Boom => 42 }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42))
    },
    test(
      "a catch arm whose class does not match the awaited failure propagates the original cause as a ready failure"
    ) {
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async {
        try failed.await
        catch { case _: IllegalStateException => -1 }
      }
      ZIO.succeed(assertTrue(AsyncTestSupport.blockAsLeftCause(prog) == Some(Boom)))
    },
    test("nested try/catch over awaits recovers at the matching level") {
      // The inner handler translates the awaited failure into a new throw; only
      // the outer catch may observe it — and the whole chain is synchronous, so
      // the block must stay ready.
      val inner              = new RuntimeException("inner")
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async {
        try {
          try failed.await
          catch { case t: Throwable if t eq Boom => throw inner }
        } catch { case t: Throwable if t eq inner => 7 }
      }
      ZIO.succeed(assertTrue(scala.util.Try(prog.block) == scala.util.Success(7)))
    },
    test("try/catch/finally over a ready-failed await recovers, runs the finalizer exactly once, and stays ready") {
      var fin                = 0
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async {
        try failed.await
        catch { case t: Throwable => if (t eq Boom) 42 else -1 }
        finally fin += 1
      }
      ZIO.succeed(assertTrue(scala.util.Try(prog.block) == scala.util.Success(42), fin == 1))
    },
    test("direct awaits surrounding a try/catch body await stay ready end-to-end") {
      // One try/catch in the block must not cost the readiness of the awaits
      // around it: everything here is synchronous, so the block is ready.
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async {
        val a = Async.succeed(1).await
        val b =
          try failed.await
          catch { case _: Throwable => 10 }
        val c = Async.succeed(100).await
        a + b + c
      }
      ZIO.succeed(assertTrue(scala.util.Try(prog.block) == scala.util.Success(111)))
    },
    test("try/finally (no catch) over a ready null-cause failed await decodes the logical null and runs the finally") {
      // The null cause travels the synchronous throw channel as the transport
      // marker, crosses the user finally, and must be decoded back to the
      // logical null by the time the block settles as a READY failure.
      var fin                    = 0
      def failedNull: Async[Int] = Async.fail(null)
      val prog                   = Async.async[Int] {
        try failedNull.await
        finally fin += 1
      }
      ZIO.succeed(assertTrue(AsyncTestSupport.outcome(prog) == Left(null), fin == 1))
    },
    test(
      "try/finally whose pending finalizer await crosses an in-flight null-cause failure still fails with the logical null"
    ) {
      // The failure is in flight when the finalizer suspends, so it must cross
      // the asynchronous channel — and still come out as the logical null.
      var fin                    = 0
      def failedNull: Async[Int] = Async.fail(null)
      val pendingFin: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async[Int] {
        try failedNull.await
        finally {
          fin = pendingFin.await
        }
      }
      ZIO
        .fromFuture(_ => run(prog))
        .either
        .map(e => assertTrue(AsyncTestSupport.unwindFutureEither(e) == Left(null), fin == 1))
    },
    test("a throwing finalizer replaces the in-flight awaited failure (plain try/finally semantics)") {
      // Scala semantics: a throw from `finally` replaces the in-flight
      // exception.
      val finBoom            = new RuntimeException("fin")
      def finThrow(): Unit   = throw finBoom
      def failed: Async[Int] = Async.fail(Boom)
      val prog               = Async.async[Int] {
        try failed.await
        finally finThrow()
      }
      ZIO.succeed(assertTrue(AsyncTestSupport.blockAsLeftCause(prog) == Some(finBoom)))
    },
    test("a throwing finalizer replaces an in-flight null-cause awaited failure with the finalizer's throw") {
      // Same replacement law when the in-flight failure carries the logical
      // null cause: the block must fail with the finalizer's throw — never an
      // internal NullPointerException from combining the two, and never the
      // transport marker.
      val finBoom                = new RuntimeException("fin")
      def finThrow(): Unit       = throw finBoom
      def failedNull: Async[Int] = Async.fail(null)
      val progNull               = Async.async[Int] {
        try failedNull.await
        finally finThrow()
      }
      ZIO.succeed(assertTrue(AsyncTestSupport.blockAsLeftCause(progNull) == Some(finBoom)))
    },
    test("awaits only in the catch handler (await-free body) recover a thrown body failure") {
      // With no await in the try body the native arm is kept; the handler's own
      // awaits must still work — suspending (pending) and ready (block stays
      // ready) alike.
      def boomNow(): Int       = throw Boom
      val pendingV: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(40)); ()
      }
      val pendingProg = Async.async {
        try boomNow()
        catch { case t: Throwable => if (t eq Boom) pendingV.await + 2 else -1 }
      }
      val readyProg = Async.async {
        try boomNow()
        catch { case t: Throwable => if (t eq Boom) Async.succeed(41).await + 1 else -1 }
      }
      ZIO
        .fromFuture(_ => run(pendingProg))
        .map(p => assertTrue(p == 42, scala.util.Try(readyProg.block) == scala.util.Success(42)))
    },
    test("a catch handler that awaits a ready-failed Async replaces the body failure with the handler's cause") {
      // A failure thrown by an await inside the HANDLER must not be re-fed to
      // the same catch — it propagates as the block's failure (and a null
      // handler cause comes out as the logical null, decoded).
      val handlerCause           = new RuntimeException("handler")
      def boomNow(): Int         = throw Boom
      def failedH: Async[Int]    = Async.fail(handlerCause)
      def failedNull: Async[Int] = Async.fail(null)
      val prog                   = Async.async {
        try boomNow()
        catch { case _: Throwable => failedH.await }
      }
      val progNull = Async.async {
        try boomNow()
        catch { case _: Throwable => failedNull.await }
      }
      ZIO.succeed(
        assertTrue(
          AsyncTestSupport.blockAsLeftCause(prog) == Some(handlerCause),
          AsyncTestSupport.outcome(progNull) == Left(null)
        )
      )
    },
    test("awaits only in the finalizer (await-free body, catch present) run after recovery") {
      var fin            = 0
      def boomNow(): Int = throw Boom
      val prog           = Async.async {
        try boomNow()
        catch { case t: Throwable => if (t eq Boom) 1 else -1 }
        finally {
          fin = Async.succeed(1).await
        }
      }
      ZIO.succeed(assertTrue(scala.util.Try(prog.block) == scala.util.Success(1), fin == 1))
    },
    test("a try/catch with an await-free body does not disturb readiness of surrounding direct awaits") {
      val prog = Async.async {
        val a = Async.succeed("4").await
        val b =
          try a.toInt
          catch { case _: NumberFormatException => -1 }
        b + Async.succeed(38).await
      }
      ZIO.succeed(assertTrue(scala.util.Try(prog.block) == scala.util.Success(42)))
    },
    test("try/catch/finally with awaits in all three regions recovers and runs the suspending finalizer") {
      var fin                    = 0
      def failed: Async[Int]     = Async.fail(Boom)
      val pendingFin: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async {
        try failed.await
        catch { case t: Throwable => if (t eq Boom) Async.succeed(40).await + 2 else -1 }
        finally {
          fin = pendingFin.await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42, fin == 1))
    },
    test("a pending Async[Unit] awaited directly compiles and completes") {
      // The boxed promise transport keeps the underlying js.Promise element
      // type non-Unit, so this shape must work on Scala.js 3.8.3 too (the raw
      // `js.await(js.Promise[Unit])` compiler limitation does not apply).
      val pending: Async[Unit] = Async.promiseInternal[Unit] { c =>
        js.timers.setTimeout(0.0)(c.succeed(())); ()
      }
      val prog = Async.async {
        pending.await
        5
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 5))
    },
    test("a pending await delivers a js.Promise success value by identity") {
      // A js.Promise held as DATA (promise-as-value) must come out of `.await`
      // as the promise itself, never replaced by its adopted/settled result.
      val inner: js.Promise[Int]          = js.Promise.resolve[Int](42)
      val pending: Async[js.Promise[Int]] = Async.promiseInternal[js.Promise[Int]] { c =>
        js.timers.setTimeout(0.0)(c.succeed(inner)); ()
      }
      val prog = Async.async {
        val p = pending.await
        (p: AnyRef) eq (inner: AnyRef)
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("the block result preserves a js.Promise value by identity") {
      // Same promise-as-value contract for the block's RESULT position.
      val inner: js.Promise[Int] = js.Promise.resolve[Int](42)
      val prog: Async[AnyRef]    = Async.async {
        val x = Async.succeed(1).await
        val _ = x
        inner: AnyRef
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r eq (inner: AnyRef)))
    },
    test("a pending await delivers a still-pending js.Promise value by identity") {
      // The promise-as-value contract must hold even when the delivered
      // js.Promise has not yet settled: transport-level thenable adoption
      // would otherwise stall on (or replace) the pending inner promise.
      val inner: js.Promise[Int] = new js.Promise[Int]((resolve, _) => {
        js.timers.setTimeout(30.0)(resolve(42)); ()
      })
      val pending: Async[js.Promise[Int]] = Async.promiseInternal[js.Promise[Int]] { c =>
        js.timers.setTimeout(0.0)(c.succeed(inner)); ()
      }
      val prog = Async.async {
        val p = pending.await
        (p: AnyRef) eq (inner: AnyRef)
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("a pending await preserves a raw null failure cause") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(null)); ()
      }
      val prog = Async.async(pending.await)
      ZIO
        .fromFuture(_ => run(prog))
        .either
        .map(e => assertTrue(AsyncTestSupport.unwindFutureEither(e) == Left(null)))
    },
    test("a body that throws after its first suspension fails with the same cause") {
      // A post-suspension throw publishes through the asynchronous channel
      // (the block is already pending); the cause identity must survive.
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async[Int] {
        val x = pending.await
        if (x == 1) throw Boom
        x
      }
      ZIO.fromFuture(_ => run(prog)).either.map(e => assertTrue(e == Left(Boom)))
    },
    test("ready, pending, and ready awaits interleave side effects in source order") {
      val order               = scala.collection.mutable.ListBuffer[String]()
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0) { order += "settle"; c.succeed(2) }; ()
      }
      val prog = Async.async {
        order += "a"
        val x = Async.succeed(1).await
        order += "b"
        val y = pending.await
        order += "c"
        val z = Async.succeed(3).await
        order += "d"
        x + y + z
      }
      // Ready awaits never suspend: everything before the first pending await
      // runs synchronously at block construction, on every backend.
      val syncPrefix = order.toList
      ZIO
        .fromFuture(_ => run(prog))
        .map(r =>
          assertTrue(
            r == 6,
            syncPrefix == List("a", "b"),
            order.toList == List("a", "b", "settle", "c", "d")
          )
        )
    },
    test("three-level nested async blocks compose") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async {
        val l2 = Async.async {
          val l3 = Async.async(pending.await + 1)
          l3.await + 10
        }
        l2.await + 100
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 112))
    },
    test("awaiting a started (Running) computation delivers its result") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(20)); ()
      }
      val running = Async.start(pending)
      val prog    = Async.async(running.await + 1)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 21))
    },
    test("a pending await delivers a raw null success value") {
      val pending: Async[String] = Async.promiseInternal[String] { c =>
        js.timers.setTimeout(0.0)(c.succeed(null)); ()
      }
      val prog = Async.async {
        val s = pending.await
        s == null
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("a pending await of a pollable-as-value delivers the bare user pollable") {
      val inner: Pollable[Int] = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
      }
      val pending: Async[Pollable[Int]] = Async.promiseInternal[Pollable[Int]] { c =>
        js.timers.setTimeout(0.0)(c.succeed(inner)); ()
      }
      val prog = Async.async {
        (pending.await: AnyRef) eq (inner: AnyRef)
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("the body runs synchronously up to the first pending await") {
      val c          = new Completer[Int]
      var sideEffect = 0
      val prog       = Async.async {
        sideEffect = 1
        c.peek.await
      }
      val syncObserved = sideEffect
      c.succeed(5)
      ZIO.fromFuture(_ => run(prog)).map(v => assertTrue(syncObserved == 1, v == 5))
    },
    test("a pending await preserves failure-cause identity") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.fail(Boom)); ()
      }
      val prog = Async.async {
        try { pending.await; "no-throw" }
        catch { case t: Throwable => if (t eq Boom) "same" else s"different: $t" }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == "same"))
    },
    test("getOrElse: an awaiting by-name default is skipped on a Some") {
      var forced = 0
      val prog   = Async.async {
        Option(1).getOrElse { forced += 1; Async.succeed(2).await }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 1, forced == 0))
    },
    test("&& short-circuits a failing right await") {
      val prog = Async.async {
        Async.succeed(false).await && (Async.fail(Boom).await: Boolean)
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == false))
    },
    test("|| short-circuits after a pending left await") {
      val pending: Async[Boolean] = Async.promiseInternal[Boolean] { c =>
        js.timers.setTimeout(0.0)(c.succeed(true)); ()
      }
      val prog = Async.async {
        pending.await || (Async.fail(Boom).await: Boolean)
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r))
    },
    test("try/finally: a pending awaited body still runs an awaiting finally block") {
      var fin                 = 0
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(7)); ()
      }
      val prog = Async.async {
        try pending.await
        finally {
          fin = Async.succeed(1).await
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 7, fin == 1))
    },
    test("string interpolation over a pending await") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(7)); ()
      }
      val prog = Async.async(s"v=${pending.await}")
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == "v=7"))
    },
    test("out-of-order named arguments around an await") {
      def g(a: Int, b: Int): Int = a * 10 + b
      val prog                   = Async.async(g(b = Async.succeed(2).await, a = 1))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 12))
    },
    test("constructor argument awaits a pending value") {
      final case class Box(v: Int)
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(9)); ()
      }
      val prog = Async.async(Box(pending.await))
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == Box(9)))
    },
    test("match with pending awaits in scrutinee and case body") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(2)); ()
      }
      val prog = Async.async {
        pending.await match {
          case 2 => Async.succeed("two").await
          case _ => "other"
        }
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == "two"))
    },
    test("while loop driven by pending awaits") {
      var calls              = 0
      def step(): Async[Int] = {
        calls += 1
        Async.promiseInternal[Int] { c =>
          js.timers.setTimeout(0.0)(c.succeed(calls)); ()
        }
      }
      val prog = Async.async {
        var sum = 0
        while (sum < 6) sum += step().await
        sum
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 6, calls == 3))
    },
    test("a while loop whose body recovers a failed await via try/catch iterates to completion") {
      // Composes the iterative while rewrite with the try/catch emulation: the
      // recovery must be local to each iteration and the loop must keep going.
      val prog = Async.async {
        var i   = 0
        var sum = 0
        while (i < 3) {
          sum += (try {
            if (i == 1) Async.fail(Boom).await else Async.succeed(10).await
          } catch { case t: Throwable => if (t eq Boom) 5 else -100 })
          i += 1
        }
        sum
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 25))
    },
    test("an await nested in the qualifier of another await is rewritten inside-out") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(1)); ()
      }
      val prog = Async.async(Async.succeed(pending.await + 1).await)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 2))
    },
    test("awaits in varargs positions") {
      val prog = Async.async(List(Async.succeed(1).await, Async.succeed(2).await).sum)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 3))
    },
    test("a lazy val initializer containing await is rejected with a named diagnostic") {
      // Every Scala 3 backend (DCA and the 3.8+ native arm) shares the same
      // rejection: suspending lazy initialization is unsupported, and silently
      // forcing the initializer eagerly (or surfacing a raw js.await error)
      // would be a miscompile.
      typeCheck {
        """
        import zio.blocks.async._
        val a = Async.async {
          lazy val x = Async.succeed(5).await
          Async.succeed(1).await
        }
        a
        """
      }.map(result => assert(result)(Assertion.isLeft))
    },
    test("a user extension method named `await` is not hijacked by the rewrite") {
      userAwaitOps.hits = 0
      val prog = Async.async {
        userAwaitOps.await(Async.succeed(20)) * 2 + Async.succeed(2).await
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42, userAwaitOps.hits == 1))
    },
    test("a lazy val whose initializer is a nested Async.async block is not rejected") {
      // The inner block expands before the outer macro runs, so its awaits are
      // its own: the outer lazy-val rejection must not fire on the expanded,
      // opaque Async value (on either the native or the DCA arm).
      val prog = Async.async {
        lazy val inner = Async.async(Async.succeed(20).await + 1)
        inner.await * 2
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 42))
    },
    test("a nested Async.async block awaited by the outer one composes") {
      // The inner block expands first (inline arguments are typed before the
      // outer macro runs) and must land as an opaque Async value: every
      // backend rewrites its own awaits during expansion, so the outer block
      // never mistakes the inner's awaits for its own.
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(5)); ()
      }
      val prog = Async.async {
        val inner = Async.async(pending.await + 1)
        inner.await + 10
      }
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 16))
    },
    test("an inner async block built OUTSIDE the outer block composes via await") {
      val pending: Async[Int] = Async.promiseInternal[Int] { c =>
        js.timers.setTimeout(0.0)(c.succeed(5)); ()
      }
      val inner = Async.async(pending.await + 1)
      val prog  = Async.async(inner.await + 10)
      ZIO.fromFuture(_ => run(prog)).map(r => assertTrue(r == 16))
    }
  )
}
