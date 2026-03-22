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
