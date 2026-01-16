package zio.schema.migration.examples

import zio._
import zio.schema._
import zio.schema.codec.JsonCodec
import zio.schema.migration._

/**
 * Example demonstrating serialization and deserialization of migrations.
 *
 * Key capability: Migrations can be serialized to JSON, stored in a database,
 * and loaded at runtime without needing the original schema definitions.
 */
object SerializationExample extends ZIOAppDefault {

  // Example schemas
  case class UserV1(name: String)
  object UserV1 {
    implicit val schema: Schema[UserV1] = DeriveSchema.gen[UserV1]
  }

  case class UserV2(name: String, email: String, age: Int)
  object UserV2 {
    implicit val schema: Schema[UserV2] = DeriveSchema.gen[UserV2]
  }

  def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Migration Serialization Example ===\n")

      // Create a migration
      migration = MigrationBuilder[UserV1, UserV2]
                    .addField[String]("email", "user@example.com")
                    .addField[Int]("age", 18)
                    .build

      _ <- Console.printLine("1. Created migration V1 -> V2")
      _ <- Console.printLine(s"   Actions: ${migration.dynamicMigration.actions.length}")

      // Extract the pure data representation
      dynamicMigration = migration.toDynamic
      _               <- Console.printLine("\n2. Extracted DynamicMigration (pure data)")

      // Serialize to JSON
      jsonResult = serializeToJson(dynamicMigration)
      _         <- jsonResult match {
             case Right(json) =>
               for {
                 _ <- Console.printLine("\n3. Serialized to JSON:")
                 _ <- Console.printLine(s"   ${json.take(200)}${if (json.length > 200) "..." else ""}")

                 // Deserialize from JSON
                 _ <- deserializeFromJson(json) match {
                        case Right(restored) =>
                          for {
                            _ <- Console.printLine("\n4. Deserialized from JSON successfully")
                            _ <- Console.printLine(s"   Actions: ${restored.actions.length}")

                            // Verify it works
                            _       <- Console.printLine("\n5. Testing restored migration:")
                            testUser = UserV1("Alice")
                            _       <- Console.printLine(s"   Input: $testUser")

                            // Apply using restored migration (without type info!)
                            dynamicValue = DynamicValue.fromSchemaAndValue(UserV1.schema, testUser)
                            result       = restored(dynamicValue)

                            _ <- result match {
                                   case Right(migrated) =>
                                     migrated.toTypedValue(UserV2.schema) match {
                                       case Right(userV2) =>
                                         Console.printLine(s"   Output: $userV2")
                                       case Left(error) =>
                                         Console.printLine(s"   Error converting to typed: $error")
                                     }
                                   case Left(error) =>
                                     Console.printLine(s"   Migration error: ${error.message}")
                                 }
                          } yield ()
                        case Left(error) =>
                          Console.printLine(s"\n4. Failed to deserialize: $error")
                      }
               } yield ()
             case Left(error) =>
               Console.printLine(s"\n3. Failed to serialize: $error")
           }

      // Show the key insight
      _ <- Console.printLine("\n=== Key Insight ===")
      _ <- Console.printLine("The migration was serialized, stored, and restored")
      _ <- Console.printLine("without needing the UserV1 or UserV2 type definitions!")
      _ <- Console.printLine("This enables:")
      _ <- Console.printLine("  • Storing migrations in a database")
      _ <- Console.printLine("  • Transmitting migrations over the network")
      _ <- Console.printLine("  • Applying migrations in different processes")
      _ <- Console.printLine("  • Building a migration registry service")

    } yield ()

  /**
   * Serialize a DynamicMigration to JSON
   */
  def serializeToJson(migration: DynamicMigration): Either[String, String] =
    try {
      // Note: This assumes MigrationAction has proper Schema derivation
      // For TransformField with functions, you'd need custom serialization
      val codec = JsonCodec.schemaBasedBinaryCodec[DynamicMigration](DynamicMigration.schema)
      val bytes = codec.encode(migration)
      Right(new String(bytes.toArray, "UTF-8"))
    } catch {
      case e: Exception =>
        Left(s"Serialization failed: ${e.getMessage}")
    }

  /**
   * Deserialize a DynamicMigration from JSON
   */
  def deserializeFromJson(json: String): Either[String, DynamicMigration] =
    try {
      val codec = JsonCodec.schemaBasedBinaryCodec[DynamicMigration](DynamicMigration.schema)
      val bytes = Chunk.fromArray(json.getBytes("UTF-8"))
      codec.decode(bytes) match {
        case Left(error)      => Left(error.message)
        case Right(migration) => Right(migration)
      }
    } catch {
      case e: Exception =>
        Left(s"Deserialization failed: ${e.getMessage}")
    }
}

/**
 * Example of a migration registry that stores migrations
 */
object MigrationRegistryExample {

  case class Version(major: Int, minor: Int, patch: Int) {
    override def toString: String = s"$major.$minor.$patch"
  }

  /**
   * A simple migration registry backed by a Map. In production, this would use
   * a database.
   */
  class MigrationRegistry {
    private val storage = scala.collection.concurrent.TrieMap[String, String]()

    /**
     * Store a migration as JSON
     */
    def store(
      from: Version,
      to: Version,
      migration: DynamicMigration
    ): Task[Unit] =
      ZIO.attempt {
        val key = s"$from->$to"
        SerializationExample.serializeToJson(migration) match {
          case Right(json) =>
            storage.put(key, json)
            ()
          case Left(error) =>
            throw new Exception(s"Failed to serialize: $error")
        }
      }

    /**
     * Retrieve a migration from JSON
     */
    def retrieve(
      from: Version,
      to: Version
    ): Task[Option[DynamicMigration]] =
      ZIO.attempt {
        val key = s"$from->$to"
        storage.get(key).flatMap { json =>
          SerializationExample.deserializeFromJson(json).toOption
        }
      }

    /**
     * Find a migration path between versions using graph search
     */
    def findPath(
      from: Version,
      to: Version
    ): Task[Option[DynamicMigration]] =
      // In production, implement BFS/Dijkstra to find shortest path
      // through migration graph
      retrieve(from, to)

    /**
     * List all available migrations
     */
    def listMigrations(): Task[List[(String, String)]] =
      ZIO.succeed(storage.keys.map(key => (key, key)).toList)
  }

  /**
   * Example usage of the registry
   */
  def example: ZIO[Any, Throwable, Unit] =
    for {
      registry <- ZIO.succeed(new MigrationRegistry())

      // Store some migrations
      v1 = Version(1, 0, 0)
      v2 = Version(2, 0, 0)
      v3 = Version(3, 0, 0)

      migration1to2 = DynamicMigration.single(
                        MigrationAction.AddField(
                          FieldPath("email"),
                          DynamicValue.Primitive("", StandardType.StringType)
                        )
                      )

      migration2to3 = DynamicMigration.single(
                        MigrationAction.AddField(
                          FieldPath("verified"),
                          DynamicValue.Primitive(false, StandardType.BoolType)
                        )
                      )

      _ <- registry.store(v1, v2, migration1to2)
      _ <- registry.store(v2, v3, migration2to3)

      // Retrieve a migration
      retrieved <- registry.retrieve(v1, v2)
      _         <- retrieved match {
             case Some(m) =>
               Console.printLine(s"Retrieved migration 1.0.0 -> 2.0.0 with ${m.actions.length} actions")
             case None =>
               Console.printLine("Migration not found")
           }

      // In production, you could:
      // 1. Store registry in PostgreSQL/Redis
      // 2. Build migration paths automatically
      // 3. Cache frequently used migrations
      // 4. Version the registry itself
      // 5. Validate migrations on storage

    } yield ()
}

/**
 * Example showing how to handle function serialization limitation
 *
 * Note: We now use the real SerializableTransformation from the main package
 */
object TransformationSerializationExample {

  /**
   * Alternative: Expression-based transformations (more complex but more
   * flexible)
   */
  sealed trait TransformExpression

  object TransformExpression {
    case class Literal(value: DynamicValue)                                    extends TransformExpression
    case class FieldRef(path: String)                                          extends TransformExpression
    case class Add(left: TransformExpression, right: TransformExpression)      extends TransformExpression
    case class Multiply(left: TransformExpression, right: TransformExpression) extends TransformExpression
    case class Concat(parts: List[TransformExpression])                        extends TransformExpression

    // This can be fully serialized and evaluated
    implicit val schema: Schema[TransformExpression] =
      DeriveSchema.gen[TransformExpression]
  }
}
