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

/*
 * A small in-memory `EventStore`, shared by the examples that feed events to an
 * engine or a rebuild directly.
 *
 * Events are kept in a list and also published on a hub, so a projection engine
 * can both replay history (`readFrom`) and follow new events (`subscribe`). It
 * is a stand-in for a real store, such as the SQLite one, and keeps nothing on
 * disk.
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.stream.ZStream

import java.time.Instant

final class DemoEventStore[E](
  hub: Hub[EventEnvelope[E]],
  buffer: Ref[List[EventEnvelope[E]]],
  counter: Ref[Long]
) extends EventStore[E] {
  def subscribe: Hub[EventEnvelope[E]]               = hub
  def append(entityId: String, event: E): Task[Long] =
    for {
      seq <- counter.updateAndGet(_ + 1)
      env  = EventEnvelope(seq, event.getClass.getSimpleName.stripSuffix("$"), event, Instant.now(), entityId)
      _   <- buffer.update(_ :+ env)
      _   <- hub.publish(env).unit
    } yield seq
  def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
    ZStream.unwrap {
      buffer.get.map { list =>
        val filtered = list.filter(_.seq > afterSeq).filter(e => tags.isEmpty || tags.contains(e.tag))
        ZStream.fromIterable(filtered)
      }
    }
  def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
    readFrom(0L, tags)
}
object DemoEventStore {
  def make[E]: ZIO[Any, Nothing, DemoEventStore[E]] =
    for {
      hub     <- Hub.unbounded[EventEnvelope[E]]
      buffer  <- Ref.make(List.empty[EventEnvelope[E]])
      counter <- Ref.make(0L)
    } yield new DemoEventStore[E](hub, buffer, counter)
}
