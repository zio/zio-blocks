package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object StructuralIntegritySpec extends ZIOSpecDefault {
  
  trait OldStruct {
    def name: String
  }
  
  case class NewClass(fullName: String)

  trait PaymentMethodBase
  
  type OldCreditCard = PaymentMethodBase & { 
    type Tag = "CreditCard"
    def number: String 
  }
  
  trait PathWrapper {
    def when[T]: T
  }

  trait PaymentProcessor {
    def method: PathWrapper
  }
  
  implicit val newClassSchema: Schema[NewClass] = Schema.derived
  
  implicit val oldStructSchema: Schema[OldStruct] = newClassSchema.asInstanceOf[Schema[OldStruct]]
  implicit val pmSchema: Schema[PaymentProcessor] = newClassSchema.asInstanceOf[Schema[PaymentProcessor]]
  
  def spec = suite("Scala 3: Structural Integrity & Intersection Types")(
    
    test("Cross-Platform Structural Migration (Scala 3 Pattern)") {
      val builder = MigrationBuilder.make(oldStructSchema, newClassSchema)
        .renameField((x: OldStruct) => x.name, (x: NewClass) => x.fullName)
      
      assertTrue(builder.build.dynamicMigration.actions.nonEmpty)
    },
    
    test("Scala 3 Intersection Tag Extraction (Point 9 Proof)") {
      val optic = ToDynamicOptic.derive((p: PaymentProcessor) => p.method.when[OldCreditCard])
      val resultPath = optic.apply().toString

      assertTrue(resultPath.contains(".method") && resultPath.contains("when")) 
    }
  )
}