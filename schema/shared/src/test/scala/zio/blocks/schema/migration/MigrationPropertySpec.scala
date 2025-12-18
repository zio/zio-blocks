package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._

object MigrationPropertySpec extends ZIOSpecDefault {

  def spec = suite("MigrationPropertySpec")(
    test("Mandate reverse is Optionalize") {
      val path = DynamicOptic.root.field("a")
      val default = SchemaExpr.Constant(1)
      val action = Mandate(path, default)
      
      assertTrue(action.reverse == Optionalize(path))
    },
    
    test("Optionalize reverse is Mandate") {
      val path = DynamicOptic.root.field("a")
      val action = Optionalize(path)
      
      assertTrue(action.reverse == Mandate(path, SchemaExpr.DefaultValue()))
    },
    
    test("Join reverse is Split") {
      val target = DynamicOptic.root.field("fullName")
      val sources = Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last"))
      // Stubbing a combiner since we just test structure here
      val combiner = SchemaExpr.DefaultValue[Any]() 
      
      val action = Join(target, sources, combiner)
      
      assertTrue(action.reverse.isInstanceOf[Split])
      val reverse = action.reverse.asInstanceOf[Split]
      assertTrue(reverse.at == target)
      assertTrue(reverse.targetPaths == sources)
    },
    
    test("Split reverse is Join") {
      val source = DynamicOptic.root.field("fullName")
      val targets = Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last"))
      val splitter = SchemaExpr.DefaultValue[Any]()
      
      val action = Split(source, targets, splitter)
      
      assertTrue(action.reverse.isInstanceOf[Join])
    },
    
    test("DynamicOptic.set works for records") {
      val record = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(1, StandardType.IntType)))
      val path = DynamicOptic.root.field("a")
      
      val result = DynamicOptic.set(path, record, DynamicValue.Primitive(2, StandardType.IntType))
      
      assertTrue(
        result == Right(DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(2, StandardType.IntType))))
      )
    },
    
    test("DynamicOptic.set fails for missing field") {
      val record = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(1, StandardType.IntType)))
      val path = DynamicOptic.root.field("b")
      
      val result = DynamicOptic.set(path, record, DynamicValue.Primitive(2, StandardType.IntType))
      
      assertTrue(result.isLeft)
    }
  )
}
