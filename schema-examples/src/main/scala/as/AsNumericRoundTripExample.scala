package as

import zio.blocks.schema.As
import util.ShowExpr.show

// As.derived pairs widening with narrowing automatically.
// Widening (Int → Long) is lossless and always returns Right.
// Narrowing (Long → Int) validates at runtime and returns Left on overflow.
object AsWideningAndNarrowingExample extends App {

  case class IntPrice(cents: Int)
  case class LongPrice(cents: Long)

  val priceAs = As.derived[IntPrice, LongPrice]

  // Forward (widening): always succeeds
  show(priceAs.into(IntPrice(4999)))

  // Reverse (narrowing): succeeds when value fits in Int
  show(priceAs.from(LongPrice(4999L)))

  // Reverse (narrowing): fails when value is out of Int range
  show(priceAs.from(LongPrice(Long.MaxValue)))
}

// A full round-trip (into then from) succeeds only when the value survives
// the narrowing step on the way back.
object AsRoundTripExample extends App {

  case class IntPrice(cents: Int)
  case class LongPrice(cents: Long)

  val priceAs = As.derived[IntPrice, LongPrice]

  // In-range: Int → Long → Int returns the original value
  show(priceAs.into(IntPrice(100)).flatMap(priceAs.from))

  // Out-of-range is not possible starting from Int — every Int fits in Long,
  // so the round-trip from IntPrice always succeeds
  show(priceAs.into(IntPrice(Int.MaxValue)).flatMap(priceAs.from))
}
