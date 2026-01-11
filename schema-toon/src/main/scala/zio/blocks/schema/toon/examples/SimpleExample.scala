package zio.blocks.schema.toon.examples

import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonFormat

/**
 * Simple example demonstrating basic TOON codec usage with a case class.
 *
 * Run with:
 * `sbt "schema-toon/runMain zio.blocks.schema.toon.examples.SimpleExample"`
 */
object SimpleExample extends App {

  // Define a simple case class
  case class Person(name: String, age: Int, email: String)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  // Derive the TOON codec
  val codec = Person.schema.derive(ToonFormat.deriver)

  // Create some sample data
  val alice = Person("Alice Smith", 30, "alice@example.com")
  val bob   = Person("Bob", 25, "bob@test.org")

  // Encode to TOON
  println("=== Simple Case Class Encoding ===")
  println()
  println("Person 1:")
  println(codec.encodeToString(alice))
  println()
  println("Person 2:")
  println(codec.encodeToString(bob))
  println()

  // With different string types
  case class Message(from: String, to: String, body: String)
  object Message {
    implicit val schema: Schema[Message] = Schema.derived
  }

  val messageCodec = Message.schema.derive(ToonFormat.deriver)
  val message      = Message("alice", "bob", "Hello, how are you?")

  println("=== Message with Special Characters ===")
  println()
  println(messageCodec.encodeToString(message))
}
