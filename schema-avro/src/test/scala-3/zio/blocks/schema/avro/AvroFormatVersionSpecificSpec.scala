package zio.blocks.schema.avro

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.blocks.schema.avro.AvroTestUtils._
import zio.test._

object AvroFormatVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatVersionSpecificSpec")(
    suite("records")(
      test("generic tuples") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple4] = Schema.derived

        avroSchema[GenericTuple4](
          "{\"type\":\"record\",\"name\":\"Tuple4\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"int\"},{\"name\":\"_3\",\"type\":\"int\"},{\"name\":\"_4\",\"type\":\"long\"}]}"
        ) &&
        roundTrip[GenericTuple4]((1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple, 4)
      }
    ),
    suite("variants")(
      test("constant values") {
        avroSchema[TrafficLight](
          "[{\"type\":\"record\",\"name\":\"Red\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Yellow\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Green\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.TrafficLight\",\"fields\":[]}]"
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
          "[{\"type\":\"record\",\"name\":\"End\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Node\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"},{\"name\":\"next\",\"type\":[{\"type\":\"record\",\"name\":\"End_1\",\"fields\":[]},\"Node\"]}]}]"
        )(schema1) &&
        roundTrip(Node(1, Node(2, End)), 5)(schema1) &&
        avroSchema[LinkedList[Option[String]]](
          "[{\"type\":\"record\",\"name\":\"End\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Node\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatVersionSpecificSpec.LinkedList\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]},{\"name\":\"next\",\"type\":[{\"type\":\"record\",\"name\":\"End_1\",\"fields\":[]},\"Node\"]}]}]"
        )(schema2) &&
        roundTrip(Node(Some("VVV"), Node(None, End)), 9)(schema2)
      },
      test("union type") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int]

        implicit val schema: Schema[Value] = Schema.derived

        avroSchema[Value](
          "[\"string\",\"boolean\",\"int\",{\"type\":\"record\",\"name\":\"Tuple2\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"boolean\"}]},{\"type\":\"array\",\"items\":\"int\"}]"
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
    )
  )

  enum TrafficLight derives Schema {
    case Red, Yellow, Green
  }

  enum LinkedList[+T] {
    case End

    case Node(value: T, next: LinkedList[T])
  }
}
