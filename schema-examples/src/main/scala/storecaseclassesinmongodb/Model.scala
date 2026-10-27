package storecaseclassesinmongodb

import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.bson.ObjectIdSupport

/**
 * How to Store Case Classes in MongoDB — the ticket model shared by every
 * example in this folder.
 */
sealed trait Status
case object Open                              extends Status
final case class InProgress(assignee: String) extends Status
final case class Closed(resolution: String)   extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

final case class Comment(authorName: String, body: String)

object Comment {
  implicit val schema: Schema[Comment] = Schema.derived
}

final case class Ticket(
  @Modifier.rename("_id") id: ObjectId,
  title: String,
  status: Status,
  customerEmail: Option[String] = None,
  comments: List[Comment] = Nil
)

object Ticket {
  import ObjectIdSupport.objectIdSchema

  implicit val schema: Schema[Ticket] = Schema.derived
}
