package golem.data

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.test._

object DataInteropReflectionSpec extends ZIOSpecDefault {
  final case class Person(name: String, age: Int, tags: List[String], nickname: Option[String])
  implicit val personSchema: Schema[Person] = Schema.derived

  final case class Tuple2Like(_1: Int, _2: String)
  implicit val tuple2LikeSchema: Schema[Tuple2Like] = Schema.derived

  sealed trait Choice
  case object Yes extends Choice
  final case class No(reason: String) extends Choice
  implicit val choiceSchema: Schema[Choice] = Schema.derived

  object Maybe {
    sealed trait Value
    case object None extends Value
    final case class Some(payload: Int, label: String) extends Value
    implicit val schema: Schema[Value] = Schema.derived
  }

  sealed trait ValueChoice
  case object Empty extends ValueChoice
  final case class Value(value: Int) extends ValueChoice
  implicit val valueChoiceSchema: Schema[ValueChoice] = Schema.derived

  private def invokeDynamicToDataValue[A](schema: Schema[A], dynamic: DynamicValue): Either[Throwable, DataValue] = {
    val reflectArg = schema.reflect.asInstanceOf[AnyRef]
    val dynamicArg = dynamic.asInstanceOf[AnyRef]
    val method =
      DataInterop.getClass.getDeclaredMethods
        .find { m =>
          m.getName.contains("dynamicToDataValue") &&
          m.getParameterCount == 2 &&
          m.getParameterTypes.apply(0).isAssignableFrom(reflectArg.getClass) &&
          m.getParameterTypes.apply(1).isAssignableFrom(dynamicArg.getClass)
        }
        .getOrElse(throw new RuntimeException("dynamicToDataValue method not found"))
    method.setAccessible(true)
    scala.util.Try(method.invoke(DataInterop, reflectArg, dynamicArg).asInstanceOf[DataValue]).toEither
  }

  private def invokeSimpleCaseName(input: String): String = {
    val method =
      DataInterop.getClass.getDeclaredMethods.find(_.getName.contains("simpleCaseName")).getOrElse(
        throw new RuntimeException("simpleCaseName method not found")
      )
    method.setAccessible(true)
    method.invoke(DataInterop, input).asInstanceOf[String]
  }

  override def spec: Spec[TestEnvironment, Any] =
    suite("DataInteropReflectionSpec")(
      test("dynamicToDataValue rejects invalid dynamic shapes") {
        val intSchema     = Schema[Int]
        val optionSchema  = Schema[Option[Int]]
        val tupleSchema   = Schema[Tuple2Like]
        val recordSchema  = Schema[Person]
        val listSchema    = Schema[List[Int]]
        val mapSchema     = Schema[Map[String, Int]]
        val variantSchema = Schema[Choice]

        val attempts = List(
          invokeDynamicToDataValue(intSchema, DynamicValue.Record(Chunk.empty)),
          invokeDynamicToDataValue(optionSchema, DynamicValue.Primitive(PrimitiveValue.Int(1))),
          invokeDynamicToDataValue(tupleSchema, DynamicValue.Primitive(PrimitiveValue.Int(1))),
          invokeDynamicToDataValue(recordSchema, DynamicValue.Primitive(PrimitiveValue.Int(1))),
          invokeDynamicToDataValue(listSchema, DynamicValue.Record(Chunk.empty)),
          invokeDynamicToDataValue(mapSchema, DynamicValue.Sequence(Chunk.empty)),
          invokeDynamicToDataValue(variantSchema, DynamicValue.Record(Chunk.empty))
        )

        assertTrue(attempts.forall(_.isLeft))
      },
      test("dynamicToDataValue rejects non-string map keys") {
        val mapSchema = Schema[Map[String, Int]]
        val badMap =
          DynamicValue.Map(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)) ->
                DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )

        assertTrue(invokeDynamicToDataValue(mapSchema, badMap).isLeft)
      },
      test("dynamicToDataValue converts option variants") {
        val optionSchema = Schema[Option[Int]]
        val noneDyn      = DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
        val someDyn =
          DynamicValue.Variant(
            "Some",
            DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )

        assertTrue(invokeDynamicToDataValue(optionSchema, noneDyn) == Right(DataValue.OptionalValue(None))) &&
        assertTrue(
          invokeDynamicToDataValue(optionSchema, someDyn) ==
            Right(DataValue.OptionalValue(Some(DataValue.IntValue(1))))
        )
      },
      test("dynamicToDataValue rejects non-record Some payloads for Option") {
        val optionSchema = Schema[Option[Int]]
        val badSome      = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(invokeDynamicToDataValue(optionSchema, badSome).isLeft)
      },
      test("dynamicToDataValue converts custom option-like variants") {
        val maybeSchema = Schema[Maybe.Value]
        val someDyn =
          DynamicValue.Variant(
            "Some",
            DynamicValue.Record(
              Chunk(
                "payload" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
                "label"   -> DynamicValue.Primitive(PrimitiveValue.String("x"))
              )
            )
          )

        assertTrue(invokeDynamicToDataValue(maybeSchema, someDyn).isRight)
      },
      test("dynamicToDataValue validates tuple arity") {
        val tupleSchema = Schema[Tuple2Like]
        val badTuple =
          DynamicValue.Record(
            Chunk(
              "_1" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )

        assertTrue(invokeDynamicToDataValue(tupleSchema, badTuple).isLeft)
      },
      test("dynamicToDataValue handles non-record payload for value wrappers") {
        val schema = Schema[ValueChoice]
        val payload = DynamicValue.Variant("Value", DynamicValue.Primitive(PrimitiveValue.Int(9)))
        assertTrue(invokeDynamicToDataValue(schema, payload).isLeft)
      },
      test("dynamicToDataValue converts map entries") {
        val mapSchema = Schema[Map[String, Int]]
        val okMap =
          DynamicValue.Map(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.String("k")) ->
                DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )

        assertTrue(invokeDynamicToDataValue(mapSchema, okMap) == Right(DataValue.MapValue(Map("k" -> DataValue.IntValue(1)))))
      },
      test("dynamicToDataValue converts sequences to list/set values") {
        val listSchema = Schema[List[Int]]
        val setSchema  = Schema[Set[String]]
        val listDyn    = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val setDyn     = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.String("a"))))

        assertTrue(
          invokeDynamicToDataValue(listSchema, listDyn) == Right(DataValue.ListValue(List(DataValue.IntValue(1)))),
          invokeDynamicToDataValue(setSchema, setDyn) == Right(DataValue.SetValue(Set(DataValue.StringValue("a"))))
        )
      },
      test("dynamicToDataValue converts records and empty variants") {
        val personSchema = Schema[Person]
        val personDyn =
          DynamicValue.Record(
            Chunk(
              "name"     -> DynamicValue.Primitive(PrimitiveValue.String("x")),
              "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
              "tags"     -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.String("t")))),
              "nickname" -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
            )
          )

        val choiceSchema = Schema[Choice]
        val yesDyn       = DynamicValue.Variant("Yes", DynamicValue.Record(Chunk.empty))

        assertTrue(invokeDynamicToDataValue(personSchema, personDyn).isRight) &&
        assertTrue(invokeDynamicToDataValue(choiceSchema, yesDyn) == Right(DataValue.EnumValue("Yes", None)))
      },
      test("dynamicToDataValue rejects unknown variant cases") {
        val schema = Schema[Choice]
        val unknown = DynamicValue.Variant("Unknown", DynamicValue.Record(Chunk.empty))
        assertTrue(invokeDynamicToDataValue(schema, unknown).isLeft)
      },
      test("dynamicToDataValue falls back for dynamic schemas") {
        val schema = Schema[DynamicValue]
        val dv     = DynamicValue.Primitive(PrimitiveValue.String("x"))
        assertTrue(invokeDynamicToDataValue(schema, dv).isRight)
      },
      test("dynamicToDataValue rejects missing value fields in wrappers") {
        val schema = Schema[ValueChoice]
        val badPayload =
          DynamicValue.Variant(
            "Value",
            DynamicValue.Record(Chunk("other" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
          )
        assertTrue(invokeDynamicToDataValue(schema, badPayload).isLeft)
      },
      test("simpleCaseName strips prefixes and trailing $") {
        assertTrue(
          invokeSimpleCaseName("scala.None$") == "None",
          invokeSimpleCaseName("Some") == "Some",
          invokeSimpleCaseName("golem.data.Foo") == "Foo"
        )
      }
    )
}
