/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.bson

import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.{Owner, TypeId}

/**
 * A schema for `org.bson.types.ObjectId`, which `Schema.derived` cannot derive
 * on its own. Import [[ObjectIdSupport.objectIdSchema]] where you derive the
 * schema of a type with an `ObjectId` field; a BSON codec then stores the field
 * as BSON `OBJECT_ID`, the type MongoDB uses for `_id`.
 *
 * The schema wraps the 24-character hex string of the id, so other formats,
 * such as JSON, write that string. A BSON codec decodes the field only from an
 * `OBJECT_ID`, not from the hex string.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
 */
object ObjectIdSupport {

  /**
   * The schema of `ObjectId`: a wrapper of its hex string whose type id is
   * `org.bson.types.ObjectId`. [[BsonCodecDeriver]] recognises that type id and
   * uses [[BsonCodec.objectId]] instead of the string. Wrapping a string that
   * is not a valid id throws `IllegalArgumentException`.
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
