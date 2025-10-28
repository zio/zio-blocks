package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import zio.blocks.schema.{CompanionOptics, DynamicValue, Lens, PrimitiveValue, Schema}
import zio.blocks.avro.AvroTestUtils._
import zio.test._
import java.util.UUID

object AvroFormatVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatVersionSpecificSpec")(
    suite("records")(
      test("generic tuples") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple4] = Schema.derived

        roundTrip[GenericTuple4]((1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple, 4)
      }
    ),
    suite("enums")(
      test("constant values") {
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
      },
      test("complex recursive values") {
        import LinkedList._

        roundTrip(Node(1, Node(2, End)), 5)(Schema.derived[LinkedList[Int]]) &&
        roundTrip(Node(Some("VVV"), Node(None, End)), 9)(Schema.derived[LinkedList[Option[String]]])
      },
      test("union type") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int]

        implicit val schema: Schema[Value] = Schema.derived

        roundTrip[Value](1, 2) &&
        roundTrip[Value](true, 2) &&
        roundTrip[Value]("VVV", 5) &&
        roundTrip[Value]((1, true), 3) &&
        roundTrip[Value](List(1, 2, 3), 6)
      }
    ),
    suite("sequences")(
      test("immutable array") {
        roundTrip(IArray(1, 2, 3), 5)(Schema.derived[IArray[Int]]) &&
        roundTrip(IArray(1L, 2L, 3L), 5)(Schema.derived[IArray[Long]]) &&
        roundTrip(IArray("A", "B", "C"), 8)(Schema.derived[IArray[String]])
      }
    ),
    suite("dynamic value")(
      test("top-level") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")), 6) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())), 18) &&
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          16
        ) &&
        roundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))), 8) &&
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          12
        ) &&
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          18
        )
      },
      test("as record field values") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        roundTrip[Dynamic](value, 19)
      },
      test("as record field values with custom codecs injected by optic") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        val codec: AvroBinaryCodec[Dynamic] = Schema[Dynamic]
          .deriving(AvroFormat.deriver)
          .instance(
            Dynamic.primitive,
            new AvroBinaryCodec[DynamicValue.Primitive]() {
              private val default = DynamicValue.Primitive(PrimitiveValue.Int(1))
              private val codec   =
                Schema[DynamicValue].derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[DynamicValue.Primitive]]

              def decode(d: BinaryDecoder): DynamicValue.Primitive =
                if (d.readBoolean()) default
                else codec.decode(d)

              def encode(x: DynamicValue.Primitive, e: BinaryEncoder): Unit =
                if (x == default) e.writeBoolean(true)
                else {
                  e.writeBoolean(false)
                  codec.encode(x, e)
                }
            }
          )
          .instance(
            Dynamic.map,
            new AvroBinaryCodec[DynamicValue.Map]() {
              private val default = DynamicValue.Map(
                Vector(
                  (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
                  (
                    DynamicValue.Primitive(PrimitiveValue.Long(2L)),
                    DynamicValue.Primitive(PrimitiveValue.String("VVV"))
                  )
                )
              )
              private val codec =
                Schema[DynamicValue].derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[DynamicValue.Map]]

              def decode(d: BinaryDecoder): DynamicValue.Map =
                if (d.readBoolean()) default
                else codec.decode(d)

              def encode(x: DynamicValue.Map, e: BinaryEncoder): Unit =
                if (x == default) e.writeBoolean(true)
                else {
                  e.writeBoolean(false)
                  codec.encode(x, e)
                }
            }
          )
          .derive
        shortRoundTrip[Dynamic](value, 2, codec)
      }
    )
  )

  enum TrafficLight derives Schema {
    case Red, Yellow, Green
  }

  enum LinkedList[+T] {
    case End

    case Node(value: T, next: LinkedList[T])
  }

  case class Dynamic(primitive: DynamicValue.Primitive, map: DynamicValue.Map) derives Schema

  object Dynamic extends CompanionOptics[Dynamic] {
    val primitive: Lens[Dynamic, DynamicValue.Primitive] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue.Map]             = $(_.map)
  }
}
