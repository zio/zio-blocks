package ziosschemamigration

import zio.blocks.schema._

/**
 * Migrating from ZIO Schema to ZIO Blocks Schema — Step 2: Modifiers and Transform
 *
 * This example demonstrates:
 *   - Replacing ZIO Schema's Chunk[Any] annotations with strongly-typed Modifier.Term
 *   - @Modifier.transient()  replaces @transientField
 *   - @Modifier.rename(name) replaces @fieldName(name)
 *   - @Modifier.alias(name)  replaces @fieldNameAliases(name)
 *   - @Modifier.config(key, value) for codec-specific configuration
 *   - Schema.transform replaces Schema.transformOrFail (throwing to indicate failure)
 *
 * Run with: sbt "examples/runMain ziosschemamigration.Step2ModifiersAndTransform"
 */
object Step2ModifiersAndTransform extends App {

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Transient fields
  //    ZIO Schema: @transientField
  //    ZIO Blocks: @Modifier.transient()
  // ─────────────────────────────────────────────────────────────────────────

  // Transient fields must have a default value in ZIO Blocks Schema
  // (they are excluded from serialization, so a default is needed for deserialization)
  final case class User(
    name: String,
    @Modifier.transient() password: String = ""
  )

  object User {
    implicit val schema: Schema[User] = Schema.derived[User]
  }

  val user = User("alice", "s3cr3t")
  println(s"User: $user")
  val userDv = User.schema.toDynamicValue(user)
  println(s"DynamicValue (password excluded by codec): $userDv")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Field renaming
  //    ZIO Schema: @fieldName("product_name")
  //    ZIO Blocks: @Modifier.rename("product_name")
  // ─────────────────────────────────────────────────────────────────────────

  final case class Product(
    @Modifier.rename("product_name") name: String,
    price: Double
  )

  object Product {
    implicit val schema: Schema[Product] = Schema.derived[Product]
  }

  val product = Product("Widget", 9.99)
  println(s"\nProduct: $product")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Field aliases (for decoding alternative names)
  //    ZIO Schema: @fieldNameAliases("max-size", "max_size")
  //    ZIO Blocks: @Modifier.alias repeated per name
  // ─────────────────────────────────────────────────────────────────────────

  final case class Config(
    @Modifier.alias("max-size")
    @Modifier.alias("max_size")
    maxSize: Int
  )

  object Config {
    implicit val schema: Schema[Config] = Schema.derived[Config]
  }

  println(s"Config schema modifiers on maxSize field:")
  Config.schema.reflect.asRecord.foreach { record =>
    record.fields.foreach { term =>
      println(s"  Field '${term.name}' has modifiers: ${term.modifiers}")
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Codec-specific configuration
  //    ZIO Schema: no standard mechanism; each codec module defines its own
  //    ZIO Blocks: @Modifier.config("format.property", "value")
  // ─────────────────────────────────────────────────────────────────────────

  final case class Message(
    @Modifier.config("protobuf.field-id", "1") id: Long,
    @Modifier.config("protobuf.field-id", "2") content: String
  )

  object Message {
    implicit val schema: Schema[Message] = Schema.derived[Message]
  }

  println(s"\nMessage schema field modifiers:")
  Message.schema.reflect.asRecord.foreach { record =>
    record.fields.foreach { term =>
      println(s"  Field '${term.name}': ${term.modifiers}")
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Transform (newtype / wrapper)
  //    ZIO Schema: Schema.transform(f, g) / Schema.transformOrFail(f, g)
  //    ZIO Blocks: Schema[A].transform(to, from) — throw SchemaError on failure
  // ─────────────────────────────────────────────────────────────────────────

  // Simple lossless newtype
  final case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] =
      Schema[Long].transform(UserId(_), _.value)
  }

  val uid   = UserId(42L)
  val uidDv = UserId.schema.toDynamicValue(uid)
  println(s"\nUserId: $uid  →  DynamicValue: $uidDv")
  println(s"Roundtrip: ${UserId.schema.fromDynamicValue(uidDv)}")

  // Validated newtype — throw SchemaError to signal failure
  final case class Email(value: String)
  object Email {
    implicit val schema: Schema[Email] =
      Schema[String].transform(
        to = str =>
          if (str.contains('@')) Email(str)
          else throw SchemaError.validationFailed("Not a valid email address"),
        from = _.value
      )
  }

  val validEmail   = Email.schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("alice@example.com")))
  val invalidEmail = Email.schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("notanemail")))

  println(s"\nValid email:   $validEmail")
  println(s"Invalid email: $invalidEmail")

  // ─────────────────────────────────────────────────────────────────────────
  // 6. Programmatic modifier attachment (schema.modifier)
  //    ZIO Schema: schema.annotate(annotation)
  //    ZIO Blocks: schema.modifier(Modifier.config(...))
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait Event
  final case class Created(id: String) extends Event

  object Event {
    implicit val schema: Schema[Event] =
      Schema
        .derived[Event]
        .modifier(Modifier.config("json.discriminator", "type"))
  }

  println(s"\nEvent schema modifiers: ${Event.schema.reflect.modifiers}")
}
