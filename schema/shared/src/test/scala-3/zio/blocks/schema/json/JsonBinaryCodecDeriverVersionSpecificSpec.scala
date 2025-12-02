package zio.blocks.schema.json

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object JsonBinaryCodecDeriverVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecDeriverVersionSpecificSpec")(
    suite("records")(
      test("generic tuples") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple4] = Schema.derived

        roundTrip[GenericTuple4]((1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple, """[1,2,3,4]""")
      }
    ),
    suite("enums")(
      test("constant values") {
        roundTrip[TrafficLight](TrafficLight.Green, """"Green"""") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, """"Yellow"""") &&
        roundTrip[TrafficLight](TrafficLight.Red, """"Red"""")
      },
      test("complex recursive values") {
        import LinkedList._

        val schema1 = Schema.derived[LinkedList[Int]]
        val schema2 = Schema.derived[LinkedList[Option[String]]]

        roundTrip(
          Node(1, Node(2, End)),
          """{"::":{"val":1,"nxt":{"::":{"val":2,"nxt":{"End":{}}}}}}"""
        )(schema1) &&
        roundTrip(
          Node(Some("VVV"), Node(None, End)),
          """{"::":{"val":"VVV","nxt":{"::":{"nxt":{"End":{}}}}}}"""
        )(schema2)
      },
      test("union type") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int]

        implicit val schema: Schema[Value] = Schema.derived

        roundTrip[Value](1, """{"Int":1}""") &&
        roundTrip[Value](true, """{"Boolean":true}""") &&
        roundTrip[Value]("VVV", """{"String":"VVV"}""") &&
        roundTrip[Value]((1, true), """{"Tuple2":[1,true]}""") &&
        roundTrip[Value](List(1, 2, 3), """{"collection.immutable.List":[1,2,3]}""")
      }
    ),
    suite("sequences")(
      test("immutable array") {
        implicit val schema1: Schema[IArray[Int]]    = Schema.derived
        implicit val schema2: Schema[IArray[Long]]   = Schema.derived
        implicit val schema3: Schema[IArray[String]] = Schema.derived

        roundTrip(IArray(1, 2, 3), """[1,2,3]""") &&
        roundTrip(IArray(1L, 2L, 3L), """[1,2,3]""") &&
        roundTrip(IArray("A", "B", "C"), """["A","B","C"]""")
      }
    )
  )

  enum TrafficLight derives Schema {
    case Red, Yellow, Green
  }

  enum LinkedList[+T] {
    case End

    @Modifier.config("json.rename", "::")
    case Node(
      @Modifier.config("json.rename", "val") value: T,
      @Modifier.config("json.rename", "nxt") next: LinkedList[T]
    )
  }
}
