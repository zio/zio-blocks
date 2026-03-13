package zio.blocks.schema

import zio.durationInt
import zio.test._

trait SchemaBaseSpec extends ZIOSpecDefault {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)
    else zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))
}
