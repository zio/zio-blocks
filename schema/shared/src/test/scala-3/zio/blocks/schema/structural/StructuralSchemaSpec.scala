package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object StructuralSchemaSpec extends ZIOSpecDefault {

  // Phase 1 types
  case class Person(name: String, age: Int)
  case class Point(x: Double, y: Double)

  // Phase 2 types
  case class Address(city: String, zip: Int)
  case class PersonWithAddress(name: String, address: Address)
  case class WithList(items: List[String])
  case class WithOption(value: Option[Int])
  case class WithMap(data: Map[String, Int])

  // Phase 3 types
  case class Empty()
  case class SingleField(value: String)

  // Opaque types for Phase 5
  opaque type UserId = String
  object UserId {
    def apply(s: String): UserId = s
    extension (id: UserId) def value: String = id
  }

  opaque type Age = Int
  object Age {
    def apply(i: Int): Age = i
    extension (age: Age) def value: Int = age
  }

  case class UserWithOpaque(id: UserId, name: String, age: Age)
  case class LargeProduct(
    f1: String,
    f2: Int,
    f3: Boolean,
    f4: Double,
    f5: Long,
    f6: String,
    f7: Int,
    f8: Boolean,
    f9: Double,
    f10: Long
  )
  case class AllPrimitives(
    b: Boolean,
    by: Byte,
    s: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    str: String
  )

  def spec = suite("StructuralSchemaSpec")(
    suite("Phase 1: Simple Case Classes")(
      test("toDynamicValue works for simple case class") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        val structural = ts.toStructural(Person("Alice", 30))
        val dv         = structSchema.toDynamicValue(structural)

        // Should produce a Record with name and age fields
        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            assertTrue(
              fieldMap.contains("name"),
              fieldMap.contains("age"),
              fieldMap("name") == DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              fieldMap("age") == DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("fromDynamicValue works for simple case class") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        // Create a DynamicValue manually
        val dv = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )

        val result = structSchema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.map(_.name) == Right("Bob"),
          result.map(_.age) == Right(25)
        )
      },
      test("round-trip preserves data") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        val original   = Person("Charlie", 35)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.name) == Right("Charlie"),
          roundTrip.map(_.age) == Right(35)
        )
      },
      test("works with Double fields") {
        val ts              = ToStructural.derived[Point]
        given Schema[Point] = Schema.derived[Point]
        val structSchema    = ts.structuralSchema

        val structural = ts.toStructural(Point(1.5, 2.5))
        val dv         = structSchema.toDynamicValue(structural)
        val roundTrip  = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.x) == Right(1.5),
          roundTrip.map(_.y) == Right(2.5)
        )
      }
    ),
    suite("Phase 2: Nested Case Classes + Collections")(
      test("nested case class round-trip") {
        val ts                          = ToStructural.derived[PersonWithAddress]
        given Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]
        val structSchema                = ts.structuralSchema

        val original   = PersonWithAddress("Alice", Address("NYC", 10001))
        val structural = ts.toStructural(original)

        // toStructural works - verify field access
        assertTrue(
          structural.name == "Alice",
          structural.address.city == "NYC",
          structural.address.zip == 10001
        ) &&
        // Now test structuralSchema round-trip
        {
          val dv        = structSchema.toDynamicValue(structural)
          val roundTrip = structSchema.fromDynamicValue(dv)

          assertTrue(
            roundTrip.isRight,
            roundTrip.map(_.name) == Right("Alice"),
            roundTrip.map(_.address.city) == Right("NYC"),
            roundTrip.map(_.address.zip) == Right(10001)
          )
        }
      },
      test("case class with List round-trip") {
        val ts                 = ToStructural.derived[WithList]
        given Schema[WithList] = Schema.derived[WithList]
        val structSchema       = ts.structuralSchema

        val original   = WithList(List("a", "b", "c"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.items) == Right(List("a", "b", "c"))
        )
      },
      test("case class with Option (Some) round-trip") {
        val ts                   = ToStructural.derived[WithOption]
        given Schema[WithOption] = Schema.derived[WithOption]
        val structSchema         = ts.structuralSchema

        val original   = WithOption(Some(42))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(Some(42))
        )
      },
      test("case class with Option (None) round-trip") {
        val ts                   = ToStructural.derived[WithOption]
        given Schema[WithOption] = Schema.derived[WithOption]
        val structSchema         = ts.structuralSchema

        val original   = WithOption(None)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(None)
        )
      },
      test("case class with Map round-trip") {
        val ts                = ToStructural.derived[WithMap]
        given Schema[WithMap] = Schema.derived[WithMap]
        val structSchema      = ts.structuralSchema

        val original   = WithMap(Map("x" -> 1, "y" -> 2))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map("x" -> 1, "y" -> 2))
        )
      }
    ),
    suite("Phase 3: Edge Cases")(
      test("empty case class round-trip") {
        val ts              = ToStructural.derived[Empty]
        given Schema[Empty] = Schema.derived[Empty]
        val structSchema    = ts.structuralSchema

        val original   = Empty()
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      },
      test("single field case class round-trip") {
        val ts                    = ToStructural.derived[SingleField]
        given Schema[SingleField] = Schema.derived[SingleField]
        val structSchema          = ts.structuralSchema

        val original   = SingleField("hello")
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right("hello")
        )
      },
      test("large product (10 fields) round-trip") {
        val ts                     = ToStructural.derived[LargeProduct]
        given Schema[LargeProduct] = Schema.derived[LargeProduct]
        val structSchema           = ts.structuralSchema

        val original   = LargeProduct("a", 1, true, 1.5, 100L, "b", 2, false, 2.5, 200L)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.f1) == Right("a"),
          roundTrip.map(_.f5) == Right(100L),
          roundTrip.map(_.f10) == Right(200L)
        )
      },
      test("all primitive types round-trip") {
        val ts                      = ToStructural.derived[AllPrimitives]
        given Schema[AllPrimitives] = Schema.derived[AllPrimitives]
        val structSchema            = ts.structuralSchema

        val original = AllPrimitives(
          b = true,
          by = 42.toByte,
          s = 1000.toShort,
          i = 123456,
          l = 999999999L,
          f = 3.14f,
          d = 2.71828,
          c = 'X',
          str = "test"
        )
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.b) == Right(true),
          roundTrip.map(_.by) == Right(42.toByte),
          roundTrip.map(_.i) == Right(123456),
          roundTrip.map(_.l) == Right(999999999L),
          roundTrip.map(_.c) == Right('X'),
          roundTrip.map(_.str) == Right("test")
        )
      }
    ),
    suite("Phase 4: TypeName Normalization")(
      test("TypeName is normalized with alphabetical field order") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        // Fields should be sorted alphabetically: age before name
        assertTrue(typeName == "{age:Int,name:String}")
      },
      test("TypeName uses Namespace.empty") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        assertTrue(structSchema.reflect.typeName.namespace == Namespace.empty)
      },
      test("TypeName has no type parameters") {
        val ts               = ToStructural.derived[Person]
        given Schema[Person] = Schema.derived[Person]
        val structSchema     = ts.structuralSchema

        assertTrue(structSchema.reflect.typeName.params.isEmpty)
      },
      test("same structure produces same TypeName") {
        // Two different case classes with same fields should have same structural TypeName
        case class PersonA(name: String, age: Int)
        case class PersonB(age: Int, name: String) // different field order

        val tsA               = ToStructural.derived[PersonA]
        val tsB               = ToStructural.derived[PersonB]
        given Schema[PersonA] = Schema.derived[PersonA]
        given Schema[PersonB] = Schema.derived[PersonB]

        val typeNameA = tsA.structuralSchema.reflect.typeName.name
        val typeNameB = tsB.structuralSchema.reflect.typeName.name

        // Same structure = same normalized name (alphabetical order)
        assertTrue(
          typeNameA == typeNameB,
          typeNameA == "{age:Int,name:String}"
        )
      }
    ),
    suite("Schema.structural convenience method")(
      test("schema.structural returns same result as ts.structuralSchema") {
        given ts: ToStructural[Person] = ToStructural.derived[Person]
        given schema: Schema[Person]   = Schema.derived[Person]

        // Old way
        val structSchema1 = ts.structuralSchema

        // New convenience method
        val structSchema2 = schema.structural

        // Both should work identically
        val structural = ts.toStructural(Person("Alice", 30))

        val dv1 = structSchema1.toDynamicValue(structural)
        val dv2 = structSchema2.toDynamicValue(structural)

        assertTrue(dv1 == dv2)
      },
      test("schema.structural round-trip works") {
        given ts: ToStructural[Person] = ToStructural.derived[Person]
        given schema: Schema[Person]   = Schema.derived[Person]

        val structSchema = schema.structural
        val structural   = ts.toStructural(Person("Bob", 25))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        // Use selectDynamic since the refined type is erased through the implicit
        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.asInstanceOf[StructuralRecord].selectDynamic("name")) == Right("Bob"),
          roundTrip.map(_.asInstanceOf[StructuralRecord].selectDynamic("age")) == Right(25)
        )
      }
    ),
    suite("Phase 5: Opaque Types")(
      test("opaque type fields are unwrapped in structural representation") {
        val ts                         = ToStructural.derived[UserWithOpaque]
        val original   = UserWithOpaque(UserId("user123"), "Alice", Age(30))
        val structural = ts.toStructural(original)

        // Verify field access returns unwrapped values
        assertTrue(
          structural.id == "user123",
          structural.name == "Alice",
          structural.age == 30
        )
      },
      test("opaque type round-trip works") {
        val ts                       = ToStructural.derived[UserWithOpaque]
        given Schema[UserWithOpaque] = Schema.derived[UserWithOpaque]
        val structSchema             = ts.structuralSchema

        val original   = UserWithOpaque(UserId("user456"), "Bob", Age(25))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.id) == Right("user456"),
          roundTrip.map(_.name) == Right("Bob"),
          roundTrip.map(_.age) == Right(25)
        )
      },
      test("opaque type DynamicValue contains unwrapped primitive values") {
        val ts                       = ToStructural.derived[UserWithOpaque]
        given Schema[UserWithOpaque] = Schema.derived[UserWithOpaque]
        val structSchema             = ts.structuralSchema

        val structural = ts.toStructural(UserWithOpaque(UserId("test"), "Test", Age(99)))
        val dv         = structSchema.toDynamicValue(structural)

        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            assertTrue(
              fieldMap("id") == DynamicValue.Primitive(PrimitiveValue.String("test")),
              fieldMap("name") == DynamicValue.Primitive(PrimitiveValue.String("Test")),
              fieldMap("age") == DynamicValue.Primitive(PrimitiveValue.Int(99))
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("TypeName shows underlying types for opaque fields") {
        val ts                       = ToStructural.derived[UserWithOpaque]
        given Schema[UserWithOpaque] = Schema.derived[UserWithOpaque]
        val structSchema             = ts.structuralSchema

        val typeName = structSchema.reflect.typeName.name
        // Fields sorted alphabetically: age (Int), id (String), name (String)
        assertTrue(typeName == "{age:Int,id:String,name:String}")
      }
    )
  )
}
