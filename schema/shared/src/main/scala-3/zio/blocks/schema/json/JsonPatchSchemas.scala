package zio.blocks.schema.json

import zio.blocks.schema.Schema

/**
 * Schema instances for JsonPatch inner types, enabling direct serialization.
 *
 * These schemas are derived using Scala 3 macros. Import this object to get
 * implicit Schema instances for [[JsonPatch.Op]], [[JsonPatch.PrimitiveOp]],
 * [[JsonPatch.StringOp]], [[JsonPatch.ArrayOp]], and [[JsonPatch.ObjectOp]].
 *
 * For full JsonPatch serialization, use [[JsonPatch.schema]] directly.
 */
object JsonPatchSchemas {

  /** Schema for [[JsonPatch.Op]] sealed trait. */
  implicit lazy val opSchema: Schema[JsonPatch.Op] = Schema.derived

  /** Schema for [[JsonPatch.PrimitiveOp]] sealed trait. */
  implicit lazy val primitiveOpSchema: Schema[JsonPatch.PrimitiveOp] = Schema.derived

  /** Schema for [[JsonPatch.StringOp]] sealed trait. */
  implicit lazy val stringOpSchema: Schema[JsonPatch.StringOp] = Schema.derived

  /** Schema for [[JsonPatch.ArrayOp]] sealed trait. */
  implicit lazy val arrayOpSchema: Schema[JsonPatch.ArrayOp] = Schema.derived

  /** Schema for [[JsonPatch.ObjectOp]] sealed trait. */
  implicit lazy val objectOpSchema: Schema[JsonPatch.ObjectOp] = Schema.derived
}
