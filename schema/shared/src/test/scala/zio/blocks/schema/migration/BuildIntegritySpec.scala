package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object BuildIntegritySpec extends ZIOSpecDefault {
  case class V1(id: Int)
  case class V2(id: Int, version: String) // একটি নতুন ফিল্ড 'version' যোগ হয়েছে

  implicit val v1Schema: Schema[V1] = Schema.derived
  implicit val v2Schema: Schema[V2] = Schema.derived

  def spec = suite("Point 5: Final Build Integrity Verification")(
    test("build() should block incomplete migrations (Google Standard)") {
      // এখানে আমরা V1 থেকে V2 তে যেতে চাইছি, কিন্তু 'version' ফিল্ডটি অ্যাড করিনি।
      // আমাদের বিল্ডারকে এটি ডিটেক্ট করতে হবে।
      val builder = MigrationBuilder.make[V1, V2]
      
      try {
        builder.build // এটি এরর থ্রো করার কথা
        assertTrue(false) // যদি এখানে পৌঁছায়, তবে ভেরিফিকেশন ফেইল
      } catch {
        case e: RuntimeException => 
          assertTrue(e.getMessage.contains("Incomplete Migration"))
      }
    }
  )
}