package zio.blocks.schema

import zio.test._

object StructuralVersionSpecificSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("StructuralVersionSpecificSpec (Scala 2)")(
    test("StructuralRecord creates record") {
      val record = StructuralRecord("name" -> "Alice")
      assertTrue(record.selectDynamic("name") == "Alice")
    }
  )
}
