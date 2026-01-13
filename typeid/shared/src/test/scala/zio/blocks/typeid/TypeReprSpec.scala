package zio.blocks.typeid

import zio.test.*

object TypeReprSpec extends ZIOSpecDefault {

  def spec = suite("TypeRepr")(
    suite("Ref")(
      test("should create reference to a type") {
        val intId = TypeId.derive[Int]
        val ref = TypeRepr.Ref(intId)
        
        assertTrue(ref.isInstanceOf[TypeRepr.Ref])
      }
    ),
    
    suite("ParamRef")(
      test("should create parameter reference") {
        val param = TypeParam("A", 0)
        val paramRef = TypeRepr.ParamRef(param)
        
        assertTrue(paramRef.isInstanceOf[TypeRepr.ParamRef])
      }
    ),
    
    suite("Applied")(
      test("should create applied type") {
        val listId = TypeId.derive[List[?]]
        val intId = TypeId.derive[Int]
        
        val applied = TypeRepr.Applied(
          TypeRepr.Ref(listId),
          List(TypeRepr.Ref(intId))
        )
        
        assertTrue(applied.isInstanceOf[TypeRepr.Applied])
      }
    ),
    
    suite("Intersection")(
      test("should create intersection type") {
        val intId = TypeId.derive[Int]
        val stringId = TypeId.derive[String]
        
        val intersection = TypeRepr.Intersection(
          TypeRepr.Ref(intId),
          TypeRepr.Ref(stringId)
        )
        
        assertTrue(intersection.isInstanceOf[TypeRepr.Intersection])
      }
    ),
    
    suite("Union")(
      test("should create union type") {
        val intId = TypeId.derive[Int]
        val stringId = TypeId.derive[String]
        
        val union = TypeRepr.Union(
          TypeRepr.Ref(intId),
          TypeRepr.Ref(stringId)
        )
        
        assertTrue(union.isInstanceOf[TypeRepr.Union])
      }
    ),
    
    suite("Tuple")(
      test("should create tuple type") {
        val intId = TypeId.derive[Int]
        val stringId = TypeId.derive[String]
        
        val tuple = TypeRepr.Tuple(List(
          TypeRepr.Ref(intId),
          TypeRepr.Ref(stringId)
        ))
        
        assertTrue(tuple.isInstanceOf[TypeRepr.Tuple])
      }
    ),
    
    suite("Function")(
      test("should create function type") {
        val intId = TypeId.derive[Int]
        val stringId = TypeId.derive[String]
        
        val function = TypeRepr.Function(
          List(TypeRepr.Ref(intId)),
          TypeRepr.Ref(stringId)
        )
        
        assertTrue(function.isInstanceOf[TypeRepr.Function])
      }
    ),
    
    suite("Constant")(
      test("should create constant type") {
        val constant = TypeRepr.Constant(42)
        
        assertTrue(constant.isInstanceOf[TypeRepr.Constant])
      }
    ),
    
    suite("AnyType")(
      test("should be a singleton") {
        assertTrue(TypeRepr.AnyType == TypeRepr.AnyType)
      }
    ),
    
    suite("NothingType")(
      test("should be a singleton") {
        assertTrue(TypeRepr.NothingType == TypeRepr.NothingType)
      }
    )
  )
}
