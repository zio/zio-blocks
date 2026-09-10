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

import java.util.concurrent.{CountDownLatch, TimeUnit}

import zio.ZIO
import zio.blocks.streams.internal.EndOfStream
import zio.blocks.streams.io.Reader
import zio.durationInt
import zio.test._

object MapParInterruptedCoordinatorSpec extends StreamsBaseSpec {
  def spec = suite("mapPar interrupted coordinator")(
    test("an interrupted coordinator still delivers an ordered stop marker to every worker") {
      ZIO.attemptBlocking {
        val mappingStarted = new CountDownLatch(1)
        val releaseMapping = new CountDownLatch(1)
        val eofReached     = new CountDownLatch(1)
        val upstream       = new Reader.SyncReader[Int] {
          private var pulls = 0

          override def jvmType: JvmType      = JvmType.Int
          def isClosed: Boolean              = pulls >= 3
          def close(): Unit                  = ()
          def read[A >: Int](sentinel: A): A = sentinel

          override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Int <:< Int): Int = {
            pulls += 1
            pulls match {
              case 1 =>
                dest(offset) = 1
                1
              case 2 =>
                require(mappingStarted.await(5, TimeUnit.SECONDS), "worker did not start mapping")
                dest(offset) = 2
                1
              case _ =>
                Thread.currentThread().interrupt()
                eofReached.countDown()
                -1
            }
          }
        }
        val reader = Platform.createMapParReader[Int, Int](
          upstream,
          1,
          value => {
            if (value == 1) {
              mappingStarted.countDown()
              releaseMapping.await()
            }
            value
          },
          1,
          JvmType.Int,
          JvmType.Int
        )

        require(eofReached.await(5, TimeUnit.SECONDS), "coordinator did not reach EOF")
        releaseMapping.countDown()
        val first = reader.read[Any](EndOfStream)
        val next  = reader.read[Any](EndOfStream)
        val eof   = reader.read[Any](EndOfStream)
        reader.close()

        assertTrue(
          Set(first.asInstanceOf[Int], next.asInstanceOf[Int]) == Set(1, 2),
          eof.asInstanceOf[AnyRef] eq EndOfStream
        )
      }
    } @@ TestAspect.timeout(15.seconds)
  )
}
