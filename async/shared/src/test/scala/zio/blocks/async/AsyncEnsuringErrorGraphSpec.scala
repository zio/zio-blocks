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

import zio.test._

import scala.util.Try

/**
 * FINAL CONVERGENCE re-attack on `ensuring`'s suppressed-finalizer attachment
 * (`AsyncSlowPath.EnsuringPollable`, the `addSuppressed` call site). Earlier
 * passes closed BUG-006 (self-suppression, same instance) and BUG-007 (null
 * finalizer cause). These probes hammer the SAME call site with harder error
 * graphs that could re-open it:
 *
 *   - finalizer cause that is already a member of the primary's CAUSE chain,
 *   - an `addSuppressed` CYCLE (primary suppresses finalizer whose own cause IS
 *     the primary),
 *   - deeply NESTED `ensuring` with a mix of null / same-instance / distinct
 *     finalizer causes interleaved.
 *
 * Oracle: the documented `ensuring` contract — "propagate the original outcome
 * ... the original outcome wins"; a failing finalizer must never replace the
 * primary failure with an `addSuppressed`-thrown exception, and driving must not
 * loop or crash on a cyclic throwable graph. All of these are expected to PASS
 * (convergence evidence that the guard is complete).
 */
object AsyncEnsuringErrorGraphSpec extends ZIOSpecDefault {

  def spec = suite("AsyncEnsuringErrorGraphSpec")(
    test("finalizer cause that is also the primary's cause still surfaces the primary") {
      val root           = new RuntimeException("root")
      val primary        = new RuntimeException("primary", root) // primary.getCause eq root
      val a: Async[Int]  = Async.fail(primary).ensuring(Async.fail(root))
      val thrown         = Try(a.block).failed.toOption
      assertTrue(
        thrown.contains(primary),
        thrown.exists(_.getSuppressed.toList.contains(root))
      )
    },
    test("a suppressed-graph cycle (finalizer caused-by primary) surfaces the primary without looping") {
      val primary       = new RuntimeException("primary")
      val fin           = new RuntimeException("fin", primary) // fin.getCause eq primary -> cycle once suppressed
      val a: Async[Int] = Async.fail(primary).ensuring(Async.fail(fin))
      val thrown        = Try(a.block).failed.toOption
      // Force the cyclic graph to be walked (printStackTrace uses a dejaVu set).
      thrown.foreach { t =>
        val sw = new java.io.StringWriter
        t.printStackTrace(new java.io.PrintWriter(sw))
      }
      assertTrue(
        thrown.contains(primary),
        thrown.exists(_.getSuppressed.toList.contains(fin))
      )
    },
    test("deeply nested ensuring with mixed null / same / distinct finalizer causes keeps the primary") {
      val primary = new RuntimeException("primary")
      val f1      = new RuntimeException("f1")
      val f2      = new RuntimeException("f2")
      val a: Async[Int] =
        Async
          .fail(primary)
          .ensuring(Async.fail(f1))      // distinct -> attached
          .ensuring(Async.fail(primary)) // same instance as primary -> skipped (guarded)
          .ensuring(Async.fail(null))    // null finalizer cause -> skipped (guarded)
          .ensuring(Async.fail(f2))      // distinct -> attached
      val thrown = Try(a.block).failed.toOption
      val supp   = thrown.toList.flatMap(_.getSuppressed.toList)
      assertTrue(
        thrown.contains(primary),
        supp.contains(f1),
        supp.contains(f2)
      )
    },
    test("ensuring with a succeeding primary drops a failing finalizer without surfacing it") {
      val fin           = new RuntimeException("fin")
      val a: Async[Int] = Async.succeed(1).ensuring(Async.fail(fin))
      assertTrue(a.block == 1)
    }
  )
}
