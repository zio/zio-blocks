package zio.blocks.schema

import org.scalatest.funsuite.AnyFunSuite

class SchemaDiffTest extends AnyFunSuite {
  test("V1ToV2 migration") {
    assert(Migration.V1ToV2(1) === Some(2))
    assert(Migration.V1ToV2(2) === None)
  }

  // Add more tests as needed
}