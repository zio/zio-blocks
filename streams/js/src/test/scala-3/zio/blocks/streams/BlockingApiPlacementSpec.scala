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

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object BlockingApiPlacementSpec extends StreamsBaseSpec {
  private inline def missingMember(inline code: String, member: String): Boolean =
    typeCheckErrors(code).exists(error => error.message.contains(member) && error.message.contains("not a member"))

  def spec = suite("JS blocking API placement")(
    test("AsyncReader.toSync is absent directly and through wildcard imports") {
      val direct = missingMember(
        """
          import zio.blocks.streams.io.Reader
          val reader: Reader.AsyncReader[Int] = Reader.singleInt(1).toAsync
          reader.toSync
        """,
        "toSync"
      )
      val imported = missingMember(
        """
          import zio.blocks.streams._
          import zio.blocks.streams.io._
          val reader: Reader.AsyncReader[Int] = Reader.singleInt(1).toAsync
          reader.toSync
        """,
        "toSync"
      )
      assertTrue(direct, imported)
    },
    test("plain blocking terminals and start are absent") {
      val run     = missingMember("import zio.blocks.streams._; Stream(1).run(Sink.count)", "run")
      val collect = missingMember("import zio.blocks.streams._; Stream(1).runCollect", "runCollect")
      val start   = missingMember("import zio.blocks.streams._; Stream(1).start", "start")
      assertTrue(run, collect, start)
    },
    test("synchronous Sink.create is absent") {
      assertTrue(missingMember("import zio.blocks.streams._; Sink.create[Int, Unit](_ => ())", "create"))
    }
  )
}
