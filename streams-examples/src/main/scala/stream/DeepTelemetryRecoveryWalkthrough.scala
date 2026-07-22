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

package stream

import zio.blocks.chunk.Chunk
import zio.blocks.streams.*

/**
 * Semi-realistic walkthrough: a sensor-telemetry quality gate.
 *
 * A plant streams per-minute readings through a long chain of calibration
 * stages (deep enough that the library switches to its flat interpreter), then
 * fans each reading out into a per-sensor inspection window, recovers from a
 * flaky decoder, and exports survivors in bulk chunks.
 *
 * Composes: deep `map` chains (interpreter compilation), `flatMap` with inner
 * `filter`/`take`/`++`, `catchAll`/`catchDefect` recovery, bulk consumption via
 * `Sink.create` + `Reader.readUpToN`, zip (`&&`), `scan`, `sliding`, and
 * `chunked`.
 *
 * Each section prints OK/FAIL with expected vs actual; a mismatch localizes a
 * defect. Sections 2 and 3 originally exposed BUG-A01/BUG-A02 (since fixed);
 * the minimal regressions live in:
 *   - InterpreterSpec "regressions" [AdversarialFlatMapInnerSealSpec] (BUG-A01)
 *   - StreamSpec "regressions" [AdversarialReadUpToNRecoverySpec] (BUG-A02)
 */
object DeepTelemetryRecoveryWalkthrough extends App {
  private var failures = 0

  private def section[A](name: String)(expected: A)(actual: => A): Unit = {
    val a  = actual
    val ok = a == expected
    if (!ok) failures += 1
    println(s"${if (ok) "  OK " else "  FAIL"} $name")
    if (!ok) {
      println(s"        expected: $expected")
      println(s"        actual:   $a")
    }
  }

  /** 120 calibration stages: forces the interpreter compilation path. */
  private def calibrated(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
    (0 until 120).foldLeft(s)((acc, _) => acc.map(identity))

  println("=== Deep Telemetry Recovery Walkthrough ===\n")

  // --------------------------------------------------------------------------
  println("Section 1: deep calibration chain — values survive the interpreter")
  // --------------------------------------------------------------------------
  locally {
    val total = calibrated(Stream.range(1, 5)).runFold(0)(_ + _)
    section("calibrated sum of 1..4")(Right(10): Either[Nothing, Int])(total)
  }

  // --------------------------------------------------------------------------
  println("\nSection 2: per-reading inspection windows — flatMap inner filter/take/++")
  // --------------------------------------------------------------------------
  // Each reading opens an inspection window: keep the first 2 odd samples, then
  // append a `-1` end-of-window marker scaled for display. The marker segment
  // must NOT be re-scaled, and `take(2)` must count POST-filter samples.
  // Originally exposed BUG-A01 (since fixed): at interpreter depth the inner
  // `take`/`++` were applied below the fused filter/map ops.
  locally {
    def window(reading: Int): Stream[Nothing, Int] =
      Stream.range(reading, reading + 6).filter(_ % 2 == 1).take(2)

    val deepWindows    = calibrated(Stream(0)).flatMap(window).runCollect
    val shallowWindows = Stream(0).flatMap(window).runCollect
    section("inspection window keeps the first 2 odd samples (deep == shallow)")(shallowWindows)(deepWindows)

    def marked(reading: Int): Stream[Nothing, Int] =
      Stream(reading, reading + 1).map(_ * 10) ++ Stream(-1)

    val deepMarked    = calibrated(Stream(0)).flatMap(marked).runCollect
    val shallowMarked = Stream(0).flatMap(marked).runCollect
    section("end-of-window marker is not re-scaled (deep == shallow)")(shallowMarked)(deepMarked)
  }

  // --------------------------------------------------------------------------
  println("\nSection 3: flaky decoder — recovery must not lose decoded readings")
  // --------------------------------------------------------------------------
  // The decoder yields two good readings, then crashes; the gate falls back to
  // a `-999` quality marker. Bulk export via readUpToN must still deliver the
  // two good readings. Originally exposed BUG-A02 (since fixed): the recovery
  // readers discarded the bulk-buffered prefix.
  locally {
    def decoded: Stream[Nothing, Int] =
      Stream(1, 2, 3)
        .map(x => if (x == 3) throw new RuntimeException("decoder crash") else x * 10)
        .catchDefect { case e if e.getMessage == "decoder crash" => Stream(-999) }

    val bulkExport = decoded.run(Sink.create[Nothing, Int, Chunk[Int]] { r =>
      var acc      = Chunk.empty[Int]
      var continue = true
      while (continue) {
        val c = r.readUpToN[Int](16)
        if (c.isEmpty) continue = false else acc = acc ++ c
      }
      acc
    })
    section("bulk export keeps readings decoded before the crash")(
      Right(Chunk(10, 20, -999)): Either[Nothing, Chunk[Int]]
    )(bulkExport)
  }

  // --------------------------------------------------------------------------
  println("\nSection 4: labelling and trend detection — zip, scan, sliding")
  // --------------------------------------------------------------------------
  locally {
    val labels   = Stream("s1", "s2", "s3")
    val readings = Stream(7, 3, 9, 4)
    val paired   = (labels && readings).runCollect
    section("zip pairs labels with readings, shorter side wins")(
      Right(Chunk(("s1", 7), ("s2", 3), ("s3", 9))): Either[Nothing, Chunk[(String, Int)]]
    )(paired)

    val runningPeak = Stream(7, 3, 9, 4).scan(0)(math.max).runCollect
    section("scan emits the running peak (init first)")(
      Right(Chunk(0, 7, 7, 9, 9)): Either[Nothing, Chunk[Int]]
    )(runningPeak)

    val smoothed = calibrated(Stream.range(0, 6)).sliding(3, 2).runCollect.map(_.map(_.toList).toList)
    section("sliding(3,2) windows at interpreter depth match stdlib")(
      Right((0 until 6).toList.sliding(3, 2).map(_.toList).toList): Either[Nothing, List[List[Int]]]
    )(smoothed)
  }

  // --------------------------------------------------------------------------
  println("\nSection 5: batched shipping — chunked through a composed pipeline")
  // --------------------------------------------------------------------------
  locally {
    val gate: Pipeline[Int, Int] =
      Pipeline.filter[Int](_ >= 0).andThen(Pipeline.map[Int, Int](_ * 2))
    val shipped = calibrated(Stream.range(-2, 4)).via(gate).chunked(2).runCollect
    section("pipeline gate + chunked batches")(
      Right(Chunk(Chunk(0, 2), Chunk(4, 6))): Either[Nothing, Chunk[Chunk[Int]]]
    )(shipped)
  }

  // --------------------------------------------------------------------------
  println("\nSection 6: nightly replay — repeated over zip with a managed gauge feed")
  // --------------------------------------------------------------------------
  // The reconciliation job replays the (gauge && shift-label) pairing until it
  // has collected enough samples. The gauge connection is acquired once via
  // fromAcquireRelease, so its release hook must run exactly once no matter how
  // many replay cycles occur. Currently FAILS (BUG-R7-01): zip eagerly closes
  // the longer side at each cycle boundary, `repeated`'s reset() clears the
  // close-once guards, and the acquireRelease wrapper's close() is not
  // idempotent — the single acquire is released once per cycle plus once at the
  // end. Minimal regression: StreamResourceSpec
  // [AdversarialRepeatedEagerCloseSpec].
  locally {
    val opens  = new java.util.concurrent.atomic.AtomicInteger(0)
    val closes = new java.util.concurrent.atomic.AtomicInteger(0)
    val gauge  =
      Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
        Stream(7, 3, 9)
      )
    val replayed = (gauge && Stream("night-shift")).repeated.take(2).runCollect
    section("replayed reconciliation pairs the first gauge sample each cycle")(
      Right(Chunk((7, "night-shift"), (7, "night-shift"))): Either[Any, Chunk[(Int, String)]]
    )(replayed)
    section("gauge connection released exactly once per acquire")(opens.get())(closes.get())
  }

  println()
  if (failures == 0) println("All sections OK.")
  else println(s"$failures section(s) FAILED — see extracted regression specs.")
}
