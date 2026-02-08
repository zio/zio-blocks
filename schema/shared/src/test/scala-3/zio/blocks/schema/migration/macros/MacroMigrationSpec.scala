package zio.blocks.schema.migration.macros

import zio.test._
import zio.blocks.schema.Schema
import zio.blocks.schema.migration._

object MacroMigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }
  
  case class UserV1(id: Int, active: Boolean)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
  }

  case class UserV2(id: Int, active: Boolean)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived[UserV2]
  }

  override def spec = suite("MacroMigrationSpec")(
    test("Derive RenameField migration") {
      val p1 = PersonV1("Alice", 30)
      val expected = PersonV2("Alice", 30)
      
      // Inline the migration definition to trigger the macro
      val migration: Migration[PersonV1, PersonV2] = MacroMigration.derive[PersonV1, PersonV2](
        DynamicMigration.RenameField("name", "fullName")
      )
      
      val result = migration.migrate(p1)
      assertTrue(result == Right(expected))
    },
    
    test("Derive Identity migration") {
       val u1 = UserV1(1, true)
       val expected = UserV2(1, true)
       
       val migration = MacroMigration.derive[UserV1, UserV2](DynamicMigration.Identity)
       // This test might fail if fallback isn't working or logic is too strict
       // But fallback logic is: '{ Migration($dynamic) } so it should work via interpreter
       
       val result = migration.migrate(u1)
       assertTrue(result == Right(expected))
    },
    
    test("Fallback to interpreter for non-case classes") {
      // Testing that compilation succeeds even if macro returns fallback
      sealed trait Pet
      case class Dog(name: String) extends Pet
      case class Cat(name: String) extends Pet
      
      object Pet {
         implicit val schema: Schema[Pet] = Schema.derived[Pet]
      }
      
      // We can't easily verify it used fallback without inspecting logs/bytecode
      // But we can verify it works at all.
      // DynamicMigration.Identity works on anything.
      
      @scala.annotation.nowarn("msg=Macro migration only supports Case Classes")
      val migration: Migration[Pet, Pet] = MacroMigration.derive[Pet, Pet](DynamicMigration.Identity)
      val d = Dog("Spot")
      val result = migration.migrate(d)
      assertTrue(result == Right(d))
    },
    
    test("Derive AddField migration with constant value") {
      case class PersonV3(name: String, age: Int, active: Boolean)
      implicit val sV3: Schema[PersonV3] = Schema.derived[PersonV3]
      
      val p1 = PersonV1("Alice", 30)
      val expected = PersonV3("Alice", 30, true)
      
      import zio.blocks.schema.PrimitiveValue
      import zio.blocks.schema.DynamicValue
      
      // We manually construct the expression to simulate what the DSL would produce
      val migration = MacroMigration.derive[PersonV1, PersonV3](
          DynamicMigration.AddClassField("active", DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      )
      
      val result = migration.migrate(p1)
      assertTrue(result == Right(expected))
    }
  )
}
