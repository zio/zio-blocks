package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Tests for Scala 2 selector-based MigrationBuilder API.
 */
object MigrationBuilderSelectorSpec extends SchemaBaseSpec {

  // Test types
  case class PersonV1(name: String, age: Int, legacy: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(name: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSelectorSpec - Scala 2")(
    suite("Selector-based APIs")(
      test("dropFieldWithSelector uses macro to extract path") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[PersonV1, PersonV2]
          .dropFieldWithSelector(_.legacy)

        val actions = builder.currentActions
        assertTrue(actions.size == 1) &&
        assertTrue(actions.head match {
          case MigrationAction.DropField(_, "legacy", _) => true
          case _                                         => false
        })
      },
      test("renameFieldWithSelector uses macro to extract path") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[PersonV1, PersonV3]
          .dropFieldWithSelector(_.legacy)
          .renameFieldWithSelector(_.name, "fullName")

        val actions = builder.currentActions
        assertTrue(actions.exists {
          case MigrationAction.Rename(_, "name", "fullName") => true
          case _                                             => false
        })
      },
      test("selector API integrates with path-based API") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[PersonV1, PersonV2]
          .dropFieldWithSelector(_.legacy)
          .addFieldLiteral("email", "default@test.com")

        val actions = builder.currentActions
        assertTrue(actions.size == 2)
      }
    )
  )
}
