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
import zio.blocks.scope.*
import zio.blocks.streams.*
import zio.blocks.streams.io.{Reader, Writer}

import java.io.{IOException, OutputStream}
import scala.util.Try

/**
 * Semi-realistic walkthrough: ingesting a day's transaction ledger that arrives
 * as HOURLY PARTITIONS — several of which are legitimately empty (quiet hours)
 * — concatenated into one stream of `Long` amounts.
 *
 * Composes: `Stream.fromChunk`, `++` (concat with empty middle segments),
 * `filter`, `runFold`, manual pull via `start` + the bulk `Reader.readLongs`
 * API, parallel enrichment via `mapPar`, fan-in via `mergeAll` over a managed
 * (`ensuring`) feed, and spooling through `Writer.fromOutputStream`.
 *
 * Each section prints OK/FAIL with expected vs actual; a mismatch localizes a
 * defect. Sections 2-5 originally FAILED and documented live defects discovered
 * by this walkthrough; the production fixes have since landed, so every section
 * now prints OK (re-verified during the round-10 convergence run). The minimal
 * regressions extracted at discovery time live in:
 *   - ReaderSpec "readLongs / readDoubles"/"regressions"
 *     [AdversarialConcatBulkReadSpec]
 *   - StreamConcurrencySpec "mapPar"/"mergeAll"/"buffer"
 *     [AdversarialConcatBulkReadSpec], [AdversarialCloseFailureSwallowSpec]
 *   - WriterSpec "Writer.fromOutputStream"/"Writer.fromWriter"
 *     [AdversarialWriterCloseAfterErrorSpec]
 */
object SegmentedLedgerIngestWalkthrough extends App {
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

  /** The day's ledger: hourly partitions, some hours had no transactions. */
  private def ledger(): Stream[Nothing, Long] =
    Stream.fromChunk(Chunk(120L, 95L)) ++ // 09:00
      Stream.empty ++                     // 10:00 — quiet hour
      Stream.fromChunk(Chunk(310L)) ++    // 11:00
      Stream.empty ++                     // 12:00 — quiet hour
      Stream.fromChunk(Chunk(40L, 88L))   // 13:00

  println("=== Segmented Ledger Ingest Walkthrough ===\n")

  // --------------------------------------------------------------------------
  println("Section 1: sequential ingest — concat with empty partitions, filter, fold")
  // --------------------------------------------------------------------------
  locally {
    val total = ledger().filter(_ >= 50L).runFold(0L)(_ + _)
    section("large-transaction total over all partitions")(Right(613L): Either[Nothing, Long])(total)
  }

  // --------------------------------------------------------------------------
  println("\nSection 2: bulk export — manual pull via start + Reader.readLongs")
  // --------------------------------------------------------------------------
  locally {
    // The settlement exporter pulls amounts in bulk batches. An empty middle
    // partition must not look like end-of-day.
    val exported = List.newBuilder[Long]
    Scope.global.scoped { scope =>
      import scope.*
      val reader: $[Reader[Long]] = ledger().start(using scope)
      $(reader) { r =>
        val buf = new Array[Long](16)
        var n   = r.readLongs(buf, 0, 16)
        while (n > 0) {
          exported ++= buf.take(n)
          n = r.readLongs(buf, 0, 16)
        }
      }
    }
    section("bulk export sees every partition after a quiet hour")(
      List(120L, 95L, 310L, 40L, 88L)
    )(exported.result())
  }

  // --------------------------------------------------------------------------
  println("\nSection 3: parallel enrichment — mapPar over the segmented ledger")
  // --------------------------------------------------------------------------
  locally {
    // Fee calculation is CPU-bound, so it runs on parallel workers. The
    // primitive-lane coordinator pulls the upstream in bulk; completeness
    // must match the sequential path.
    val enriched = ledger().mapPar(2)(amount => amount + 1L).runCollect
    section("mapPar enrichment loses no transactions")(
      Right(List(41L, 89L, 96L, 121L, 311L)): Either[Nothing, List[Long]]
    )(enriched.map(_.toList.sorted))
  }

  // --------------------------------------------------------------------------
  println("\nSection 4: managed feed fan-in — an ensuring finalizer failure must surface")
  // --------------------------------------------------------------------------
  locally {
    // Each upstream feed holds a session that is torn down by `ensuring`.
    // When teardown fails (e.g. the session server rejects the logout), the
    // failure must surface as a thrown defect — exactly as it does for the
    // sequential flatten — never silent success.
    def managedFeed(): Stream[Nothing, Long] =
      Stream.fromChunk(Chunk(7L, 9L)).ensuring(throw new RuntimeException("session teardown failed"))

    val sequential = Try(Stream.flattenAll(Stream(managedFeed())).runDrain).isFailure
    val merged     = Try(Stream.mergeAll(1)(Stream(managedFeed())).runDrain).isFailure
    section("sequential flatten surfaces the teardown failure")(true)(sequential)
    section("mergeAll surfaces the teardown failure (lossless errors)")(true)(merged)
  }

  // --------------------------------------------------------------------------
  println("\nSection 5: spool to disk — close() after a write failure must release the file")
  // --------------------------------------------------------------------------
  locally {
    // The archive disk fills mid-spool: writes start failing. The operator
    // closes the writer; the underlying file handle must still be released
    // ("Calling close() flushes and closes the underlying stream").
    var handleReleased         = false
    val fullDisk: OutputStream = new OutputStream {
      override def write(b: Int): Unit = throw new IOException("disk full")
      override def close(): Unit       = handleReleased = true
    }
    val spool    = Writer.fromOutputStream(fullDisk)
    val accepted = spool.write(1.toByte) // fails; absorbed as `false`
    spool.close()
    section("write is rejected once the disk is full")(false)(accepted)
    section("close() still releases the underlying file handle")(true)(handleReleased)
  }

  println()
  if (failures == 0) println("All walkthrough sections completed as expected.")
  else println(s"$failures walkthrough section(s) FAILED — see the extracted minimal regressions listed above.")
}
