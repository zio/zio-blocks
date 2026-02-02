/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import zio.durationInt
import zio.test._

import scala.annotation.tailrec

trait ChunkBaseSpec extends ZIOSpecDefault {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)
    else zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))

  // Generator helpers that convert from zio.Chunk to zio.blocks.chunk.Chunk
  def genChunk[R, A](gen: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOf(gen).map(zioChunkToChunk)

  def genChunkN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOfN(n)(gen).map(zioChunkToChunk)

  def genChunkBounded[R, A](min: Int, max: Int)(gen: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOfBounded(min, max)(gen).map(zioChunkToChunk)

  def genNonEmptyChunk[R, A](gen: Gen[R, A]): Gen[R, NonEmptyChunk[A]] =
    Gen.chunkOf1(gen).map(zioNonEmptyChunkToNonEmptyChunk)

  // Conversion functions
  def zioChunkToChunk[A](zioChunk: zio.Chunk[A]): Chunk[A] =
    Chunk.fromIterable(zioChunk)

  def zioNonEmptyChunkToNonEmptyChunk[A](zioChunk: zio.NonEmptyChunk[A]): NonEmptyChunk[A] =
    NonEmptyChunk.fromIterable(zioChunk.head, zioChunk.tail)

  def chunkToZioChunk[A](chunk: Chunk[A]): zio.Chunk[A] =
    zio.Chunk.fromIterable(chunk)

  sealed trait ZIOTag {
    val value: String
    val subTags: List[ZIOTag] = Nil
  }
  object ZIOTag {
    case object errors  extends ZIOTag { override val value = "errors" }
    case object future  extends ZIOTag { override val value = "future" }
    case object interop extends ZIOTag {
      override val value                 = "interop"
      override val subTags: List[ZIOTag] = List(future)
    }
    case object interruption extends ZIOTag { override val value = "interruption" }
    case object regression   extends ZIOTag { override val value = "regression"   }
    case object supervision  extends ZIOTag { override val value = "supervision"  }
  }

  def zioTag(zioTag: ZIOTag, zioTags: ZIOTag*): TestAspectPoly = {
    val tags = zioTags.map(_.value) ++ getSubTags(zioTag) ++ zioTags.flatMap(getSubTags)
    TestAspect.tag(zioTag.value, tags.distinct: _*)
  }

  private def getSubTags(zioTag: ZIOTag): List[String] = {
    @tailrec
    def loop(currentZioTag: ZIOTag, remainingZioTags: List[ZIOTag], result: List[String]): List[String] =
      (currentZioTag.subTags, remainingZioTags) match {
        case (Nil, Nil)      => currentZioTag.value :: result
        case (Nil, t :: ts)  => loop(t, ts, currentZioTag.value :: result)
        case (st :: sts, ts) => loop(st, sts ++ ts, currentZioTag.value :: result)
      }
    zioTag.subTags match {
      case t :: ts => loop(t, ts, Nil)
      case Nil     => Nil
    }
  }

}
