package zio.blocks.schema.structural

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object PureStructuralTypeSpec extends SchemaBaseSpec {

  def spec: Spec[Any, Nothing] = suite("PureStructuralTypeSpec - DISABLED: needs TypeId migration")(
    test("placeholder - structural types need TypeId migration") {
      assertTrue(true)
    }
  ) @@ TestAspect.ignore
}
