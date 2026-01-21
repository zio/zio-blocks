package zio.blocks.schema

import org.bson.types.ObjectId
import zio.blocks.schema._

package object bson {

  implicit val objectIdSchema: Schema[ObjectId] = new Schema(
    Reflect.Wrapper(
      Schema.string.reflect,
      TypeName(Namespace(Seq("org", "bson", "types")), "ObjectId"),
      None,
      zio.blocks.schema.binding.Binding.Wrapper(
        (s: String) => Right(new ObjectId(s)),
        (o: ObjectId) => o.toHexString
      )
    )
  )
}
