package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for nested product types to structural conversion.
 */
object NestedProductSpec extends ZIOSpecDefault {

  case class Address(street: String, city: String, zip: Int)
  case class Person(name: String, age: Int, address: Address)

  case class Inner(value: Int)
  case class Middle(inner: Inner)
  case class Outer(middle: Middle)

  // Helper to create DynamicValue.Primitive for common types
  private def intPrim(i: Int) = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def strPrim(s: String) = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def doublePrim(d: Double) = DynamicValue.Primitive(PrimitiveValue.Double(d))

  def spec = suite("NestedProductSpec")(
    suite("Type Name Verification")(
      test("nested case classes produce type name with all fields") {
        val nominalSchema = Schema.derived[Person]
        val structuralSchema = nominalSchema.structural

        val typeName = structuralSchema.reflect.typeName.name
        assertTrue(
          typeName.contains("name"),
          typeName.contains("age"),
          typeName.contains("address")
        )
      },
      test("deeply nested structures produce non-empty type name") {
        val schema = Schema.derived[Outer]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(
          typeName.nonEmpty,
          typeName.contains("middle")
        )
      }
    ),
    suite("Field Hierarchy")(
      test("structural schema preserves top-level field names") {
        val schema = Schema.derived[Person]
        val structural = schema.structural

        val fieldNames = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }

        assertTrue(
          fieldNames.contains("name"),
          fieldNames.contains("age"),
          fieldNames.contains("address"),
          fieldNames.size == 3
        )
      },
      test("nested field count matches original") {
        val schema = Schema.derived[Address]
        val structural = schema.structural

        val fieldCount = structural.reflect match {
          case record: Reflect.Record[_, _] => record.fields.size
          case _                            => -1
        }

        assertTrue(fieldCount == 3)
      }
    ),
    suite("Construction and Destruction")(
      test("nested case class round-trip preserves all data") {
        val person = Person("Alice", 30, Address("123 Main St", "Springfield", 12345))
        val schema = Schema.derived[Person]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(person)
        
        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("name").contains(strPrim("Alice")),
              fieldMap.get("age").contains(intPrim(30)),
              fieldMap.get("address") match {
                case Some(DynamicValue.Record(addressFields)) =>
                  val addrMap = addressFields.toMap
                  addrMap.get("street").contains(strPrim("123 Main St")) &&
                  addrMap.get("city").contains(strPrim("Springfield")) &&
                  addrMap.get("zip").contains(intPrim(12345))
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("deeply nested case class extracts all levels correctly") {
        val outer = Outer(Middle(Inner(42)))
        val schema = Schema.derived[Outer]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(outer)

        dynamic match {
          case record: DynamicValue.Record =>
            val outerMap = record.fields.toMap
            assertTrue(
              outerMap.get("middle") match {
                case Some(DynamicValue.Record(middleFields)) =>
                  val middleMap = middleFields.toMap
                  middleMap.get("inner") match {
                    case Some(DynamicValue.Record(innerFields)) =>
                      val innerMap = innerFields.toMap
                      innerMap.get("value").contains(intPrim(42))
                    case _ => false
                  }
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("construction from DynamicValue succeeds for nested structures") {
        val schema = Schema.derived[Person]
        val structural = schema.structural

        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> strPrim("Bob"),
            "age" -> intPrim(25),
            "address" -> DynamicValue.Record(
              Vector(
                "street" -> strPrim("456 Oak Ave"),
                "city" -> strPrim("Shelbyville"),
                "zip" -> intPrim(67890)
              )
            )
          )
        )

        val result = structural.fromDynamicValue(dynamic)
        assertTrue(result.isRight)
      }
    ),
    suite("Nested Type Conversion")(
      test("inner case class is converted to structural representation") {
        val schema = Schema.derived[Person]
        val structural = schema.structural

        // The address field should have a nested Record reflect, not just a primitive
        val addressField = structural.reflect match {
          case record: Reflect.Record[_, _] =>
            record.fields.find(_.name == "address").map(_.value)
          case _ => None
        }

        assertTrue(
          addressField.isDefined,
          addressField.get.isInstanceOf[Reflect.Record[_, _]]
        )
      },
      test("deeply nested inner types are all records") {
        val schema = Schema.derived[Outer]
        val structural = schema.structural

        val middleField = structural.reflect match {
          case record: Reflect.Record[_, _] =>
            record.fields.find(_.name == "middle").map(_.value)
          case _ => None
        }

        assertTrue(middleField.isDefined)

        val innerField = middleField.get match {
          case record: Reflect.Record[_, _] =>
            record.fields.find(_.name == "inner").map(_.value)
          case _ => None
        }

        assertTrue(
          innerField.isDefined,
          innerField.get.isInstanceOf[Reflect.Record[_, _]]
        )
      }
    ),
    suite("Mixed Nesting Patterns")(
      test("case class with multiple nested fields") {
        case class Contact(email: String, phone: String)
        case class Employee(name: String, address: Address, contact: Contact)

        val employee = Employee(
          "John",
          Address("789 Elm St", "Capital City", 11111),
          Contact("john@example.com", "555-1234")
        )

        val schema = Schema.derived[Employee]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(employee)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("name").contains(strPrim("John")),
              fieldMap.get("address").isInstanceOf[Some[DynamicValue.Record]],
              fieldMap.get("contact").isInstanceOf[Some[DynamicValue.Record]]
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("case class with primitive and nested fields mixed") {
        case class Order(id: Int, customer: Person, total: Double)

        val order = Order(
          1,
          Person("Customer", 40, Address("100 Shop Lane", "Market Town", 22222)),
          99.99
        )

        val schema = Schema.derived[Order]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(order)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("id").contains(intPrim(1)),
              fieldMap.get("total").contains(doublePrim(99.99)),
              fieldMap.get("customer") match {
                case Some(DynamicValue.Record(customerFields)) =>
                  val custMap = customerFields.toMap
                  custMap.get("name").contains(strPrim("Customer"))
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    )
  )
}

