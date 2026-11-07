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

object ScenarioGen {
  private val edges = Vector(Int.MinValue, -1, 0, 1, 127, 128, 255, Int.MaxValue)
  private final class Random(var state: Long) {
    def next(bound: Int): Int = {
      state = state * 6364136223846793005L + 1442695040888963407L
      ((state >>> 33) % bound).toInt
    }
  }
  val finite: Vector[Scenario] = for {
    (lane, li) <- ValueType.all.zipWithIndex
    terminal   <- Vector[Terminal](
                  Terminal.Collect,
                  Terminal.Head,
                  Terminal.Last,
                  Terminal.Count,
                  Terminal.Drain,
                  Terminal.Exists(1),
                  Terminal.ForallBelow(256)
                )
    async <- Vector(false, true)
  } yield {
    val source = Graph.Input(Source.ReaderValues(edges, lane, async))
    val graph  = Graph.Take(
      Graph
        .Pipeline(Graph.Tap(Graph.FlatMap(Graph.Filter(Graph.Map(source, li, async), 2, async), async), async), 1, 2),
      9
    )
    Scenario(
      "ASYNC-MATRIX-" + lane.id + "-" + terminal.productPrefix + "-" + async,
      graph,
      terminal,
      SinkMode.Collect,
      ReaderProgram.Read,
      None,
      FaultScript(Vector.empty),
      Validation(0, edges.length, edges.length),
      Ownership(managed = true, -1, -1),
      Schedule(Vector("read", "callback", "close"))
    )
  }
  val physicalBridges: Vector[Scenario] = for {
    source <- ValueType.physical
    target <- ValueType.physical
    async  <- Vector(false, true)
  } yield Scenario.simple(
    "ASYNC-BRIDGE-" + source.id + "-" + target.id + "-" + async,
    Graph.Bridge(Graph.Input(Source.Values(edges, source, async)), target),
    Terminal.Collect
  )

  val all: Vector[Scenario] = finite ++ physicalBridges

  def generated(seed: Long, count: Int, maxDepth: Int): Vector[Scenario] = {
    val random = new Random(seed)
    Vector.tabulate(count) { ordinal =>
      val lane         = ValueType.all(random.next(ValueType.all.length))
      val async        = random.next(2) == 0
      var graph: Graph = Graph.Input(Source.Values(Vector.tabulate(1 + random.next(12))(i => i - 3), lane, async))
      var depth        = 0
      val limit        = 1 + random.next(math.max(1, maxDepth))
      while (depth < limit) {
        graph = random.next(10) match {
          case 0 => Graph.Map(graph, random.next(7) - 3, random.next(2) == 0)
          case 1 => Graph.Filter(graph, 1 + random.next(4), random.next(2) == 0)
          case 2 => Graph.Take(graph, random.next(8))
          case 3 => Graph.Drop(graph, random.next(8))
          case 4 => Graph.FlatMap(graph, random.next(2) == 0)
          case 5 => Graph.Pipeline(graph, random.next(5) - 2, 1 + random.next(4))
          case 6 => Graph.Tap(graph, random.next(2) == 0)
          case 7 => Graph.Bridge(graph, ValueType.all(random.next(ValueType.all.length)))
          case 8 =>
            Graph.Concat(
              graph,
              Graph.Input(Source.Values(Vector(random.next(9) - 4), ValueType.Int, random.next(2) == 0))
            )
          case _ => Graph.Map(Graph.Take(graph, random.next(8)), random.next(3), asynchronous = true)
        }
        depth += 1
      }
      val terminal: Terminal = random.next(7) match {
        case 0 => Terminal.Collect
        case 1 => Terminal.Head
        case 2 => Terminal.Last
        case 3 => Terminal.Count
        case 4 => Terminal.Drain
        case 5 => Terminal.Exists(random.next(8) - 2)
        case _ => Terminal.ForallBelow(random.next(8))
      }
      Scenario.simple("ASYNC-GENERATED-" + seed + "-" + ordinal, graph, terminal)
    }
  }
  def shrink(s: Scenario): Vector[Scenario] = s.graph match {
    case Graph.Map(in, _, async)              => Vector(s.copy(graph = Graph.Map(in, 0, async)))
    case Graph.Take(in, n) if n > 0           => Vector(s.copy(graph = in), s.copy(graph = Graph.Take(in, n / 2)))
    case Graph.Drop(in, n) if n > 0           => Vector(s.copy(graph = in), s.copy(graph = Graph.Drop(in, n / 2)))
    case Graph.Filter(in, m, async) if m != 1 => Vector(s.copy(graph = Graph.Filter(in, 1, async)))
    case Graph.Concat(a, b)                   => Vector(s.copy(graph = a), s.copy(graph = b))
    case Graph.FlatMap(in, async)             => Vector(s.copy(graph = Graph.Map(in, 0, async)))
    case Graph.Pipeline(in, _, _)             => Vector(s.copy(graph = in))
    case Graph.Tap(in, async)                 => Vector(s.copy(graph = Graph.Map(in, 0, async)))
    case Graph.Bridge(in, _)                  => Vector(s.copy(graph = in))
    case _                                    => Vector.empty
  }
}
