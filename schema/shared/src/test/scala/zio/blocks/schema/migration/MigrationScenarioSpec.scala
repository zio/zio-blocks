package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for real-world migration scenarios.
 *
 * Covers:
 *   - Common schema evolution patterns
 *   - Multi-version migrations
 *   - Complex domain model migrations
 *   - Production-like scenarios
 */
object MigrationScenarioSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  // User profile evolution
  case class UserV1(name: String, email: String)
  case class UserV2(name: String, email: String, verified: Boolean)
  case class UserV3(fullName: String, email: String, verified: Boolean, createdAt: Long)

  // Order evolution
  case class OrderItemV1(productId: String, quantity: Int)
  case class OrderV1(id: String, items: List[OrderItemV1])

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicLong(l: Long): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Long(l))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def dynamicDouble(d: Double): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Double(d))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicSome(value: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(("value", value)))

  def dynamicNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record())

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationScenarioSpec")(
    suite("User profile evolution")(
      test("V1 -> V2: add verified flag with default") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "verified", Resolved.Literal.boolean(false))
        )
        val v1 = dynamicRecord(
          "name"  -> dynamicString("Alice"),
          "email" -> dynamicString("alice@example.com")
        )
        val result = migration.apply(v1)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"     -> dynamicString("Alice"),
              "email"    -> dynamicString("alice@example.com"),
              "verified" -> dynamicBool(false)
            )
          )
        )
      },
      test("V2 -> V3: rename name to fullName, add createdAt") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "createdAt", Resolved.Literal.long(0L))
          )
        )
        val v2 = dynamicRecord(
          "name"     -> dynamicString("Alice"),
          "email"    -> dynamicString("alice@example.com"),
          "verified" -> dynamicBool(true)
        )
        val result = migration.apply(v2)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName"  -> dynamicString("Alice"),
              "email"     -> dynamicString("alice@example.com"),
              "verified"  -> dynamicBool(true),
              "createdAt" -> dynamicLong(0L)
            )
          )
        )
      },
      test("V1 -> V3: composed multi-version migration") {
        val v1ToV2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "verified", Resolved.Literal.boolean(false))
        )
        val v2ToV3 = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "createdAt", Resolved.Literal.long(0L))
          )
        )
        val v1ToV3 = v1ToV2 ++ v2ToV3

        val v1 = dynamicRecord(
          "name"  -> dynamicString("Bob"),
          "email" -> dynamicString("bob@example.com")
        )
        val result = v1ToV3.apply(v1)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName"  -> dynamicString("Bob"),
              "email"     -> dynamicString("bob@example.com"),
              "verified"  -> dynamicBool(false),
              "createdAt" -> dynamicLong(0L)
            )
          )
        )
      }
    ),
    suite("E-commerce order scenarios")(
      test("add discount field to order item") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "discount", Resolved.Literal.double(0.0))
        )
        val item = dynamicRecord(
          "productId" -> dynamicString("SKU-001"),
          "quantity"  -> dynamicInt(2)
        )
        val result = migration.apply(item)
        assertTrue(
          result == Right(
            dynamicRecord(
              "productId" -> dynamicString("SKU-001"),
              "quantity"  -> dynamicInt(2),
              "discount"  -> dynamicDouble(0.0)
            )
          )
        )
      },
      test("migrate items array within order") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val order = dynamicRecord(
          "id"    -> dynamicString("ORD-001"),
          "items" -> dynamicSequence(
            dynamicRecord("productId" -> dynamicString("SKU-001"), "quantity" -> dynamicInt(2)),
            dynamicRecord("productId" -> dynamicString("SKU-002"), "quantity" -> dynamicInt(1))
          )
        )
        val result = migration.apply(order)
        assertTrue(result == Right(order))
      },
      test("add shipping address to order") {
        val defaultAddress = dynamicRecord(
          "street"  -> dynamicString(""),
          "city"    -> dynamicString(""),
          "country" -> dynamicString("US")
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "shippingAddress",
            Resolved.Literal(defaultAddress)
          )
        )
        val order = dynamicRecord(
          "id"    -> dynamicString("ORD-001"),
          "items" -> dynamicSequence()
        )
        val result = migration.apply(order)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "shippingAddress"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Payment status evolution")(
      test("rename payment status cases") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Pending", "AwaitingPayment"),
            MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Complete", "PaymentReceived")
          )
        )
        val order = dynamicRecord(
          "id"     -> dynamicString("ORD-001"),
          "status" -> dynamicVariant("Pending", DynamicValue.Primitive(PrimitiveValue.Unit))
        )
        val result = migration.apply(order)
        assertTrue(
          result == Right(
            dynamicRecord(
              "id"     -> dynamicString("ORD-001"),
              "status" -> dynamicVariant("AwaitingPayment", DynamicValue.Primitive(PrimitiveValue.Unit))
            )
          )
        )
      },
      test("add metadata to success case") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("status"),
            "Success",
            Vector(
              MigrationAction.AddField(DynamicOptic.root, "processedAt", Resolved.Literal.long(0L))
            )
          )
        )
        val order = dynamicRecord(
          "id"     -> dynamicString("ORD-001"),
          "status" -> dynamicVariant("Success", dynamicRecord("txId" -> dynamicString("TX-123")))
        )
        val result = migration.apply(order)
        assertTrue(
          result == Right(
            dynamicRecord(
              "id"     -> dynamicString("ORD-001"),
              "status" -> dynamicVariant(
                "Success",
                dynamicRecord(
                  "txId"        -> dynamicString("TX-123"),
                  "processedAt" -> dynamicLong(0L)
                )
              )
            )
          )
        )
      }
    ),
    suite("Configuration evolution")(
      test("add new config section") {
        val defaultLogging = dynamicRecord(
          "level"  -> dynamicString("INFO"),
          "format" -> dynamicString("json")
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "logging",
            Resolved.Literal(defaultLogging)
          )
        )
        val config = dynamicRecord(
          "database" -> dynamicRecord("host" -> dynamicString("localhost")),
          "server"   -> dynamicRecord("port" -> dynamicInt(8080))
        )
        val result = migration.apply(config)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "logging"))
            assertTrue(fields.exists(_._1 == "database"))
            assertTrue(fields.exists(_._1 == "server"))
          case _ => assertTrue(false)
        }
      },
      test("migrate nested config values") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("database"), "host", "hostname")
        )
        val config = dynamicRecord(
          "database" -> dynamicRecord(
            "host" -> dynamicString("localhost"),
            "port" -> dynamicInt(5432)
          )
        )
        val result = migration.apply(config)
        assertTrue(
          result == Right(
            dynamicRecord(
              "database" -> dynamicRecord(
                "hostname" -> dynamicString("localhost"),
                "port"     -> dynamicInt(5432)
              )
            )
          )
        )
      },
      test("convert config value types") {
        val migration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("server"),
            "port",
            Resolved.Convert("Int", "String", Resolved.Identity),
            Resolved.Convert("String", "Int", Resolved.Identity)
          )
        )
        val config = dynamicRecord(
          "server" -> dynamicRecord("port" -> dynamicInt(8080))
        )
        val result = migration.apply(config)
        assertTrue(
          result == Right(
            dynamicRecord(
              "server" -> dynamicRecord("port" -> dynamicString("8080"))
            )
          )
        )
      }
    ),
    suite("API response evolution")(
      test("wrap single result in response envelope") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "success", Resolved.Literal.boolean(true))
        )
        val response = dynamicRecord("data" -> dynamicInt(42))
        val result   = migration.apply(response)
        assertTrue(
          result == Right(
            dynamicRecord(
              "data"    -> dynamicInt(42),
              "success" -> dynamicBool(true)
            )
          )
        )
      },
      test("add pagination metadata") {
        val pagination = dynamicRecord(
          "page"       -> dynamicInt(1),
          "pageSize"   -> dynamicInt(20),
          "totalCount" -> dynamicInt(0)
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "pagination",
            Resolved.Literal(pagination)
          )
        )
        val response = dynamicRecord(
          "items" -> dynamicSequence()
        )
        val result = migration.apply(response)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "pagination"))
          case _ => assertTrue(false)
        }
      },
      test("migrate error response format") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "error", "message"),
            MigrationAction.AddField(DynamicOptic.root, "code", Resolved.Literal.int(500))
          )
        )
        val errorResponse = dynamicRecord(
          "error" -> dynamicString("Something went wrong")
        )
        val result = migration.apply(errorResponse)
        assertTrue(
          result == Right(
            dynamicRecord(
              "message" -> dynamicString("Something went wrong"),
              "code"    -> dynamicInt(500)
            )
          )
        )
      }
    ),
    suite("Event sourcing scenarios")(
      test("migrate event payload") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("payload"), "userId", "customerId"),
            MigrationAction.AddField(
              DynamicOptic.root.field("payload"),
              "version",
              Resolved.Literal.int(2)
            )
          )
        )
        val event = dynamicRecord(
          "type"      -> dynamicString("OrderCreated"),
          "timestamp" -> dynamicLong(1234567890L),
          "payload"   -> dynamicRecord(
            "orderId" -> dynamicString("ORD-001"),
            "userId"  -> dynamicString("USER-001")
          )
        )
        val result = migration.apply(event)
        assertTrue(
          result == Right(
            dynamicRecord(
              "type"      -> dynamicString("OrderCreated"),
              "timestamp" -> dynamicLong(1234567890L),
              "payload"   -> dynamicRecord(
                "orderId"    -> dynamicString("ORD-001"),
                "customerId" -> dynamicString("USER-001"),
                "version"    -> dynamicInt(2)
              )
            )
          )
        )
      },
      test("add event metadata") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "metadata",
            Resolved.Construct(
              Vector(
                "schemaVersion" -> Resolved.Literal.int(2),
                "source"        -> Resolved.Literal.string("migration")
              )
            )
          )
        )
        val event = dynamicRecord(
          "type" -> dynamicString("UserUpdated"),
          "data" -> dynamicRecord("name" -> dynamicString("Alice"))
        )
        val result = migration.apply(event)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "metadata"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Optionality changes")(
      test("make required field optional") {
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root, "middleName")
        )
        val person = dynamicRecord(
          "firstName"  -> dynamicString("John"),
          "middleName" -> dynamicString("Q"),
          "lastName"   -> dynamicString("Public")
        )
        val result = migration.apply(person)
        assertTrue(
          result == Right(
            dynamicRecord(
              "firstName"  -> dynamicString("John"),
              "middleName" -> dynamicSome(dynamicString("Q")),
              "lastName"   -> dynamicString("Public")
            )
          )
        )
      },
      test("make optional field required with default") {
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "phone", Resolved.Literal.string("N/A"))
        )
        val personWithPhone = dynamicRecord(
          "name"  -> dynamicString("Alice"),
          "phone" -> dynamicSome(dynamicString("555-1234"))
        )
        val personWithoutPhone = dynamicRecord(
          "name"  -> dynamicString("Bob"),
          "phone" -> dynamicNone
        )
        val result1 = migration.apply(personWithPhone)
        val result2 = migration.apply(personWithoutPhone)
        assertTrue(
          result1 == Right(
            dynamicRecord(
              "name"  -> dynamicString("Alice"),
              "phone" -> dynamicString("555-1234")
            )
          )
        )
        assertTrue(
          result2 == Right(
            dynamicRecord(
              "name"  -> dynamicString("Bob"),
              "phone" -> dynamicString("N/A")
            )
          )
        )
      }
    ),
    suite("Complex multi-step migrations")(
      test("complete schema overhaul") {
        val migration = DynamicMigration(
          Vector(
            // Rename fields
            MigrationAction.Rename(DynamicOptic.root, "firstName", "givenName"),
            MigrationAction.Rename(DynamicOptic.root, "lastName", "familyName"),
            // Add new fields
            MigrationAction.AddField(
              DynamicOptic.root,
              "displayName",
              Resolved.Concat(
                Vector(
                  Resolved.FieldAccess("givenName", Resolved.Identity),
                  Resolved.Literal.string(" "),
                  Resolved.FieldAccess("familyName", Resolved.Identity)
                ),
                ""
              )
            ),
            // Type conversion
            MigrationAction.ChangeType(
              DynamicOptic.root,
              "age",
              Resolved.Convert("Int", "String", Resolved.Identity),
              Resolved.Convert("String", "Int", Resolved.Identity)
            )
          )
        )
        val person = dynamicRecord(
          "firstName" -> dynamicString("Jane"),
          "lastName"  -> dynamicString("Doe"),
          "age"       -> dynamicInt(30)
        )
        val result = migration.apply(person)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "givenName"))
            assertTrue(fields.exists(_._1 == "familyName"))
            assertTrue(fields.exists(_._1 == "displayName"))
          case _ => assertTrue(false)
        }
      }
    )
  )
}
