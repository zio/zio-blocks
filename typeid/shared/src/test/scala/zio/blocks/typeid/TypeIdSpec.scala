package zio.blocks.typeid

import zio.test.*

object TypeIdSpec extends ZIOSpecDefault {

  def spec = suite("TypeId")(
    suite("derive")(
      test("should derive TypeId for primitive types") {
        val intId = TypeId.derive[Int]
        val stringId = TypeId.derive[String]
        val booleanId = TypeId.derive[Boolean]
        
        assertTrue(
          intId.name == "Int",
          stringId.name == "String",
          booleanId.name == "Boolean"
        )
      },
      
      test("should derive TypeId for generic types") {
        val listId = TypeId.derive[List[?]]
        val optionId = TypeId.derive[Option[?]]
        val mapId = TypeId.derive[Map[?, ?]]
        
        assertTrue(
          listId.name == "List",
          optionId.name == "Option",
          mapId.name == "Map",
          listId.arity == 1,
          optionId.arity == 1,
          mapId.arity == 2
        )
      },
      
      test("should derive TypeId for case classes") {
        case class Person(name: String, age: Int)
        val personId = TypeId.derive[Person]
        
        assertTrue(
          personId.name == "Person",
          personId.arity == 0
        )
      },
      
      test("should capture owner information") {
        val intId = TypeId.derive[Int]
        assertTrue(
          intId.owner.asString.contains("scala")
        )
      },
      
      test("should have correct fullName") {
        val intId = TypeId.derive[Int]
        assertTrue(
          intId.fullName.contains("Int")
        )
      }
    ),
    
    suite("manual construction")(
      test("should create nominal TypeId") {
        val id = TypeId.nominal[String](
          "MyType",
          Owner.Root,
          Nil
        )
        
        assertTrue(
          id.name == "MyType",
          id.owner == Owner.Root,
          id.arity == 0
        )
      },
      
      test("should create alias TypeId") {
        val stringTypeRepr = TypeRepr.Ref(TypeId.derive[String])
        val id = TypeId.alias[String](
          "MyAlias",
          Owner.Root,
          Nil,
          stringTypeRepr
        )
        
        assertTrue(
          id.name == "MyAlias"
        )
      },
      
      test("should create opaque TypeId") {
        val stringTypeRepr = TypeRepr.Ref(TypeId.derive[String])
        val id = TypeId.opaque[String](
          "MyOpaque",
          Owner.Root,
          Nil,
          stringTypeRepr
        )
        
        assertTrue(
          id.name == "MyOpaque"
        )
      }
    ),
    
    suite("pattern matching")(
      test("should extract nominal TypeId") {
        val id = TypeId.nominal[String]("MyType", Owner.Root, Nil)
        
        id match {
          case TypeId.Nominal(name, owner, params) =>
            assertTrue(
              name == "MyType",
              owner == Owner.Root,
              params.isEmpty
            )
          case _ => assertTrue(false)
        }
      },
      
      test("should extract alias TypeId") {
        val stringTypeRepr = TypeRepr.Ref(TypeId.derive[String])
        val id = TypeId.alias[String]("MyAlias", Owner.Root, Nil, stringTypeRepr)
        
        id match {
          case TypeId.Alias(name, owner, params, underlying) =>
            assertTrue(
              name == "MyAlias",
              owner == Owner.Root,
              params.isEmpty
            )
          case _ => assertTrue(false)
        }
      },
      
      test("should extract opaque TypeId") {
        val stringTypeRepr = TypeRepr.Ref(TypeId.derive[String])
        val id = TypeId.opaque[String]("MyOpaque", Owner.Root, Nil, stringTypeRepr)
        
        id match {
          case TypeId.Opaque(name, owner, params, repr) =>
            assertTrue(
              name == "MyOpaque",
              owner == Owner.Root,
              params.isEmpty
            )
          case _ => assertTrue(false)
        }
      }
    )
  )
}
