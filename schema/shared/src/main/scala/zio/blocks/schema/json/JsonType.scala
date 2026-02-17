package zio.blocks.schema.json

import zio.blocks.chunk.Chunk

/**
 * A sealed trait representing the type of a JSON value.
 *
 * This is used by [[MergeStrategy]] to make decisions about recursion without
 * needing access to the actual JSON values.
 *
 * Each case provides two type members:
 *   - `Type`: The corresponding [[Json]] subtype (e.g., `Json.Object`)
 *   - `Unwrap`: The underlying Scala type of the value (e.g.,
 *     `Chunk[(String, Json)]`)
 */
sealed trait JsonType extends (Json => Boolean) {

  /** The corresponding [[Json]] subtype for this JSON type. */
  type Type <: Json

  /** The underlying Scala type of the value contained in this JSON type. */
  type Unwrap

  /**
   * Returns the type index for ordering: Null=0, Boolean=1, Number=2, String=3,
   * Array=4, Object=5
   */
  def typeIndex: Int

  /** Returns true if the given JSON value is of this type. */
  override def apply(json: Json): Boolean = json.jsonType == this
}

object JsonType {
  case object Null extends JsonType {
    override final type Type   = Json.Null.type
    override final type Unwrap = Unit
    val typeIndex = 0
  }

  case object Boolean extends JsonType {
    override final type Type   = Json.Boolean
    override final type Unwrap = scala.Boolean
    val typeIndex = 1
  }

  case object Number extends JsonType {
    override final type Type   = Json.Number
    override final type Unwrap = BigDecimal
    val typeIndex = 2
  }

  case object String extends JsonType {
    override final type Type   = Json.String
    override final type Unwrap = java.lang.String
    val typeIndex = 3
  }

  case object Array extends JsonType {
    override final type Type   = Json.Array
    override final type Unwrap = Chunk[Json]
    val typeIndex = 4
  }

  case object Object extends JsonType {
    override final type Type   = Json.Object
    override final type Unwrap = Chunk[(java.lang.String, Json)]
    val typeIndex = 5
  }
}
