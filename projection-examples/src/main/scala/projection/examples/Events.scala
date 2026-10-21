/*
 * Events shared by the projection examples.
 *
 * `UserCreated` is appended to the "users" source and `RepoCreated` to the
 * "repos" source. Each event type needs a `Schema` so the event store can encode
 * it and the projection can recognise it.
 */

package projection.examples

import zio.blocks.schema.Schema

case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}
