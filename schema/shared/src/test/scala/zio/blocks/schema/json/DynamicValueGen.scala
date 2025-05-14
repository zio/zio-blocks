package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue.{Primitive, Record, Sequence, Variant}
import zio.test.Gen

object DynamicValueGen {

  private def genPrimitiveValue: Gen[Any, PrimitiveValue] =
    Gen.oneOf(
      Gen.alphaNumericString.map(PrimitiveValue.String),
      Gen.int.map(PrimitiveValue.Int),
      Gen.boolean.map(PrimitiveValue.Boolean),
      Gen.byte.map(PrimitiveValue.Byte),
      Gen.boolean.map(PrimitiveValue.Boolean),
      Gen.double.map(PrimitiveValue.Double),
      Gen.float.map(PrimitiveValue.Float),
      Gen.long.map(PrimitiveValue.Long),
      Gen.short.map(PrimitiveValue.Short),
      Gen.char.map(PrimitiveValue.Char),
      Gen.bigInt(BigInt.apply(100), BigInt.apply(1000)).map(PrimitiveValue.BigInt),
      Gen.bigDecimal(BigDecimal.apply(100), BigDecimal.apply(1000)).map(PrimitiveValue.BigDecimal)
      // TODO: Add more here...
    )

  def genRecord: Gen[Any, Record] = Gen
    .listOfBounded(0, 10) {
      for {
        key   <- Gen.alphaNumericString
        value <- genPrimitiveValue
      } yield key -> Primitive(value)
    }
    .map(f => Record(f.toIndexedSeq))

  def genVariant: Gen[Any, Variant] = for {
    caseName <- Gen.alphaNumericString
    value    <- genPrimitiveValue
  } yield Variant(caseName, Primitive(value))

  def genSequence: Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 10)(genPrimitiveValue.map(Primitive(_)))
      .map(f => Sequence(f.toIndexedSeq))

  def genAlphaNumericSequence: Gen[Any, Sequence] =
    Gen
      .listOfBounded(0, 10)(
        Gen
          .oneOf(
            Gen.alphaNumericString.map(PrimitiveValue.String),
            Gen.int.map(PrimitiveValue.Int)
          )
          .map(Primitive(_))
      )
      .map(f => Sequence(f.toIndexedSeq))

  def genMap: Gen[Any, DynamicValue.Map] =
    Gen
      .listOfBounded(0, 10) {
        for {
          key   <- genPrimitiveValue.map(Primitive(_))
          value <- genPrimitiveValue.map(Primitive(_))
        } yield key -> value
      }
      .map(_.distinctBy(_._1.value))
      .map(list => DynamicValue.Map(list.toIndexedSeq))

  def genDynamicValue: Gen[Any, DynamicValue] =
    Gen.oneOf(
      genRecord,
      genVariant,
      genSequence,
      genMap
    )
}
