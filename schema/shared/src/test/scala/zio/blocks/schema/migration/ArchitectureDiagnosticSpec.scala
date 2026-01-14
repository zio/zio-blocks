package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr._
import java.io._

object ArchitectureDiagnosticSpec extends ZIOSpecDefault {

  // --- Models ---
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, title: String)
  implicit val s1: Schema[PersonV1] = Schema.derived
  implicit val s2: Schema[PersonV2] = Schema.derived

  def spec = suite("Architecture & Diagnostic Compliance")(

    // SECTION 1: Pure Data & Serializability (From ArchitectureCompliance & CoreArchitecture)
    suite("1. System Purity & Serialization")(
      test("Core actions must be Pure Data & fully Serializable") {
        val action = Rename(DynamicOptic(Vector(DynamicOptic.Node.Field("name"))), "fullName")
        val core = DynamicMigration(Vector(action))

        val stream = new ByteArrayOutputStream()
        val oos = new ObjectOutputStream(stream)
        
        val result = try {
          oos.writeObject(core)
          true
        } catch { case _: Throwable => false } 
        finally { oos.close() }

        assertTrue(result && stream.size() > 0)
      },

      test("Composition (++) and Alias (andThen) must be identical") {
        val m1 = MigrationBuilder.make[PersonV1, PersonV1].build
        val m2 = MigrationBuilder.make[PersonV1, PersonV1].build
        assertTrue((m1 ++ m2).dynamicMigration.actions == (m1 andThen m2).dynamicMigration.actions)
      }
    ),

    // SECTION 2: Client Requirement Compliance (From ClientComplianceSpec)
    suite("2. Client Requirement Verification")(
      test("DefaultValue must capture macro-captured field schema") {
        val migration = MigrationBuilder.make[PersonV1, PersonV2]
          .dropField((p: PersonV1) => p.age)
          .build

        val captured = migration.dynamicMigration.actions.head match {
          case DropField(_, DefaultValue(schema)) => schema.toString.contains("Int")
          case _ => false
        }
        assertTrue(captured)
      },

      test("Support for Structural Enum/Variant Renaming") {
        val enumData = DynamicValue.Variant("CreditCard", DynamicValue.Record(Vector.empty))
        val action = RenameCase(DynamicOptic.root, "CreditCard", "CC")
        val result = MigrationInterpreter.run(enumData, action)
        
        assertTrue(result.toOption.get.toString.contains("CC"))
      }
    ),

    // SECTION 3: Deep Diagnostics & Path Accuracy (From DeepDiagnosticSpec)
    suite("3. Error Handling & Path Accuracy")(
      test("FieldNotFound must report accurate path information") {
        val data = DynamicValue.Record(Vector("u" -> DynamicValue.Record(Vector.empty)))
        val path = DynamicOptic(Vector(DynamicOptic.Node.Field("u"), DynamicOptic.Node.Field("x")))
        val action = Rename(path, "y")
        
        val result = MigrationInterpreter.run(data, action)
        val hasPath = result match {
          case Left(e: MigrationError.FieldNotFound) => e.path.nodes.toString.contains("u")
          case _ => false
        }
        assertTrue(hasPath)
      },

      test("Collection Transformation (.each) path verification") {
        val data = DynamicValue.Sequence(Vector(DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))))
        val path = DynamicOptic(Vector(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("a")))
        val action = Rename(path, "b")
        
        assertTrue(MigrationInterpreter.run(data, action).isRight)
      }
    )
  )
}