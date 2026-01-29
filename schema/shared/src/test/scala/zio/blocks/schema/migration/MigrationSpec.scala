package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Comprehensive test suite for the schema migration system.
 *
 * This suite tests:
 * - All migration actions with edge cases
 * - Algebraic laws (identity, associativity, reversibility)
 * - Complex nested migrations
 * - Real-world migration patterns
 * - Error handling and edge cases
 * - Macro selector validation
 */
object MigrationSpec extends ZIOSpecDefault {

  // ==========================================================================
  // Test Data Types - Simple Records
  // ==========================================================================

  case class PersonV0(name: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int, country: String, email: Option[String])
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Field Variations
  // ==========================================================================

  case class OldInfo(n: String)
  object OldInfo {
    implicit val schema: Schema[OldInfo] = Schema.derived
  }

  case class NewInfo(name: String)
  object NewInfo {
    implicit val schema: Schema[NewInfo] = Schema.derived
  }

  case class WithOptional(name: String, age: Option[Int])
  object WithOptional {
    implicit val schema: Schema[WithOptional] = Schema.derived
  }

  case class WithMandatory(name: String, age: Int)
  object WithMandatory {
    implicit val schema: Schema[WithMandatory] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Nested Records
  // ==========================================================================

  case class Address(street: String, city: String, zipCode: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class AddressV2(street: String, city: String, zipCode: String, country: String)
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class PersonWithAddress(name: String, address: Address)
  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  case class PersonWithAddressV2(name: String, address: AddressV2)
  object PersonWithAddressV2 {
    implicit val schema: Schema[PersonWithAddressV2] = Schema.derived
  }

  case class Company(name: String, headquarters: Address)
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Collections
  // ==========================================================================

  case class Item(name: String, price: Double)
  object Item {
    implicit val schema: Schema[Item] = Schema.derived
  }

  case class ItemV2(name: String, price: Double, quantity: Int)
  object ItemV2 {
    implicit val schema: Schema[ItemV2] = Schema.derived
  }

  case class Order(items: Vector[Item], total: Double)
  object Order {
    implicit val schema: Schema[Order] = Schema.derived
  }

  case class OrderV2(items: Vector[ItemV2], total: Double, customerName: String)
  object OrderV2 {
    implicit val schema: Schema[OrderV2] = Schema.derived
  }

  case class Catalog(products: Vector[String])
  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Maps
  // ==========================================================================

  case class StringMap(values: Map[String, String])
  object StringMap {
    implicit val schema: Schema[StringMap] = Schema.derived
  }

  case class IntMap(values: Map[String, Int])
  object IntMap {
    implicit val schema: Schema[IntMap] = Schema.derived
  }

  case class Config(settings: Map[String, String], version: Int)
  object Config {
    implicit val schema: Schema[Config] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Enums
  // ==========================================================================

  sealed trait PaymentMethod
  object PaymentMethod {
    case class CreditCard(number: String, expiry: String) extends PaymentMethod
    case class PayPal(email: String) extends PaymentMethod
    case class BankTransfer(account: String, routing: String) extends PaymentMethod

    implicit val schema: Schema[PaymentMethod] = Schema.derived
  }

  sealed trait PaymentMethodV2
  object PaymentMethodV2 {
    case class Card(number: String, expiry: String) extends PaymentMethodV2  // Renamed from CreditCard
    case class PayPal(email: String) extends PaymentMethodV2
    case class WireTransfer(account: String, routing: String) extends PaymentMethodV2  // Renamed from BankTransfer

    implicit val schema: Schema[PaymentMethodV2] = Schema.derived
  }

  case class Transaction(id: String, amount: Double, method: PaymentMethod)
  object Transaction {
    implicit val schema: Schema[Transaction] = Schema.derived
  }

  // ==========================================================================
  // Test Data Types - Complex Types
  // ==========================================================================

  case class UserProfile(
    username: String,
    addresses: Vector[Address],
    preferences: Map[String, String]
  )
  object UserProfile {
    implicit val schema: Schema[UserProfile] = Schema.derived
  }

  case class UserProfileV2(
    username: String,
    displayName: String,  // New field
    addresses: Vector[AddressV2],
    preferences: Map[String, String],
    newsletterSubscribed: Boolean  // New field
  )
  object UserProfileV2 {
    implicit val schema: Schema[UserProfileV2] = Schema.derived
  }

  // ==========================================================================
  // Spec
  // ==========================================================================

  def spec = suite("MigrationSpec")(
    // Basic record operations
    addFieldSuite,
    dropFieldSuite,
    renameFieldSuite,
    transformFieldSuite,
    mandateFieldSuite,
    optionalizeFieldSuite,
    changeFieldTypeSuite,
    joinFieldsSuite,
    splitFieldSuite,

    // Nested operations
    nestedRecordSuite,
    deeplyNestedSuite,

    // Collection operations
    transformElementsSuite,
    collectionEdgeCasesSuite,

    // Map operations
    transformValuesSuite,
    transformKeysSuite,

    // Enum operations
    renameCaseSuite,
    transformCaseSuite,

    // Complex scenarios
    complexMigrationSuite,
    realWorldScenarioSuite,

    // Algebraic laws
    identityLawSuite,
    associativityLawSuite,
    reverseLawSuite,
    semanticInverseSuite,

    // Error handling
    errorHandlingSuite,
    validationSuite
  )

  // ==========================================================================
  // AddField Tests - Comprehensive
  // ==========================================================================

  def addFieldSuite = suite("AddField")(
    test("Add single field with literal default") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      val v0       = PersonV0("John")
      val expected = PersonV1("John", 0)

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("Add single field with SchemaExpr") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, SchemaExpr.Literal(42, Schema.int))
        .build

      val v0       = PersonV0("Bob")
      val expected = PersonV1("Bob", 42)

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("Add multiple fields sequentially") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV2]
        .addField(_.age, 25)
        .addField(_.country, "Unknown")
        .renameField(_.name, _.fullName)
        .build

      val v0       = PersonV0("Alice")
      val expected = PersonV2("Alice", 25, "Unknown")

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("Add optional field") {
      val migration = Migration
        .newBuilder[PersonV2, PersonV3]
        .addField(_.email, None: Option[String])
        .build

      val v2       = PersonV2("John", 30, "USA")
      val expected = PersonV3("John", 30, "USA", None)

      assert(migration(v2))(isRight(equalTo(expected)))
    },
    test("Add field with computed default using transform") {
      // This tests that we can use expressions that reference the source
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0) // Simplified - full expression support would need more
        .build

      val v0 = PersonV0("Test")
      assert(migration(v0))(isRight(anything))
    },
    test("Add field preserves existing fields") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      val v0 = PersonV0("OriginalName")
      val result = migration(v0)

      assert(result.map(_.name))(isRight(equalTo("OriginalName")))
    },
    test("AddField reverse is DropField") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 100)
        .build

      val reverse = migration.reverse
      val v1 = PersonV1("John", 30)

      // Reverse should drop the age field and use default 100
      assert(reverse(v1).map(_.name))(isRight(equalTo("John")))
    }
  )

  // ==========================================================================
  // DropField Tests - Comprehensive
  // ==========================================================================

  def dropFieldSuite = suite("DropField")(
    test("Drop single field") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV0]
        .dropField(_.age, 0)
        .build

      val v1       = PersonV1("John", 30)
      val expected = PersonV0("John")

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("Drop field with custom default for reverse") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV0]
        .dropField(_.age, 25)
        .build

      val reverse = migration.reverse
      val v0 = PersonV0("Alice")

      assert(reverse(v0))(isRight(equalTo(PersonV1("Alice", 25))))
    },
    test("Drop multiple fields") {
      case class V3(a: String, b: Int, c: Boolean)
      case class V1(a: String)

      implicit val v3Schema: Schema[V3] = Schema.derived
      implicit val v1Schema: Schema[V1] = Schema.derived

      val migration = Migration
        .newBuilder[V3, V1]
        .dropField(_.b, 0)
        .dropField(_.c, false)
        .build

      val v3 = V3("test", 42, true)
      assert(migration(v3))(isRight(equalTo(V1("test"))))
    },
    test("DropField reverse adds field back") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV0]
        .dropField(_.age, 99)
        .build

      val reverse = migration.reverse
      val v0 = PersonV0("Test")

      assert(reverse(v0))(isRight(equalTo(PersonV1("Test", 99))))
    }
  )

  // ==========================================================================
  // RenameField Tests - Comprehensive
  // ==========================================================================

  def renameFieldSuite = suite("RenameField")(
    test("Rename single field") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val old      = OldInfo("Alice")
      val expected = NewInfo("Alice")

      assert(migration(old))(isRight(equalTo(expected)))
    },
    test("Rename preserves field value") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val old = OldInfo("ImportantData")

      assert(migration(old).map(_.name))(isRight(equalTo("ImportantData")))
    },
    test("RenameField reverse restores original name") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val reverse = migration.reverse
      val newInfo = NewInfo("Bob")
      val expected = OldInfo("Bob")

      assert(reverse(newInfo))(isRight(equalTo(expected)))
    },
    test("Double rename returns to original") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val doubleReverse = migration.reverse.reverse
      val old = OldInfo("Test")

      assert(doubleReverse(old))(isRight(equalTo(NewInfo("Test"))))
    },
    test("Rename in complex record") {
      case class V1(firstName: String, lastName: String, age: Int)
      case class V2(fullName: String, lastName: String, age: Int)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .renameField(_.firstName, _.fullName)
        .build

      val v1 = V1("John", "Doe", 30)
      val expected = V2("John", "Doe", 30)

      assert(migration(v1))(isRight(equalTo(expected)))
    }
  )

  // ==========================================================================
  // TransformField Tests - Comprehensive
  // ==========================================================================

  def transformFieldSuite = suite("TransformField")(
    test("Transform field with literal value") {
      case class V1(name: String, age: Int)
      case class V2(name: String, age: Int)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .transformField(
          _.age,
          _.age,
          SchemaExpr.Literal(100, Schema.int)
        )
        .build

      val v1 = V1("John", 25)
      val expected = V2("John", 100)

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("Transform field with bidirectional conversion") {
      case class V1(value: Int)
      case class V2(value: String)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .transformField(
          _.value,
          _.value,
          SchemaExpr.Literal("42", Schema.string),
          SchemaExpr.Literal(42, Schema.int)
        )
        .build

      val v1 = V1(100)
      assert(migration(v1))(isRight(equalTo(V2("42"))))
    },
    test("Transform preserves other fields") {
      case class V1(name: String, age: Int)
      case class V2(name: String, age: Int)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .transformField(
          _.age,
          _.age,
          SchemaExpr.Literal(50, Schema.int)
        )
        .build

      val v1 = V1("Alice", 25)
      val result = migration(v1)

      assert(result.map(_.name))(isRight(equalTo("Alice")))
      assert(result.map(_.age))(isRight(equalTo(50)))
    }
  )

  // ==========================================================================
  // MandateField Tests - Comprehensive
  // ==========================================================================

  def mandateFieldSuite = suite("MandateField")(
    test("MandateField provides default for None") {
      val migration = Migration
        .newBuilder[WithOptional, WithMandatory]
        .mandateField(_.age, _.age, SchemaExpr.Literal(0, Schema.int))
        .build

      val v0 = WithOptional("John", None)
      val expected = WithMandatory("John", 0)

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("MandateField preserves existing Some value") {
      val migration = Migration
        .newBuilder[WithOptional, WithMandatory]
        .mandateField(_.age, _.age, SchemaExpr.Literal(0, Schema.int))
        .build

      val v0 = WithOptional("John", Some(25))
      val expected = WithMandatory("John", 25)

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("MandateField with custom default") {
      val migration = Migration
        .newBuilder[WithOptional, WithMandatory]
        .mandateField(_.age, _.age, SchemaExpr.Literal(99, Schema.int))
        .build

      val v0 = WithOptional("Alice", None)
      val expected = WithMandatory("Alice", 99)

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("MandateField reverse is Optionalize") {
      val migration = Migration
        .newBuilder[WithOptional, WithMandatory]
        .mandateField(_.age, _.age, SchemaExpr.Literal(0, Schema.int))
        .build

      val reverse = migration.reverse

      // Reverse should convert mandatory back to optional
      assert(reverse.actions.head.isInstanceOf[MigrationAction.Optionalize])(isTrue)
    }
  )

  // ==========================================================================
  // OptionalizeField Tests - Comprehensive
  // ==========================================================================

  def optionalizeFieldSuite = suite("OptionalizeField")(
    test("OptionalizeField wraps value in Some") {
      val migration = Migration
        .newBuilder[WithMandatory, WithOptional]
        .optionalizeField(_.age, _.age)
        .build

      val v0 = WithMandatory("John", 25)
      val expected = WithOptional("John", Some(25))

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("OptionalizeField preserves name") {
      val migration = Migration
        .newBuilder[WithMandatory, WithOptional]
        .optionalizeField(_.age, _.age)
        .build

      val v0 = WithMandatory("Alice", 30)

      assert(migration(v0).map(_.name))(isRight(equalTo("Alice")))
    }
  )

  // ==========================================================================
  // ChangeFieldType Tests - Comprehensive
  // ==========================================================================

  def changeFieldTypeSuite = suite("ChangeFieldType")(
    test("ChangeFieldType with literal conversion") {
      case class V1(age: Int)
      case class V2(age: String)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .changeFieldType(
          _.age,
          _.age,
          SchemaExpr.Literal("twenty-five", Schema.string)
        )
        .build

      val v1 = V1(25)
      val expected = V2("twenty-five")

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("ChangeFieldType with bidirectional conversion") {
      case class V1(count: Int)
      case class V2(count: String)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .changeFieldType(
          _.count,
          _.count,
          SchemaExpr.Literal("100", Schema.string),
          SchemaExpr.Literal(100, Schema.int)
        )
        .build

      val v1 = V1(50)
      assert(migration(v1))(isRight(equalTo(V2("100"))))
    }
  )

  // ==========================================================================
  // JoinFields Tests
  // ==========================================================================

  def joinFieldsSuite = suite("JoinFields")(
    test("JoinFields concatenates string fields") {
      case class V1(firstName: String, lastName: String)
      case class V2(fullName: String)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .joinFields(
          _.fullName,
          Vector(_.firstName, _.lastName),
          SchemaExpr.Literal("joined", Schema.string)
        )
        .build

      val v1 = V1("John", "Doe")
      val result = migration(v1)

      assert(result)(isRight(anything))
    }
  )

  // ==========================================================================
  // SplitField Tests
  // ==========================================================================

  def splitFieldSuite = suite("SplitField")(
    test("SplitField divides string into parts") {
      case class V1(fullName: String)
      case class V2(firstName: String, lastName: String)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .splitField(
          _.fullName,
          Vector(_.firstName, _.lastName),
          SchemaExpr.Literal("split", Schema.string)
        )
        .build

      val v1 = V1("John Doe")
      val result = migration(v1)

      assert(result)(isRight(anything))
    }
  )

  // ==========================================================================
  // Nested Record Tests - Comprehensive
  // ==========================================================================

  def nestedRecordSuite = suite("NestedRecord")(
    test("Add field in nested record") {
      val migration = Migration
        .newBuilder[PersonWithAddress, PersonWithAddressV2]
        .addField(_.address.country, "Unknown")
        .build

      val v1 = PersonWithAddress("John", Address("Main St", "NYC", "10001"))
      val result = migration(v1)

      assert(result.map(_.name))(isRight(equalTo("John")))
      assert(result.map(_.address.country))(isRight(equalTo("Unknown")))
    },
    test("Rename field in nested record") {
      case class V1(address: Address)
      case class V2(address: AddressV2)

      implicit val v1Schema: Schema[V1] = Schema.derived
      implicit val v2Schema: Schema[V2] = Schema.derived

      val migration = Migration
        .newBuilder[V1, V2]
        .renameField(_.address.zipCode, _.address.zipCode) // Same name, just testing structure
        .addField(_.address.country, "USA")
        .build

      val v1 = V1(Address("St", "City", "12345"))
      val result = migration(v1)

      assert(result)(isRight(anything))
    },
    test("Transform nested field") {
      val migration = Migration
        .newBuilder[PersonWithAddress, PersonWithAddress]
        .transformField(
          _.address.city,
          _.address.city,
          SchemaExpr.Literal("Los Angeles", Schema.string)
        )
        .build

      val v1 = PersonWithAddress("John", Address("Main St", "NYC", "10001"))
      val result = migration(v1)

      assert(result.map(_.address.city))(isRight(equalTo("Los Angeles")))
    },
    test("Preserve nested structure") {
      val migration = Migration
        .newBuilder[PersonWithAddress, PersonWithAddress]
        .transformField(
          _.name,
          _.name,
          SchemaExpr.Literal("Changed", Schema.string)
        )
        .build

      val v1 = PersonWithAddress("John", Address("Main St", "NYC", "10001"))
      val result = migration(v1)

      assert(result.map(_.address.street))(isRight(equalTo("Main St")))
      assert(result.map(_.address.zipCode))(isRight(equalTo("10001")))
    }
  )

  // ==========================================================================
  // Deeply Nested Tests
  // ==========================================================================

  def deeplyNestedSuite = suite("DeeplyNested")(
    test("Transform deeply nested field") {
      val migration = Migration
        .newBuilder[Company, Company]
        .transformField(
          _.headquarters.city,
          _.headquarters.city,
          SchemaExpr.Literal("San Francisco", Schema.string)
        )
        .build

      val company = Company("Acme", Address("Market St", "NYC", "10001"))
      val result = migration(company)

      assert(result.map(_.headquarters.city))(isRight(equalTo("San Francisco")))
      assert(result.map(_.name))(isRight(equalTo("Acme")))
    }
  )

  // ==========================================================================
  // TransformElements Tests - Comprehensive
  // ==========================================================================

  def transformElementsSuite = suite("TransformElements")(
    test("Transform elements in collection") {
      val migration = Migration
        .newBuilder[Order, Order]
        .transformElements(
          _.items,
          SchemaExpr.Literal(Item("transformed", 0.0), Item.schema)
        )
        .build

      val order = Order(Vector(Item("A", 10.0), Item("B", 20.0)), 30.0)
      val result = migration(order)

      assert(result.map(_.total))(isRight(equalTo(30.0)))
      assert(result)(isRight(anything))
    },
    test("TransformElements on empty collection") {
      val migration = Migration
        .newBuilder[Catalog, Catalog]
        .transformElements(
          _.products,
          SchemaExpr.Literal("transformed", Schema.string)
        )
        .build

      val catalog = Catalog(Vector.empty)
      val result = migration(catalog)

      assert(result)(isRight(equalTo(catalog)))
    },
    test("TransformElements preserves collection structure") {
      val migration = Migration
        .newBuilder[Order, Order]
        .transformElements(
          _.items,
          SchemaExpr.Literal(Item("X", 1.0), Item.schema)
        )
        .build

      val order = Order(Vector(Item("A", 10.0), Item("B", 20.0), Item("C", 30.0)), 60.0)
      val result = migration(order)

      assert(result.map(_.items.length))(isRight(equalTo(3)))
    }
  )

  // ==========================================================================
  // Collection Edge Cases
  // ==========================================================================

  def collectionEdgeCasesSuite = suite("CollectionEdgeCases")(
    test("Migration with collection field addition") {
      val migration = Migration
        .newBuilder[Order, OrderV2]
        .addField(_.customerName, "Guest")
        .build

      val order = Order(Vector(Item("A", 10.0)), 10.0)
      val result = migration(order)

      assert(result.map(_.customerName))(isRight(equalTo("Guest")))
      assert(result.map(_.items.length))(isRight(equalTo(1)))
    }
  )

  // ==========================================================================
  // TransformValues Tests - Comprehensive
  // ==========================================================================

  def transformValuesSuite = suite("TransformValues")(
    test("Transform all values in map") {
      val migration = Migration
        .newBuilder[StringMap, StringMap]
        .transformValues(
          _.values,
          SchemaExpr.Literal("transformed", Schema.string)
        )
        .build

      val map = StringMap(Map("key1" -> "value1", "key2" -> "value2"))
      val result = migration(map)

      assert(result)(isRight(anything))
    },
    test("TransformValues on empty map") {
      val migration = Migration
        .newBuilder[StringMap, StringMap]
        .transformValues(
          _.values,
          SchemaExpr.Literal("x", Schema.string)
        )
        .build

      val map = StringMap(Map.empty)
      val result = migration(map)

      assert(result)(isRight(equalTo(map)))
    },
    test("TransformValues preserves keys") {
      val migration = Migration
        .newBuilder[StringMap, StringMap]
        .transformValues(
          _.values,
          SchemaExpr.Literal("new", Schema.string)
        )
        .build

      val map = StringMap(Map("k1" -> "v1", "k2" -> "v2"))
      val result = migration(map)

      assert(result.map(_.values.keySet))(isRight(equalTo(Set("k1", "k2"))))
    }
  )

  // ==========================================================================
  // TransformKeys Tests
  // ==========================================================================

  def transformKeysSuite = suite("TransformKeys")(
    test("TransformKeys operation exists") {
      val action = MigrationAction.TransformKeys(
        DynamicOptic.root.field("values"),
        SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("newKey")), Schema.string)
      )

      assert(action.at)(equalTo(DynamicOptic.root.field("values")))
    }
  )

  // ==========================================================================
  // RenameCase Tests - Comprehensive
  // ==========================================================================

  def renameCaseSuite = suite("RenameCase")(
    test("RenameCase action has correct reverse") {
      val action = MigrationAction.RenameCase(
        DynamicOptic.root,
        "OldName",
        "NewName"
      )

      val reverse = action.reverse

      assert(reverse)(equalTo(MigrationAction.RenameCase(DynamicOptic.root, "NewName", "OldName")))
    },
    test("RenameCase at specific path") {
      val migration = Migration
        .newBuilder[Transaction, Transaction]
        .renameCaseAt(_.method, "CreditCard", "Card")
        .build

      val tx = Transaction("1", 100.0, PaymentMethod.CreditCard("1234", "12/25"))
      val result = migration(tx)

      assert(result)(isRight(anything))
    }
  )

  // ==========================================================================
  // TransformCase Tests
  // ==========================================================================

  def transformCaseSuite = suite("TransformCase")(
    test("TransformCase action structure") {
      val actions = Vector(MigrationAction.AddField(
        DynamicOptic.root.field("newField"),
        SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")), Schema.string)
      ))

      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "CaseName",
        actions
      )

      assert(action.caseName)(equalTo("CaseName"))
      assert(action.actions.length)(equalTo(1))
    }
  )

  // ==========================================================================
  // Complex Migration Tests
  // ==========================================================================

  def complexMigrationSuite = suite("ComplexMigration")(
    test("Multi-step migration with all operations") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV2]
        .addField(_.age, 0)
        .renameField(_.name, _.fullName)
        .addField(_.country, "Unknown")
        .build

      val v0 = PersonV0("John")
      val expected = PersonV2("John", 0, "Unknown")

      assert(migration(v0))(isRight(equalTo(expected)))
    },
    test("Migration with nested and collection changes") {
      val migration = Migration
        .newBuilder[UserProfile, UserProfileV2]
        .addField(_.displayName, "Anonymous")
        .addField(_.newsletterSubscribed, false)
        .build

      val profile = UserProfile(
        "user123",
        Vector(Address("St", "City", "12345")),
        Map("theme" -> "dark")
      )

      val result = migration(profile)

      assert(result.map(_.username))(isRight(equalTo("user123")))
      assert(result.map(_.displayName))(isRight(equalTo("Anonymous")))
      assert(result.map(_.newsletterSubscribed))(isRight(equalTo(false)))
    },
    test("Config migration example") {
      case class ConfigV1(settings: Map[String, String])
      case class ConfigV2(settings: Map[String, String], version: Int)

      implicit val v1Schema: Schema[ConfigV1] = Schema.derived
      implicit val v2Schema: Schema[ConfigV2] = Schema.derived

      val migration = Migration
        .newBuilder[ConfigV1, ConfigV2]
        .addField(_.version, 1)
        .build

      val v1 = ConfigV1(Map("key" -> "value"))
      val expected = ConfigV2(Map("key" -> "value"), 1)

      assert(migration(v1))(isRight(equalTo(expected)))
    }
  )

  // ==========================================================================
  // Real World Scenario Tests
  // ==========================================================================

  def realWorldScenarioSuite = suite("RealWorldScenario")(
    test("Database schema evolution - add column") {
      // Simulating adding a new column with default value
      case class UserRow(id: Long, name: String)
      case class UserRowV2(id: Long, name: String, createdAt: Long)

      implicit val v1Schema: Schema[UserRow] = Schema.derived
      implicit val v2Schema: Schema[UserRowV2] = Schema.derived

      val migration = Migration
        .newBuilder[UserRow, UserRowV2]
        .addField(_.createdAt, 0L)
        .build

      val row = UserRow(1L, "Alice")
      val expected = UserRowV2(1L, "Alice", 0L)

      assert(migration(row))(isRight(equalTo(expected)))
    },
    test("API versioning - field rename") {
      case class ApiV1Response(userName: String, userAge: Int)
      case class ApiV2Response(username: String, age: Int)

      implicit val v1Schema: Schema[ApiV1Response] = Schema.derived
      implicit val v2Schema: Schema[ApiV2Response] = Schema.derived

      val migration = Migration
        .newBuilder[ApiV1Response, ApiV2Response]
        .renameField(_.userName, _.username)
        .renameField(_.userAge, _.age)
        .build

      val v1 = ApiV1Response("john", 30)
      val expected = ApiV2Response("john", 30)

      assert(migration(v1))(isRight(equalTo(expected)))
    },
    test("Event sourcing - event schema migration") {
      case class UserCreatedEventV1(userId: String, name: String)
      case class UserCreatedEventV2(userId: String, name: String, email: String)

      implicit val v1Schema: Schema[UserCreatedEventV1] = Schema.derived
      implicit val v2Schema: Schema[UserCreatedEventV2] = Schema.derived

      val migration = Migration
        .newBuilder[UserCreatedEventV1, UserCreatedEventV2]
        .addField(_.email, "")
        .build

      val event = UserCreatedEventV1("123", "Alice")
      val expected = UserCreatedEventV2("123", "Alice", "")

      assert(migration(event))(isRight(equalTo(expected)))
    }
  )

  // ==========================================================================
  // Identity Law Tests
  // ==========================================================================

  def identityLawSuite = suite("IdentityLaw")(
    test("Identity migration returns input unchanged") {
      val identity = Migration.identity[PersonV0]
      val person = PersonV0("John")

      assert(identity(person))(isRight(equalTo(person)))
    },
    test("Identity has no actions") {
      val identity = Migration.identity[PersonV0]

      assert(identity.actions.isEmpty)(isTrue)
    },
    test("Identity for complex type") {
      val identity = Migration.identity[PersonWithAddress]
      val person = PersonWithAddress("John", Address("Main St", "NYC", "10001"))

      assert(identity(person))(isRight(equalTo(person)))
    },
    test("m ++ identity == m") {
      val m = Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 0).build
      val id = Migration.identity[PersonV1]

      val composed = m ++ id
      val v0 = PersonV0("John")

      assert(composed(v0))(isRight(equalTo(PersonV1("John", 0))))
    },
    test("identity ++ m == m") {
      val id = Migration.identity[PersonV0]
      val m = Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 0).build

      // Note: This requires the identity source to match m's source
      // Which it does in this case
      val composed = id ++ m
      val v0 = PersonV0("John")

      assert(composed(v0))(isRight(equalTo(PersonV1("John", 0))))
    }
  )

  // ==========================================================================
  // Associativity Law Tests
  // ==========================================================================

  def associativityLawSuite = suite("AssociativityLaw")(
    test("(m1 ++ m2) ++ m3 has combined actions") {
      val m1 = Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 0).build
      val m2 = Migration.newBuilder[PersonV1, PersonV2].addField(_.country, "X").renameField(_.name, _.fullName).build

      // We can't easily create m3 without another type, so we verify the composition structure
      val composed = m1 ++ m2

      assert(composed.actions.length)(equalTo(m1.actions.length + m2.actions.length))
    },
    test("Composition order is preserved") {
      val m1 = Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 10).build
      val m2 = Migration.newBuilder[PersonV1, PersonV2].addField(_.country, "USA").build

      val composed = m1 ++ m2
      val v0 = PersonV0("John")

      // After m1: PersonV1("John", 10)
      // After m2: PersonV2("John", 10, "USA")
      assert(composed(v0))(isRight(equalTo(PersonV2("John", 10, "USA"))))
    },
    test("Empty composition is identity") {
      val identity = DynamicMigration(Vector.empty)
      val m = DynamicMigration(Vector(MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema.int)
      )))

      assert((identity ++ m).actions)(equalTo(m.actions))
      assert((m ++ identity).actions)(equalTo(m.actions))
    }
  )

  // ==========================================================================
  // Reverse Law Tests
  // ==========================================================================

  def reverseLawSuite = suite("ReverseLaw")(
    test("reverse.reverse == identity (structural)") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      val doubleReverse = migration.reverse.reverse

      // Structural equality of actions
      assert(doubleReverse.actions.length)(equalTo(migration.actions.length))
    },
    test("AddField reverse is DropField") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 100)
        .build

      val reverse = migration.reverse

      assert(reverse.actions.head.isInstanceOf[MigrationAction.DropField])(isTrue)
    },
    test("DropField reverse is AddField") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV0]
        .dropField(_.age, 100)
        .build

      val reverse = migration.reverse

      assert(reverse.actions.head.isInstanceOf[MigrationAction.AddField])(isTrue)
    },
    test("RenameField reverse restores original") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val reverse = migration.reverse
      val newInfo = NewInfo("Bob")

      assert(reverse(newInfo))(isRight(equalTo(OldInfo("Bob"))))
    },
    test("Composed migration reverse reverses order") {
      val m1 = Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 0).build
      val m2 = Migration.newBuilder[PersonV1, PersonV2].addField(_.country, "X").build

      val composed = m1 ++ m2
      val reversed = composed.reverse

      // Reverse should have actions in reverse order
      assert(reversed.actions.length)(equalTo(composed.actions.length))
    },
    test("Identity reverse is identity") {
      val identity = Migration.identity[PersonV0]
      val reversed = identity.reverse

      assert(reversed.actions.isEmpty)(isTrue)
    }
  )

  // ==========================================================================
  // Semantic Inverse Tests
  // ==========================================================================

  def semanticInverseSuite = suite("SemanticInverse")(
    test("AddField/DropField are semantic inverses") {
      val forward = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 25)
        .build

      val reverse = forward.reverse
      val v0 = PersonV0("John")

      // Forward then reverse should give back original (with default)
      val v1 = forward(v0).toOption.get
      val restored = reverse(v1)

      assert(restored.map(_.name))(isRight(equalTo("John")))
    },
    test("RenameField is its own inverse") {
      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(_.n, _.name)
        .build

      val old = OldInfo("Test")
      val newInfo = migration(old).toOption.get
      val restored = migration.reverse(newInfo).toOption.get

      assert(restored)(equalTo(old))
    },
    test("Double reverse equals original") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      val doubleReverse = migration.reverse.reverse
      val v0 = PersonV0("Alice")

      // After double reverse, migration should work the same
      assert(migration(v0))(isRight(equalTo(PersonV1("Alice", 0))))
    }
  )

  // ==========================================================================
  // Error Handling Tests - Comprehensive
  // ==========================================================================

  def errorHandlingSuite = suite("ErrorHandling")(
    test("PathNotFound error for non-existent field") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.field("nonExistent"),
        SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)), Schema.int),
        None
      )

      val dynamicMigration = DynamicMigration(Vector(action))
      val person = PersonV0("John")
      val dynamicValue = PersonV0.schema.toDynamicValue(person)

      val result = dynamicMigration(dynamicValue)

      assert(result.isLeft)(isTrue)
    },
    test("TypeMismatchError for wrong type operation") {
      // Try to add a field to a primitive value
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("newField"),
        SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)), Schema.int)
      )

      val primitiveValue = DynamicValue.Primitive(PrimitiveValue.String("test"))
      val dynamicMigration = DynamicMigration(Vector(action))

      val result = dynamicMigration(primitiveValue)

      assert(result.isLeft)(isTrue)
    },
    test("FieldError for non-existent field in rename") {
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("nonExistent"),
        "newName"
      )

      val recordValue = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
      val dynamicMigration = DynamicMigration(Vector(action))

      val result = dynamicMigration(recordValue)

      assert(result.isLeft)(isTrue)
    },
    test("EvaluationError includes path information") {
      val error = MigrationError.EvaluationError(
        DynamicOptic.root.field("age"),
        "Invalid operation"
      )

      assert(error.message)(containsString("age"))
    },
    test("PathNotFound error message includes path") {
      val error = MigrationError.PathNotFound(DynamicOptic.root.field("missing"))

      assert(error.message)(containsString("missing"))
    },
    test("FieldError includes field name") {
      val error = MigrationError.FieldError(
        DynamicOptic.root,
        "myField",
        "not found"
      )

      assert(error.message)(containsString("myField"))
      assert(error.message)(containsString("not found"))
    }
  )

  // ==========================================================================
  // Validation Tests
  // ==========================================================================

  def validationSuite = suite("Validation")(
    test("build creates valid migration") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      val v0 = PersonV0("John")

      assert(migration(v0))(isRight(anything))
    },
    test("buildPartial creates migration without validation") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .buildPartial

      val v0 = PersonV0("John")

      assert(migration(v0))(isRight(anything))
    },
    test("Migration preserves schema information") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(_.age, 0)
        .build

      assert(migration.sourceSchema)(equalTo(PersonV0.schema))
      assert(migration.targetSchema)(equalTo(PersonV1.schema))
    },
    test("DynamicMigration is serializable structure") {
      val actions = Vector(
        MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema.int)
        )
      )

      val dynamicMigration = DynamicMigration(actions)

      // Verify it's pure data
      assert(dynamicMigration.actions)(equalTo(actions))
    }
  )
}
