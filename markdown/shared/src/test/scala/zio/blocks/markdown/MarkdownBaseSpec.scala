package zio.blocks.markdown

import zio.durationInt
import zio.test._

trait MarkdownBaseSpec extends ZIOSpecDefault {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)
    else if (TestPlatform.isNative) {
      zio.Chunk(
        TestAspect.timeout(120.seconds),
        TestAspect.timed,
        TestAspect.sequential,
        TestAspect.size(10),
        TestAspect.samples(50)
      )
    } else zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))
}
