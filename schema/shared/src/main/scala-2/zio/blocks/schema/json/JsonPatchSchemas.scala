package zio.blocks.schema.json

/**
 * Placeholder for JsonPatch inner type schemas on Scala 2.
 *
 * Schema.derived is a Scala 3 macro and not available on Scala 2.13. For Scala
 * 2 users, use [[JsonPatch.schema]] which roundtrips all inner types through
 * the DynamicPatch bridge.
 *
 * On Scala 3, import [[JsonPatchSchemas]] from scala-3 for direct Schema
 * instances of [[JsonPatch.Op]], [[JsonPatch.PrimitiveOp]], etc.
 */
object JsonPatchSchemas {
  // No schemas available on Scala 2 - use JsonPatch.schema for serialization.
}
