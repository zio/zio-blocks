package into

import zio.blocks.schema.{Into, SchemaError}

/**
 * Runnable examples for the Into type class.
 *
 * Covers: schema evolution, domain boundaries, collection reshaping,
 * sealed trait migration, error accumulation, and numeric conversions.
 */
object IntoExample extends App {

  def header(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // =========================================================
  // 1. Schema Evolution
  //    Migrating a product type across API versions.
  // =========================================================

  header("1. Schema Evolution")

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long, email: Option[String])

  val migratePerson = Into.derived[PersonV1, PersonV2]

  // New optional field defaults to None; Int widens to Long
  println(migratePerson.into(PersonV1("Alice", 30)))
  println(migratePerson.into(PersonV1("Bob", 25)))

  // =========================================================
  // 2. Domain Boundaries
  //    Converting from an external DTO to an internal model.
  //    Uses a custom Into instance for validated wrapper types.
  // =========================================================

  header("2. Domain Boundaries — DTO to Domain Model")

  case class Email(value: String)

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email address: '$s'"))
  }

  case class UserDto(name: String, email: String, age: Int)
  case class User(name: String, email: Email, age: Long)

  val validateUser = Into.derived[UserDto, User]

  // Happy path
  println(validateUser.into(UserDto("Alice", "alice@example.com", 30)))

  // Validation failure
  println(validateUser.into(UserDto("Bob", "not-an-email", 25)))

  // =========================================================
  // 3. Collection Reshaping
  //    Converting between different collection types,
  //    with element-level type coercion applied automatically.
  // =========================================================

  header("3. Collection Reshaping")

  // List → Vector with Int → Long element widening
  println(Into[List[Int], Vector[Long]].into(List(1, 2, 3)))

  // List → Set (duplicates removed)
  println(Into[List[Int], Set[Long]].into(List(1, 2, 2, 3)))

  // Map key and value coercion
  println(Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2)))

  // Option element coercion
  println(Into[Option[Int], Option[Long]].into(Some(42)))
  println(Into[Option[Int], Option[Long]].into(None))

  // Case class with collection field conversion
  case class OrderV1(id: String, quantities: List[Int])
  case class OrderV2(id: String, quantities: Vector[Long])

  println(Into.derived[OrderV1, OrderV2].into(OrderV1("order-1", List(5, 10, 3))))

  // =========================================================
  // 4. Sealed Trait / Enum Evolution
  //    Adding cases to a coproduct; existing cases migrate
  //    by name, with field types coerced automatically.
  // =========================================================

  header("4. Sealed Trait Evolution")

  sealed trait EventV1
  object EventV1 {
    case class Created(id: String, count: Int)  extends EventV1
    case class Deleted(id: String)              extends EventV1
  }

  sealed trait EventV2
  object EventV2 {
    case class Created(id: String, count: Long) extends EventV2
    case class Deleted(id: String)              extends EventV2
    case class Archived(id: String)             extends EventV2  // new case
  }

  val migrateEvent = Into.derived[EventV1, EventV2]

  println(migrateEvent.into(EventV1.Created("e1", 42)))
  println(migrateEvent.into(EventV1.Deleted("e2")))

  // =========================================================
  // 5. Error Accumulation
  //    All failing fields are collected into one SchemaError.
  // =========================================================

  header("5. Error Accumulation")

  case class RawRecord(id: Long, email: String, score: Double)

  // score: Double → Int narrowing may fail if not a whole number
  case class ValidRecord(id: Int, email: Email, score: Int)

  val validateRecord = Into.derived[RawRecord, ValidRecord]

  // Two fields fail: id overflow and invalid email
  val bad = RawRecord(Long.MaxValue, "not-an-email", 7.5)

  validateRecord.into(bad) match {
    case Right(r)    => println(s"OK: $r")
    case Left(error) => println(s"Failed:\n${error.message}")
  }

  // All fields valid
  val good = RawRecord(42L, "carol@example.com", 100.0)
  println(validateRecord.into(good))

  // =========================================================
  // 6. Numeric Conversions
  //    Widening is always safe; narrowing validates at runtime.
  // =========================================================

  header("6. Numeric Conversions")

  // Widening — always succeeds
  println(Into[Byte,  Int   ].into(42.toByte))
  println(Into[Int,   Long  ].into(100))
  println(Into[Float, Double].into(3.14f))

  // Narrowing — succeeds when value fits
  println(Into[Long,   Int  ].into(42L))
  println(Into[Double, Float].into(1.5))

  // Narrowing — fails when out of range
  println(Into[Long,   Int  ].into(Long.MaxValue))
  println(Into[Double, Int  ].into(3.14))
}
