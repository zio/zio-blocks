package zio.blocks.schema

import zio._
import zio.test._
import zio.test.environment._
import scala.concurrent.duration._

object MigrationSpec extends DefaultRunnableSpec {
  def spec = suite("MigrationSpec")(
    test("test migration functionality") {
      val schema = Schema(
        fields = Map(
          "id" -> Field("id", FieldType.Int, isRequired = true),
          "name" -> Field("name", FieldType.String, isRequired = false, defaultValue = Some("unknown"))
        )
      )

      val migratedSchema = migrate(schema)

      assert(migratedSchema.fields) {
        contains("id") and contains("name")
      } && assert(migratedSchema.fields("name").defaultValue) {
        isSome(equalTo("unknown"))
      }
    }
  )
}

test("test field addition with defaults") {
      val schema = Schema(
        fields = Map(
          "id" -> Field("id", FieldType.Int, isRequired = true),
          "name" -> Field("name", FieldType.String, isRequired = false, defaultValue = Some("unknown"))
        )
      )

      val migratedSchema = migrate(schema)

      assert(migratedSchema.fields) {
        contains("id") and contains("name")
      } && assert(migratedSchema.fields("name").defaultValue) {
        isSome(equalTo("unknown"))
      }
    }