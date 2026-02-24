package into

import zio.blocks.schema.{Into, SchemaError}

// Demonstrates converting an external DTO into a validated domain model.
// A custom Into instance is provided for Email so the macro can wire it
// into the derived UserDto → User conversion automatically.
object IntoDomainBoundaryExample extends App {

  case class Email(value: String)

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email address: '$s'"))
  }

  case class UserDto(name: String, email: String, age: Int)
  case class User(name: String, email: Email, age: Long)

  val toUser = Into.derived[UserDto, User]

  // Happy path — all fields convert successfully
  println(toUser.into(UserDto("Alice", "alice@example.com", 30)))

  // Failure — email validation rejects the value
  println(toUser.into(UserDto("Bob", "not-an-email", 25)))
}
