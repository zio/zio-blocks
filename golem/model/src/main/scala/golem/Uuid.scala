package golem

import zio.blocks.schema.Schema

/**
 * Cross-platform UUID representation used by the Scala SDK.
 */
final case class Uuid(highBits: BigInt, lowBits: BigInt)

object Uuid {
  implicit val schema: Schema[Uuid] = Schema.derived
}
