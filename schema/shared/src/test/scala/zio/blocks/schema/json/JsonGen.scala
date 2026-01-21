package zio.blocks.schema.json

import zio.test.Gen

object JsonGen {

  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genJsonBoolean: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))

  val genJsonNumber: Gen[Any, Json] = Gen.oneOf(
    Gen.int.map(i => Json.Number(BigDecimal(i).toString)),
    Gen.long.map(l => Json.Number(BigDecimal(l).toString)),
    Gen.double.map(d => Json.Number(BigDecimal(d).toString)),
    Gen.bigDecimal(BigDecimal(0), BigDecimal(1000000000)).map(bd => Json.Number(bd.toString))
  )

  val genJsonString: Gen[Any, Json] = Gen.alphaNumericStringBounded(0, 100).map(Json.String(_))

  def genJsonArray(maxDepth: Int): Gen[Any, Json] =
    Gen.listOfBounded(0, 5)(genJson(maxDepth - 1)).map(l => Json.Array(l.toVector))

  def genJsonObject(maxDepth: Int): Gen[Any, Json] =
    Gen
      .listOfBounded(0, 5)(
        Gen.alphaNumericStringBounded(1, 10).zip(genJson(maxDepth - 1))
      )
      .map(l => Json.Object(l.toVector))

  def genJson(maxDepth: Int = 2): Gen[Any, Json] =
    if (maxDepth <= 0) Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString)
    else
      Gen.oneOf(
        genJsonNull,
        genJsonBoolean,
        genJsonNumber,
        genJsonString,
        genJsonArray(maxDepth),
        genJsonObject(maxDepth)
      )
}
