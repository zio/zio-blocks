package example

import golem.data.{
  DataInterop,
  DataType,
  DataValue,
  ElementSchema,
  ElementValue,
  GolemSchema,
  NamedElementValue,
  StructuredSchema,
  StructuredValue
}
import zio.test._
import zio.blocks.schema.Schema

import java.util.UUID

private sealed trait Status
private object Status {
  case object Ok                        extends Status
  final case class Missing(key: String) extends Status
  implicit val schema: Schema[Status] = Schema.derived
}

object SchemaInteropRoundtripSpec extends ZIOSpecDefault {

  private def roundTrip[A](a: A)(implicit gs: GolemSchema[A]): A = {
    val encoded: StructuredValue = gs.encode(a).fold(err => throw new RuntimeException(err), identity)
    gs.decode(encoded).fold(err => throw new RuntimeException(err), identity)
  }

  def spec = suite("SchemaInteropRoundtripSpec")(
    test("round-trip: struct (case class) + optional field + collections") {
      final case class UserId(value: String)
      object UserId { implicit val schema: Schema[UserId] = Schema.derived }

      final case class Profile(id: UserId, age: Option[Int], tags: Set[String], attrs: Map[String, Int])
      object Profile { implicit val schema: Schema[Profile] = Schema.derived }

      val in  = Profile(UserId("u-1"), Some(42), Set("a", "b"), Map("x" -> 1, "y" -> 2))
      val out = roundTrip(in)
      assertTrue(out == in)
    },
    test("round-trip: enum/variant-style ADT with payload") {
      assertTrue(
        roundTrip[Status](Status.Ok) == Status.Ok,
        roundTrip[Status](Status.Missing("k")) == Status.Missing("k")
      )
    },
    test("round-trip: UUID") {
      final case class Id(value: UUID)
      object Id {
        implicit val schema: Schema[Id] = Schema.derived
      }

      val in  = Id(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
      val out = roundTrip(in)
      assertTrue(out == in)
    },
    test("custom conversion example: bytes round-trip via custom GolemSchema") {
      implicit val bytesSchema: GolemSchema[Array[Byte]] = new GolemSchema[Array[Byte]] {
        override val schema: StructuredSchema =
          StructuredSchema.single(ElementSchema.Component(DataType.BytesType))

        override def encode(value: Array[Byte]): Either[String, StructuredValue] =
          Right(StructuredValue.single(ElementValue.Component(DataValue.BytesValue(value))))

        override def decode(value: StructuredValue): Either[String, Array[Byte]] =
          value match {
            case StructuredValue.Tuple(
                  NamedElementValue(_, ElementValue.Component(DataValue.BytesValue(bytes))) :: Nil
                ) =>
              Right(bytes)
            case other =>
              Left(s"Expected component bytes payload, found: $other")
          }
      }

      val in  = Array[Byte](1, 2, 3)
      val out = roundTrip(in)
      assertTrue(out.toSeq == in.toSeq)
    },
    test("Schema -> DataType shape is stable for common constructs") {
      final case class Rec(a: String, b: Option[Int], c: List[String])
      object Rec { implicit val schema: Schema[Rec] = Schema.derived }

      val dt = DataInterop.schemaToDataType(implicitly[Schema[Rec]])

      dt match {
        case DataType.StructType(fields) =>
          assertTrue(fields.map(_.name) == List("a", "b", "c"))
        case other =>
          throw new RuntimeException(s"Expected StructType, got: $other")
      }
    }
  )
}
