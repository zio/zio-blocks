package zio.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

object StructuralTypeSpec extends ZIOSpecDefault {

  // definition of structural types
  type SimpleV0 = { def age: Int }
  case class SimpleV0Repr(age: Int)
  // Casting Repr schema to structural type schema to simulate the behavior without writing a macro
  val simpleV0Schema: Schema[SimpleV0] = 
    Schema.derived[SimpleV0Repr].asInstanceOf[Schema[SimpleV0]]
  
  case class SimpleV1(years: Int)
  val simpleV1Schema: Schema[SimpleV1] = Schema.derived[SimpleV1]

  def spec = suite("StructuralTypeSpec")(
    test("Can build and run migration using field rename on structural type") {
      // 1. Build migration using structural type selectors
      val migrationResult = Migration.newBuilder(simpleV0Schema, simpleV1Schema)
        .renameField(_.age, _.years)
        .build
      
      assert(migrationResult)(isRight(anything))
      val migration = migrationResult.toOption.get
        
      // 2. Create dynamic data matching the structural type (V0)
      val oldData = DynamicValue.Record(
        Vector(
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
      )
      
      // 3. Apply migration
      val result = migration.dynamicMigration.apply(oldData)
      
      // 4. Verify result matches V1 structure
      val expected = DynamicValue.Record(
        Vector("years" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
      )
      
      assert(result)(isRight(equalTo(expected)))
    }
  )
}
