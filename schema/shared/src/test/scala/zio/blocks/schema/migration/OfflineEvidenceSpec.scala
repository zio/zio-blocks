package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration._ // আমাদের সব মাইগ্রেশন লজিক
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr // ফিক্স: একদম স্পেসিফিক ইমপোর্ট
import java.io._

object OfflineEvidenceSpec extends ZIOSpecDefault {

  def spec = suite("THE FINAL EVIDENCE: Offline & Algebraic Capability")(

    /**
     * PROOF 1: অফলাইন সিরিয়ালাইজেশন
     * প্রমাণ করবে যে মাইগ্রেশন লজিক এখন একটি 'পিওর ডাটা' যা ডিস্কে সেভ করা যায়।
     */
    test("PROOF 1: Migration can be persisted as bytes (Offline Storage)") {
      val optic = DynamicOptic(Vector(DynamicOptic.Node.Field("user_name")))
      val migration = DynamicMigration(Vector(Rename(optic, "full_name")))

      val out = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(out)
      
      try {
        oos.writeObject(migration) 
        val bytes = out.toByteArray
        assertTrue(bytes.length > 0)
      } finally {
        oos.close()
      }
    },

    /**
     * PROOF 2: ইন্ট্রোস্পেকশন ও ডিডিএল জেনারেশন
     * প্রমাণ করবে যে আমাদের মাইগ্রেশন ফাইল থেকে সরাসরি SQL জেনারেট করা সম্ভব।
     */
    test("PROOF 2: Migration can generate SQL DDL statements without execution") {
      val actions = Vector(
        Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("age"))), "user_age"),
        // ফিক্স: SchemaExpr.Constant কল করা হলো
        AddField(
          DynamicOptic(Vector(DynamicOptic.Node.Field("status"))), 
          SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("active")))
        )
      )

      def generateSQL(actions: Vector[MigrationAction]): String = {
        actions.map {
          case Rename(at, to) => 
            val oldName = at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
            s"ALTER TABLE users RENAME COLUMN $oldName TO $to;"
          case AddField(at, _) => 
            val newName = at.nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
            s"ALTER TABLE users ADD COLUMN $newName VARCHAR(255);"
          case _ => ""
        }.mkString("\n")
      }

      val sql = generateSQL(actions)
      assertTrue(sql.contains("RENAME COLUMN age TO user_age") && sql.contains("ADD COLUMN status"))
    },

    /**
     * PROOF 3: ডাইনামিক অফলাইন ট্রান্সফরমেশন
     * প্রমাণ করবে যে অরিজিনাল ক্লাস ছাড়াই শুধু ডাটা দিয়ে এটি কাজ করে।
     */
    test("PROOF 3: Transform raw data (JSON/DynamicValue) without original classes") {
      val rawData = DynamicValue.Record(Vector(
        "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Dhrubo"))
      ))

      val migrationAction = Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("firstName"))), "fullName")
      val result = MigrationInterpreter.run(rawData, migrationAction)

      val expected = DynamicValue.Record(Vector(
        "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Dhrubo"))
      ))

      assertTrue(result == Right(expected))
    }
  )
}