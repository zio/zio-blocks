package golem.runtime.autowire

import org.scalatest.funsuite.AnyFunSuite

import scala.scalajs.js
import zio.blocks.schema.Schema

private[autowire] object JsPlainSchemaCodecSpecTypes {
  final case class Nested(x: Double, tags: List[String])
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  final case class Payload(
    name: String,
    count: Int,
    note: Option[String],
    flags: List[String],
    nested: Nested
  )
  object Payload {
    implicit val schema: Schema[Payload] = Schema.derived
  }
}

final class JsPlainSchemaCodecSpec extends AnyFunSuite {
  import JsPlainSchemaCodecSpecTypes._

  test("roundtrip: Scala value -> JS plain -> Scala value") {
    val v = Payload("abc", 7, Some("n"), List("x", "y", "z"), Nested(1.5, List("a", "b")))

    val jsAny = JsPlainSchemaCodec.encode(v)
    val back  = JsPlainSchemaCodec.decode[Payload](jsAny)

    assert(back == Right(v))
  }

  test("decode from manual JS object (null option)") {
    val jsObj =
      js.Dynamic.literal(
        "name"   -> "abc",
        "count"  -> 7,    // JS number (double)
        "note"   -> null, // None
        "flags"  -> js.Array("x", "y"),
        "nested" -> js.Dynamic.literal("x" -> 1.5, "tags" -> js.Array("a", "b"))
      )

    val got = JsPlainSchemaCodec.decode[Payload](jsObj.asInstanceOf[js.Any])

    assert(
      got == Right(
        Payload(
          name = "abc",
          count = 7,
          note = None,
          flags = List("x", "y"),
          nested = Nested(1.5, List("a", "b"))
        )
      )
    )
  }
}
