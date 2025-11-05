package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
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

        avroSchema[GenericTuple4](
          "{\"type\":\"record\",\"name\":\"Tuple4_fcd61909\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"int\"},{\"name\":\"_3\",\"type\":\"int\"},{\"name\":\"_4\",\"type\":\"long\"}]}"
        ) &&
        roundTrip[GenericTuple4]((1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple, 4)
      }
    ),
    suite("enums")(
      test("constant values") {
        avroSchema[TrafficLight](
          "[{\"type\":\"record\",\"name\":\"Red\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Yellow\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Green\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]}]"
        ) &&
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
      },
      test("complex recursive values") {
        import LinkedList._

        val schema1 = Schema.derived[LinkedList[Int]]
        val schema2 = Schema.derived[LinkedList[Option[String]]]

        avroSchema[LinkedList[Int]](
          "[{\"type\":\"record\",\"name\":\"End\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Node_ccb86a7f\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"},{\"name\":\"next\",\"type\":[\"End\",\"Node_ccb86a7f\"]}]}]"
        )(schema1) &&
        roundTrip(Node(1, Node(2, End)), 5)(schema1) &&
        avroSchema[LinkedList[Option[String]]](
          "[{\"type\":\"record\",\"name\":\"End\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Node_29e3482a\",\"namespace\":\"zio.blocks.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some_10c51065\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]},{\"name\":\"next\",\"type\":[\"End\",\"Node_29e3482a\"]}]}]"
        )(schema2) &&
        roundTrip(Node(Some("VVV"), Node(None, End)), 9)(schema2)
      },
      test("union type") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int]

        implicit val schema: Schema[Value] = Schema.derived

        avroSchema[Value](
          "[\"int\",\"boolean\",\"string\",{\"type\":\"record\",\"name\":\"Tuple2_dee37272\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"boolean\"}]},{\"type\":\"array\",\"items\":\"int\"}]"
        ) &&
        roundTrip[Value](1, 2) &&
        roundTrip[Value](true, 2) &&
        roundTrip[Value]("VVV", 5) &&
        roundTrip[Value]((1, true), 3) &&
        roundTrip[Value](List(1, 2, 3), 6)
      }
    ),
    suite("sequences")(
      test("immutable array") {
        implicit val schema1: Schema[IArray[Int]]    = Schema.derived
        implicit val schema2: Schema[IArray[Long]]   = Schema.derived
        implicit val schema3: Schema[IArray[String]] = Schema.derived

        avroSchema[IArray[Int]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(IArray(1, 2, 3), 5) &&
        avroSchema[IArray[Long]]("{\"type\":\"array\",\"items\":\"long\"}") &&
        roundTrip(IArray(1L, 2L, 3L), 5) &&
        avroSchema[IArray[String]]("{\"type\":\"array\",\"items\":\"string\"}") &&
        roundTrip(IArray("A", "B", "C"), 8)
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
              private val codec =
                Schema[DynamicValue].derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[DynamicValue.Primitive]]

              val avroSchema: AvroSchema =
                AvroSchema.createUnion(AvroSchema.create(AvroSchema.Type.NULL), codec.avroSchema)

              def decodeUnsafe(decoder: BinaryDecoder): DynamicValue.Primitive = {
                val idx = decoder.readInt()
                if (idx == 0) null
                else codec.decodeUnsafe(decoder)
              }

              def encode(value: DynamicValue.Primitive, encoder: BinaryEncoder): Unit =
                if (value eq null) encoder.writeInt(0)
                else {
                  encoder.writeInt(1)
                  codec.encode(value, encoder)
                }
            }
          )
          .instance(
            Dynamic.map,
            new AvroBinaryCodec[DynamicValue.Map]() {
              private val codec =
                Schema[DynamicValue].derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[DynamicValue.Map]]

              val avroSchema: AvroSchema =
                AvroSchema.createUnion(AvroSchema.create(AvroSchema.Type.NULL), codec.avroSchema)

              def decodeUnsafe(decoder: BinaryDecoder): DynamicValue.Map = {
                val idx = decoder.readInt()
                if (idx == 0) null
                else codec.decodeUnsafe(decoder)
              }

              def encode(value: DynamicValue.Map, encoder: BinaryEncoder): Unit =
                if (value eq null) encoder.writeInt(0)
                else {
                  encoder.writeInt(1)
                  codec.encode(value, encoder)
                }
            }
          )
          .derive
        roundTrip[Dynamic](value, 23, codec) &&
        roundTrip[Dynamic](Dynamic(null, null), 2, codec)
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
