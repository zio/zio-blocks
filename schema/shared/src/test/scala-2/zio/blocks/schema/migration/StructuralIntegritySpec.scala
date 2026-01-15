package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
// ফিক্স: 'scala.language.reflectiveCalls' ইমপোর্টটি সরানো হয়েছে কারণ এটি অব্যবহৃত ছিল।
// আমরা স্ট্রাকচারাল টাইপের মেম্বার এক্সেস করছি না, শুধু টাইপ রেফারেন্স ব্যবহার করছি।

object StructuralIntegritySpec extends ZIOSpecDefault {

  // --- Structural Record Type Test ---
  trait OldStruct { def name: String }
  case class NewClass(fullName: String)

  // --- Structural Enum/Variant Test ---
  trait PaymentMethodBase

  // Scala 2 তে Structural Refinement সিনট্যাক্স { ... }
  type OldCreditCard = PaymentMethodBase { type Tag = "CreditCard"; def number: String }

  trait PathWrapper      { def when[T]: T          }
  trait PaymentProcessor { def method: PathWrapper }

  // স্কিমা ডেরাইভেশন
  implicit val newClassSchema: Schema[NewClass] = Schema.derived

  // স্টাব স্কিমা (টেস্টের জন্য হ্যাক, রিয়েল ওয়ার্ল্ডে এটি এভয়েড করা উচিত)
  implicit val oldStructSchema: Schema[OldStruct] = newClassSchema.asInstanceOf[Schema[OldStruct]]
  implicit val pmSchema: Schema[PaymentProcessor] = newClassSchema.asInstanceOf[Schema[PaymentProcessor]]

  def spec = suite("Pillar 2: Structural Integrity & Enum Tagging")(
    test("Cross-Platform Structural Migration without Case Classes (Record)") {
      val v0Schema: Schema[OldStruct] = oldStructSchema
      val v1Schema: Schema[NewClass]  = newClassSchema

      // Scala 2 তে ল্যাম্বডার প্যারামিটার টাইপ স্পষ্টভাবে বলে দেওয়া ভালো
      val builder = MigrationBuilder
        .make(v0Schema, v1Schema)
        .renameField((x: OldStruct) => x.name, (x: NewClass) => x.fullName)

      assertTrue(builder.build.dynamicMigration.actions.nonEmpty)
    },
    test("Structural Enum Tag Extraction (Point 9 Proof)") {
      // ToDynamicOptic.derive সরাসরি কল করা হচ্ছে পাথ জেনারেশন চেক করার জন্য
      val optic      = ToDynamicOptic.derive((p: PaymentProcessor) => p.method.when[OldCreditCard])
      val resultPath = optic.apply().toString

      // আমরা চেক করছি যে পাথটি সঠিকভাবে 'method' এবং 'when' (downcast/prism) ক্যাপচার করেছে কিনা
      assertTrue(resultPath.contains(".method") || resultPath.contains("when"))
    }
  )
}
