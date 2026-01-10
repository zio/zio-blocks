package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.Selectable 

object MigrationEndToEndSpec extends ZIOSpecDefault {

  // Structural type definition using standard Scala 3 Selectable
  type PersonV0 = Selectable { val firstName: String; val lastName: String }
  type PersonV1 = Selectable { val firstName: String; val lastName: String; val age: Int }

  def spec = suite("MigrationEndToEndSpec")(
    test("End-to-End: Structural Type -> Case Class (Issue #519 Example)") {
      // Target case class
      case class Person(firstName: String, lastName: String, age: Int)
      
      implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
      implicit val v1Schema: Schema[Person]   = Schema.derived[Person]

      val migration = Migration.builder[PersonV0, Person]
        .addField("age", 42)
        .buildPartial 

      // Create a structural instance using a dynamic proxy or helper
      // Since we can't easily instantiate a structural type without a helper,
      // and we want to verify the migration LOGIC, we will verify the actions are correct.
      // Applying it requires a PersonV0, which requires reflection/macros to instantiate at runtime.
      // However, we can use DynamicValue!
      // Migration.apply takes A.
      // We can bypass apply(A) and use migration.dynamic.apply(DynamicValue).
      // This verifies the engine logic end-to-end.
      
      val v0Dynamic = DynamicValue.Record(Vector(
        "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
      ))
      
      val resultDynamic = migration.dynamic(v0Dynamic)
      
      // Expected: Record with firstName, lastName, and age=42
      val expectedDynamic = DynamicValue.Record(Vector(
        "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
        "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      ))
      
      assert(resultDynamic)(isRight(equalTo(expectedDynamic))) &&
      // Also verify we decode it to Person correctly if we could
      assert(v1Schema.fromDynamicValue(resultDynamic.toOption.get))(isRight(equalTo(Person("John", "Doe", 42))))
    },
    
    test("Reversibility Compliance: Optionalize restores default values") {
      case class UserV1(id: Int, name: String)
      case class UserV2(id: Int, name: Option[String])
      
      // V1 has name: String. Default is NOT in schema, but we want to ensure we can reverse if we provide one?
      // Optionalize(at, defaultForReverse).
      // If V1 schema HAS a default, macro picks it up.
      
      val v1Default = UserV1(1, "defaultName")
      implicit val s1: Schema[UserV1] = Schema.derived[UserV1].defaultValue(v1Default)
      implicit val s2: Schema[UserV2] = Schema.derived[UserV2]
      
      // Migration: Make name optional.
      val migration = Migration.builder[UserV1, UserV2]
        .optionalize(_.name)
        .build
        .toOption.get
        
      val v1 = UserV1(1, "foo")
      val result = migration(v1)
      
      val forwardAssertion = assert(result)(isRight(equalTo(UserV2(1, Some("foo")))))

      val reverseAssertion = {
        val reverseMigration = migration.reverse.toOption.get
        val v2None = UserV2(1, None)
        val reversed = reverseMigration(v2None)
        assert(reversed)(isRight(equalTo(UserV1(1, "defaultName"))))
      }
      
      forwardAssertion && reverseAssertion
    }
  )
}
