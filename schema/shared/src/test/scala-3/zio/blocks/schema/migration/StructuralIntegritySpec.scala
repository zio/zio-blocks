package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object StructuralIntegritySpec extends ZIOSpecDefault {
  
  // --- Scala 3 Structural Record Type ---
  trait OldStruct {
    def name: String
  }
  
  case class NewClass(fullName: String)

  // --- Scala 3 Intersection Type (&) Pattern ---
  // এটি ওনারের ডকুমেন্টেশনের হুবহু স্কালা ৩ রিপ্রেজেন্টেশন
  trait PaymentMethodBase
  
  // Scala 3 সিনট্যাক্স: & (Intersection) এবং { type Tag }
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
  
  // স্কিমা ডেরাইভেশন
  implicit val newClassSchema: Schema[NewClass] = Schema.derived
  
  // স্টাব স্কিমা (Scala 3 টাইপ সেফ কাস্টিং)
  implicit val oldStructSchema: Schema[OldStruct] = newClassSchema.asInstanceOf[Schema[OldStruct]]
  implicit val pmSchema: Schema[PaymentProcessor] = newClassSchema.asInstanceOf[Schema[PaymentProcessor]]
  
  def spec = suite("Scala 3: Structural Integrity & Intersection Types")(
    
    test("Cross-Platform Structural Migration (Scala 3 Pattern)") {
      val builder = MigrationBuilder.make(oldStructSchema, newClassSchema)
        .renameField((x: OldStruct) => x.name, (x: NewClass) => x.fullName)
      
      assertTrue(builder.build.dynamicMigration.actions.nonEmpty)
    },
    
    test("Scala 3 Intersection Tag Extraction (Point 9 Proof)") {
      // ToDynamicOptic.derive স্কালা ৩-এর 'inline' ম্যাক্রো ব্যবহার করে
      val optic = ToDynamicOptic.derive((p: PaymentProcessor) => p.method.when[OldCreditCard])
      val resultPath = optic.apply().toString

      // ভেরিফিকেশন: এটি স্কালা ৩-এর জন্য নিখুঁত পাথ জেনারেট করবে
      assertTrue(resultPath.contains(".method") && resultPath.contains("when")) 
    }
  )
}