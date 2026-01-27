package zio.blocks.schema

import zio.durationInt
import zio.test.{TestAspect, TestAspectAtLeastR, TestEnvironment, TestPlatform, ZIOSpecDefault}

trait SchemaBaseSpec extends ZIOSpecDefault {
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
    } else {
      zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))
    }

  import zio.blocks.typeid.{TypeId, TypeDefKind}

  protected def stripMetadata[A](typeId: TypeId[A]): TypeId[A] =
    TypeId(
      typeId.dynamic.copy(
        kind = TypeDefKind.Class(),
        typeParams = Nil,
        parents = Nil,
        annotations = Nil
      )
    )
}
