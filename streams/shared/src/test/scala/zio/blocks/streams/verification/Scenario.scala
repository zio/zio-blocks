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

sealed trait Source extends Product with Serializable
object Source {
  final case class Values(values: Vector[Int], lane: ValueType, asynchronous: Boolean)       extends Source
  final case class ReaderValues(values: Vector[Int], lane: ValueType, asynchronous: Boolean) extends Source
  final case class Repeated(value: Int, count: Int, lane: ValueType)                         extends Source
  final case class Failure(token: String, trusted: Boolean)                                  extends Source
}

sealed trait ReaderProgram extends Product with Serializable
object ReaderProgram {
  case object Read                  extends ReaderProgram
  final case class ReadN(n: Int)    extends ReaderProgram
  final case class ReadUpTo(n: Int) extends ReaderProgram
  final case class Skip(n: Long)    extends ReaderProgram
  final case class Limit(n: Long)   extends ReaderProgram
  case object Repeat                extends ReaderProgram
  case object Reset                 extends ReaderProgram
  case object Close                 extends ReaderProgram
}

sealed trait Graph extends Product with Serializable
object Graph {
  final case class Input(source: Source)                                  extends Graph
  final case class Map(in: Graph, delta: Int, asynchronous: Boolean)      extends Graph
  final case class Filter(in: Graph, modulus: Int, asynchronous: Boolean) extends Graph
  final case class Take(in: Graph, count: Int)                            extends Graph
  final case class Drop(in: Graph, count: Int)                            extends Graph
  final case class Concat(left: Graph, right: Graph)                      extends Graph
  final case class FlatMap(in: Graph, asynchronous: Boolean)              extends Graph
  final case class Pipeline(in: Graph, delta: Int, modulus: Int)          extends Graph
  final case class Tap(in: Graph, asynchronous: Boolean)                  extends Graph
  final case class Bridge(in: Graph, target: ValueType)                   extends Graph
}

sealed trait Terminal extends Product with Serializable
object Terminal {
  case object Collect                      extends Terminal; case object Head  extends Terminal; case object Last extends Terminal
  case object Count                        extends Terminal; case object Drain extends Terminal
  final case class Exists(value: Int)      extends Terminal
  final case class ForallBelow(value: Int) extends Terminal
}
sealed trait SinkMode extends Product with Serializable
object SinkMode {
  case object Collect extends SinkMode; case object Fold extends SinkMode; case object Foreach extends SinkMode;
  case object Fail    extends SinkMode
}

sealed trait WriterProgram extends Product with Serializable
object WriterProgram {
  final case class Scalar(values: Vector[Int], lane: ValueType, deferred: Boolean) extends WriterProgram
  final case class Bulk(values: Vector[Int], lane: ValueType, offset: Int, length: Int, deferred: Boolean)
      extends WriterProgram
  final case class Fail(token: String, deferred: Boolean) extends WriterProgram
  final case class Close(deferred: Boolean)               extends WriterProgram
}

final case class Scenario(
  id: String,
  graph: Graph,
  terminal: Terminal,
  sink: SinkMode,
  reader: ReaderProgram,
  writer: Option[WriterProgram],
  faults: FaultScript,
  validation: Validation,
  ownership: Ownership,
  schedule: Schedule
) extends Serializable

object Scenario {
  def simple(id: String, graph: Graph, terminal: Terminal): Scenario =
    Scenario(
      id,
      graph,
      terminal,
      SinkMode.Collect,
      ReaderProgram.Read,
      None,
      FaultScript(Vector.empty),
      Validation(0, 0, 0),
      Ownership(managed = true, -1, -1),
      Schedule(Vector.empty)
    )
}
