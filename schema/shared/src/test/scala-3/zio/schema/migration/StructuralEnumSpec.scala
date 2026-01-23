package zio.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._


object StructuralEnumSpec extends ZIOSpecDefault {

  // 1. Structural Types Definition (Compile-time only)
  type OldCreditCard = {
    def tag: "CreditCard"
    def number: String
  }
  
  type OldWire = {
    def tag: "Wire"
    def account: String
  }
  
  // Union Type
  type OldPayment = OldCreditCard | OldWire

  // 2. Runtime Representation 
  sealed trait PaymentRepr
  case class CreditCard(number: String) extends PaymentRepr
  case class Wire(account: String) extends PaymentRepr
  
  // Define schemas as implicits to help resolution, but careful with names
  val oldSchema: Schema[OldPayment] = 
    Schema.derived[PaymentRepr].asInstanceOf[Schema[OldPayment]]

  sealed trait NewPayment
  case class CC(number: String) extends NewPayment
  case class WireTransfer(account: String) extends NewPayment
  
  val newSchema: Schema[NewPayment] = Schema.derived[NewPayment]

  def spec = suite("StructuralEnumSpec (Scala 3)")(
    test("Can migrate structural union type (Enum) using renameCase") {
      
      // Pass schemas explicitly providing type parameters to help inference
      val migrationResult = Migration.newBuilder[OldPayment, NewPayment](oldSchema, newSchema)
        .renameCase("CreditCard", "CC")
        .renameCase("Wire", "WireTransfer")
        .build
        
      assert(migrationResult)(isRight(anything))
      val migration = migrationResult.toOption.get
      
      // Test 1: CreditCard
      val oldCard = DynamicValue.Variant(
        "CreditCard",
        DynamicValue.Record(Vector("number" -> DynamicValue.Primitive(PrimitiveValue.String("123"))))
      )
      
      val resCard = migration.dynamicMigration.apply(oldCard)
      
      val expectedCard = DynamicValue.Variant(
        "CC",
        DynamicValue.Record(Vector("number" -> DynamicValue.Primitive(PrimitiveValue.String("123"))))
      )
      
      // Test 2: Wire
      val oldWire = DynamicValue.Variant(
        "Wire",
        DynamicValue.Record(Vector("account" -> DynamicValue.Primitive(PrimitiveValue.String("ABC"))))
      )
       
      val resWire = migration.dynamicMigration.apply(oldWire)
      
      val expectedWire = DynamicValue.Variant(
        "WireTransfer",
        DynamicValue.Record(Vector("account" -> DynamicValue.Primitive(PrimitiveValue.String("ABC"))))
      )
      
      assert(resCard)(isRight(equalTo(expectedCard))) &&
      assert(resWire)(isRight(equalTo(expectedWire)))
    }
  )
}
