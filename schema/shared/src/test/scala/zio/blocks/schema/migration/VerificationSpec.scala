package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.lang.reflect.Modifier

object VerificationSpec extends ZIOSpecDefault {

  // --- 🛠️ 1. Verification Engine (Reflection & Serialization) ---

  // Serializer Helper
  def serialize[T](obj: T): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(obj)
    oos.close()
    stream.toByteArray
  }

  // Deserializer Helper
  def deserialize[T](bytes: Array[Byte]): T = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    ois.readObject().asInstanceOf[T]
  }

  // **Strict Auditor: Checks for Functions, Lambdas, and Vars**
  def inspectObjectForForbiddenTypes(obj: Any): List[String] = {
    if (obj == null) return Nil
    
    val clazz = obj.getClass
    if (clazz.isPrimitive || clazz == classOf[String] || clazz == classOf[Integer]) return Nil

    // Get all fields (private/public)
    val fields = clazz.getDeclaredFields
    var errors = List.empty[String]

    fields.foreach { field =>
      field.setAccessible(true)
      val fieldType = field.getType.getName
      val fieldName = field.getName

      // ❌ Check A: Forbidden Runtime Types
      if (fieldType.contains("scala.Function") || 
          fieldType.contains("java.util.function") || 
          fieldType.contains("scala.reflect.ClassTag") ||
          fieldType.contains("scala.reflect.runtime.universe.TypeTag") ||
          fieldType.contains("scala.reflect.api.Mirror")) {
        errors = errors :+ s"❌ VIOLATION: Field '$fieldName' in '${clazz.getSimpleName}' contains a Function/Lambda/Mirror ($fieldType)"
      }

      // ❌ Check B: Mutability (var)
      // Note: In Scala, 'val' creates private final fields. If not final, it's suspicious.
      // We skip internal scalac generated fields (like bitmaps for lazy vals)
      if (!Modifier.isFinal(field.getModifiers) && !fieldName.contains("$")) {
        errors = errors :+ s"❌ VIOLATION: Field '$fieldName' in '${clazz.getSimpleName}' is MUTABLE (var detected)."
      }
    }
    errors
  }

  // --- 🧪 2. The Verification Suite ---

  def spec = suite("Strict Compliance Verification (Scala 2 & 3)")(
    
    test("AUDIT 1: SchemaExpr.Literal must be PURE DATA (No Functions)") {
      // Create a literal with a primitive value
      val dv = DynamicValue.Primitive(100)
      val literal = SchemaExpr.ConstantValue(dv)

      // Audit the object internals
      val errors = inspectObjectForForbiddenTypes(literal)
      
      if (errors.nonEmpty) {
        println("\n🚨 AUDIT FAILED FOR LITERAL:")
        errors.foreach(println)
      }

      assert(errors)(isEmpty)
    },

    test("AUDIT 2: MigrationAction must NOT contain Reflection Mirrors or TypeTags") {
      val path = DynamicOptic(Vector(OpticStep.Field("age")))
      val action = MigrationAction.RenameField(path, "newAge")

      val errors = inspectObjectForForbiddenTypes(action)
      
      if (errors.nonEmpty) {
        println("\n🚨 AUDIT FAILED FOR ACTION:")
        errors.foreach(println)
      }
      
      assert(errors)(isEmpty)
    },

    test("AUDIT 3: Serialization Round-Trip (Binary Compatibility)") {
      // Construct a complex object
      val path = DynamicOptic(Vector(OpticStep.Field("user"), OpticStep.Field("isActive")))
      val trueVal = DynamicValue.Primitive(true)
      val expr = SchemaExpr.ConstantValue[Any](trueVal)
      
      val migration = DynamicMigration(Vector(
        MigrationAction.AddField(path, expr),
        MigrationAction.DeleteField(path, expr)
      ))

      // 1. Serialize to Bytes
      val bytes = serialize(migration)
      
      // 2. Deserialize back to Object
      val loaded = deserialize[DynamicMigration](bytes)

      // 3. Verify Integrity
      assert(bytes.length)(isGreaterThan(0)) &&
      assert(loaded)(equalTo(migration))
    }
  )
}