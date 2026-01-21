package zio.blocks.schema.json

import zio.blocks.schema.{Modifier, Schema, SchemaBaseSpec}
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object JsonBinaryCodecDeriverVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecDeriverVersionSpecificSpec")(
    suite("records")(
      test("generic tuples") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple4] = Schema.derived

        roundTrip[GenericTuple4]((1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple, """[1,2,3,4]""")
      },
      test("nested generic records") {
        case class Parent(child: Child[MySealedTrait]) derives Schema

        case class Child[T <: MySealedTrait](test: T) derives Schema

        sealed trait MySealedTrait derives Schema

        object MySealedTrait {
          case class Foo(foo: Int) extends MySealedTrait

          case class Bar(bar: String) extends MySealedTrait
        }

        roundTrip[Parent](Parent(Child(MySealedTrait.Foo(1))), """{"child":{"test":{"Foo":{"foo":1}}}}""") &&
        roundTrip[Parent](Parent(Child(MySealedTrait.Bar("WWW"))), """{"child":{"test":{"Bar":{"bar":"WWW"}}}}""")
      },
      test("record with array field") {
        roundTrip(Arrays(IArray()), """{}""") &&
        roundTrip(Arrays(IArray("VVV", "WWW")), """{"xs":["VVV","WWW"]}""")
      }
    ),
    suite("variants")(
      test("constant values on different hierarchy levels") {
        roundTrip[Foo](Foo1, """"Foo1"""") &&
        roundTrip[Foo](Bar1, """"Bar1"""")
      },
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
      test("complex recursive values without discriminator") {
        import LinkedList._

        val codec = Schema
          .derived[LinkedList[Double]]
          .derive(JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None))
        roundTrip(Node(1.0, Node(2.0, End)), """{"val":1.0,"nxt":{"val":2.0,"nxt":{}}}""", codec)
      },
      test("union type with key discriminator") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int] | Unit

        implicit val schema: Schema[Value] = Schema.derived

        roundTrip[Value](1, """{"scala.Int":1}""") &&
        roundTrip[Value](true, """{"scala.Boolean":true}""") &&
        roundTrip[Value]("VVV", """{"java.lang.String":"VVV"}""") &&
        roundTrip[Value]((1, true), """{"scala.Tuple2":[1,true]}""") &&
        roundTrip[Value](List(1, 2, 3), """{"scala.collection.immutable.List":[1,2,3]}""") &&
        roundTrip[Value]((), """{"Unit":{}}""")
      },
      test("union type without discriminator") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int] | Unit

        val codec = Schema.derived[Value].derive(JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None))
        roundTrip(1, "1", codec) &&
        roundTrip(true, "true", codec) &&
        roundTrip("VVV", """"VVV"""", codec) &&
        roundTrip((1, true), "[1,true]", codec) &&
        roundTrip(List(1, 2, 3), "[1,2,3]", codec) &&
        roundTrip((), "{}", codec) &&
        decodeError("[1,true,2]", "expected a variant value at: .", codec) &&
        decodeError("[1.0,2.0]", "expected a variant value at: .", codec) &&
        decodeError("1.001", "expected a variant value at: .", codec) &&
        decodeError("01", "expected a variant value at: .", codec) &&
        decodeError("1e+1", "expected a variant value at: .", codec)
      },
      test("nested variants without discriminator") {
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int]

        sealed trait Base

        case class Case1(value: Value) extends Base

        case class Case2(value: Map[Int, Long]) extends Base

        val codec = Schema.derived[Base].derive(JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None))
        roundTrip(Case1(1), """{"value":1}""", codec) &&
        roundTrip(Case1(true), """{"value":true}""", codec) &&
        roundTrip(Case1("VVV"), """{"value":"VVV"}""", codec) &&
        roundTrip(Case1((1, true)), """{"value":[1,true]}""", codec) &&
        roundTrip(Case1(List(1, 2, 3)), """{"value":[1,2,3]}""", codec) &&
        roundTrip(Case2(Map(1 -> 2L)), """{"value":{"1":2}}""", codec) &&
        roundTrip(Case2(Map.empty), """{}""", codec) &&
        decodeError("""{"value":[1,2.0,3]}""", "expected a variant value at: .", codec) &&
        decodeError("""{"value":{"VVV":1}}""", "expected a variant value at: .", codec) &&
        decodeError("""{"value":}""", "expected a variant value at: .", codec)
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
    @Modifier.rename("::")
    case Node(
      @Modifier.rename("val") value: T,
      @Modifier.rename("nxt") next: LinkedList[T]
    )

    case End
  }

  sealed trait Foo derives Schema

  case object Foo1 extends Foo

  sealed trait Bar extends Foo

  case object Bar1 extends Bar

  case class Arrays(xs: IArray[String]) {
    override def hashCode(): Int = java.util.Arrays.hashCode(xs.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case that: Arrays => java.util.Arrays.equals(xs.asInstanceOf[Array[AnyRef]], that.xs.asInstanceOf[Array[AnyRef]])
      case _            => false
    }
  }

  object Arrays {
    implicit val schema: Schema[Arrays] = Schema.derived
  }
}
