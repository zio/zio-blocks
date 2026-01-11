package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

object RecordCodecSpec extends ZIOSpecDefault {
  def spec = suite("RecordCodec")(
    test("simple record encoding") {
      val codec: ToonBinaryCodec[Person] = Person.schema.derive(ToonFormat.deriver)
      val person                         = Person("Alice", 30)
      val toon                           = codec.encodeToString(person)
      assertTrue(toon == "name: Alice\nage: 30")
    },
    test("record with special characters in string field") {
      val codec: ToonBinaryCodec[Person] = Person.schema.derive(ToonFormat.deriver)
      val person                         = Person("John Doe", 25)
      val toon                           = codec.encodeToString(person)
      // Since "John Doe" contains a space, it should be quoted
      assertTrue(toon == "name: \"John Doe\"\nage: 25")
    }
  )
}
