package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for structural union type (enum) support in the migration system.
 *
 * Structural union types (Scala 3 union types) are compile-time only types. At
 * runtime, they are represented as DynamicValue.Variant.
 *
 * This demonstrates the migration system's ability to work with enum-like
 * structural types as specified in issue #519.
 */
object StructuralEnumSpec extends ZIOSpecDefault {

  // Structural union type definitions (compile-time only)
  type OldCreditCard = {
    def tag: "CreditCard"
    def number: String
  }

  type OldWire = {
    def tag: "Wire"
    def account: String
  }

  // Union type (Scala 3 feature)
  type OldPayment = OldCreditCard | OldWire

  // Runtime representation for testing
  sealed trait PaymentRepr
  case class CreditCard(number: String) extends PaymentRepr
  case class Wire(account: String)      extends PaymentRepr

  val oldSchema: Schema[OldPayment] =
    Schema.derived[PaymentRepr].asInstanceOf[Schema[OldPayment]]

  // Target enum
  sealed trait NewPayment
  case class CC(number: String)            extends NewPayment
  case class WireTransfer(account: String) extends NewPayment

  val newSchema: Schema[NewPayment] = Schema.derived[NewPayment]

  def spec = suite("StructuralEnumSpec (Scala 3)")(
    test("Can migrate structural union type using renameCase") {
      val migration = Migration
        .newBuilder[OldPayment, NewPayment](oldSchema, newSchema)
        .renameCase("CreditCard", "CC")
        .renameCase("Wire", "WireTransfer")
        .buildUnchecked

      {

        // Test CreditCard -> CC
        val oldCard = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(Vector("number" -> DynamicValue.Primitive(PrimitiveValue.String("1234-5678"))))
        )

        val resCard = migration.dynamicMigration.apply(oldCard)

        val expectedCard = DynamicValue.Variant(
          "CC",
          DynamicValue.Record(Vector("number" -> DynamicValue.Primitive(PrimitiveValue.String("1234-5678"))))
        )

        // Test Wire -> WireTransfer
        val oldWire = DynamicValue.Variant(
          "Wire",
          DynamicValue.Record(Vector("account" -> DynamicValue.Primitive(PrimitiveValue.String("ABC123"))))
        )

        val resWire = migration.dynamicMigration.apply(oldWire)

        val expectedWire = DynamicValue.Variant(
          "WireTransfer",
          DynamicValue.Record(Vector("account" -> DynamicValue.Primitive(PrimitiveValue.String("ABC123"))))
        )

        assert(resCard)(isRight(equalTo(expectedCard))) &&
        assert(resWire)(isRight(equalTo(expectedWire)))
      }
    },

    test("Can handle multiple enum case renames") {
      sealed trait OldColor
      case object Red   extends OldColor
      case object Green extends OldColor
      case object Blue  extends OldColor

      sealed trait NewColor
      case object R extends NewColor
      case object G extends NewColor
      case object B extends NewColor

      val oldColorSchema: Schema[OldColor] = Schema.derived[OldColor]
      val newColorSchema: Schema[NewColor] = Schema.derived[NewColor]

      val migration = Migration
        .newBuilder(oldColorSchema, newColorSchema)
        .renameCase("Red", "R")
        .renameCase("Green", "G")
        .renameCase("Blue", "B")
        .buildUnchecked

      {

        val oldRed   = DynamicValue.Variant("Red", DynamicValue.Primitive(PrimitiveValue.Unit))
        val result   = migration.dynamicMigration.apply(oldRed)
        val expected = DynamicValue.Variant("R", DynamicValue.Primitive(PrimitiveValue.Unit))

        assert(result)(isRight(equalTo(expected)))
      }
    }
  )
}
