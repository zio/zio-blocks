package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Integration tests for real-world migration scenarios.
 *
 * These tests demonstrate how the migration system can be used in
 * practical applications like:
 * - Database schema evolution
 * - API versioning
 * - Event sourcing
 * - Configuration migration
 */
object MigrationIntegrationSpec extends ZIOSpecDefault {

  // ==========================================================================
  // Database Schema Evolution Scenarios
  // ==========================================================================

  case class UserRowV1(id: Long, username: String, email: String)
  object UserRowV1 {
    implicit val schema: Schema[UserRowV1] = Schema.derived
  }

  case class UserRowV2(
    id: Long,
    username: String,
    email: String,
    createdAt: Long,     // New field
    isActive: Boolean    // New field
  )
  object UserRowV2 {
    implicit val schema: Schema[UserRowV2] = Schema.derived
  }

  case class UserRowV3(
    id: Long,
    username: String,
    email: String,
    createdAt: Long,
    isActive: Boolean,
    profile: Option[String]  // New optional field
  )
  object UserRowV3 {
    implicit val schema: Schema[UserRowV3] = Schema.derived
  }

  // ==========================================================================
  // API Versioning Scenarios
  // ==========================================================================

  case class ApiV1UserResponse(
    userId: String,
    userName: String,
    userEmail: String
  )
  object ApiV1UserResponse {
    implicit val schema: Schema[ApiV1UserResponse] = Schema.derived
  }

  case class ApiV2UserResponse(
    id: String,
    name: String,
    email: String,
    registeredAt: String
  )
  object ApiV2UserResponse {
    implicit val schema: Schema[ApiV2UserResponse] = Schema.derived
  }

  // ==========================================================================
  // Event Sourcing Scenarios
  // ==========================================================================

  sealed trait DomainEvent
  object DomainEvent {
    case class OrderCreatedV1(orderId: String, customerId: String, total: Double) extends DomainEvent
    case class OrderCreatedV2(
      orderId: String,
      customerId: String,
      total: Double,
      currency: String  // New field
    ) extends DomainEvent

    implicit val schema: Schema[DomainEvent] = Schema.derived
  }

  // ==========================================================================
  // Configuration Migration Scenarios
  // ==========================================================================

  case class AppConfigV1(
    databaseUrl: String,
    maxConnections: Int
  )
  object AppConfigV1 {
    implicit val schema: Schema[AppConfigV1] = Schema.derived
  }

  case class AppConfigV2(
    databaseUrl: String,
    maxConnections: Int,
    timeout: Int,           // New field
    retryPolicy: String     // New field
  )
  object AppConfigV2 {
    implicit val schema: Schema[AppConfigV2] = Schema.derived
  }

  // ==========================================================================
  // Complex Nested Scenarios
  // ==========================================================================

  case class Address(street: String, city: String, zip: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class AddressV2(
    street: String,
    city: String,
    zip: String,
    country: String  // New field
  )
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class CustomerV1(
    id: String,
    name: String,
    address: Address
  )
  object CustomerV1 {
    implicit val schema: Schema[CustomerV1] = Schema.derived
  }

  case class CustomerV2(
    id: String,
    firstName: String,  // Renamed from name
    lastName: String,   // New field
    address: AddressV2,
    email: Option[String]  // New optional field
  )
  object CustomerV2 {
    implicit val schema: Schema[CustomerV2] = Schema.derived
  }

  // ==========================================================================
  // Collection Scenarios
  // ==========================================================================

  case class ProductV1(sku: String, name: String, price: Double)
  object ProductV1 {
    implicit val schema: Schema[ProductV1] = Schema.derived
  }

  case class ProductV2(
    sku: String,
    name: String,
    price: Double,
    inStock: Boolean  // New field
  )
  object ProductV2 {
    implicit val schema: Schema[ProductV2] = Schema.derived
  }

  case class CatalogV1(products: Vector[ProductV1])
  object CatalogV1 {
    implicit val schema: Schema[CatalogV1] = Schema.derived
  }

  case class CatalogV2(
    products: Vector[ProductV2],
    lastUpdated: Long  // New field
  )
  object CatalogV2 {
    implicit val schema: Schema[CatalogV2] = Schema.derived
  }

  // ==========================================================================
  // Spec
  // ==========================================================================

  def spec = suite("MigrationIntegrationSpec")(
    databaseEvolutionSuite,
    apiVersioningSuite,
    eventSourcingSuite,
    configurationMigrationSuite,
    complexNestedSuite,
    collectionMigrationSuite,
    rollbackScenarioSuite,
    multiVersionChainSuite
  )

  // ==========================================================================
  // Database Evolution Tests
  // ==========================================================================

  def databaseEvolutionSuite = suite("DatabaseEvolution")(
    test("Add columns with defaults") {
      val migration = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      val v1 = UserRowV1(1L, "alice", "alice@example.com")
      val v2 = migration(v1).toOption.get

      assert(v2.id)(equalTo(1L))
      assert(v2.username)(equalTo("alice"))
      assert(v2.email)(equalTo("alice@example.com"))
      assert(v2.createdAt)(equalTo(0L))
      assert(v2.isActive)(isTrue)
    },
    test("Add optional column") {
      val migration = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val v2 = UserRowV2(1L, "bob", "bob@example.com", 123456L, true)
      val v3 = migration(v2).toOption.get

      assert(v3.profile)(isNone)
    },
    test("Reverse migration for rollback") {
      val migration = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      val rollback = migration.reverse
      val v2 = UserRowV2(1L, "charlie", "charlie@example.com", 999L, false)
      val v1 = rollback(v2).toOption.get

      assert(v1.id)(equalTo(1L))
      assert(v1.username)(equalTo("charlie"))
      assert(v1.email)(equalTo("charlie@example.com"))
    },
    test("Chain multiple schema versions") {
      val v1ToV2 = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      val v2ToV3 = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val v1ToV3 = v1ToV2 ++ v2ToV3
      val v1 = UserRowV1(1L, "dave", "dave@example.com")
      val v3 = v1ToV3(v1).toOption.get

      assert(v3.id)(equalTo(1L))
      assert(v3.profile)(isNone)
    }
  )

  // ==========================================================================
  // API Versioning Tests
  // ==========================================================================

  def apiVersioningSuite = suite("APIVersioning")(
    test("Rename fields for API consistency") {
      val migration = Migration
        .newBuilder[ApiV1UserResponse, ApiV2UserResponse]
        .renameField(_.userId, _.id)
        .renameField(_.userName, _.name)
        .renameField(_.userEmail, _.email)
        .addField(_.registeredAt, "")
        .build

      val v1 = ApiV1UserResponse("123", "John", "john@example.com")
      val v2 = migration(v1).toOption.get

      assert(v2.id)(equalTo("123"))
      assert(v2.name)(equalTo("John"))
      assert(v2.email)(equalTo("john@example.com"))
      assert(v2.registeredAt)(equalTo(""))
    },
    test("Backward compatibility via reverse") {
      val forward = Migration
        .newBuilder[ApiV1UserResponse, ApiV2UserResponse]
        .renameField(_.userId, _.id)
        .renameField(_.userName, _.name)
        .renameField(_.userEmail, _.email)
        .addField(_.registeredAt, "")
        .build

      val backward = forward.reverse
      val v2 = ApiV2UserResponse("456", "Jane", "jane@example.com", "2024-01-01")
      val v1 = backward(v2).toOption.get

      assert(v1.userId)(equalTo("456"))
      assert(v1.userName)(equalTo("Jane"))
      assert(v1.userEmail)(equalTo("jane@example.com"))
    }
  )

  // ==========================================================================
  // Event Sourcing Tests
  // ==========================================================================

  def eventSourcingSuite = suite("EventSourcing")(
    test("Add field to event") {
      val eventV1 = DomainEvent.OrderCreatedV1("order-1", "cust-1", 100.0)

      // For sealed traits, we'd need to handle each case
      // This is a simplified test showing the concept
      val migration = Migration
        .newBuilder[DomainEvent.OrderCreatedV1, DomainEvent.OrderCreatedV2]
        .addField(_.currency, "USD")
        .build

      val eventV2 = migration(eventV1).toOption.get

      assert(eventV2.orderId)(equalTo("order-1"))
      assert(eventV2.customerId)(equalTo("cust-1"))
      assert(eventV2.total)(equalTo(100.0))
      assert(eventV2.currency)(equalTo("USD"))
    }
  )

  // ==========================================================================
  // Configuration Migration Tests
  // ==========================================================================

  def configurationMigrationSuite = suite("ConfigurationMigration")(
    test("Add new configuration options") {
      val migration = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .addField(_.timeout, 30)
        .addField(_.retryPolicy, "exponential")
        .build

      val v1 = AppConfigV1("jdbc:postgresql://localhost/db", 10)
      val v2 = migration(v1).toOption.get

      assert(v2.databaseUrl)(equalTo("jdbc:postgresql://localhost/db"))
      assert(v2.maxConnections)(equalTo(10))
      assert(v2.timeout)(equalTo(30))
      assert(v2.retryPolicy)(equalTo("exponential"))
    },
    test("Configuration rollback") {
      val migration = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .addField(_.timeout, 30)
        .addField(_.retryPolicy, "exponential")
        .build

      val rollback = migration.reverse
      val v2 = AppConfigV2("url", 5, 60, "linear")
      val v1 = rollback(v2).toOption.get

      assert(v1.databaseUrl)(equalTo("url"))
      assert(v1.maxConnections)(equalTo(5))
    }
  )

  // ==========================================================================
  // Complex Nested Tests
  // ==========================================================================

  def complexNestedSuite = suite("ComplexNested")(
    test("Migrate nested address structure") {
      val migration = Migration
        .newBuilder[CustomerV1, CustomerV2]
        .renameField(_.name, _.firstName)
        .addField(_.lastName, "")
        .addField(_.address.country, "USA")
        .addField(_.email, None: Option[String])
        .build

      val v1 = CustomerV1("cust-1", "John", Address("123 Main", "NYC", "10001"))
      val v2 = migration(v1).toOption.get

      assert(v2.id)(equalTo("cust-1"))
      assert(v2.firstName)(equalTo("John"))
      assert(v2.lastName)(equalTo(""))
      assert(v2.address.country)(equalTo("USA"))
      assert(v2.email)(isNone)
    },
    test("Preserve nested structure during migration") {
      val migration = Migration
        .newBuilder[CustomerV1, CustomerV2]
        .addField(_.address.country, "USA")
        .addField(_.email, None: Option[String])
        .build

      val v1 = CustomerV1("cust-1", "Alice", Address("456 Oak", "LA", "90001"))
      val v2 = migration(v1).toOption.get

      assert(v2.address.street)(equalTo("456 Oak"))
      assert(v2.address.city)(equalTo("LA"))
      assert(v2.address.zip)(equalTo("90001"))
    }
  )

  // ==========================================================================
  // Collection Migration Tests
  // ==========================================================================

  def collectionMigrationSuite = suite("CollectionMigration")(
    test("Migrate catalog with products") {
      val migration = Migration
        .newBuilder[CatalogV1, CatalogV2]
        .addField(_.lastUpdated, 0L)
        .build

      val v1 = CatalogV1(Vector(
        ProductV1("SKU-1", "Widget", 9.99),
        ProductV1("SKU-2", "Gadget", 19.99)
      ))
      val v2 = migration(v1).toOption.get

      assert(v2.products.length)(equalTo(2))
      assert(v2.products.head.sku)(equalTo("SKU-1"))
      assert(v2.lastUpdated)(equalTo(0L))
    },
    test("Migrate empty catalog") {
      val migration = Migration
        .newBuilder[CatalogV1, CatalogV2]
        .addField(_.lastUpdated, 0L)
        .build

      val v1 = CatalogV1(Vector.empty)
      val v2 = migration(v1).toOption.get

      assert(v2.products.isEmpty)(isTrue)
      assert(v2.lastUpdated)(equalTo(0L))
    }
  )

  // ==========================================================================
  // Rollback Scenario Tests
  // ==========================================================================

  def rollbackScenarioSuite = suite("RollbackScenario")(
    test("Full rollback of multi-step migration") {
      val v1ToV2 = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      val v2ToV3 = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val forward = v1ToV2 ++ v2ToV3
      val rollback = forward.reverse

      val v3 = UserRowV3(1L, "test", "test@test.com", 123L, true, Some("bio"))
      val v1 = rollback(v3).toOption.get

      assert(v1.id)(equalTo(1L))
      assert(v1.username)(equalTo("test"))
      assert(v1.email)(equalTo("test@test.com"))
    },
    test("Partial rollback") {
      val migration = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      // Rollback just one step
      val partialRollback = migration.reverse
      val v2 = UserRowV2(1L, "user", "user@test.com", 999L, false)
      val v1 = partialRollback(v2).toOption.get

      assert(v1.username)(equalTo("user"))
    }
  )

  // ==========================================================================
  // Multi-Version Chain Tests
  // ==========================================================================

  def multiVersionChainSuite = suite("MultiVersionChain")(
    test("Chain 3 versions") {
      val v1ToV2 = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .build

      val v2ToV3 = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val fullMigration = v1ToV2 ++ v2ToV3

      val v1 = UserRowV1(1L, "chain", "chain@test.com")
      val v3 = fullMigration(v1).toOption.get

      assert(v3.id)(equalTo(1L))
      assert(v3.username)(equalTo("chain"))
      assert(v3.createdAt)(equalTo(0L))
      assert(v3.profile)(isNone)
    },
    test("Jump versions via composition") {
      // Instead of migrating V1 -> V2 -> V3, compose and go directly V1 -> V3
      val v1ToV2 = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .addField(_.isActive, true)
        .build

      val v2ToV3 = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val directV1ToV3 = v1ToV2 ++ v2ToV3

      val v1 = UserRowV1(1L, "jump", "jump@test.com")
      val v3 = directV1ToV3(v1).toOption.get

      assert(v3.username)(equalTo("jump"))
      assert(v3.createdAt)(equalTo(0L))
      assert(v3.isActive)(isTrue)
      assert(v3.profile)(isNone)
    },
    test("Multi-version rollback") {
      val v1ToV2 = Migration
        .newBuilder[UserRowV1, UserRowV2]
        .addField(_.createdAt, 0L)
        .build

      val v2ToV3 = Migration
        .newBuilder[UserRowV2, UserRowV3]
        .addField(_.profile, None: Option[String])
        .build

      val fullMigration = v1ToV2 ++ v2ToV3
      val fullRollback = fullMigration.reverse

      val v3 = UserRowV3(1L, "rollback", "rb@test.com", 123L, true, Some("data"))
      val v1 = fullRollback(v3).toOption.get

      assert(v1.username)(equalTo("rollback"))
      assert(v1.email)(equalTo("rb@test.com"))
    }
  )
}
