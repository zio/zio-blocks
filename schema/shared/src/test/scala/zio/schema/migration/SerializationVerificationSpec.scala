package zio.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._
import zio.blocks.schema.json.JsonFormat

object SerializationVerificationSpec extends ZIOSpecDefault {

  // Use the same generators or simpler ones
  val testMigration = DynamicMigration(
    Vector(
      MigrationAction.Rename(DynamicOptic.root.field("old"), "new"),
      MigrationAction.Optionalize(DynamicOptic.root.field("optionalField"))
    )
  )

  override def spec = suite("Serialization Verification")(
    test("DynamicMigration can be serialized and deserialized via JSON") {
      val schema = Schema[DynamicMigration]
      val codec  = schema.derive(JsonFormat.deriver)

      val encoded = codec.encode(testMigration)
      // Verify encoded is not empty/error
      // Note: encode returns Chunk[Byte] usually.

      val decoded = codec.decode(encoded)

      assert(decoded)(isRight(equalTo(testMigration)))
    }
  )
}
