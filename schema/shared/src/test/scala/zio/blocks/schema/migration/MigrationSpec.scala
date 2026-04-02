package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = DeriveSchema.gen[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, active: Boolean)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = DeriveSchema.gen[PersonV2]
  }

  def spec = suite("MigrationSpec")(
    test("apply dynamic actions to add, rename, and drop fields") {
      val v1    = PersonV1("Alice", 30)
      val dynV1 = PersonV1.schema.toDynamicValue(v1)

      val migration =
        MigrationBuilder[PersonV1, PersonV2, PersonV1](PersonV1.schema, PersonV2.schema, DynamicMigration.empty)
          .rename(p".name", "fullName")
          .addField(p".active", SchemaExpr.Literal(true, Schema[Boolean]))
          .buildPartial

      val result = migration(v1)

      assertTrue(result == Right(PersonV2("Alice", 30, true)))
    }
  )

}
