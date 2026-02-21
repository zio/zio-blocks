package zio.blocks.schema.migration

import zio.test._
// [FIX] Avoid wildcard import from zio.blocks.schema to prevent SchemaExpr conflict
import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.migration.MigrationAction._
// [FIX] Explicitly import the correct SchemaExpr
import zio.blocks.schema.migration.SchemaExpr

object SchemaExprSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. MODELS FOR TYPE MODELING VERIFICATION
  // =================================================================================

  case class UserV1(name: String, age: Int)
  case class UserV2(name: String, age: String)

  sealed trait PaymentV1
  case class CreditCardV1(number: String)    extends PaymentV1
  case class WireTransferV1(account: String) extends PaymentV1

  sealed trait PaymentV2
  case class PlasticCardV2(number: String)   extends PaymentV2
  case class WireTransferV2(account: String) extends PaymentV2

  case class ProductV1(id: String)
  case class ProductV2(id: String, stock: Int = 100)

  // Mocking Schemas
  implicit val sUserV1: Schema[UserV1]    = null.asInstanceOf[Schema[UserV1]]
  implicit val sUserV2: Schema[UserV2]    = null.asInstanceOf[Schema[UserV2]]
  implicit val sPayV1: Schema[PaymentV1]  = null.asInstanceOf[Schema[PaymentV1]]
  implicit val sPayV2: Schema[PaymentV2]  = null.asInstanceOf[Schema[PaymentV2]]
  implicit val sProdV1: Schema[ProductV1] = null.asInstanceOf[Schema[ProductV1]]
  implicit val sProdV2: Schema[ProductV2] = null.asInstanceOf[Schema[ProductV2]]

  implicit val sCCV1: Schema[CreditCardV1]  = null.asInstanceOf[Schema[CreditCardV1]]
  implicit val sPCV2: Schema[PlasticCardV2] = null.asInstanceOf[Schema[PlasticCardV2]]

  // [FIX] Using the correct SchemaExpr from zio.blocks.schema.migration (1 type arg)
  // We use SchemaExpr[_] or specific type if known. Assuming Int/String for verification.

  val defaultStockExpr: SchemaExpr[Int] = null.asInstanceOf[SchemaExpr[Int]]

  // For conversion, we assume the type arg represents the result type or the expression type.
  val intToStringExpr: SchemaExpr[String] = null.asInstanceOf[SchemaExpr[String]]

  // =================================================================================
  // 2. VERIFICATION SUITE
  // =================================================================================

  def spec = suite("Deep Verification: SchemaExpr & Type Modeling")(
    suite("1. Constraint Check: Primitive -> Primitive")(
      test("Should allow transforming Int to String (Primitive Conversion)") {
        val migration = MigrationBuilder
          .make[UserV1, UserV2]
          .changeFieldType(
            (s: UserV1) => s.age,
            (t: UserV2) => t.age,
            intToStringExpr
          )
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case ChangeType(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("age")
          case _ => false
        })
      }
    ),

    suite("2. SchemaExpr.DefaultValue Verification")(
      test("addField should store the DefaultValue expression correctly") {
        val migration = MigrationBuilder
          .make[ProductV1, ProductV2]
          .addField(
            (t: ProductV2) => t.stock,
            defaultStockExpr
          )
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case AddField(at, default) =>
            at.nodes.last == DynamicOptic.Node.Field("stock") &&
            default == defaultStockExpr
          case _ => false
        })
      },

      test("DropField should store default for reverse migration") {
        val migration = MigrationBuilder
          .make[ProductV2, ProductV1]
          .dropField(
            (s: ProductV2) => s.stock,
            defaultStockExpr
          )
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case DropField(at, defaultRev) =>
            at.nodes.last == DynamicOptic.Node.Field("stock") &&
            defaultRev == defaultStockExpr
          case _ => false
        })
      }
    ),

    suite("3. Enum Type Modeling (Union Tags)")(
      test("Macro should correctly identify Case Tags for renaming") {
        val migration = MigrationBuilder
          .make[PaymentV1, PaymentV2]
          .renameCase("CreditCardV1", "PlasticCardV2")
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case RenameCase(_, from, to) =>
            from == "CreditCardV1" && to == "PlasticCardV2"
          case _ => false
        })
      },

      test("Macro should identify Tag for transformation context") {
        // Use the helper from MigrationActionsSpec
        import MigrationActionsSpec.MigrationBuilderOps

        val migration = MigrationBuilder
          .make[PaymentV1, PaymentV2]
          .transformCaseManual[CreditCardV1, PlasticCardV2](
            "CreditCardV1",
            (b) =>
              b.renameField(
                (s: CreditCardV1) => s.number,
                (t: PlasticCardV2) => t.number
              )
          )
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.exists {
          case TransformCase(at, actions) =>
            at.nodes.last == DynamicOptic.Node.Field("CreditCardV1") &&
            actions.exists {
              case Rename(fieldAt, to) =>
                fieldAt.nodes.last == DynamicOptic.Node.Field("number")
              case _ => false
            }
          case _ => false
        })
      }
    )
  )
}
