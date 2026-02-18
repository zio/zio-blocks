package zio.blocks.schema

import org.scalatest.wordspec.AnyWordSpec
import zio.blocks.schema.Migration

class MigrationTest extends AnyWordSpec {
  "Migration" should {
    "correctly remove a field from a schema" in {
      val oldSchema = Map("name" -> "John", "age" -> 30, "email" -> "john@example.com")
      val newSchema = Migration.removeField(oldSchema, "email")
      assert(newSchema == Map("name" -> "John", "age" -> 30))
    }
  }
}