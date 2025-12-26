package cloud.golem.sdk

import zio.blocks.schema.Schema

/**
 * Cross-platform UUID representation used by the Scala SDK.
 *
 * We intentionally avoid exposing Scala.js host facades (like `js.BigInt`) in
 * user-facing APIs. The runtime converts this to/from host literals as needed.
 */
final case class Uuid(highBits: BigInt, lowBits: BigInt)

object Uuid {
  implicit val schema: Schema[Uuid] = Schema.derived
}
