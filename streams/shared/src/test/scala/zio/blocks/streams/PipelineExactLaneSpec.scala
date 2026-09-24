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

package zio.blocks.streams

import zio.ZIO
import zio.blocks.async.Async
import zio.test._

/**
 * Exact physical-input proof for the complete public Pipeline factory surface.
 * Factories are deliberately instantiated at widened `Any` input types: the
 * physical lane must come from the reader, never from call-site evidence.
 */
object PipelineExactLaneSpec extends StreamsBaseSpec {
  import SinkExactLaneSpec.{AsyncProbe, State, SyncProbe, lanes}

  private val factories: List[(String, Pipeline[Any, Any])] = List(
    "buffer"       -> Pipeline.buffer[Any](1),
    "chunked"      -> Pipeline.chunked[Any](1).asInstanceOf[Pipeline[Any, Any]],
    "collect"      -> Pipeline.collect[Any, Any] { case a => a },
    "collectAsync" -> Pipeline.collectAsync[Any, Any](a => Async.succeed(Some(a))),
    "drop"         -> Pipeline.drop[Any](0),
    "filter"       -> Pipeline.filter[Any](_ => true),
    "filterAsync"  -> Pipeline.filterAsync[Any](_ => Async.succeed(true)),
    "identity"     -> Pipeline.identity[Any],
    "map"          -> Pipeline.map[Any, Any](identity),
    "mapAsync"     -> Pipeline.mapAsync[Any, Any](a => Async.succeed(a)),
    "take"         -> Pipeline.take[Any](1)
  )

  private val synchronousFactories: List[(String, Pipeline[Any, Any])] =
    factories.filterNot { case (name, _) => name == "buffer" || name.endsWith("Async") }

  private def checked(state: State, route: String, lane: JvmType, factory: String): TestResult =
    assertTrue(state.calls.nonEmpty).label(s"$route/$lane/$factory")

  private def syncMatrix: TestResult = {
    val results = for {
      (lane, value)    <- lanes
      (name, pipeline) <- synchronousFactories
      route            <- List("applyToStream", "applyToSink", "andThenSink")
    } yield {
      val state = new State(lane, List(value))
      val probe = new SyncProbe(state)
      route match {
        case "applyToStream" =>
          val output = pipeline.applyToStream(Stream.fromReader[Nothing, Any](probe))
          Sink.head[Any].drain(Sink.toSyncReader(Stream.compileToReader(output)))
        case "applyToSink" => pipeline.applyToSink(Sink.head[Any]).drain(probe)
        case _             => pipeline.andThenSink(Sink.head[Any]).drain(probe)
      }
      checked(state, s"sync/$route", lane, name)
    }
    results.reduce(_ && _)
  }

  private def asyncMatrix: ZIO[Any, Throwable, TestResult] =
    ZIO
      .foreach(lanes) { case (lane, value) =>
        ZIO
          .foreach(factories) { case (name, pipeline) =>
            ZIO
              .foreach(List("applyToStream", "applyToSink", "andThenSink")) { route =>
                val state  = new State(lane, List(value))
                val probe  = new AsyncProbe(state)
                val effect = route match {
                  case "applyToStream" =>
                    pipeline.applyToStream(Stream.fromReader[Nothing, Any](probe)).runAsync(Sink.head[Any])
                  case "applyToSink" => pipeline.applyToSink(Sink.head[Any]).drain(probe)
                  case _             => pipeline.andThenSink(Sink.head[Any]).drain(probe)
                }
                runAsync(effect).as(checked(state, s"async/$route", lane, name))
              }
              .map(_.reduce(_ && _))
          }
          .map(_.reduce(_ && _))
      }
      .map(_.reduce(_ && _))

  def spec = suite("Pipeline exact primitive lanes")(
    test("every factory and application route preserves widened synchronous inputs")(syncMatrix) @@
      TestAspect.jvmOnly,
    test("every factory and application route preserves widened asynchronous inputs")(asyncMatrix)
  )
}
