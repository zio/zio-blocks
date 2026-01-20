package zio.blocks.schema

import org.bson.types.ObjectId
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.Modifier
import scala.util.Try

package object bson {
  val ObjectIdTag: String                     = "$oid"
  private[bson] val ObjectIdConfigKey: String = "bson.objectId"

  implicit val ObjectIdSchema: Schema[ObjectId] = {
    val stringSchema = Schema[String]
    val typeName     = new TypeName[ObjectId](new Namespace(Seq("org", "bson", "types")), "ObjectId")
    val binding      = new Binding.Wrapper[ObjectId, String](
      value => Try(new ObjectId(value)).toEither.left.map(_.getMessage),
      _.toHexString
    )
    new Schema(
      new Reflect.Wrapper[Binding, ObjectId, String](
        stringSchema.reflect,
        typeName,
        None,
        binding
      )
    ).modifier(Modifier.config(ObjectIdConfigKey, "true"))
  }
}
