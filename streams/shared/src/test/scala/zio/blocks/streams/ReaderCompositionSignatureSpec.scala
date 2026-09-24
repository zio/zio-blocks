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

import scala.concurrent.ExecutionContext
import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.io.Reader
import zio.test._

object ReaderCompositionSignatureSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }
  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def staticResultMatrix(): Unit = {
    val sync                 = Reader.single("sync")
    val async                = Reader.single("async").toAsync
    val root: Reader[String] = sync

    val ss: Reader.SyncReader[String]   = sync ++ sync
    val sa: Reader.AsyncReader[String]  = sync ++ async
    val sr: Reader.AsyncReader[String]  = sync ++ root
    val as: Reader.AsyncReader[String]  = async ++ sync
    val aa: Reader.AsyncReader[String]  = async ++ async
    val ar: Reader.AsyncReader[String]  = async ++ root
    val rs: Reader[String]              = root ++ sync
    val css: Reader.SyncReader[String]  = sync.concat(() => sync)
    val csa: Reader.AsyncReader[String] = sync.concat(() => async)
    val csr: Reader.AsyncReader[String] = sync.concat(() => root)
    val cas: Reader.AsyncReader[String] = async.concat(() => sync)
    val caa: Reader.AsyncReader[String] = async.concat(() => async)
    val car: Reader.AsyncReader[String] = async.concat(() => root)
    val crs: Reader[String]             = root.concat(() => sync)

    val chained1: Reader.AsyncReader[String] = sync ++ sync ++ async
    val chained2: Reader.AsyncReader[String] = sync ++ async ++ sync
    val widened: Reader.SyncReader[Any]      = Reader.single[Int](1) ++ Reader.single[Any]("widened")

    val _ = (ss, sa, sr, as, aa, ar, rs, css, csa, csr, cas, caa, car, crs, chained1, chained2, widened)
  }

  def spec: Spec[TestEnvironment, Any] = suite("Reader composition signatures")(
    test("all static result combinations compile") {
      staticResultMatrix()
      assertTrue(true)
    },
    test("known sync composition does not evaluate its RHS during construction") {
      var evaluated                           = 0
      val composed: Reader.SyncReader[String] = Reader.single("left") ++ {
        evaluated += 1
        Reader.single("right")
      }
      val before = evaluated == 0
      val left   = composed.read[Any](null)
      val middle = evaluated == 0
      val right  = composed.read[Any](null)
      assertTrue(before, left == "left", middle, right == "right", evaluated == 1)
    },
    test("unknown composition does not evaluate its RHS during construction") {
      var evaluated                            = 0
      val tail: Reader[String]                 = Reader.single("right").toAsync
      val composed: Reader.AsyncReader[String] = Reader.single("left") ++ {
        evaluated += 1
        tail
      }
      val before = evaluated == 0
      for {
        left  <- run(composed.read[Any](null))
        middle = evaluated == 0
        right <- run(composed.read[Any](null))
      } yield assertTrue(before, left == "left", middle, right == "right", evaluated == 1)
    }
  )
}
