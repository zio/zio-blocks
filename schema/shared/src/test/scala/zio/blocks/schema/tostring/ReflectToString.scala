package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.test._

object ReflectSpec extends ZIOSpecDefault {

  def spec = suite("Reflect toString")(
    suite("Primitive types")(
      test("renders String") {
        val schema = Schema.string
        assertTrue(schema.reflect.toString == "String")
      },
      test("renders Int") {
        val schema = Schema.int
        assertTrue(schema.reflect.toString == "Int")
      },
      test("renders Boolean") {
        val schema = Schema.boolean
        assertTrue(schema.reflect.toString == "Boolean")
      },
      test("renders Long") {
        val schema = Schema.long
        assertTrue(schema.reflect.toString == "Long")
      },
      test("renders Double") {
        val schema = Schema.double
        assertTrue(schema.reflect.toString == "Double")
      },
      test("renders BigDecimal") {
        val schema = Schema.bigDecimal
        assertTrue(schema.reflect.toString == "BigDecimal")
      },
      test("renders java.time.Instant") {
        val schema = Schema.instant
        assertTrue(schema.reflect.toString == "java.time.Instant")
      },
      test("renders java.util.UUID") {
        val schema = Schema.uuid
        assertTrue(schema.reflect.toString == "java.util.UUID")
      }
    ),

    suite("Simple Record")(
      test("renders empty record") {
        lazy implicit val schema: Schema[EmptyRecord] = Schema.derived[EmptyRecord]
        assertTrue(schema.reflect.toString == "record EmptyRecord {}")
      },
      test("renders record with single primitive field") {
        lazy implicit val schema: Schema[Point1D] = Schema.derived[Point1D]
        val expected =
          """record Point1D {
            |  x: Int
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      },
      test("renders record with multiple primitive fields") {
        lazy implicit val schema: Schema[Point2D] = Schema.derived[Point2D]
        val expected =
          """record Point2D {
            |  x: Int
            |  y: Int
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      },
      test("renders record with mixed primitive types") {
        lazy implicit val schema: Schema[Person] = Schema.derived[Person]
        val expected =
          """record Person {
            |  name: String
            |  age: Int
            |  active: Boolean
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      }
    ),

    suite("Nested Record")(
      test("renders record with nested record field") {
        lazy implicit val addressSchema: Schema[Address] = Schema.derived[Address]
        lazy implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

        val expected =
          """record PersonWithAddress {
            |  name: String
            |  age: Int
            |  address:   record Address {
            |    street: String
            |    city: String
            |    zip: String
            |  }
            |}""".stripMargin
        assertTrue(personWithAddressSchema.reflect.toString == expected)
      }
    ),

    suite("Simple Variant")(
      test("renders variant with enum-style cases (no fields)") {
        lazy implicit val schema: Schema[BooleanEnum] = Schema.derived[BooleanEnum]

        val expected =
          """variant BooleanEnum {
            |  | True
            |  | False
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      },
      test("renders Option[Int] variant") {
        lazy implicit val schema: Schema[Option[Int]] = Schema.optionInt
        val expected =
          """variant Option[Int] {
            |  | None
            |  | Some(value: Int)
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      }
    ),

    suite("Complex Variant with Different Payloads")(
      test("renders PaymentMethod variant with mixed case structures") {
        lazy implicit val bankAccountSchema: Schema[BankAccount] = Schema.derived[BankAccount]
        lazy implicit val paymentMethodSchema: Schema[PaymentMethod] = Schema.derived[PaymentMethod]

        val expected =
          """variant PaymentMethod {
            |  | Cash
            |  | CreditCard(
            |      number: String,
            |      expiry: String,
            |      cvv: String
            |    )
            |  | BankTransfer(
            |      account:       record BankAccount {
            |        routing: String
            |        number: String
            |      }
            |    )
            |}""".stripMargin
        assertTrue(paymentMethodSchema.reflect.toString == expected)
      }
    ),

    suite("More Primitive types")(
      test("renders Byte") {
        val schema = Schema.byte
        assertTrue(schema.reflect.toString == "Byte")
      },
      test("renders Short") {
        val schema = Schema.short
        assertTrue(schema.reflect.toString == "Short")
      },
      test("renders Float") {
        val schema = Schema.float
        assertTrue(schema.reflect.toString == "Float")
      },
      test("renders Char") {
        val schema = Schema.char
        assertTrue(schema.reflect.toString == "Char")
      },
      test("renders Unit") {
        val schema = Schema.unit
        assertTrue(schema.reflect.toString == "Unit")
      },
      test("renders Duration") {
        val schema = Schema.duration
        assertTrue(schema.reflect.toString == "java.time.Duration")
      },
      test("renders Period") {
        val schema = Schema.period
        assertTrue(schema.reflect.toString == "java.time.Period")
      },
      test("renders LocalDate") {
        val schema = Schema.localDate
        assertTrue(schema.reflect.toString == "java.time.LocalDate")
      }
    ),

    suite("Sequence types")(
      test("renders sequence with primitive element") {
        lazy implicit val schema: Schema[List[Int]] = Schema.list[Int]
        assertTrue(schema.reflect.toString == "sequence List[Int]")
      },
      test("renders sequence with complex element") {
        lazy implicit val itemSchema: Schema[OrderItem] = Schema.derived[OrderItem]
        lazy implicit val vectorSchema: Schema[Vector[OrderItem]] = Schema.vector[OrderItem]

        val expected =
          """sequence Vector[
            |  record OrderItem {
            |    product: String
            |    quantity: Int
            |    price: BigDecimal
            |  }
            |]""".stripMargin
        assertTrue(vectorSchema.reflect.toString == expected)
      }
    ),

    suite("Map types")(
      test("renders map with primitive types") {
        lazy implicit val schema: Schema[collection.immutable.Map[String, Int]] = Schema.map[String, Int]
        assertTrue(schema.reflect.toString == "map Map[String, Int]")
      },
      test("renders map with complex value type") {
        lazy implicit val configSchema: Schema[Config] = Schema.derived[Config]
        lazy implicit val mapSchema: Schema[collection.immutable.Map[String, Config]] = Schema.map[String, Config]

        val expected =
          """map Map[
            |  String,
            |  record Config {
            |    value: String
            |    enabled: Boolean
            |  }
            |]""".stripMargin
        assertTrue(mapSchema.reflect.toString == expected)
      }
    ),

    suite("Wrapper types")(
      test("renders wrapper with primitive underlying type") {
        // Note: UserId is AnyVal but Schema.derived treats it as a record (see bug-unrelated in Bug.md)
        // When that bug is fixed, this test will need updating
        lazy implicit val schema: Schema[UserId] = Schema.derived[UserId]
        // Current output (as record):
        val expected =
          """record UserId {
            |  value: String
            |}""".stripMargin
        assertTrue(schema.reflect.toString == expected)
      },
      test("renders wrapper with complex underlying type") {
        // ValidatedEmail is a regular case class, so it's derived as a record (not a wrapper)
        lazy implicit val emailPartsSchema: Schema[EmailParts] = Schema.derived[EmailParts]
        lazy implicit val validatedEmailSchema: Schema[ValidatedEmail] = Schema.derived[ValidatedEmail]

        val expected =
          """record ValidatedEmail {
            |  parts:   record EmailParts {
            |    local: String
            |    domain: String
            |  }
            |}""".stripMargin
        assertTrue(validatedEmailSchema.reflect.toString == expected)
      },
      test("renders nested wrappers") {
        // Note: Both UUID and OrderId are AnyVal but Schema.derived treats them as records
        // When bug-unrelated is fixed, this test will need updating
        lazy implicit val uuidSchema: Schema[UUID] = Schema.derived[UUID]
        lazy implicit val orderIdSchema: Schema[OrderId] = Schema.derived[OrderId]

        // Current output (as nested records):
        val expected =
          """record OrderId {
            |  uuid:   record UUID {
            |    value: String
            |  }
            |}""".stripMargin
        assertTrue(orderIdSchema.reflect.toString == expected)
      }
    ),

    suite("Deferred types (recursive)")(
      test("renders simple recursive type") {
        lazy implicit val treeSchema: Schema[Tree] = Schema.derived[Tree]

        val expected =
          """record Tree {
            |  value: Int
            |  children:   sequence List[
            |    deferred => Tree
            |  ]
            |}""".stripMargin
        assertTrue(treeSchema.reflect.toString == expected)
      },
      test("renders mutually recursive types") {
        lazy implicit val nodeSchema: Schema[Node] = Schema.derived[Node]
        lazy implicit val edgeSchema: Schema[Edge] = Schema.derived[Edge]

        val expected =
          """record Node {
            |  id: Int
            |  edges:   sequence List[
            |    record Edge {
            |      label: String
            |      target: deferred => Node
            |    }
            |  ]
            |}""".stripMargin
        assertTrue(nodeSchema.reflect.toString == expected)
      }
    ),

    suite("Complex nested example")(
      test("renders full complex schema") {
        // This test demonstrates a real-world schema with multiple levels of nesting
        // Note: Email is an AnyVal but Schema.derived treats it as a record (see bug-unrelated in Bug.md)
        lazy implicit val addressSchema: Schema[Address] = Schema.derived[Address]
        lazy implicit val emailSchema: Schema[Email] = Schema.derived[Email]
        lazy implicit val customerSchema: Schema[Customer] = Schema.derived[Customer]

        // Current output (Email as record instead of wrapper):
        val expected =
          """record Customer {
            |  name: String
            |  email:   record Email {
            |    value: String
            |  }
            |  address:   record Address {
            |    street: String
            |    city: String
            |    zip: String
            |  }
            |}""".stripMargin
        assertTrue(customerSchema.reflect.toString == expected)
      }
    ),

    suite("Dynamic type")(
      test("renders dynamic as its type name") {
        val schema = Schema.dynamic
        assertTrue(schema.reflect.toString == "DynamicValue")
      }
    ),

    suite("Edge cases")(
      test("handles deeply nested records") {
        lazy implicit val level3Schema: Schema[Level3] = Schema.derived[Level3]
        lazy implicit val level2Schema: Schema[Level2] = Schema.derived[Level2]
        lazy implicit val level1Schema: Schema[Level1] = Schema.derived[Level1]

        val expected =
          """record Level1 {
            |  nested:   record Level2 {
            |    nested:   record Level3 {
            |      value: Int
            |    }
            |  }
            |}""".stripMargin
        assertTrue(level1Schema.reflect.toString == expected)
      }
    )
  )

  // Test data types
  case class EmptyRecord()
  case class Point1D(x: Int)
  case class Point2D(x: Int, y: Int)
  case class Person(name: String, age: Int, active: Boolean)
  case class Address(street: String, city: String, zip: String)
  case class PersonWithAddress(name: String, age: Int, address: Address)

  sealed trait PaymentMethod
  case object Cash                                                   extends PaymentMethod
  case class CreditCard(number: String, expiry: String, cvv: String) extends PaymentMethod
  case class BankTransfer(account: BankAccount)                      extends PaymentMethod
  case class BankAccount(routing: String, number: String)

  sealed trait BooleanEnum
  object BooleanEnum {
    case object True  extends BooleanEnum
    case object False extends BooleanEnum
  }

  case class OrderItem(product: String, quantity: Int, price: BigDecimal)
  case class Config(value: String, enabled: Boolean)

  case class UserId(value: String) extends AnyVal
  case class EmailParts(local: String, domain: String)
  case class ValidatedEmail(parts: EmailParts)
  case class UUID(value: String) extends AnyVal
  case class OrderId(uuid: UUID)

  case class Tree(value: Int, children: List[Tree])

  case class Node(id: Int, edges: List[Edge])
  case class Edge(label: String, target: Node)

  case class Email(value: String) extends AnyVal
  case class Customer(name: String, email: Email, address: Address)

  case class Level3(value: Int)
  case class Level2(nested: Level3)
  case class Level1(nested: Level2)
}
