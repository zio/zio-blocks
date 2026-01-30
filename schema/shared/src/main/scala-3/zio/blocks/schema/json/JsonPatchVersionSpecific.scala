package zio.blocks.schema.json

import zio.blocks.schema.Schema

/** Scala 3 version provides Schema.derived for Op. */
private[json] trait OpVersionSpecific {
  implicit lazy val schema: Schema[JsonPatch.Op] = Schema.derived
}

/** Scala 3 version provides Schema.derived for PrimitiveOp. */
private[json] trait PrimitiveOpVersionSpecific {
  implicit lazy val schema: Schema[JsonPatch.PrimitiveOp] = Schema.derived
}

/** Scala 3 version provides Schema.derived for StringOp. */
private[json] trait StringOpVersionSpecific {
  implicit lazy val schema: Schema[JsonPatch.StringOp] = Schema.derived
}

/** Scala 3 version provides Schema.derived for ArrayOp. */
private[json] trait ArrayOpVersionSpecific {
  implicit lazy val schema: Schema[JsonPatch.ArrayOp] = Schema.derived
}

/** Scala 3 version provides Schema.derived for ObjectOp. */
private[json] trait ObjectOpVersionSpecific {
  implicit lazy val schema: Schema[JsonPatch.ObjectOp] = Schema.derived
}
