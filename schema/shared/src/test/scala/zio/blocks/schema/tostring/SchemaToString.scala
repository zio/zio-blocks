package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.test._

object SchemaToStringSpec extends ZIOSpecDefault {

  def spec = suite("Schema toString")(
    suite("Primitive types")(
      test("renders String") {
        lazy implicit val schema: Schema[String] = Schema.string
        assertTrue(schema.toString == "Schema {\n  String\n}")
      },
      test("renders Int") {
        lazy implicit val schema: Schema[Int] = Schema.int
        assertTrue(schema.toString == "Schema {\n  Int\n}")
      },
      test("renders Boolean") {
        lazy implicit val schema: Schema[Boolean] = Schema.boolean
        assertTrue(schema.toString == "Schema {\n  Boolean\n}")
      },
      test("renders Long") {
        lazy implicit val schema: Schema[Long] = Schema.long
        assertTrue(schema.toString == "Schema {\n  Long\n}")
      },
      test("renders Double") {
        lazy implicit val schema: Schema[Double] = Schema.double
        assertTrue(schema.toString == "Schema {\n  Double\n}")
      },
      test("renders BigDecimal") {
        lazy implicit val schema: Schema[BigDecimal] = Schema.bigDecimal
        assertTrue(schema.toString == "Schema {\n  BigDecimal\n}")
      },
      test("renders java.time.Instant") {
        lazy implicit val schema: Schema[java.time.Instant] = Schema.instant
        assertTrue(schema.toString == "Schema {\n  java.time.Instant\n}")
      },
      test("renders java.util.UUID") {
        lazy implicit val schema: Schema[java.util.UUID] = Schema.uuid
        assertTrue(schema.toString == "Schema {\n  java.util.UUID\n}")
      }
    ),

    suite("Simple Record")(
      test("renders empty record") {
        lazy implicit val schema: Schema[EmptyRecord] = Schema.derived[EmptyRecord]
        assertTrue(schema.toString == "Schema {\n  record EmptyRecord {}\n}")
      },
      test("renders record with single primitive field") {
        lazy implicit val schema: Schema[Point1D] = Schema.derived[Point1D]
        val expected                              =
          """Schema {
            |  record Point1D {
            |    x: Int
            |  }
            |}""".stripMargin
        assertTrue(schema.toString == expected)
      },
      test("renders record with multiple primitive fields") {
        lazy implicit val schema: Schema[Point2D] = Schema.derived[Point2D]
        val expected                              =
          """Schema {
            |  record Point2D {
            |    x: Int
            |    y: Int
            |  }
            |}""".stripMargin
        assertTrue(schema.toString == expected)
      },
      test("renders record with mixed primitive types") {
        lazy implicit val schema: Schema[Person] = Schema.derived[Person]
        val expected                             =
          """Schema {
            |  record Person {
            |    name: String
            |    age: Int
            |    active: Boolean
            |  }
            |}""".stripMargin
        assertTrue(schema.toString == expected)
      }
    ),

    suite("Nested Record")(
      test("renders record with nested record field") {
        lazy implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
        lazy implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

        val expected =
          """Schema {
            |  record PersonWithAddress {
            |    name: String
            |    age: Int
            |    address:   record Address {
            |      street: String
            |      city: String
            |      zip: String
            |    }
            |  }
            |}""".stripMargin
        assertTrue(personWithAddressSchema.toString == expected)
      }
    ),

    suite("Simple Variant")(
      test("renders variant with enum-style cases (no fields)") {
        lazy implicit val schema: Schema[BooleanEnum] = Schema.derived[BooleanEnum]
        val expected                                  =
          """Schema {
            |  variant BooleanEnum {
            |    | True
            |    | False
            |  }
            |}""".stripMargin
        assertTrue(schema.toString == expected)
      },
      test("renders Option[Int] variant") {
        lazy implicit val schema: Schema[Option[Int]] = Schema.optionInt
        val expected                                  =
          """Schema {
            |  variant Option[Int] {
            |    | None
            |    | Some(value: Int)
            |  }
            |}""".stripMargin
        assertTrue(schema.toString == expected)
      }
    ),

    suite("Complex Variant with Different Payloads")(
      test("renders PaymentMethod-like variant with mixed case structures") {

        lazy implicit val bankAccountSchema: Schema[BankAccountTest]     = Schema.derived
        lazy implicit val paymentMethodSchema: Schema[PaymentMethodTest] = Schema.derived

        val str = paymentMethodSchema.toString
        assertTrue(
          str.contains("variant PaymentMethodTest {"),
          str.contains("| CashTest"),
          str.contains("| CreditCardTest("),
          str.contains("number: String"),
          str.contains("| BankTransferTest(")
        )
      }
    ),

    suite("More Primitive types")(
      test("renders Byte") {
        lazy implicit val schema: Schema[Byte] = Schema.byte
        assertTrue(schema.toString == "Schema {\n  Byte\n}")
      },
      test("renders Short") {
        lazy implicit val schema: Schema[Short] = Schema.short
        assertTrue(schema.toString == "Schema {\n  Short\n}")
      },
      test("renders Float") {
        lazy implicit val schema: Schema[Float] = Schema.float
        assertTrue(schema.toString == "Schema {\n  Float\n}")
      },
      test("renders Char") {
        lazy implicit val schema: Schema[Char] = Schema.char
        assertTrue(schema.toString == "Schema {\n  Char\n}")
      },
      test("renders Unit") {
        lazy implicit val schema: Schema[Unit] = Schema.unit
        assertTrue(schema.toString == "Schema {\n  Unit\n}")
      },
      test("renders Duration") {
        lazy implicit val schema: Schema[java.time.Duration] = Schema.duration
        assertTrue(schema.toString == "Schema {\n  java.time.Duration\n}")
      },
      test("renders Period") {
        lazy implicit val schema: Schema[java.time.Period] = Schema.period
        assertTrue(schema.toString == "Schema {\n  java.time.Period\n}")
      },
      test("renders LocalDate") {
        lazy implicit val schema: Schema[java.time.LocalDate] = Schema.localDate
        assertTrue(schema.toString == "Schema {\n  java.time.LocalDate\n}")
      }
    ),

    suite("Sequence types")(
      test("renders sequence with primitive element") {
        lazy implicit val schema: Schema[List[Int]] = Schema.list[Int]
        assertTrue(schema.toString == "Schema {\n  sequence List[Int]\n}")
      },
      test("renders sequence with complex element") {
        lazy implicit val itemSchema: Schema[OrderItem]           = Schema.derived[OrderItem]
        lazy implicit val vectorSchema: Schema[Vector[OrderItem]] = Schema.vector[OrderItem]

        val expected =
          """Schema {
            |  sequence Vector[
            |    record OrderItem {
            |      product: String
            |      quantity: Int
            |      price: BigDecimal
            |    }
            |  ]
            |}""".stripMargin
        assertTrue(vectorSchema.toString == expected)
      }
    ),

    suite("Map types")(
      test("renders map with primitive types") {
        lazy implicit val schema: Schema[collection.immutable.Map[String, Int]] =
          Schema.map[String, Int]
        assertTrue(schema.toString == "Schema {\n  map Map[String, Int]\n}")
      },
      test("renders map with complex value type") {
        lazy implicit val configSchema: Schema[Config]                                = Schema.derived[Config]
        lazy implicit val mapSchema: Schema[collection.immutable.Map[String, Config]] =
          Schema.map[String, Config]

        val expected =
          """Schema {
            |  map Map[
            |    String,
            |    record Config {
            |      value: String
            |      enabled: Boolean
            |    }
            |  ]
            |}""".stripMargin
        assertTrue(mapSchema.toString == expected)
      }
    ),

    suite("Wrapper types")(
      test("renders AnyVal with primitive underlying type") {
        // Note: Schema.derived treats AnyVal as records, not wrappers
        lazy implicit val schema: Schema[UserId] = Schema.derived[UserId]
        val expected                             = """Schema {
                                                     |  record UserId {
                                                     |    value: String
                                                     |  }
                                                     |}""".stripMargin
        assertTrue(schema.toString == expected)
      },
      test("renders case class with complex underlying type") {
        // Note: ValidatedEmail is a regular case class derived as a record
        lazy implicit val emailPartsSchema: Schema[EmailParts]         = Schema.derived[EmailParts]
        lazy implicit val validatedEmailSchema: Schema[ValidatedEmail] = Schema.derived[ValidatedEmail]

        val expected =
          """Schema {
            |  record ValidatedEmail {
            |    parts:   record EmailParts {
            |      local: String
            |      domain: String
            |    }
            |  }
            |}""".stripMargin
        assertTrue(validatedEmailSchema.toString == expected)
      },
      test("renders nested AnyVal types") {
        // Both UUID and OrderId are AnyVal but Schema.derived treats them as records
        lazy implicit val uuidSchema: Schema[UUID]       = Schema.derived[UUID]
        lazy implicit val orderIdSchema: Schema[OrderId] = Schema.derived[OrderId]

        val expected = """Schema {
                         |  record OrderId {
                         |    uuid:   record UUID {
                         |      value: String
                         |    }
                         |  }
                         |}""".stripMargin
        assertTrue(orderIdSchema.toString == expected)
      }
    ),

    suite("Deferred types (recursive)")(
      test("renders simple recursive type") {
        lazy implicit val treeSchema: Schema[Tree] = Schema.derived[Tree]

        val expected =
          """Schema {
            |  record Tree {
            |    value: Int
            |    children:   sequence List[
            |      deferred => Tree
            |    ]
            |  }
            |}""".stripMargin
        assertTrue(treeSchema.toString == expected)
      },
      test("renders mutually recursive types") {
        lazy implicit val nodeSchema: Schema[Node] = Schema.derived[Node]
        lazy implicit val edgeSchema: Schema[Edge] = Schema.derived[Edge]

        val expected =
          """Schema {
            |  record Node {
            |    id: Int
            |    edges:   sequence List[
            |      record Edge {
            |        label: String
            |        target: deferred => Node
            |      }
            |    ]
            |  }
            |}""".stripMargin
        assertTrue(nodeSchema.toString == expected)
      }
    ),

    suite("Complex nested example")(
      test("renders full complex schema") {
        // This test demonstrates a real-world schema with multiple levels of nesting
        // Note: Email is an AnyVal but Schema.derived treats it as a record, not a wrapper
        lazy implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
        lazy implicit val emailSchema: Schema[Email]       = Schema.derived[Email]
        lazy implicit val customerSchema: Schema[Customer] = Schema.derived[Customer]

        val expected =
          """Schema {
            |  record Customer {
            |    name: String
            |    email:   record Email {
            |      value: String
            |    }
            |    address:   record Address {
            |      street: String
            |      city: String
            |      zip: String
            |    }
            |  }
            |}""".stripMargin
        assertTrue(customerSchema.toString == expected)
      }
    ),

    suite("Dynamic type")(
      test("renders dynamic as its type name") {
        lazy implicit val schema: Schema[DynamicValue] = Schema.dynamic
        assertTrue(schema.toString == "Schema {\n  DynamicValue\n}")
      }
    ),

    suite("Edge cases")(
      test("handles deeply nested records") {
        lazy implicit val level3Schema: Schema[Level3] = Schema.derived[Level3]
        lazy implicit val level2Schema: Schema[Level2] = Schema.derived[Level2]
        lazy implicit val level1Schema: Schema[Level1] = Schema.derived[Level1]

        val expected =
          """Schema {
            |  record Level1 {
            |    nested:   record Level2 {
            |      nested:   record Level3 {
            |        value: Int
            |      }
            |    }
            |  }
            |}""".stripMargin
        assertTrue(level1Schema.toString == expected)
      }
    ),

    suite("Primitive types with validations")(
      test("renders String with NonEmpty validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, String](
            new PrimitiveType.String(Validation.String.NonEmpty),
            TypeName.string,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  String @NonEmpty\n}")
      },
      test("renders String with Length validation (both bounds)") {
        val schema = new Schema(
          Reflect.Primitive[Binding, String](
            new PrimitiveType.String(Validation.String.Length(Some(3), Some(50))),
            TypeName.string,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  String @Length(min=3, max=50)\n}")
      },
      test("renders String with Length validation (min only)") {
        val schema = new Schema(
          Reflect.Primitive[Binding, String](
            new PrimitiveType.String(Validation.String.Length(Some(3), None)),
            TypeName.string,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  String @Length(min=3)\n}")
      },
      test("renders String with Pattern validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, String](
            new PrimitiveType.String(Validation.String.Pattern("^[a-z]+$")),
            TypeName.string,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  String @Pattern(\"^[a-z]+$\")\n}")
      },
      test("renders Int with Positive validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, Int](
            new PrimitiveType.Int(Validation.Numeric.Positive),
            TypeName.int,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  Int @Positive\n}")
      },
      test("renders Int with NonNegative validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, Int](
            new PrimitiveType.Int(Validation.Numeric.NonNegative),
            TypeName.int,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  Int @NonNegative\n}")
      },
      test("renders Int with Range validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, Int](
            new PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(100))),
            TypeName.int,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  Int @Range(min=0, max=100)\n}")
      },
      test("renders Long with Negative validation") {
        val schema = new Schema(
          Reflect.Primitive[Binding, Long](
            new PrimitiveType.Long(Validation.Numeric.Negative),
            TypeName.long,
            Binding.Primitive()
          )
        )
        assertTrue(schema.toString == "Schema {\n  Long @Negative\n}")
      },
      test("renders record schema with validated primitive fields") {
        // Derive schema to get proper binding, then replace fields with validated versions
        lazy implicit val baseSchema: Schema[ValidatedUserWithEmail] = Schema.derived[ValidatedUserWithEmail]
        val derivedRecord                                            = baseSchema.reflect.asRecord.get

        // Create validated primitives
        val nameReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.Length(Some(1), Some(100))),
          TypeName.string,
          Binding.Primitive()
        )
        val ageReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(150))),
          TypeName.int,
          Binding.Primitive()
        )
        val emailReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.Pattern("^[^@]+@[^@]+$")),
          TypeName.string,
          Binding.Primitive()
        )

        // Create new record with validated fields, reusing derived binding
        val validatedRecord = derivedRecord.copy(
          fields = Vector(
            Term[Binding, ValidatedUserWithEmail, String]("name", nameReflect),
            Term[Binding, ValidatedUserWithEmail, Int]("age", ageReflect),
            Term[Binding, ValidatedUserWithEmail, String]("email", emailReflect)
          )
        )

        val schema   = new Schema(validatedRecord)
        val expected =
          """Schema {
            |  record ValidatedUserWithEmail {
            |    name: String @Length(min=1, max=100)
            |    age: Int @Range(min=0, max=150)
            |    email: String @Pattern("^[^@]+@[^@]+$")
            |  }
            |}""".stripMargin

        assertTrue(schema.toString == expected)
      },
      test("renders sequence schema with validated element type") {
        val intReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Positive),
          TypeName.int,
          Binding.Primitive()
        )

        val listReflect = Reflect.Sequence[Binding, Int, List](
          element = intReflect,
          typeName = TypeName.list(TypeName.int),
          seqBinding = Binding.Seq.list
        )

        val schema = new Schema(listReflect)
        assertTrue(schema.toString == "Schema {\n  sequence List[Int @Positive]\n}")
      },
      test("renders map schema with validated key and value types") {
        val keyReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.NonBlank),
          TypeName.string,
          Binding.Primitive()
        )
        val valueReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.NonNegative),
          TypeName.int,
          Binding.Primitive()
        )

        val mapReflect = Reflect.Map[Binding, String, Int, scala.collection.immutable.Map](
          key = keyReflect,
          value = valueReflect,
          typeName = TypeName.map(TypeName.string, TypeName.int),
          mapBinding = Binding.Map.map
        )

        val schema = new Schema(mapReflect)
        assertTrue(schema.toString == "Schema {\n  map Map[String @NonBlank, Int @NonNegative]\n}")
      }
    )
  )

  // Test data types - using the same types as ReflectSpec for consistency
  case class EmptyRecord()
  case class Point1D(x: Int)
  case class Point2D(x: Int, y: Int)
  case class Person(name: String, age: Int, active: Boolean)
  case class Address(street: String, city: String, zip: String)
  case class PersonWithAddress(name: String, age: Int, address: Address)

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

  sealed trait PaymentMethodTest
  case object CashTest                                                   extends PaymentMethodTest
  case class CreditCardTest(number: String, expiry: String, cvv: String) extends PaymentMethodTest
  case class BankTransferTest(account: BankAccountTest)                  extends PaymentMethodTest
  case class BankAccountTest(routing: String, number: String)

  // For validation tests
  case class ValidatedUserWithEmail(name: String, age: Int, email: String)
}
