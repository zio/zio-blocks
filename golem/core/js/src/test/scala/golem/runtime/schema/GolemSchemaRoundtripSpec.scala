package golem.runtime.schema

import golem.data.GolemSchema
import org.scalatest.funsuite.AnyFunSuite
import zio.blocks.schema.Schema

private[schema] object GolemSchemaRoundtripTypes {
  final case class Person(name: String, age: Int, tags: List[String])
  object Person { implicit val schema: Schema[Person] = Schema.derived }

  sealed trait Color
  object Color {
    case object Red  extends Color
    case object Blue extends Color
    implicit val schema: Schema[Color] = Schema.derived
  }
}

final class GolemSchemaRoundtripSpec extends AnyFunSuite {
  import GolemSchemaRoundtripTypes._

  private def roundTrip[A](label: String)(value: A)(implicit schema: Schema[A]): Unit = {
    implicit val gs: GolemSchema[A] = GolemSchema.fromBlocksSchema[A]
    test(s"roundtrip: $label") {
      val encoded = gs.encode(value).fold(err => fail(err), identity)
      val decoded = gs.decode(encoded).fold(err => fail(err), identity)
      assert(decoded == value)
    }
  }

  private implicit val tuple2Schema: Schema[(String, Int)]          = Schema.derived
  private implicit val tuple3Schema: Schema[(String, Int, Boolean)] = Schema.derived

  roundTrip("unit")(())
  roundTrip("string")("hello")
  roundTrip("int")(123)
  roundTrip("option some")(Option("x"))
  roundTrip("option none")(Option.empty[String])
  roundTrip("list")(List(1, 2, 3))
  roundTrip("map")(Map("a" -> 1, "b" -> 2))
  roundTrip("product")(Person("alice", 42, List("x", "y")))(Person.schema)
  roundTrip[Color]("enum")(Color.Blue)(Color.schema)
  roundTrip("tuple2")(("a", 1))
  roundTrip("tuple3")(("a", 1, true))
}
