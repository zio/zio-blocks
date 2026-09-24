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

object ReferenceModel {
  def normalize(value: Int, lane: ValueType): Int = lane match {
    case ValueType.Boolean => if ((value & 1) == 0) 0 else 1
    case ValueType.Byte    => value.toByte.toInt
    case ValueType.Char    => value.toChar.toInt
    case ValueType.Short   => value.toShort.toInt
    case _                 => value
  }
  private final case class Eval(
    exit: Either[Vector[String], Vector[Int]],
    pulls: Int,
    callbacks: Int,
    provenance: Vector[String],
    kind: String,
    trace: Vector[String]
  )

  private def eval(graph: Graph): Eval = graph match {
    case Graph.Input(Source.Values(v, lane, async))       => leaf(v, lane, async, "source")
    case Graph.Input(Source.ReaderValues(v, lane, async)) => leaf(v, lane, async, "reader")
    case Graph.Input(Source.Repeated(v, n, lane))         => leaf(Vector.fill(math.max(0, n))(v), lane, false, "repeat")
    case Graph.Input(Source.Failure(token, trusted))      =>
      Eval(Left(Vector(prefix(trusted) + token)), 0, 0, Vector(prefix(trusted)), "sync", Vector("failure:" + token))
    case Graph.Map(in, delta, async)      => transform(in, "map", async)(_.map(_ + delta))
    case Graph.Filter(in, modulus, async) =>
      transform(in, "filter", async)(_.filter(i => modulus != 0 && Math.floorMod(i, modulus) == 0))
    case Graph.Take(in, n)  => transform(in, "take", false)(_.take(math.max(0, n)))
    case Graph.Drop(in, n)  => transform(in, "drop", false)(_.drop(math.max(0, n)))
    case Graph.Concat(a, b) =>
      val x = eval(a);
      x.exit match {
        case Left(_)   => x.copy(trace = x.trace :+ "concat:left-failed")
        case Right(xs) =>
          val y = eval(b);
          y.copy(
            exit = y.exit.map(xs ++ _),
            pulls = x.pulls + y.pulls,
            callbacks = x.callbacks + y.callbacks,
            provenance = x.provenance ++ y.provenance,
            kind = promote(x.kind, y.kind),
            trace = x.trace ++ y.trace :+ "concat"
          )
      }
    case Graph.FlatMap(in, async)           => transform(in, "flatMap", async)(_.flatMap(i => Vector(i, i)))
    case Graph.Pipeline(in, delta, modulus) =>
      transform(in, "pipeline", false)(_.map(_ + delta).filter(i => modulus != 0 && Math.floorMod(i, modulus) == 0))
    case Graph.Tap(in, async)     => transform(in, "tap", async)(identity)
    case Graph.Bridge(in, target) => transform(in, "bridge:" + target.id, false)(_.map(normalize(_, target)))
  }
  private def leaf(v: Vector[Int], lane: ValueType, async: Boolean, origin: String): Eval = {
    val values = v.map(normalize(_, lane))
    Eval(
      Right(values),
      values.length + 1,
      0,
      Vector("trusted:" + origin),
      if (async) "async" else "sync",
      Vector("input:" + lane.id)
    )
  }
  private def prefix(trusted: Boolean)                                                                 = if (trusted) "trusted:" else "untrusted:"
  private def promote(a: String, b: String)                                                            = if (a == "async" || b == "async") "async" else "sync"
  private def transform(in: Graph, label: String, async: Boolean)(f: Vector[Int] => Vector[Int]): Eval = {
    val x = eval(in)
    x.copy(
      exit = x.exit.map(f),
      callbacks = x.callbacks + (if (async) x.exit.fold(_ => 0, _.length) else 0),
      kind = if (async) "async" else x.kind,
      trace = x.trace :+ label + (if (async) ":async" else ":sync")
    )
  }
  private def terminal(value: Either[Vector[String], Vector[Int]], t: Terminal): Either[Vector[String], Vector[Int]] =
    t match {
      case Terminal.Collect        => value
      case Terminal.Head           => value.map(_.headOption.toVector)
      case Terminal.Last           => value.map(_.lastOption.toVector)
      case Terminal.Count          => value.map(v => Vector(v.length))
      case Terminal.Drain          => value.map(_ => Vector.empty)
      case Terminal.Exists(want)   => value.map(v => Vector(if (v.contains(want)) 1 else 0))
      case Terminal.ForallBelow(n) => value.map(v => Vector(if (v.forall(_ < n)) 1 else 0))
    }
  def evaluate(s: Scenario): Observation = {
    val e      = eval(s.graph); val result = terminal(e.exit, s.terminal)
    val demand = s.terminal match {
      case Terminal.Head      => math.min(1, e.pulls);
      case Terminal.Exists(w) =>
        e.exit.fold(_ => e.pulls, v => v.indexOf(w) match { case -1 => v.length + 1; case n => n + 1 });
      case _ => e.pulls
    }
    val faults = s.faults.events.map(_.token)
    Observation(
      result,
      e.provenance,
      faults,
      e.kind,
      demand,
      e.callbacks,
      ownership(s),
      0,
      faults.drop(1),
      if (s.ownership.cancelAt >= 0) 1L else 0L,
      e.trace ++ s.schedule.steps
    )
  }
  private def ownership(s: Scenario): Vector[String] =
    (if (s.ownership.managed) Vector("acquire", "close", "release") else Vector("borrow")) ++
      (if (s.ownership.cancelAt >= 0) Vector("cancel") else Vector.empty)
}
