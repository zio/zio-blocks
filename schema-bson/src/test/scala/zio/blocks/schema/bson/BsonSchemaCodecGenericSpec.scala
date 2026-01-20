package zio.blocks.schema.bson

import zio.bson.BsonBuilder._
import zio.bson.BsonCodec
import zio.blocks.schema.Schema
import zio.blocks.schema.bson.BsonSchemaCodecSpec.roundTripTest
import zio.Scope
import zio.test._

object BsonSchemaCodecGenericSpec extends zio.blocks.schema.SchemaBaseSpec {
  case class SimpleGeneric[T](value: T)

  object SimpleGeneric {
    implicit def schema[T: Schema]: Schema[SimpleGeneric[T]]   = Schema.derived
    implicit def codec[T: Schema]: BsonCodec[SimpleGeneric[T]] = BsonSchemaCodec.bsonCodec(schema)
  }

  sealed trait GenericTree[T]

  object GenericTree {
    case class Branch[T](left: GenericTree[T], right: GenericTree[T]) extends GenericTree[T]

    object Branch {
      implicit def schema[T: Schema]: Schema[Branch[T]] = Schema.derived
    }

    case class Leaf[T](value: T) extends GenericTree[T]

    object Leaf {
      implicit def schema[T: Schema]: Schema[Leaf[T]] = Schema.derived
    }

    implicit def schema[T: Schema]: Schema[GenericTree[T]]   = Schema.derived
    implicit def codec[T: Schema]: BsonCodec[GenericTree[T]] = BsonSchemaCodec.bsonCodec(schema)

    private def genLeafOf[R, A](gen: Gen[R, A]) = gen.map(Leaf(_))

    def genOf[R, A](gen: Gen[R, A]): Gen[R, GenericTree[A]] = Gen.sized { i =>
      if (i >= 2) Gen.oneOf(genLeafOf(gen), Gen.suspend(genOf(gen).zipWith(genOf(gen))(Branch(_, _))).resize(i / 2))
      else genLeafOf(gen)
    }
  }

  case class GenericRec[T](t: T, next: Option[GenericRec[T]])

  object GenericRec {
    implicit def schema[T: Schema]: Schema[GenericRec[T]]   = Schema.derived
    implicit def codec[T: Schema]: BsonCodec[GenericRec[T]] = BsonSchemaCodec.bsonCodec(schema)
  }

  def spec: Spec[TestEnvironment with Scope with Sized, Any] = suite("BsonSchemaCodecGenericSpec")(
    suite("round trip")(
      roundTripTest("SimpleGeneric[String]")(
        for {
          value <- Gen.string
        } yield SimpleGeneric(value),
        SimpleGeneric("str"),
        doc("value" -> str("str"))
      ),
      roundTripTest("GenericRec[Int]")(
        for {
          t    <- Gen.int
          next <- Gen.option(
                    Gen.int.map { value =>
                      GenericRec(value, None)
                    }
                  )
        } yield GenericRec(t, next),
        GenericRec(1, Some(GenericRec(2, None))),
        doc("t" -> int(1), "next" -> doc("t" -> int(2)))
      ),
      roundTripTest("GenericTree[Int]")(
        GenericTree.genOf(Gen.int),
        GenericTree.Leaf(1),
        doc("Leaf" -> doc("value" -> int(1)))
      )
    )
  )
}
