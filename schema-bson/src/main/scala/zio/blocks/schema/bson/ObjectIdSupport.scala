package zio.blocks.schema.bson

import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

/**
 * Provides Schema[ObjectId] support for BSON encoding/decoding.
 *
 * The ObjectId is represented as a wrapper around String (hex representation),
 * but when encoded to BSON, it uses the native BsonType.OBJECT_ID (12-byte
 * format) via special handling in BsonSchemaCodec.
 *
 * Usage:
 * {{{
 *   import zio.blocks.schema.bson.ObjectIdSupport._
 *
 *   case class User(id: ObjectId, name: String)
 *   object User {
 *     implicit val schema: Schema[User] = Schema.derived
 *   }
 * }}}
 */
object ObjectIdSupport {

  /**
   * Schema for org.bson.types.ObjectId.
   *
   * Manually constructed with typename "ObjectId" so BsonSchemaCodec can detect
   * it and use zio-bson's native BsonCodec[ObjectId] (encodes as
   * BsonType.OBJECT_ID).
   */
  implicit val objectIdSchema: Schema[ObjectId] = new Schema(
    new Reflect.Wrapper[Binding, ObjectId, String](
      wrapped = Schema.string.reflect,
      typeName = TypeName(Namespace(List("org", "bson", "types")), "ObjectId"),
      wrapperPrimitiveType = None,
      wrapperBinding = new Binding.Wrapper[ObjectId, String](
        wrap = str => Right(new ObjectId(str)),
        unwrap = _.toHexString
      )
    )
  )
}
