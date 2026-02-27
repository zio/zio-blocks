package zio.blocks.http

import zio._
import zio.test._

trait HttpModelBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    Chunk(TestAspect.timeout(60.seconds), TestAspect.timed)
}
