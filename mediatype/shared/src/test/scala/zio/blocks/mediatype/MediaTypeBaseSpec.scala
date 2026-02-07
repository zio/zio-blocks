package zio.blocks.mediatype

import zio._
import zio.test._

trait MediaTypeBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    Chunk(TestAspect.timeout(60.seconds), TestAspect.timed)
}
