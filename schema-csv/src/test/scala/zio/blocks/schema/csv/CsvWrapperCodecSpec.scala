package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvWrapperCodecSpec extends SchemaBaseSpec {

  case class Name(value: String) extends AnyVal

  object Name {
    implicit val schema: Schema[Name] = Schema.derived
  }

  case class Age(value: Int) extends AnyVal

  object Age {
    implicit val schema: Schema[Age] = Schema.derived
  }

  private def deriveCodec[A](implicit s: Schema[A]): CsvCodec[A] =
    s.derive(CsvFormat)

  private def roundTrip[A](codec: CsvCodec[A], value: A): Either[SchemaError, A] = {
    val buf = CharBuffer.allocate(1024)
    codec.encode(value, buf)
    buf.flip()
    codec.decode(buf)
  }

  def spec = suite("CsvWrapperCodecSpec")(
    test("Name wrapper round-trips") {
      val codec = deriveCodec[Name]
      val value = Name("Alice")
      assertTrue(roundTrip(codec, value) == Right(value))
    },
    test("Age wrapper round-trips") {
      val codec = deriveCodec[Age]
      val value = Age(30)
      assertTrue(roundTrip(codec, value) == Right(value))
    },
    test("Name wrapper headerNames delegates to wrapped") {
      val codec = deriveCodec[Name]
      assertTrue(codec.headerNames == IndexedSeq("value"))
    },
    test("Name wrapper nullValue wraps wrapped nullValue") {
      val codec = deriveCodec[Name]
      assertTrue(codec.nullValue.value == null)
    },
    test("Age wrapper nullValue wraps wrapped nullValue") {
      val codec = deriveCodec[Age]
      assertTrue(codec.nullValue == Age(0))
    }
  )
}
