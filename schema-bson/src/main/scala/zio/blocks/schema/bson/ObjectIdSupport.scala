package zio.blocks.schema.bson

import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.{Owner, TypeId}

/**
 * Provides Schema[ObjectId] support for BSON encoding/decoding.
 *
 * The ObjectId is represented as a wrapper around String (hex representation),
 * but when encoded to BSON, it uses the native BsonType.OBJECT_ID (12-byte
 * format) via special handling in BsonSchemaCodec.
 */
object ObjectIdSupport {

  /**
   * Schema for org.bson.types.ObjectId.
   *
   * Manually constructed with typeId for "ObjectId" so BsonSchemaCodec can
   * detect it and use zio-bson's native BsonCodec[ObjectId] (encodes as
   * BsonType.OBJECT_ID).
   */
  implicit val objectIdSchema: Schema[ObjectId] = new Schema(
    new Reflect.Wrapper[Binding, ObjectId, String](
      wrapped = Schema.string.reflect,
      typeId = TypeId.nominal[ObjectId]("ObjectId", Owner.fromPackagePath("org.bson.types")),
      wrapperBinding = new Binding.Wrapper[ObjectId, String](
        wrap = str => new ObjectId(str),
        unwrap = oid => oid.toHexString
      )
    )
  )
}
