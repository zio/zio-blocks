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

import scala.concurrent.ExecutionContext
import zio._
import zio.blocks.async._
import zio.blocks.streams.{Stream, StreamsBaseSpec}
import zio.test._

object AsyncTestingCompletionSpec extends StreamsBaseSpec {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM)
      zio.Chunk(TestAspect.timeout(zio.Duration.fromSeconds(300)), TestAspect.timed)
    else
      zio.Chunk(
        TestAspect.timeout(zio.Duration.fromSeconds(300)),
        TestAspect.timed,
        TestAspect.sequential,
        TestAspect.size(10)
      )

  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(r: Runnable): Unit        = Async.schedule(r, forceMacrotask = false)
    def reportFailure(t: Throwable): Unit = throw t
  }
  private def run[A](a: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => a.toFuture)
  private val generatedSamples                            = sys.env.get("ASYNC_TEST_SAMPLES").fold(10000)(_.toInt)
  private val generatedDepth                              = sys.env.get("ASYNC_TEST_DEPTH").fold(8)(_.toInt)
  private val generatedScenarios                          = ScenarioGen.generated(0x5eed5eedL, generatedSamples, generatedDepth)

  private def verify(scenario: Scenario): ZIO[Any, Throwable, Unit] =
    for {
      ready <- run(ProductionRunner.run(scenario, suspended = false)).mapError { cause =>
                 new AssertionError(s"ready execution failed for ${scenario.id}: $scenario", cause)
               }
      suspended <- run(ProductionRunner.run(scenario, suspended = true)).mapError { cause =>
                     new AssertionError(s"suspended execution failed for ${scenario.id}: $scenario", cause)
                   }
      model   = ReferenceModel.evaluate(scenario)
      matches = ready.result == model.result && suspended.result == model.result && ready.result == suspended.result &&
                  ready.readerKind == model.readerKind && suspended.readerKind == model.readerKind
      _ <-
        ZIO
          .fail(
            new AssertionError(
              "scenario mismatch: " + scenario.id + "; scenario=" + scenario + "; model=" + model + "; ready=" + ready +
                "; suspended=" + suspended
            )
          )
          .unless(matches)
    } yield ()

  def spec = suite("executable async semantic verification")(
    test("forallAsync is vacuously true after take then drop produces an empty primitive stream") {
      val empty = Stream.fromIterable(Vector.range(-3, 9).map(_.toByte)).map(_.toInt).take(5).drop(6)
      for {
        collected <- run(empty.runCollectAsync)
        exists    <- run(empty.existsAsync(_ => Async.succeed(true)))
        forall    <- run(empty.forallAsync(value => Async.succeed(value < 2)))
      } yield assertTrue(
        collected.map(_.toVector) == Right(Vector.empty[Int]),
        exists == Right(false),
        forall == Right(true)
      )
    },
    test("nested suspended primitive pipelines remain isolated under concurrent execution") {
      val source = Graph.Input(Source.Values(Vector.range(-3, 6), ValueType.Byte, asynchronous = true))
      val graph  = Graph.Filter(
        Graph.FlatMap(
          Graph.Bridge(
            Graph.Pipeline(
              Graph.Bridge(
                Graph.Map(Graph.Filter(source, 3, asynchronous = false), -2, asynchronous = false),
                ValueType.Double
              ),
              1,
              1
            ),
            ValueType.Byte
          ),
          asynchronous = true
        ),
        1,
        asynchronous = true
      )
      val scenario = Scenario.simple("concurrent-nested-suspended-primitive", graph, Terminal.Drain)
      ZIO.foreachParDiscard(0 until 10000)(_ => verify(scenario)).as(assertTrue(true))
    },
    test("suspended concat, tap, and drop remain isolated under concurrent execution") {
      val scenario = ScenarioGen.generated(0x5eed5eedL, 8757, 8).last
      ZIO.foreachParDiscard(0 until 10000)(_ => verify(scenario)).as(assertTrue(true))
    },
    test("suspended Float map, drop, bridge, tap, and concat remain isolated under concurrent execution") {
      val scenario = Scenario.simple(
        "concurrent-suspended-float-map-drop-bridge-tap-concat",
        Graph.Concat(
          Graph.Tap(
            Graph.Bridge(
              Graph.Drop(
                Graph.Map(
                  Graph.Input(Source.Values(Vector.range(-3, 7), ValueType.Float, asynchronous = true)),
                  -1,
                  asynchronous = true
                ),
                7
              ),
              ValueType.Int
            ),
            asynchronous = false
          ),
          Graph.Input(Source.Values(Vector(-2), ValueType.Int, asynchronous = true))
        ),
        Terminal.Collect
      )
      ZIO.foreachParDiscard(0 until 10000)(_ => verify(scenario)).as(assertTrue(true))
    },
    test("all logical terminals, physical lanes, and ready/suspended interpreters agree with the independent model") {
      ZIO.foreachDiscard(ScenarioGen.all)(verify).as(assertTrue(true))
    } @@ TestAspect.timeout(zio.Duration.fromSeconds(300)),
    suite("the frozen generated campaign agrees through the configured depth and sample floor")(
      generatedScenarios.map(scenario => test(scenario.id)(verify(scenario).as(assertTrue(true)))): _*
    ),
    test("reference constructors observe provenance, suppression, ownership, epoch, demand, and trace") {
      val left     = Graph.Input(Source.ReaderValues(Vector(1, 2, 3), ValueType.Int, asynchronous = true))
      val graph    = Graph.Concat(Graph.Drop(Graph.Take(left, 2), 1), Graph.Input(Source.Failure("typed", trusted = true)))
      val scenario = Scenario(
        "observations",
        graph,
        Terminal.Collect,
        SinkMode.Fail,
        ReaderProgram.ReadN(2),
        Some(WriterProgram.Fail("writer", deferred = true)),
        FaultScript(Vector(Fault.Throw("primary"), Fault.Late("close"))),
        Validation(0, 2, 3),
        Ownership(managed = true, closeAt = 2, cancelAt = 1),
        zio.blocks.streams.verification.Schedule(Vector("read", "cancel"))
      )
      val o = ReferenceModel.evaluate(scenario)
      assertTrue(
        o.result == Left(Vector("trusted:typed")),
        o.provenance.nonEmpty,
        o.throwableOrder == Vector("primary", "close"),
        o.suppressed == Vector("close"),
        o.ownership == Vector("acquire", "close", "release", "cancel"),
        o.epoch == 1L,
        o.demand > 0,
        o.trace.contains("concat")
      )
    },
    test("replay records round-trip without losing delimiters") {
      val replay =
        Replay(0x5eedL, 17L, Vector(1, 0, 2), "scenario|one", Vector("late,callback"), Vector("read", "close"))
      assertTrue(Replay.decode(Replay.encode(replay)) == Right(replay), Replay.decode("bad").isLeft)
    },
    test("shrinking preserves scenario typing and asynchronous placement") {
      val leaves = ScenarioGen.all.flatMap(ScenarioGen.shrink)
      assertTrue(
        leaves.nonEmpty,
        leaves.forall(s => s.id.startsWith("ASYNC-MATRIX-") || s.id.startsWith("ASYNC-BRIDGE-"))
      )
    },
    test("deterministic schedule is reproducible and rejects stale, duplicate, and late completion") {
      def execute(): (Int, Vector[String]) = {
        val runtime = new DeterministicRuntime; var winners = 0
        runtime.submit("old")(winners += 1); runtime.cancel(); runtime.complete("old")
        runtime.submit("new")(winners += 1); runtime.duplicate("new"); runtime.checkpoint("done")
        (winners, runtime.history)
      }
      val first = execute()
      assertTrue(
        first == execute(),
        first._1 == 1,
        first._2.contains("stale:old"),
        first._2.count(_ == "complete:new") == 1
      )
    },
    suite("bounded lifecycle histories are checked against real-time precedence")(
      Vector(
        (
          "read-complete",
          Vector(
            Linearizability.Call(Linearizability.Operation.Read("r"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Complete("r"), 2, 3)
          ),
          true
        ),
        (
          "overlapping reads",
          Vector(
            Linearizability.Call(Linearizability.Operation.Read("a"), 0, 3),
            Linearizability.Call(Linearizability.Operation.Read("b"), 1, 2)
          ),
          false
        ),
        (
          "close is idempotent",
          Vector(
            Linearizability.Call(Linearizability.Operation.Close("a"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Close("b"), 2, 3)
          ),
          true
        ),
        (
          "late completion",
          Vector(
            Linearizability.Call(Linearizability.Operation.Read("r"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Cancel("c"), 2, 3),
            Linearizability.Call(Linearizability.Operation.Complete("r"), 4, 5)
          ),
          false
        ),
        (
          "replacement during pull",
          Vector(
            Linearizability.Call(Linearizability.Operation.Read("r"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Replace("n"), 2, 3)
          ),
          false
        ),
        (
          "bulk read completes",
          Vector(
            Linearizability.Call(Linearizability.Operation.BulkRead("b"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Complete("b"), 2, 3)
          ),
          true
        ),
        (
          "control completes",
          Vector(
            Linearizability.Call(Linearizability.Operation.Control("s"), 0, 1),
            Linearizability.Call(Linearizability.Operation.Complete("s"), 2, 3)
          ),
          true
        )
      ).map { case (name, history, expected) =>
        test(name)(assertTrue(Linearizability.accepts(history) == expected))
      }: _*
    )
  )
}
