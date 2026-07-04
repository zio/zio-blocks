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
import zio.blocks.streams.io.Reader

/**
 * Semi-realistic walkthrough: an audit pipeline over a heterogeneous event
 * feed.
 *
 * An ingest service receives numeric transaction IDs and free-text operator
 * annotations as ONE feed (a union-element stream built with `++`), pushes it
 * through a long chain of enrichment stages (deep enough that the library
 * switches to its flat interpreter), decouples a flaky upstream with `buffer` +
 * `catchAll` fallback, stitches archive segments at the `Reader` level with
 * release hooks, and skips a corrupt preamble with `drop` over managed I/O.
 *
 * Composes: mixed-element `++` (union types), deep `map` chains (interpreter
 * compilation), `buffer` with `catchAll` recovery, `Reader.++`/`withRelease`
 * lifecycle, `Stream.fromInputStream`/`ensuring`/`drop`, and `Sink.create`.
 *
 * Each section prints OK/FAIL with expected vs actual; a mismatch localizes a
 * defect. Sections 1, 3 and 4 originally exposed live defects (since fixed)
 * found by this campaign; minimal regressions are extracted into:
 *   - InterpreterSpec "regressions" [AdversarialMixedConcatLaneSpec]
 *     (BUG-R5-04)
 *   - ReaderSpec "close law" [AdversarialReaderCloseLawSpec] (BUG-R5-02)
 *   - StreamResourceSpec [AdversarialDropEagerSkipLeakSpec] (BUG-R5-01)
 *   - StreamConcurrencySpec buffer_errorAfterElements_… (BUG-R5-05, JS only)
 */
object MixedFeedAuditWalkthrough extends App {
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

  /** 120 enrichment stages: forces the interpreter compilation path. */
  private def enriched(s: Stream[Nothing, Int | String]): Stream[Nothing, Int | String] =
    (0 until 120).foldLeft(s)((acc, _) => acc.map(x => x))

  println("=== Mixed Feed Audit Walkthrough ===\n")

  // --------------------------------------------------------------------------
  println("Section 1: heterogeneous feed — IDs ++ annotations through deep enrichment")
  // --------------------------------------------------------------------------
  // Transaction IDs and operator annotations form one union-element feed. The
  // deep enrichment chain must preserve every event verbatim. Originally exposed a defect (since fixed)
  // (BUG-R5-04): at interpreter depth the mixed-lane `++` leaves the
  // interpreter's output lane stale and every event is replaced by 0.
  locally {
    val feed: Stream[Nothing, Int | String] = Stream(101, 102, 103) ++ Stream("review", "hold")
    val shallowAudit                        = feed.runCollect
    val deepAudit                           = enriched(feed).runCollect
    section("deep enrichment preserves the heterogeneous feed (deep == shallow)")(shallowAudit)(deepAudit)
  }

  // --------------------------------------------------------------------------
  println("\nSection 2: flaky upstream — buffer decoupling with catchAll fallback")
  // --------------------------------------------------------------------------
  // The upstream emits two IDs then fails; the audit falls back to a `-1`
  // dead-letter marker. Events received BEFORE the failure must reach the
  // sink: `buffer` is a pure decoupling transform and must not change which
  // elements precede an error. (Passes on the JVM; the extracted regression
  // fails on Scala.js — BUG-R5-05.)
  locally {
    val flaky    = (Stream(201, 202): Stream[Nothing, Int]) ++ (Stream.fail("uplink down"): Stream[String, Int])
    val received = flaky.buffer(8).catchAll(_ => Stream(-1)).runCollect
    section("buffered events received before the failure reach the audit")(
      Right(Chunk(201, 202, -1)): Either[Nothing, Chunk[Int]]
    )(received)
  }

  // --------------------------------------------------------------------------
  println("\nSection 3: archive stitching — Reader segments with release hooks")
  // --------------------------------------------------------------------------
  // Two archive segments are stitched with `Reader.++`; the second carries a
  // release hook (e.g. returning a pooled page). The auditor stops early by
  // closing the reader; per the documented Reader law, reads after close
  // return the sentinel — and each release hook runs exactly once. Originally exposed a defect (since fixed)
  // (BUG-R5-02): a read after close() materializes and reads the next segment,
  // and post-EOF reads re-run the release hook.
  locally {
    var releases                = 0
    def stitched(): Reader[Int] =
      Reader.fromChunk[Int](Chunk(1, 2)) ++
        Reader.fromChunk[Int](Chunk(3, 4)).withRelease { () => releases += 1; () }

    val earlyStop = stitched()
    val first     = earlyStop.read[Any](null)
    earlyStop.close()
    val afterClose = earlyStop.read[Any](null)
    section("reads after close() return the sentinel")((Int.box(1): Any, null: Any))((first, afterClose))

    releases = 0
    val drained = stitched()
    var v       = drained.read[Any](null)
    while (v != null) v = drained.read[Any](null)
    val again = drained.read[Any](null) // a post-EOF read must be a benign no-op
    section("release hook runs exactly once after full drain + extra read")((null: Any, 1))((again, releases))
  }

  // --------------------------------------------------------------------------
  println("\nSection 4: corrupt preamble — drop over managed I/O still releases")
  // --------------------------------------------------------------------------
  // The feed's first record is skipped with `drop(1)`. When the skipped read
  // itself fails, the typed error is reported AND the managed source must
  // still be closed (with its `ensuring` audit hook run). Originally exposed a defect (since fixed)
  // (BUG-R5-01): the eager skip happens before the stream owns a closeable
  // reader, so both finalizers are leaked.
  locally {
    var closed                          = false
    var audited                         = false
    val unreliable: java.io.InputStream = new java.io.InputStream {
      def read(): Int            = throw new java.io.IOException("preamble torn")
      override def close(): Unit = closed = true
    }
    val outcome =
      Stream.fromInputStream(unreliable).ensuring { audited = true }.drop(1).runDrain
    section("typed error reported and managed source released")((true, true, true))(
      (outcome.isLeft, audited, closed)
    )
  }

  println()
  if (failures == 0) println("All sections OK.")
  else println(s"$failures section(s) FAILED — see extracted regression specs.")
}
