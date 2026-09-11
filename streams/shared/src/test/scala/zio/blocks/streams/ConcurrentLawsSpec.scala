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

import zio.blocks.chunk.Chunk
import zio.durationInt
import zio.test._

object ConcurrentLawsSpec extends StreamsBaseSpec {

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)

  def spec: Spec[TestEnvironment, Any] = suite("Concurrent laws")(
    test("mergeAll(1) ≡ flatMap(identity) as sets") {
      check(Gen.listOfBounded(0, 5)(Gen.listOfBounded(0, 5)(Gen.int(0, 100)))) { lists =>
        val chunks          = Chunk.fromIterable(lists.map(Chunk.fromIterable(_)))
        val streamOfStreams = Stream.fromChunk(chunks.map(Stream.fromChunk(_)))
        for {
          merged     <- runAsync(Stream.mergeAll(1)(streamOfStreams).runCollectAsync)
          flatMapped <- runAsync(
                          Stream.fromChunk(chunks.map(Stream.fromChunk(_))).flatMap(identity).runCollectAsync
                        )
        } yield assertTrue(
          merged.fold(_ => Chunk.empty[Int], identity).toSet ==
            flatMapped.fold(_ => Chunk.empty[Int], identity).toSet
        )
      }
    },
    test("mapPar(1)(f) ≡ map(f) as sets") {
      check(Gen.listOf(Gen.int(0, 100))) { values =>
        val stream = Stream.fromChunk(Chunk.fromIterable(values))
        for {
          byMapPar <- runAsync(stream.mapPar(1)(_ * 2).runCollectAsync)
          byMap    <- runAsync(stream.map(_ * 2).runCollectAsync)
        } yield assertTrue(
          byMapPar.fold(_ => Chunk.empty[Int], identity).toSet ==
            byMap.fold(_ => Chunk.empty[Int], identity).toSet
        )
      }
    },
    test("flatMapPar(n)(f) ≡ mergeAll(n)(map(f)) as sets") {
      check(Gen.listOf(Gen.int(0, 10))) { values =>
        val stream = Stream.fromChunk(Chunk.fromIterable(values))
        val f      = (i: Int) => Stream(i, i * 10)
        for {
          byFlatMapPar <- runAsync(stream.flatMapPar(4)(f).runCollectAsync)
          byMergeAll   <- runAsync(Stream.mergeAll(4)(stream.map(f)).runCollectAsync)
        } yield assertTrue(
          byFlatMapPar.fold(_ => Chunk.empty[Int], identity).toSet ==
            byMergeAll.fold(_ => Chunk.empty[Int], identity).toSet
        )
      }
    }
  ) @@ TestAspect.sequential
}
