package into

import zio.blocks.schema.{Into, SchemaError}

// Demonstrates that Into collects ALL field errors into one SchemaError
// rather than stopping at the first failure.
object IntoErrorAccumulationExample extends App {

  case class Email(value: String)

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email address: '$s'"))
  }

  case class RawRecord(id: Long, email: String, score: Double)
  case class ValidRecord(id: Int, email: Email, score: Int)

  val validate = Into.derived[RawRecord, ValidRecord]

  // Three fields, three independent failures:
  //   id:    Long.MaxValue overflows Int
  //   email: missing '@'
  //   score: 7.5 is not a whole number
  val allBad = RawRecord(Long.MaxValue, "not-an-email", 7.5)
  validate.into(allBad) match {
    case Right(r)    => println(s"OK: $r")
    case Left(error) => println(s"Failed (${error.errors.length} errors):\n${error.message}")
  }

  // Only score fails — id and email are valid
  val oneBad = RawRecord(42L, "carol@example.com", 7.5)
  validate.into(oneBad) match {
    case Right(r)    => println(s"OK: $r")
    case Left(error) => println(s"Failed (${error.errors.length} error):\n${error.message}")
  }

  // All fields valid
  println(validate.into(RawRecord(42L, "carol@example.com", 100.0)))
}
