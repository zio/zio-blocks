package zio.blocks.schema.structural

import scala.annotation.nowarn
import zio.blocks.schema._
import zio.test._

object DirectStructuralDerivationSpec extends ZIOSpecDefault {
  override def spec = suite("DirectStructuralDerivationSpec")(
    suite("Simple structural types")(
      test("derive schema for simple two-field structural type") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        // Check that schema exists and has correct fields
        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name)

        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age"),
          record.fields.size == 2
        )
        assertTrue(schema.reflect.typeName.name == "{age:Int,name:String}")
      },
      test("derive schema for single-field structural type") {
        type IdStructure = StructuralRecord { def value: String }
        val schema = Schema.derived[IdStructure]

        val record = schema.reflect.asRecord.get
        assertTrue(record.fields.size == 1, record.fields.head.name == "value")
        assertTrue(schema.reflect.typeName.name == "{value:String}")
      },
      test("derive schema for multi-field structural type with different primitives") {
        type MixedStructure = StructuralRecord {
          def name: String
          def age: Int
          def score: Double
          def active: Boolean
        }
        val schema = Schema.derived[MixedStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(
          fieldNames == Set("name", "age", "score", "active"),
          record.fields.size == 4
        )
        assertTrue(schema.reflect.typeName.name == "{active:Boolean,age:Int,name:String,score:Double}")
      }
    ),
    suite("TypeName normalization")(
      test("TypeName is normalized with alphabetical field order") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        val typeName = schema.reflect.typeName.name
        // Should be {age:Int,name:String} - alphabetically sorted
        assertTrue(typeName == "{age:Int,name:String}")
      },
      test("TypeName format is {field:Type,...}") {
        type PointStructure = StructuralRecord { def x: Int; def y: Int }
        val schema = Schema.derived[PointStructure]

        // Check the .name property of TypeName, not the full toString
        val typeNameStr = schema.reflect.typeName.name
        assertTrue(typeNameStr == "{x:Int,y:Int}")
      },
      test("TypeName uses Namespace.empty") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        assertTrue(schema.reflect.typeName.namespace == Namespace.empty)
        assertTrue(schema.reflect.typeName.name == "{age:Int,name:String}")
      },
      test("TypeName has no type parameters") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        assertTrue(schema.reflect.typeName.params.isEmpty)
      },
      test("Same structure produces same TypeName regardless of field declaration order") {
        // Fields declared in different order should produce same TypeName
        type PersonA = StructuralRecord { def name: String; def age: Int }
        type PersonB = StructuralRecord { def age: Int; def name: String }

        val schemaA = Schema.derived[PersonA]
        val schemaB = Schema.derived[PersonB]

        assertTrue(schemaA.reflect.typeName.name == schemaB.reflect.typeName.name)
        assertTrue(schemaA.reflect.typeName.name == "{age:Int,name:String}")
      },
      test("TypeName with collection types shows proper format") {
        type TeamStructure = StructuralRecord { def members: List[String]; def name: String }
        val schema = Schema.derived[TeamStructure]

        val typeName = schema.reflect.typeName.name
        // Should have alphabetical order and proper collection format
        assertTrue(
          typeName == "{members:List[String],name:String}"
        )
      },
      test("Nested structural types produce normalized TypeName with recursive structure") {
        // Nested structural types should show full recursive structure in TypeName
        type AddressStructure = StructuralRecord { def city: String; def zip: Int }
        type PersonStructure  = StructuralRecord { def name: String; def address: AddressStructure }

        given Schema[AddressStructure] = Schema.derived[AddressStructure]
        val schema                     = Schema.derived[PersonStructure]

        val typeName = schema.reflect.typeName.name
        // Should show nested structure: {address:{city:String,zip:Int},name:String}
        assertTrue(
          typeName == "{address:{city:String,zip:Int},name:String}"
        )
      },
      test("Nested structural types with different field order produce same TypeName") {
        // Different field orders in nested types should normalize to same TypeName
        type Address1 = StructuralRecord { def city: String; def zip: Int }
        type Person1  = StructuralRecord { def name: String; def address: Address1 }

        type Address2 = StructuralRecord { def zip: Int; def city: String }
        type Person2  = StructuralRecord { def address: Address2; def name: String }

        @nowarn("msg=unused") given Schema[Address1] = Schema.derived[Address1]
        @nowarn("msg=unused") given Schema[Address2] = Schema.derived[Address2]
        val schema1                                  = Schema.derived[Person1]
        val schema2                                  = Schema.derived[Person2]

        // Both should normalize to same TypeName
        assertTrue(
          schema1.reflect.typeName.name == schema2.reflect.typeName.name,
          schema1.reflect.typeName.name == "{address:{city:String,zip:Int},name:String}"
        )
      }
    ),
    suite("Round-trip conversion")(
      test("toDynamicValue and fromDynamicValue work for simple structural type") {
        type PersonStructure = StructuralRecord { def name: String; def age: Int }
        val schema = Schema.derived[PersonStructure]

        // Create a StructuralRecord value
        val value: PersonStructure = new StructuralRecord(Map("name" -> "Alice", "age" -> 30))
          .asInstanceOf[PersonStructure]

        // Convert to DynamicValue
        val dv = schema.toDynamicValue(value)

        // Convert back
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Alice",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("age") == 30
        )
      },
      test("round-trip preserves primitive values") {
        type NumbersStructure = StructuralRecord { def a: Int; def b: Long; def c: Double }
        val schema = Schema.derived[NumbersStructure]

        val value: NumbersStructure = new StructuralRecord(Map("a" -> 42, "b" -> 123L, "c" -> 3.14))
          .asInstanceOf[NumbersStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("a") == 42,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("b") == 123L,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("c") == 3.14
        )
      },
      test("round-trip with enum field") {
        enum Gender { case Male, Female, Other }
        given Schema[Gender] = Schema.derived[Gender]

        type PersonStructure = StructuralRecord {
          def name: String
          def age: Int
          def gender: Gender
        }

        val schema                 = Schema.derived[PersonStructure]
        val genderStructural       = new StructuralRecord(Map("Tag" -> "Female"))
        val value: PersonStructure =
          new StructuralRecord(Map("name" -> "Alice", "age" -> 30, "gender" -> genderStructural))
            .asInstanceOf[PersonStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        val recovered = result.toOption.get.asInstanceOf[StructuralRecord]
        assertTrue(
          result.isRight,
          recovered.selectDynamic("name") == "Alice",
          recovered.selectDynamic("age") == 30,
          recovered.selectDynamic("gender").asInstanceOf[StructuralRecord].selectDynamic("Tag") == "Female"
        )
      }
    ),
    suite("Structural types with collections")(
      test("derive schema for structural type with List field") {
        type TeamStructure = StructuralRecord { def name: String; def members: List[String] }
        val schema = Schema.derived[TeamStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "members"))
        assertTrue(schema.reflect.typeName.name == "{members:List[String],name:String}")
      },
      test("derive schema for structural type with Option field") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "nickname"))
        assertTrue(schema.reflect.typeName.name == "{name:String,nickname:Option[String]}")
      },
      test("derive schema for structural type with Vector field") {
        type VectorStructure = StructuralRecord { def items: Vector[Int] }
        val schema = Schema.derived[VectorStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("items"))
        assertTrue(schema.reflect.typeName.name == "{items:Vector[Int]}")
      },
      test("derive schema for structural type with Set field") {
        type SetStructure = StructuralRecord { def tags: Set[String] }
        val schema = Schema.derived[SetStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("tags"))
        assertTrue(schema.reflect.typeName.name == "{tags:Set[String]}")
      },
      test("derive schema for structural type with Map field") {
        type MapStructure = StructuralRecord { def data: Map[String, Int] }
        val schema = Schema.derived[MapStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("data"))
        assertTrue(schema.reflect.typeName.name == "{data:Map[String,Int]}")
      }
    ),
    suite("Collection field round-trips")(
      test("List field round-trip") {
        type TeamStructure = StructuralRecord { def name: String; def members: List[String] }
        val schema = Schema.derived[TeamStructure]

        val value: TeamStructure = new StructuralRecord(Map("name" -> "TeamA", "members" -> List("Alice", "Bob")))
          .asInstanceOf[TeamStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "TeamA",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("members") == List("Alice", "Bob")
        )
      },
      test("Option field round-trip - Some") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val value: OptionalStructure =
          new StructuralRecord(Map("name" -> "Alice", "nickname" -> Some("Ali")))
            .asInstanceOf[OptionalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Alice",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("nickname") == Some("Ali")
        )
      },
      test("Option field round-trip - None") {
        type OptionalStructure = StructuralRecord { def name: String; def nickname: Option[String] }
        val schema = Schema.derived[OptionalStructure]

        val value: OptionalStructure =
          new StructuralRecord(Map("name" -> "Bob", "nickname" -> None))
            .asInstanceOf[OptionalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("name") == "Bob",
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("nickname") == None
        )
      },
      test("Vector field round-trip") {
        type VectorStructure = StructuralRecord { def items: Vector[Int] }
        val schema = Schema.derived[VectorStructure]

        val value: VectorStructure = new StructuralRecord(Map("items" -> Vector(1, 2, 3, 4, 5)))
          .asInstanceOf[VectorStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("items") == Vector(1, 2, 3, 4, 5)
        )
      },
      test("Set field round-trip") {
        type SetStructure = StructuralRecord { def tags: Set[String] }
        val schema = Schema.derived[SetStructure]

        val value: SetStructure = new StructuralRecord(Map("tags" -> Set("a", "b", "c")))
          .asInstanceOf[SetStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("tags") == Set("a", "b", "c")
        )
      },
      test("Map field round-trip") {
        type MapStructure = StructuralRecord { def data: Map[String, Int] }
        val schema = Schema.derived[MapStructure]

        val value: MapStructure = new StructuralRecord(Map("data" -> Map("x" -> 1, "y" -> 2)))
          .asInstanceOf[MapStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("data") == Map("x" -> 1, "y" -> 2)
        )
      }
    ),
    suite("Nested structural types")(
      test("derive schema for nested structural type") {
        type AddressStructure = StructuralRecord { def city: String; def zip: Int }
        type PersonStructure  = StructuralRecord { def name: String; def address: AddressStructure }

        // Create schema for nested type - requires given for the nested type
        given Schema[AddressStructure] = Schema.derived[AddressStructure]
        val schema                     = Schema.derived[PersonStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("name", "address"))
        assertTrue(schema.reflect.typeName.name == "{address:{city:String,zip:Int},name:String}")
      },
      test("nested structural type round-trip") {
        type AddressStructure = StructuralRecord { def city: String; def zip: Int }
        type PersonStructure  = StructuralRecord { def name: String; def address: AddressStructure }

        given Schema[AddressStructure] = Schema.derived[AddressStructure]
        val schema                     = Schema.derived[PersonStructure]

        val addressValue                 = new StructuralRecord(Map("city" -> "NYC", "zip" -> 10001))
        val personValue: PersonStructure =
          new StructuralRecord(Map("name" -> "Alice", "address" -> addressValue))
            .asInstanceOf[PersonStructure]

        val dv     = schema.toDynamicValue(personValue)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val person  = result.toOption.get.asInstanceOf[StructuralRecord]
          val address = person.selectDynamic("address").asInstanceOf[StructuralRecord]
          person.selectDynamic("name") == "Alice" &&
          address.selectDynamic("city") == "NYC" &&
          address.selectDynamic("zip") == 10001
        }
      },
      test("deeply nested structural types") {
        type InnerStructure  = StructuralRecord { def value: String }
        type MiddleStructure = StructuralRecord { def inner: InnerStructure }
        type OuterStructure  = StructuralRecord { def middle: MiddleStructure }

        given Schema[InnerStructure]  = Schema.derived[InnerStructure]
        given Schema[MiddleStructure] = Schema.derived[MiddleStructure]
        val schema                    = Schema.derived[OuterStructure]

        val innerValue                 = new StructuralRecord(Map("value" -> "deep"))
        val middleValue                = new StructuralRecord(Map("inner" -> innerValue))
        val outerValue: OuterStructure =
          new StructuralRecord(Map("middle" -> middleValue))
            .asInstanceOf[OuterStructure]

        val dv     = schema.toDynamicValue(outerValue)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val outer  = result.toOption.get.asInstanceOf[StructuralRecord]
          val middle = outer.selectDynamic("middle").asInstanceOf[StructuralRecord]
          val inner  = middle.selectDynamic("inner").asInstanceOf[StructuralRecord]
          inner.selectDynamic("value") == "deep"
        } &&
        assertTrue(schema.reflect.typeName.name == "{middle:{inner:{value:String}}}")
      }
    ),
    suite("Structural types with sum types")(
      test("derive schema for structural type with Either field") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("result"))
        // Either is represented structurally with Tag field and value field for each case
        assertTrue(schema.reflect.typeName.name == """{result:({Tag:"Left",value:String}|{Tag:"Right",value:Int})}""")
      },
      test("Either field round-trip - Left") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val eitherValue                = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
        val value: WithEitherStructure =
          new StructuralRecord(Map("result" -> eitherValue))
            .asInstanceOf[WithEitherStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[StructuralRecord]
          val eitherField = record.selectDynamic("result").asInstanceOf[StructuralRecord]
          eitherField.selectDynamic("Tag") == "Left" &&
          eitherField.selectDynamic("value") == "error"
        }
      },
      test("Either field round-trip - Right") {
        type WithEitherStructure = StructuralRecord { def result: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        val eitherValue                = new StructuralRecord(Map("Tag" -> "Right", "value" -> 42))
        val value: WithEitherStructure =
          new StructuralRecord(Map("result" -> eitherValue))
            .asInstanceOf[WithEitherStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[StructuralRecord]
          val eitherField = record.selectDynamic("result").asInstanceOf[StructuralRecord]
          eitherField.selectDynamic("Tag") == "Right" &&
          eitherField.selectDynamic("value") == 42
        }
      }
    ),
    suite("Structural types with tuples")(
      test("derive schema for structural type with tuple field") {
        type WithTupleStructure = StructuralRecord { def pair: (String, Int) }
        val schema = Schema.derived[WithTupleStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("pair"))
        assertTrue(schema.reflect.typeName.name == "{pair:{_1:String,_2:Int}}")
      },
      test("tuple field round-trip") {
        type WithTupleStructure = StructuralRecord { def pair: (String, Int) }
        val schema = Schema.derived[WithTupleStructure]

        val tupleValue                = new StructuralRecord(Map("_1" -> "hello", "_2" -> 42))
        val value: WithTupleStructure =
          new StructuralRecord(Map("pair" -> tupleValue))
            .asInstanceOf[WithTupleStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record     = result.toOption.get.asInstanceOf[StructuralRecord]
          val tupleField = record.selectDynamic("pair").asInstanceOf[StructuralRecord]
          tupleField.selectDynamic("_1") == "hello" &&
          tupleField.selectDynamic("_2") == 42
        } &&
        assertTrue(schema.reflect.typeName.name == "{pair:{_1:String,_2:Int}}")
      }
    ),
    suite("Extended primitives in structural types")(
      test("derive schema for structural type with BigDecimal field") {
        type WithBigDecimalStructure = StructuralRecord { def amount: BigDecimal }
        val schema = Schema.derived[WithBigDecimalStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("amount"))
        assertTrue(schema.reflect.typeName.name == "{amount:BigDecimal}")
      },
      test("BigDecimal field round-trip") {
        type WithBigDecimalStructure = StructuralRecord { def amount: BigDecimal }
        val schema = Schema.derived[WithBigDecimalStructure]

        val value: WithBigDecimalStructure =
          new StructuralRecord(Map("amount" -> BigDecimal("123456.789")))
            .asInstanceOf[WithBigDecimalStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("amount") == BigDecimal("123456.789")
        )
        assertTrue(schema.reflect.typeName.name == "{amount:BigDecimal}")
      },
      test("derive schema for structural type with UUID field") {
        import java.util.UUID
        type WithUUIDStructure = StructuralRecord { def id: UUID }
        val schema = Schema.derived[WithUUIDStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("id"))
        assertTrue(schema.reflect.typeName.name == "{id:UUID}")
      },
      test("UUID field round-trip") {
        import java.util.UUID
        type WithUUIDStructure = StructuralRecord { def id: UUID }
        val schema = Schema.derived[WithUUIDStructure]

        val uuid                     = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val value: WithUUIDStructure =
          new StructuralRecord(Map("id" -> uuid))
            .asInstanceOf[WithUUIDStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("id") == uuid
        )
        assertTrue(schema.reflect.typeName.name == "{id:UUID}")
      }
    ),
    suite("Sealed traits and enums in structural types")(
      test("derive schema for structural type with simple enum field") {
        enum Color { case Red, Green, Blue }
        given Schema[Color] = Schema.derived[Color]

        type WithEnumStructure = StructuralRecord { def color: Color }
        val schema = Schema.derived[WithEnumStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("color"))
        // Simple enums: each case becomes {Tag:"CaseName"}
        assertTrue(schema.reflect.typeName.name == """{color:({Tag:"Blue"}|{Tag:"Green"}|{Tag:"Red"})}""")
      },
      test("simple enum field round-trip") {
        enum Status { case Active, Inactive, Pending }
        given Schema[Status] = Schema.derived[Status]

        type WithStatusStructure = StructuralRecord { def status: Status }
        val schema = Schema.derived[WithStatusStructure]

        val statusStructural           = new StructuralRecord(Map("Tag" -> "Active"))
        val value: WithStatusStructure = new StructuralRecord(Map("status" -> statusStructural))
          .asInstanceOf[WithStatusStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get
            .asInstanceOf[StructuralRecord]
            .selectDynamic("status")
            .asInstanceOf[StructuralRecord]
            .selectDynamic("Tag") == "Active"
        )
      },
      test("derive schema for structural type with sealed trait field (with data)") {
        sealed trait Shape
        @nowarn("msg=unused") case class Circle(radius: Double)                   extends Shape
        @nowarn("msg=unused") case class Rectangle(width: Double, height: Double) extends Shape

        given Schema[Shape] = Schema.derived[Shape]

        type WithShapeStructure = StructuralRecord { def shape: Shape }
        val schema = Schema.derived[WithShapeStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("shape"))
        // Sealed trait with data: each case shows Tag and all fields (sorted alphabetically)
        assertTrue(
          schema.reflect.typeName.name == """{shape:({Tag:"Circle",radius:Double}|{Tag:"Rectangle",height:Double,width:Double})}"""
        )
      },
      test("sealed trait field round-trip") {
        sealed trait Result
        @nowarn("msg=unused") case class Success(value: Int)    extends Result
        @nowarn("msg=unused") case class Failure(error: String) extends Result

        given Schema[Result] = Schema.derived[Result]

        type WithResultStructure = StructuralRecord { def result: Result }
        val schema = Schema.derived[WithResultStructure]

        // At runtime, sealed trait cases become StructuralRecord with Tag
        val successValue               = new StructuralRecord(Map("Tag" -> "Success", "value" -> 42))
        val value: WithResultStructure = new StructuralRecord(Map("result" -> successValue))
          .asInstanceOf[WithResultStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[StructuralRecord]
          val resultField = record.selectDynamic("result").asInstanceOf[StructuralRecord]
          resultField.selectDynamic("Tag") == "Success" &&
          resultField.selectDynamic("value") == 42
        }
      }
    ),
    suite("Opaque types in structural types")(
      test("derive schema for structural type with opaque type field") {
        object OpaqueUserId {
          opaque type UserId = Long
          object UserId {
            def apply(id: Long): UserId = id
          }
          // For opaque types over primitives, use the underlying type's schema
          given Schema[UserId] = Schema.long.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type WithUserIdStructure = StructuralRecord { def userId: UserId }
        val schema = Schema.derived[WithUserIdStructure]

        val record     = schema.reflect.asRecord.get
        val fieldNames = record.fields.map(_.name).toSet

        assertTrue(fieldNames == Set("userId"))
        // Opaque types unwrap to their underlying type
        assertTrue(schema.reflect.typeName.name == "{userId:Long}")
      },
      test("opaque type field round-trip") {
        object OpaquePrice {
          opaque type Price = BigDecimal
          object Price {
            def apply(amount: BigDecimal): Price = amount
          }
          // For opaque types over primitives, use the underlying type's schema
          given Schema[Price] = Schema.bigDecimal.asInstanceOf[Schema[Price]]
        }
        import OpaquePrice._

        type WithPriceStructure = StructuralRecord { def price: Price }
        val schema = Schema.derived[WithPriceStructure]

        val value: WithPriceStructure = new StructuralRecord(Map("price" -> BigDecimal("99.99")))
          .asInstanceOf[WithPriceStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("price") == BigDecimal("99.99")
        )
        assertTrue(schema.reflect.typeName.name == "{price:BigDecimal}")
      },
      test("nested opaque types unwrap recursively") {
        object OpaqueNested {
          opaque type Inner = String
          object Inner {
            def apply(s: String): Inner = s
          }

          opaque type Outer = Inner
          object Outer {
            def apply(i: Inner): Outer = i
          }

          // For opaque types over primitives, use the underlying type's schema
          given Schema[Inner] = Schema.string.asInstanceOf[Schema[Inner]]
          given Schema[Outer] = Schema.string.asInstanceOf[Schema[Outer]]
        }
        import OpaqueNested._

        type WithNestedOpaqueStructure = StructuralRecord { def value: Outer }
        val schema = Schema.derived[WithNestedOpaqueStructure]

        // Both opaque layers unwrap to String
        assertTrue(schema.reflect.typeName.name == "{value:String}")
      }
    ),
    suite("Complex nested structural types")(
      test("structural type with nested sealed trait and tuple") {
        sealed trait Value
        @nowarn("msg=unused") case class IntValue(n: Int)    extends Value
        @nowarn("msg=unused") case class StrValue(s: String) extends Value

        given Schema[Value] = Schema.derived[Value]

        type ComplexStructure = StructuralRecord {
          def data: (Value, String)
        }
        val schema = Schema.derived[ComplexStructure]

        // Tuple becomes {_1:...,_2:...}, sealed trait shows Tag and fields for each case
        assertTrue(
          schema.reflect.typeName.name == """{data:{_1:({Tag:"IntValue",n:Int}|{Tag:"StrValue",s:String}),_2:String}}"""
        )
      },
      test("structural type with Either nested in List") {
        type NestedStructure = StructuralRecord {
          def items: List[Either[String, Int]]
        }
        val schema = Schema.derived[NestedStructure]

        // Either in List preserves structure with Tag and value fields
        assertTrue(
          schema.reflect.typeName.name == """{items:List[({Tag:"Left",value:String}|{Tag:"Right",value:Int})]}"""
        )
      },
      test("structural type with Map of opaque to tuple") {
        object OpaqueKey {
          opaque type Key = String
          object Key {
            def apply(s: String): Key = s
          }
          // For opaque types over primitives, use the underlying type's schema
          given Schema[Key] = Schema.string.asInstanceOf[Schema[Key]]
        }
        import OpaqueKey._

        type MapStructure = StructuralRecord {
          def data: Map[Key, (Int, String)]
        }
        val schema = Schema.derived[MapStructure]

        // Opaque unwraps, tuple becomes structural
        assertTrue(
          schema.reflect.typeName.name == "{data:Map[String,{_1:Int,_2:String}]}"
        )
      }
    ),
    suite("Collections of sum types in structural types")(
      test("List of Either in structural type - round-trip") {
        type ListEitherStructure = StructuralRecord { def items: List[Either[String, Int]] }
        val schema = Schema.derived[ListEitherStructure]

        assertTrue(
          schema.reflect.typeName.name == """{items:List[({Tag:"Left",value:String}|{Tag:"Right",value:Int})]}"""
        ) &&
        {
          val leftValue                  = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
          val rightValue                 = new StructuralRecord(Map("Tag" -> "Right", "value" -> 42))
          val value: ListEitherStructure =
            new StructuralRecord(Map("items" -> List(leftValue, rightValue)))
              .asInstanceOf[ListEitherStructure]

          val dv     = schema.toDynamicValue(value)
          val result = schema.fromDynamicValue(dv)

          assertTrue(result.isRight) &&
          assertTrue {
            val record = result.toOption.get.asInstanceOf[StructuralRecord]
            val items  = record.selectDynamic("items").asInstanceOf[List[StructuralRecord]]
            items.size == 2 &&
            items(0).selectDynamic("Tag") == "Left" &&
            items(1).selectDynamic("Tag") == "Right"
          }
        }
      },
      test("Option of enum in structural type - Some - round-trip") {
        enum Priority { case High, Medium, Low }
        given Schema[Priority] = Schema.derived[Priority]

        type OptionalEnumStructure = StructuralRecord { def priority: Option[Priority] }
        val schema = Schema.derived[OptionalEnumStructure]

        assertTrue(
          schema.reflect.typeName.name == """{priority:Option[({Tag:"High"}|{Tag:"Low"}|{Tag:"Medium"})]}"""
        ) &&
        {
          val priorityValue                = new StructuralRecord(Map("Tag" -> "High"))
          val value: OptionalEnumStructure =
            new StructuralRecord(Map("priority" -> Some(priorityValue)))
              .asInstanceOf[OptionalEnumStructure]

          val dv     = schema.toDynamicValue(value)
          val result = schema.fromDynamicValue(dv)

          assertTrue(result.isRight) &&
          assertTrue {
            val record   = result.toOption.get.asInstanceOf[StructuralRecord]
            val priority = record.selectDynamic("priority").asInstanceOf[Option[StructuralRecord]]
            priority.isDefined && priority.get.selectDynamic("Tag") == "High"
          }
        }
      },
      test("Option of enum in structural type - None - round-trip") {
        enum Priority { case High, Medium, Low }
        given Schema[Priority] = Schema.derived[Priority]

        type OptionalEnumStructure = StructuralRecord { def priority: Option[Priority] }
        val schema = Schema.derived[OptionalEnumStructure]

        assertTrue(
          schema.reflect.typeName.name == """{priority:Option[({Tag:"High"}|{Tag:"Low"}|{Tag:"Medium"})]}"""
        ) &&
        {
          val value: OptionalEnumStructure =
            new StructuralRecord(Map("priority" -> None))
              .asInstanceOf[OptionalEnumStructure]

          val dv     = schema.toDynamicValue(value)
          val result = schema.fromDynamicValue(dv)

          assertTrue(result.isRight) &&
          assertTrue {
            val record = result.toOption.get.asInstanceOf[StructuralRecord]
            record.selectDynamic("priority") == None
          }
        }
      },
      test("Vector of sealed trait in structural type") {
        sealed trait Result
        @nowarn("msg=unused") case class Ok(value: Int)   extends Result
        @nowarn("msg=unused") case class Err(msg: String) extends Result

        given Schema[Result] = Schema.derived[Result]

        type VectorResultStructure = StructuralRecord { def results: Vector[Result] }
        val schema = Schema.derived[VectorResultStructure]

        assertTrue(
          schema.reflect.typeName.name == """{results:Vector[({Tag:"Err",msg:String}|{Tag:"Ok",value:Int})]}"""
        )
      },
      test("Set of enum in structural type - TypeName") {
        enum Status { case Active, Inactive, Pending }
        given Schema[Status] = Schema.derived[Status]

        type SetStatusStructure = StructuralRecord { def statuses: Set[Status] }
        val schema = Schema.derived[SetStatusStructure]

        assertTrue(
          schema.reflect.typeName.name == """{statuses:Set[({Tag:"Active"}|{Tag:"Inactive"}|{Tag:"Pending"})]}"""
        )
      },
      test("Map with Either values in structural type - TypeName") {
        type MapEitherStructure = StructuralRecord { def data: Map[String, Either[Int, String]] }
        val schema = Schema.derived[MapEitherStructure]

        assertTrue(
          schema.reflect.typeName.name == """{data:Map[String,({Tag:"Left",value:Int}|{Tag:"Right",value:String})]}"""
        )
      }
    ),
    suite("Collections of opaque types in structural types")(
      test("List of opaque type in structural type") {
        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type ListOpaqueStructure = StructuralRecord { def ids: List[UserId] }
        val schema = Schema.derived[ListOpaqueStructure]

        // Opaque types unwrap to their underlying type
        assertTrue(schema.reflect.typeName.name == "{ids:List[String]}")

        val value: ListOpaqueStructure =
          new StructuralRecord(Map("ids" -> List("id1", "id2", "id3")))
            .asInstanceOf[ListOpaqueStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("ids") == List("id1", "id2", "id3")
        )
      },
      test("Option of opaque type in structural type - Some") {
        object OpaquePrice {
          opaque type Price = Double
          object Price {
            def apply(d: Double): Price = d
          }
          given Schema[Price] = Schema.double.asInstanceOf[Schema[Price]]
        }
        import OpaquePrice._

        type OptionalPriceStructure = StructuralRecord { def price: Option[Price] }
        val schema = Schema.derived[OptionalPriceStructure]

        assertTrue(schema.reflect.typeName.name == "{price:Option[Double]}")

        val value: OptionalPriceStructure =
          new StructuralRecord(Map("price" -> Some(99.99)))
            .asInstanceOf[OptionalPriceStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("price") == Some(99.99)
        )
      },
      test("Option of opaque type in structural type - None") {
        object OpaquePrice {
          opaque type Price = Double
          object Price {
            def apply(d: Double): Price = d
          }
          given Schema[Price] = Schema.double.asInstanceOf[Schema[Price]]
        }
        import OpaquePrice._

        type OptionalPriceStructure = StructuralRecord { def price: Option[Price] }
        val schema = Schema.derived[OptionalPriceStructure]

        assertTrue(schema.reflect.typeName.name == "{price:Option[Double]}") &&
        {
          val value: OptionalPriceStructure =
            new StructuralRecord(Map("price" -> None))
              .asInstanceOf[OptionalPriceStructure]

          val dv     = schema.toDynamicValue(value)
          val result = schema.fromDynamicValue(dv)

          assertTrue(
            result.isRight,
            result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("price") == None
          )
        }
      },
      test("Map with opaque type keys and values") {
        object OpaqueTypes {
          opaque type Key = String
          object Key {
            def apply(s: String): Key = s
          }
          opaque type Value = Int
          object Value {
            def apply(i: Int): Value = i
          }
          given Schema[Key]   = Schema.string.asInstanceOf[Schema[Key]]
          given Schema[Value] = Schema.int.asInstanceOf[Schema[Value]]
        }
        import OpaqueTypes._

        type MapOpaqueStructure = StructuralRecord { def data: Map[Key, Value] }
        val schema = Schema.derived[MapOpaqueStructure]

        // Both key and value opaque types unwrap
        assertTrue(schema.reflect.typeName.name == "{data:Map[String,Int]}")

        val value: MapOpaqueStructure =
          new StructuralRecord(Map("data" -> Map("a" -> 1, "b" -> 2)))
            .asInstanceOf[MapOpaqueStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[StructuralRecord].selectDynamic("data") == Map("a" -> 1, "b" -> 2)
        )
      },
      test("Vector of opaque type in structural type") {
        object OpaqueTimestamp {
          opaque type Timestamp = Long
          object Timestamp {
            def apply(l: Long): Timestamp = l
          }
          given Schema[Timestamp] = Schema.long.asInstanceOf[Schema[Timestamp]]
        }
        import OpaqueTimestamp._

        type VectorTimestampStructure = StructuralRecord { def timestamps: Vector[Timestamp] }
        val schema = Schema.derived[VectorTimestampStructure]

        assertTrue(schema.reflect.typeName.name == "{timestamps:Vector[Long]}")
      }
    ),
    suite("Mixed sum types and opaque types in structural types")(
      test("structural type with opaque field and Either field") {
        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type MixedStructure = StructuralRecord {
          def userId: UserId
          def result: Either[String, Int]
        }
        val schema = Schema.derived[MixedStructure]

        // Opaque unwraps, Either shows structural representation
        assertTrue(
          schema.reflect.typeName.name == """{result:({Tag:"Left",value:String}|{Tag:"Right",value:Int}),userId:String}"""
        )
      },
      test("structural type with opaque field and enum field - round-trip") {
        enum Status { case Active, Inactive }
        given Schema[Status] = Schema.derived[Status]

        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type UserStatusStructure = StructuralRecord {
          def userId: UserId
          def status: Status
        }
        val schema = Schema.derived[UserStatusStructure]

        assertTrue(
          schema.reflect.typeName.name == """{status:({Tag:"Active"}|{Tag:"Inactive"}),userId:String}"""
        )

        val statusValue                = new StructuralRecord(Map("Tag" -> "Active"))
        val value: UserStatusStructure =
          new StructuralRecord(Map("userId" -> "user123", "status" -> statusValue))
            .asInstanceOf[UserStatusStructure]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(result.isRight) &&
        assertTrue {
          val record = result.toOption.get.asInstanceOf[StructuralRecord]
          record.selectDynamic("userId") == "user123" &&
          record.selectDynamic("status").asInstanceOf[StructuralRecord].selectDynamic("Tag") == "Active"
        }
      },
      test("List of tuples with opaque and sum types") {
        enum Priority { case High, Low }
        given Schema[Priority] = Schema.derived[Priority]

        object OpaqueTaskId {
          opaque type TaskId = Int
          object TaskId {
            def apply(i: Int): TaskId = i
          }
          given Schema[TaskId] = Schema.int.asInstanceOf[Schema[TaskId]]
        }
        import OpaqueTaskId._

        type TaskListStructure = StructuralRecord {
          def tasks: List[(TaskId, Priority)]
        }
        val schema = Schema.derived[TaskListStructure]

        // Opaque unwraps, tuple becomes structural, Priority shows as sum type
        assertTrue(
          schema.reflect.typeName.name == """{tasks:List[{_1:Int,_2:({Tag:"High"}|{Tag:"Low"})}]}"""
        )
      },
      test("nested structural type with mixed opaque and sealed trait") {
        sealed trait Result
        @nowarn("msg=unused") case class Success(value: String) extends Result
        @nowarn("msg=unused") case class Failure(code: Int)     extends Result

        given Schema[Result] = Schema.derived[Result]

        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type InnerStructure = StructuralRecord { def userId: UserId }
        type OuterStructure = StructuralRecord { def inner: InnerStructure; def result: Result }

        given Schema[InnerStructure] = Schema.derived[InnerStructure]
        val schema                   = Schema.derived[OuterStructure]

        assertTrue(
          schema.reflect.typeName.name == """{inner:{userId:String},result:({Tag:"Failure",code:Int}|{Tag:"Success",value:String})}"""
        )
      }
    ),
    suite("Equality for structural types with sum types")(
      test("equal structural values with same Either produce same records") {
        type WithEitherStructure = StructuralRecord { def value: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        assertTrue(
          schema.reflect.typeName.name == """{value:({Tag:"Left",value:String}|{Tag:"Right",value:Int})}"""
        ) &&
        {
          val eitherValue1 = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
          val value1       = new StructuralRecord(Map("value" -> eitherValue1))

          val eitherValue2 = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
          val value2       = new StructuralRecord(Map("value" -> eitherValue2))

          assertTrue(value1 == value2)
        }
      },
      test("different structural values with different Either sides are not equal") {
        type WithEitherStructure = StructuralRecord { def value: Either[String, Int] }
        val schema = Schema.derived[WithEitherStructure]

        assertTrue(
          schema.reflect.typeName.name == """{value:({Tag:"Left",value:String}|{Tag:"Right",value:Int})}"""
        ) &&
        {
          val eitherValue1 = new StructuralRecord(Map("Tag" -> "Left", "value" -> "error"))
          val value1       = new StructuralRecord(Map("value" -> eitherValue1))

          val eitherValue2 = new StructuralRecord(Map("Tag" -> "Right", "value" -> 42))
          val value2       = new StructuralRecord(Map("value" -> eitherValue2))

          assertTrue(value1 != value2)
        }
      }
    ),
    suite("Equality for structural types with opaque types")(
      test("equal structural values with same opaque values are equal") {
        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type WithOpaqueStructure = StructuralRecord { def id: UserId }
        val schema = Schema.derived[WithOpaqueStructure]

        assertTrue(schema.reflect.typeName.name == "{id:String}") &&
        {
          val value1: WithOpaqueStructure =
            new StructuralRecord(Map("id" -> "user123")).asInstanceOf[WithOpaqueStructure]
          val value2: WithOpaqueStructure =
            new StructuralRecord(Map("id" -> "user123")).asInstanceOf[WithOpaqueStructure]

          assertTrue(value1 == value2)
        }
      },
      test("different structural values with different opaque values are not equal") {
        object OpaqueUserId {
          opaque type UserId = String
          object UserId {
            def apply(s: String): UserId = s
          }
          given Schema[UserId] = Schema.string.asInstanceOf[Schema[UserId]]
        }
        import OpaqueUserId._

        type WithOpaqueStructure = StructuralRecord { def id: UserId }
        val schema = Schema.derived[WithOpaqueStructure]

        assertTrue(schema.reflect.typeName.name == "{id:String}") &&
        {
          val value1: WithOpaqueStructure =
            new StructuralRecord(Map("id" -> "user1")).asInstanceOf[WithOpaqueStructure]
          val value2: WithOpaqueStructure =
            new StructuralRecord(Map("id" -> "user2")).asInstanceOf[WithOpaqueStructure]

          assertTrue(value1 != value2)
        }
      }
    )
  )
}
