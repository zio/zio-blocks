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

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext

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

  private def run[A](fa: Async[A]): Future[A] =
    AsyncInterop.toFuture(fa)(JSExecutionContext.queue)

  def spec = suite("AsyncJsAwaitSpec")(
    test("`.await` of a succeeded Async returns the value") {
      ZIO.fromFuture(_ => run(Async.async(Async.succeed(7).await + 1))).map(r => assertTrue(r == 8))
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
    }
  )
}
