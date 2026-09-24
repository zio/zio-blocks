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

package zio.blocks.streams.verification

import zio.blocks.async._
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

/**
 * Executes the declarative language through production Stream and Reader
 * implementations.
 */
object ProductionRunner {
  final class Counters { var pulls = 0; var callbacks = 0; var closes = 0 }

  private def reader(values: Vector[Int], counters: Counters): Reader.SyncReader[Int] = new Reader.SyncReader[Int] {
    private var index                  = 0
    private var closed                 = false
    def close(): Unit                  = { if (!closed) counters.closes += 1; closed = true }
    def isClosed: Boolean              = closed || index >= values.length
    def read[A >: Int](sentinel: A): A = {
      counters.pulls += 1
      if (isClosed) sentinel else { val value = values(index); index += 1; value.asInstanceOf[A] }
    }
  }
  private def typed[A](values: Vector[A], asynchronous: Boolean)(toInt: A => Int): Stream[String, Int] = {
    val sync   = Stream.fromIterable(values)
    val stream =
      if (asynchronous)
        Stream.fromReader[String, A](Stream.compileToReader(sync).asInstanceOf[Reader.SyncReader[A]].toAsync)
      else sync
    stream.map(toInt)
  }
  private def values(v: Vector[Int], lane: ValueType, asynchronous: Boolean): Stream[String, Int] = lane match {
    case ValueType.Ref     => typed(v.map(i => i.toString), asynchronous)(_.toInt)
    case ValueType.Boolean => typed(v.map(i => (i & 1) != 0), asynchronous)(if (_) 1 else 0)
    case ValueType.Byte    => typed(v.map(_.toByte), asynchronous)(_.toInt)
    case ValueType.Char    => typed(v.map(_.toChar), asynchronous)(_.toInt)
    case ValueType.Short   => typed(v.map(_.toShort), asynchronous)(_.toInt)
    case ValueType.Int     => typed(v, asynchronous)(identity)
    case ValueType.Long    => typed(v.map(_.toLong), asynchronous)(_.toInt)
    case ValueType.Float   => typed(v.map(_.toFloat), asynchronous)(_.toInt)
    case ValueType.Double  => typed(v.map(_.toDouble), asynchronous)(_.toInt)
  }
  private def bridge(stream: Stream[String, Int], target: ValueType): Stream[String, Int] = target match {
    case ValueType.Ref     => stream.map(_.toString).map(_.toInt)
    case ValueType.Boolean => stream.map(i => (i & 1) != 0).map(if (_) 1 else 0)
    case ValueType.Byte    => stream.map(_.toByte).map(_.toInt)
    case ValueType.Char    => stream.map(_.toChar).map(_.toInt)
    case ValueType.Short   => stream.map(_.toShort).map(_.toInt)
    case ValueType.Int     => stream.map(identity)
    case ValueType.Long    => stream.map(_.toLong).map(_.toInt)
    case ValueType.Float   => stream.map(_.toFloat).map(_.toInt)
    case ValueType.Double  => stream.map(_.toDouble).map(_.toInt)
  }
  private def source(s: Source, c: Counters): Stream[String, Int] = s match {
    case Source.Values(v, lane, asynchronous) => values(v, lane, asynchronous)
    case Source.ReaderValues(v, lane, async)  =>
      if (lane == ValueType.Int) {
        val r = reader(v, c)
        if (async) Stream.fromReader[String, Int](r.toAsync) else Stream.fromReader[String, Int](r)
      } else values(v, lane, async)
    case Source.Repeated(v, n, lane) =>
      Stream.fromIterable(Vector.fill(math.max(0, n))(ReferenceModel.normalize(v, lane)))
    case Source.Failure(token, _) => Stream.fail(token)
  }
  private def callback[A](value: => A, suspended: Boolean, c: Counters): Async[A] = {
    c.callbacks += 1
    if (suspended) Async.reschedule(() => Async.succeed(value)) else Async.succeed(value)
  }
  private def graph(g: Graph, suspended: Boolean, c: Counters): Stream[String, Int] = g match {
    case Graph.Input(s)          => source(s, c)
    case Graph.Map(in, d, async) =>
      if (async) graph(in, suspended, c).mapAsync(i => callback(i + d, suspended, c))
      else graph(in, suspended, c).map(_ + d)
    case Graph.Filter(in, m, async) =>
      if (async) graph(in, suspended, c).filterAsync(i => callback(m != 0 && Math.floorMod(i, m) == 0, suspended, c))
      else graph(in, suspended, c).filter(i => m != 0 && Math.floorMod(i, m) == 0)
    case Graph.Take(in, n)        => graph(in, suspended, c).take(math.max(0, n).toLong)
    case Graph.Drop(in, n)        => graph(in, suspended, c).drop(math.max(0, n).toLong)
    case Graph.Concat(a, b)       => graph(a, suspended, c) ++ graph(b, suspended, c)
    case Graph.FlatMap(in, async) =>
      if (async) graph(in, suspended, c).flatMap(i => Stream.unwrap(callback(Stream(i, i), suspended, c)))
      else graph(in, suspended, c).flatMap(i => Stream(i, i))
    case Graph.Pipeline(in, d, m) => graph(in, suspended, c).map(_ + d).filter(i => m != 0 && Math.floorMod(i, m) == 0)
    case Graph.Tap(in, async)     =>
      if (async) graph(in, suspended, c).tapEachAsync(_ => callback((), suspended, c))
      else graph(in, suspended, c).tapEach(_ => c.callbacks += 1)
    case Graph.Bridge(in, target) => bridge(graph(in, suspended, c), target)
  }
  private def terminal(s: Stream[String, Int], t: Terminal): Async[Either[String, Vector[Int]]] = t match {
    case Terminal.Collect        => s.runCollectAsync.map(_.map(_.toVector))
    case Terminal.Head           => s.headAsync.map(_.map(_.toVector))
    case Terminal.Last           => s.lastAsync.map(_.map(_.toVector))
    case Terminal.Count          => s.countAsync.map(_.map(n => Vector(n.toInt)))
    case Terminal.Drain          => s.runDrainAsync.map(_.map(_ => Vector.empty))
    case Terminal.Exists(w)      => s.existsAsync(i => Async.succeed(i == w)).map(_.map(b => Vector(if (b) 1 else 0)))
    case Terminal.ForallBelow(n) => s.forallAsync(i => Async.succeed(i < n)).map(_.map(b => Vector(if (b) 1 else 0)))
  }
  def run(s: Scenario, suspended: Boolean): Async[Observation] = {
    val counters = new Counters
    terminal(graph(s.graph, suspended, counters), s.terminal).map { result =>
      Observation(
        result.left.map(t => Vector("trusted:" + t)),
        Vector("production"),
        Vector.empty,
        if (containsAsync(s.graph)) "async" else "sync",
        counters.pulls,
        counters.callbacks,
        Vector("close:" + counters.closes),
        0,
        Vector.empty,
        0L,
        Vector("runner:" + (if (suspended) "suspended" else "ready"))
      )
    }
  }
  private def containsAsync(g: Graph): Boolean = g match {
    case Graph.Input(Source.Values(_, _, a))       => a
    case Graph.Input(Source.ReaderValues(_, _, a)) => a
    case Graph.Input(_)                            => false
    case Graph.Map(in, _, a)                       => a || containsAsync(in)
    case Graph.Filter(in, _, a)                    => a || containsAsync(in)
    case Graph.FlatMap(in, a)                      => a || containsAsync(in)
    case Graph.Tap(in, a)                          => a || containsAsync(in)
    case Graph.Concat(a, b)                        => containsAsync(a) || containsAsync(b)
    case Graph.Take(in, _)                         => containsAsync(in)
    case Graph.Drop(in, _)                         => containsAsync(in)
    case Graph.Pipeline(in, _, _)                  => containsAsync(in)
    case Graph.Bridge(in, _)                       => containsAsync(in)
  }
}
