/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.blocks.schema.bson

import org.bson.{BsonDocument, BsonInt32, BsonReader, BsonString, BsonValue, BsonWriter}
import zio.blocks.schema._
import zio.blocks.typeid.TypeId
import zio.test._

object BsonCodecDeriverSpec extends SchemaBaseSpec {
  final case class Person(name: String, age: Int, score: Int)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    val age: Lens[Person, Int] = $(_.age)
  }

  sealed trait Event
  object Event {
    final case class Data(value: Int) extends Event
    implicit val schema: Schema[Event] = Schema.derived[Event]
  }

  final case class Meter(value: Int) extends AnyVal
  object Meter {
    implicit val schema: Schema[Meter] = Schema.derived[Meter]
  }

  final case class Nested(person: Person, event: Event, events: List[Event], lookup: Map[String, Int], meter: Meter)
  object Nested extends CompanionOptics[Nested] {
    implicit val schema: Schema[Nested] = Schema.derived[Nested]
    val person: Lens[Nested, Person] = $(_.person)
    val event: Lens[Nested, Event] = $(_.event)
    val events: Lens[Nested, List[Event]] = $(_.events)
    val lookup: Lens[Nested, Map[String, Int]] = $(_.lookup)
    val meter: Lens[Nested, Meter] = $(_.meter)
  }

  private val stringIntCodec: BsonCodec[Int] = BsonCodec.string.transform[Int](_.toInt)(_.toString)

  private def tracked[A](delegate: BsonCodec[A], hits: Array[Int]): BsonCodec[A] = BsonCodec(
    new BsonEncoder[A] {
      def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit = {
        hits(0) += 1
        delegate.encoder.encode(writer, value, ctx)
      }
      def toBsonValue(value: A): BsonValue = {
        hits(0) += 1
        delegate.encoder.toBsonValue(value)
      }
    },
    new BsonDecoder[A] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
        hits(0) += 1
        delegate.decoder.decodeUnsafe(reader, trace, ctx)
      }
      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A = {
        hits(0) += 1
        delegate.decoder.fromBsonValueUnsafe(value, trace, ctx)
      }
    }
  )

  def spec = suite("BsonCodecDeriverSpec")(
    test("direct derivation and compatibility facade are equivalent") {
      val value  = Person("Ada", 31, 99)
      val direct = Person.schema.derive(BsonCodecDeriver)
      val facade = BsonSchemaCodec.bsonCodec(Person.schema)
      val bson1  = direct.encoder.toBsonValue(value)
      val bson2  = facade.encoder.toBsonValue(value)
      assertTrue(
        bson1 == new BsonDocument("name", new BsonString("Ada"))
          .append("age", new BsonInt32(31))
          .append("score", new BsonInt32(99)),
        bson1 == bson2,
        direct.decoder.fromBsonValue(bson1) == Right(value),
        facade.decoder.fromBsonValue(bson2) == Right(value)
      )
    },
    test("configured direct derivation and facade are equivalent") {
      val value  = Event.Data(7): Event
      val config = BsonSchemaCodec.Config.withSumTypeHandling(
        BsonSchemaCodec.SumTypeHandling.DiscriminatorField("kind")
      )
      val direct = Event.schema.derive(BsonCodecDeriver.withSumTypeHandling(config.sumTypeHandling))
      val facade = BsonSchemaCodec.bsonCodec(Event.schema, config)
      val bson1  = direct.encoder.toBsonValue(value)
      val bson2  = facade.encoder.toBsonValue(value)
      assertTrue(
        bson1 == new BsonDocument("value", new BsonInt32(7)).append("kind", new BsonString("Data")),
        bson1 == bson2,
        direct.decoder.fromBsonValue(bson1) == Right(value),
        facade.decoder.fromBsonValue(bson2) == Right(value)
      )
    },
    test("type, optic, parent-term, modifier, and deriver-level overrides") {
      val value = Person("Ada", 31, 99)
      val byType = Person.schema.deriving(BsonCodecDeriver).instance(TypeId.int, stringIntCodec).derive
      val byOptic = Person.schema.deriving(BsonCodecDeriver).instance(Person.age, stringIntCodec).derive
      val byTerm = Person.schema
        .deriving(BsonCodecDeriver)
        .instance(Person.schema.reflect.typeId, "score", stringIntCodec)
        .derive
      val renamed = Person.schema
        .deriving(BsonCodecDeriver)
        .modifier(Person.schema.reflect.typeId, "age", Modifier.rename("years"))
        .derive
      val byDeriver = Person.schema.derive(BsonCodecDeriver.withInstance(stringIntCodec)(TypeId.int))

      val typeDoc    = byType.encoder.toBsonValue(value).asDocument()
      val opticDoc   = byOptic.encoder.toBsonValue(value).asDocument()
      val termDoc    = byTerm.encoder.toBsonValue(value).asDocument()
      val renamedDoc = renamed.encoder.toBsonValue(value).asDocument()
      assertTrue(
        typeDoc.get("age") == new BsonString("31"),
        typeDoc.get("score") == new BsonString("99"),
        opticDoc.get("age") == new BsonString("31"),
        opticDoc.get("score") == new BsonInt32(99),
        termDoc.get("age") == new BsonInt32(31),
        termDoc.get("score") == new BsonString("99"),
        renamedDoc == new BsonDocument("name", new BsonString("Ada"))
          .append("years", new BsonInt32(31))
          .append("score", new BsonInt32(99)),
        byDeriver.encoder.toBsonValue(value) == typeDoc,
        byType.decoder.fromBsonValue(typeDoc) == Right(value),
        byOptic.decoder.fromBsonValue(opticDoc) == Right(value),
        byTerm.decoder.fromBsonValue(termDoc) == Right(value),
        renamed.decoder.fromBsonValue(renamedDoc) == Right(value)
      )
    },
    test("unsupported schema nodes retain their previous errors") {
      val mapError = scala.util.Try(Schema[Map[Int, String]].derive(BsonCodecDeriver)).failed.toOption
      val dynamicError = scala.util.Try(Schema[DynamicValue].derive(BsonCodecDeriver)).failed.toOption
      assertTrue(
        mapError.exists(_.isInstanceOf[UnsupportedOperationException]),
        mapError.exists(_.getMessage.contains("Map with non-string keys not yet supported")),
        dynamicError.exists(_.isInstanceOf[UnsupportedOperationException]),
        dynamicError.exists(_.getMessage.contains("is not yet implemented"))
      )
    },
    test("shape and variant decoding failures remain errors") {
      val person = Person.schema.derive(BsonCodecDeriver)
      val list = Schema[List[Int]].derive(BsonCodecDeriver)
      val map = Schema[Map[String, Int]].derive(BsonCodecDeriver)
      val event = Event.schema.derive(BsonCodecDeriver)
      val empty = new BsonDocument()
      val unknown = new BsonDocument("Unknown", empty)
      assertTrue(
        person.decoder.fromBsonValue(new BsonString("bad")).isLeft,
        person.decoder.fromBsonValue(empty).isLeft,
        list.decoder.fromBsonValue(new BsonString("bad")).isLeft,
        map.decoder.fromBsonValue(new BsonString("bad")).isLeft,
        event.decoder.fromBsonValue(new BsonString("bad")).isLeft,
        event.decoder.fromBsonValue(empty).isLeft,
        event.decoder.fromBsonValue(unknown).isLeft
      )
    },
    test("structural child overrides are used") {
      val value = Nested(Person("Ada", 31, 99), Event.Data(0), List(Event.Data(1)), Map("a" -> 2), Meter(3))
      // Separate counters are used so every structural lane is independently asserted.
      val personHits = Array(0)
      val variantHits = Array(0)
      val sequenceHits = Array(0)
      val mapHits = Array(0)
      val wrapperHits = Array(0)
      val codec = Nested.schema
        .deriving(BsonCodecDeriver)
        .instance(Nested.person, tracked(Person.schema.derive(BsonCodecDeriver), personHits))
        .instance(Nested.event, tracked(Event.schema.derive(BsonCodecDeriver), variantHits))
        .instance(Nested.events, tracked(Schema[List[Event]].derive(BsonCodecDeriver), sequenceHits))
        .instance(Nested.lookup, tracked(Schema[Map[String, Int]].derive(BsonCodecDeriver), mapHits))
        .instance(Nested.meter, tracked(Meter.schema.derive(BsonCodecDeriver), wrapperHits))
        .derive
      val bson = codec.encoder.toBsonValue(value)
      val out  = codec.decoder.fromBsonValue(bson)
      assertTrue(
        out == Right(value),
        personHits(0) == 2,
        variantHits(0) == 2,
        sequenceHits(0) == 2,
        mapHits(0) == 2,
        wrapperHits(0) == 2
      )
    }
  )
}
