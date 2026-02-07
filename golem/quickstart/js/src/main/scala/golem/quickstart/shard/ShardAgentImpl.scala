package golem.quickstart.shard

import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.Future

@agentImplementation()
final class ShardAgentImpl(@unused private val tableName: String, private val shardId: Int) extends ShardAgent {
  private val table: mutable.HashMap[String, String] = mutable.HashMap.empty

  override def id(): Future[Int] = Future.successful(shardId)

  override def get(key: String): Future[Option[String]] =
    Future.successful(table.get(key))

  override def set(key: String, value: String): Future[Unit] =
    Future.successful {
      table.update(key, value)
      ()
    }
}
