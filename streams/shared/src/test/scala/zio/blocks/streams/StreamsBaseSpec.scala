package zio.blocks.streams

import zio.durationInt
import zio.test._

/** Base trait for all streams test specs. */
trait StreamsBaseSpec extends ZIOSpecDefault {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM)
      zio.Chunk(TestAspect.timeout(30.seconds), TestAspect.timed)
    else
      zio.Chunk(TestAspect.timeout(30.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))
}
