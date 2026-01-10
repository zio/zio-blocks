package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.schema.DynamicValue // 🔥 FIX: Removed StandardType import
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

object SerializationSpec extends ZIOSpecDefault {

  // --- 1. Serialization Helpers (Java Standard) ---
  def serialize[T](obj: T): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(obj)
    oos.close()
    stream.toByteArray
  }

  def deserialize[T](bytes: Array[Byte]): T = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    ois.readObject().asInstanceOf[T]
  }

  // --- 2. Test Suite ---
  def spec = suite("Requirement 1: DynamicMigration Serialization")(
    
    test("DynamicMigration must be fully serializable via ObjectOutputStream") {
      
      // Step A: Create a Complex Migration Plan (Pure Data)
      val pathAge = DynamicOptic(Vector(OpticStep.Field("age")))
      val pathName = DynamicOptic(Vector(OpticStep.Field("name")))
      
      // 🔥 FIX: DynamicValue.Primitive now takes only 1 argument (the value)
      val defaultValue = DynamicValue.Primitive(18) 
      val literalExpr = SchemaExpr.ConstantValue[Any](defaultValue)

      val originalMigration = DynamicMigration(Vector(
        MigrationAction.AddField(pathAge, literalExpr),
        MigrationAction.RenameField(pathName, "fullName")
      ))

      // Step B: Serialize
      val bytes = serialize(originalMigration)
      
      // Step C: Deserialize
      val loadedMigration = deserialize[DynamicMigration](bytes)

      // Step D: Verify (Equality & Behavior)
      assert(bytes.length)(isGreaterThan(0)) &&
      assert(loadedMigration)(equalTo(originalMigration)) &&
      assert(loadedMigration.actions.length)(equalTo(2)) &&
      assert(loadedMigration.actions.head)(isSubtype[MigrationAction.AddField](anything))
    }
  )
}