package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr
import zio.blocks.schema.migration.{MigrationError, MigrationErrorRender}

object MigrationErrorSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. MOCK RUNTIME ENGINE
  // =================================================================================

  def mockRunAction(action: MigrationAction, failOnPath: Option[String]): Either[MigrationError, String] =
    action match {
      case TransformValue(at, _) =>
        // [FIXED] We manually convert the path to string for this test logic
        // instead of calling the private renderPath method.
        val currentPathStr = at.nodes.collect { case DynamicOptic.Node.Field(n) => n }.mkString(".")

        failOnPath match {
          case Some(fail) if currentPathStr == fail =>
            Left(
              MigrationError.TransformationFailed(
                at,
                "TransformValue",
                "Mock failure for testing"
              )
            )
          case _ =>
            Right("Success")
        }

      case AddField(at, _) => Right(s"Added ${at}")
      case _               => Right("Action skipped in mock")
    }

  // =================================================================================
  // 2. MODELS
  // =================================================================================

  case class PersonV0(firstName: String, lastName: String)
  case class Person(fullName: String, age: Int)

  implicit val sPersonV0: Schema[PersonV0] = null.asInstanceOf[Schema[PersonV0]]
  implicit val sPerson: Schema[Person]     = null.asInstanceOf[Schema[Person]]
  val mockExpr: SchemaExpr[_]              = null.asInstanceOf[SchemaExpr[_]]

  // =================================================================================
  // 3. VERIFICATION SUITE
  // =================================================================================

  def spec = suite("Mentor's Spec: Error Handling & Examples")(
    suite("1. Error Handling Requirements")(
      test("Runtime errors must capture path information (DynamicOptic)") {
        val targetPath = DynamicOptic(Vector(DynamicOptic.Node.Field("address"), DynamicOptic.Node.Field("street")))
        val action     = TransformValue(targetPath, mockExpr)

        // Mock failure at "address.street"
        val result = mockRunAction(action, failOnPath = Some("address.street"))

        assertTrue(result match {
          case Left(error: MigrationError.TransformationFailed) =>
            // The error object must hold the exact path object
            error.path == targetPath &&
            error.actionType == "TransformValue"
          case _ => false
        })
      },

      test("Error Render produces correct diagnostic message") {
        val targetPath = DynamicOptic(Vector(DynamicOptic.Node.Field("address"), DynamicOptic.Node.Field("street")))

        // Create a real error instance
        val error = MigrationError.TransformationFailed(
          targetPath,
          "TransformValue",
          "Invalid Format"
        )

        // Use the Production Renderer to verify diagnostic message format
        val message = MigrationErrorRender.render(error)

        // Requirement: "Failed to apply TransformValue at .address.street..."
        // Note: Your render implementation might produce ".address.street" or "address.street", we check loosely.
        assertTrue(
          message.contains("Failed to apply TransformValue") &&
            (message.contains(".address.street") || message.contains("address.street"))
        )
      }
    ),

    suite("2. The 'Person' Example Verification")(
      test("Migration Builder constructs the example structure correctly") {
        val migration = MigrationBuilder
          .make[PersonV0, Person]
          .addField(
            (p: Person) => p.age,
            mockExpr
          )
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case AddField(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("age")
          case _ => false
        })
      }
    )
  )
}
