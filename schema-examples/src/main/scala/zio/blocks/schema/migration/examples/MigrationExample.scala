package zio.blocks.schema.migration.examples

import zio.blocks.schema._
import zio.blocks.schema.migration._

/**
 * Complete example demonstrating the Schema Migration System.
 * 
 * This example shows a realistic migration scenario where a User schema
 * evolves through multiple versions.
 */
object MigrationExample {

  // ============================================================================
  // Schema Version 1
  // ============================================================================

  case class AddressV1(
    street: String,
    city: String,
    zipCode: String
  )

  case class UserV1(
    name: String,
    age: Int,
    address: AddressV1
  )

  // ============================================================================
  // Schema Version 2
  // ============================================================================

  case class AddressV2(
    streetName: String,  // renamed from 'street'
    streetNumber: Int,   // new field
    city: String,
    postalCode: String   // renamed from 'zipCode'
  )

  case class UserV2(
    fullName: String,    // renamed from 'name'
    age: Int,
    address: AddressV2,
    email: String        // new field
  )

  // ============================================================================
  // Schema Version 3
  // ============================================================================

  sealed trait UserRole
  case object Admin extends UserRole
  case object Member extends UserRole
  case object Guest extends UserRole

  case class AddressV3(
    streetName: String,
    streetNumber: Int,
    city: String,
    postalCode: String,
    country: String      // new field
  )

  case class UserV3(
    fullName: String,
    age: Option[Int],    // now optional
    addresses: List[AddressV3],  // renamed and changed to List
    email: String,
    role: UserRole       // new field
  )

  // ============================================================================
  // Migrations
  // ============================================================================

  /**
   * Migration from UserV1 to UserV2:
   * - Rename 'name' to 'fullName'
   * - Add 'email' field with default
   * - Rename nested 'address.street' to 'address.streetName'
   * - Add 'address.streetNumber' with default 0
   * - Rename nested 'address.zipCode' to 'address.postalCode'
   */
  def migrationV1ToV2(
    implicit
    schemaV1: Schema[UserV1],
    schemaV2: Schema[UserV2]
  ): Migration[UserV1, UserV2] = Migration.builder[UserV1, UserV2]
    // Top-level changes
    .renameField("name", "fullName")
    .addField("email", SchemaExpr.Literal("", Schema[String]))
    // Nested address changes
    .renameField(DynamicOptic.root.field("address"), "street", "streetName")
    .addField(DynamicOptic.root.field("address"), "streetNumber", SchemaExpr.Literal(0, Schema[Int]))
    .renameField(DynamicOptic.root.field("address"), "zipCode", "postalCode")
    .build

  /**
   * Migration from UserV2 to UserV3:
   * - Make 'age' optional
   * - Rename 'address' to 'addresses' and wrap in List
   * - Add 'address.country' field
   * - Add 'role' field with default
   */
  def migrationV2ToV3(
    implicit
    schemaV2: Schema[UserV2],
    schemaV3: Schema[UserV3]
  ): Migration[UserV2, UserV3] = Migration.builder[UserV2, UserV3]
    // Make age optional
    .optionalizeField("age")
    // Add country to nested address (would need more infrastructure)
    .addField(DynamicOptic.root.field("address"), "country", SchemaExpr.Literal("US", Schema[String]))
    // Add role with default
    .addField("role", SchemaExpr.Literal("Member", Schema[String])) // Would need UserRole schema
    .build

  /**
   * Composed migration from V1 to V3.
   */
  def migrationV1ToV3(
    implicit
    schemaV1: Schema[UserV1],
    schemaV2: Schema[UserV2],
    schemaV3: Schema[UserV3]
  ): Migration[UserV1, UserV3] = 
    migrationV1ToV2 ++ migrationV2ToV3

  // ============================================================================
  // Usage Example
  // ============================================================================

  def example(): Unit = {
    // Create a V1 user
    val userV1 = UserV1(
      name = "Alice Smith",
      age = 30,
      address = AddressV1(
        street = "123 Main St",
        city = "San Francisco",
        zipCode = "94102"
      )
    )

    println("=== Schema Migration Example ===\n")
    println(s"Original (V1): $userV1")

    // Apply migration to V2
    // In a real application, you would have implicit schemas derived:
    // implicit val addressV1Schema: Schema[AddressV1] = Schema.derive
    // implicit val addressV2Schema: Schema[AddressV2] = Schema.derive
    // implicit val userV1Schema: Schema[UserV1] = Schema.derive
    // implicit val userV2Schema: Schema[UserV2] = Schema.derive

    // val migration = migrationV1ToV2
    // val resultV2 = migration(userV1)
    
    // println(s"Migrated (V2): $resultV2")

    // The migration can be reversed
    // val reverseMigration = migration.reverse
    // val backToV1 = reverseMigration(resultV2.getOrElse(userV2))
    
    // Migrations are serializable
    // val json = migration.dynamicMigration.toJson
    // println(s"Serialized migration: $json")
  }

  // ============================================================================
  // DynamicValue Migration Example
  // ============================================================================

  def dynamicValueExample(): Unit = {
    println("\n=== DynamicValue Migration Example ===\n")

    // Create a DynamicValue representing a user
    val userDyn = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Bob Johnson")),
      ("age", DynamicValue.int(25)),
      ("address", DynamicValue.Record(Chunk(
        ("street", DynamicValue.string("456 Oak Ave")),
        ("city", DynamicValue.string("New York")),
        ("zipCode", DynamicValue.string("10001"))
      )))
    ))

    println(s"Original: $userDyn")

    // Create a dynamic migration
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "name", "fullName"),
      MigrationAction.addField(DynamicOptic.root, "email", SchemaExpr.Literal("", Schema[String])),
      MigrationAction.renameField(DynamicOptic.root.field("address"), "street", "streetName"),
      MigrationAction.addField(DynamicOptic.root.field("address"), "streetNumber", SchemaExpr.Literal(0, Schema[Int])),
      MigrationAction.renameField(DynamicOptic.root.field("address"), "zipCode", "postalCode")
    )

    // Apply migration
    val result = migration(userDyn)

    result match {
      case Right(migrated) =>
        println(s"Migrated: $migrated")
      case Left(error) =>
        println(s"Migration failed: $error")
    }

    // Show migration reversibility
    val reversed = migration.reverse
    println(s"\nReversed migration has ${reversed.actions.length} actions:")
    reversed.actions.foreach(println)
  }

  // ============================================================================
  // Error Handling Example
  // ============================================================================

  def errorHandlingExample(): Unit = {
    println("\n=== Error Handling Example ===\n")

    // Try to rename a non-existent field
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "nonExistentField", "newName")
    )

    val value = DynamicValue.Record(Chunk(
      ("otherField", DynamicValue.string("value"))
    ))

    migration(value) match {
      case Right(_) => println("Unexpected success")
      case Left(error) =>
        println(s"Error type: ${error.getClass.getSimpleName}")
        println(s"Error message: ${error.message}")
        println(s"Error path: ${error.path}")
    }
  }

  // ============================================================================
  // Main
  // ============================================================================

  def main(args: Array[String]): Unit = {
    example()
    dynamicValueExample()
    errorHandlingExample()
  }
}
