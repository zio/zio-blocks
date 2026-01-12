package zio.blocks.schema.toon

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.Schema
import java.nio.charset.StandardCharsets

object ToonPropertySpec extends ZIOSpecDefault {

  def roundTrip[A](schema: Schema[A], value: A): Either[String, A] = {
    val codec   = schema.derive(ToonFormat.deriver)
    val encoded = codec.encodeToString(value)
    val bytes   = encoded.getBytes(StandardCharsets.UTF_8)
    val reader  = new ToonReader(bytes, new Array[Char](1024), bytes.length, ToonReaderConfig)
    try {
      Right(codec.decodeValue(reader, null.asInstanceOf[A]))
    } catch {
      case e: Exception => Left(s"Failed to decode '$encoded': ${e.getMessage}")
    }
  }

  def spec = suite("ToonPropertySpec")(
    test("round-trip arbitrary integers") {
      check(Gen.int) { n =>
        val result = roundTrip(Schema.int, n)
        assert(result)(isRight(equalTo(n)))
      }
    },
    test("round-trip arbitrary strings") {
      check(Gen.string) { s =>
        val result = roundTrip(Schema.string, s)
        assert(result)(isRight(equalTo(s)))
      }
    },
    test("round-trip arbitrary lists of integers") {
      check(Gen.listOf(Gen.int)) { list =>
        val result = roundTrip(Schema.list[Int], list)
        assert(result)(isRight(equalTo(list)))
      }
    },
    test("round-trip arbitrary booleans") {
      check(Gen.boolean) { b =>
        val result = roundTrip(Schema.boolean, b)
        assert(result)(isRight(equalTo(b)))
      }
    },
    test("round-trip arbitrary doubles") {
      check(Gen.double) { d =>
        if (d.isNaN || d.isInfinity) assert(true)(isTrue)
        else {
          val result = roundTrip(Schema.double, d)
          assert(result)(isRight(equalTo(d)))
        }
      }
    }
  )
}
