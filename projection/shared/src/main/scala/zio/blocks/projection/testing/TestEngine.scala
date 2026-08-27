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

package zio.blocks.projection.testing

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema
import zio.stream.ZStream

import java.time.Instant

/**
 * Simple test engine that auto-creates an `InMemoryProjectionStore` per
 * projection and a `Hub` per distinct source. Provides `append` and `query`
 * without the verbose 4-arg `makeWithStores` wiring.
 *
 * {{{
 * val projection = Projection[UserProfile]("userProfiles")
 *   .from("users").routeToSelf
 *   .on[UserCreated].insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
 *
 * val program = ZIO.scoped {
 *   for {
 *     engine <- TestEngine.make(projection)
 *     _      <- engine.append("user-1", UserCreated("Alice", "a@b.com"))
 *     u1     <- engine.query(projection, "user-1")
 *   } yield u1
 * }
 * }}}
 */
final class TestEngine private (
  val projections: List[Projection[_]],
  val stores: Map[String, ProjectionStore[_]],
  private val eventStores: Map[String, EventStore[_]],
  val engine: ProjectionEngine
) {

  /**
   * Append an event for the given entity. If the engine has multiple sources it
   * tries to route the event to the source whose binding has a handler for the
   * event's runtime class; otherwise it broadcasts to all sources.
   */
  def append[E: Schema](entityId: String, event: E): Task[Unit] = {
    val _ = summon[Schema[E]]
    if (eventStores.size == 1) {
      eventStores.values.head.asInstanceOf[EventStore[E]].append(entityId, event).unit
    } else {
      val matchingSources = projections.flatMap { p =>
        p.bindings.filter(b => b.handlers.exists(_.eventClass.isInstance(event))).map(_.sourceName)
      }.distinct
      val targets =
        if (matchingSources.nonEmpty) matchingSources.flatMap(eventStores.get)
        else eventStores.values.toList
      ZIO.foreachDiscard(targets) { es =>
        es.asInstanceOf[EventStore[E]].append(entityId, event).unit.catchAll(_ => ZIO.unit)
      }
    }
  }

  /** Append to a specific source hub. */
  def append[E: Schema](entityId: String, event: E, source: String): Task[Unit] = {
    val _ = summon[Schema[E]]
    eventStores.get(source) match {
      case Some(es) => es.asInstanceOf[EventStore[E]].append(entityId, event).unit
      case None     => ZIO.fail(new RuntimeException(s"Unknown source '$source' known=${eventStores.keys.mkString(",")}"))
    }
  }

  def query[A](projection: Projection[A], id: String): Task[Option[A]] =
    engine.query(projection, id)

  def queryByName[A](specName: String, id: String): Task[Option[A]] =
    engine.queryByName[A](specName, id)

  def underlying: ProjectionEngine = engine
}

object TestEngine {

  def make(projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine] =
    makeWithConfig(ProjectionEngineConfig.default, projections: _*)

  def makeWithConfig(
    config: ProjectionEngineConfig,
    projections: Projection[_]*
  ): ZIO[Scope, Nothing, TestEngine] =
    for {
      cache     <- TransactorCache.make()
      storesMap <- ZIO
                     .foreach(projections.toList) { specAny =>
                       val spec = specAny.asInstanceOf[Projection[Any]]
                       // Build InMemory store using spec's schema and entityPath
                       val task: Task[ProjectionStore[_]] = {
                         implicit val schema: Schema[Any] = spec.schema.asInstanceOf[Schema[Any]]
                         implicit val ep: EntityPath[Any] =
                           spec.entityPath
                             .map(_.asInstanceOf[EntityPath[Any]])
                             .getOrElse {
                               // For global projections derive from schema to get correct id field
                               try EntityPath.derived[Any]
                               catch { case _: Throwable => EntityPath[Any](spec.name, "id") }
                             }
                         InMemoryProjectionStore.make[Any].map(_.asInstanceOf[ProjectionStore[_]])
                       }
                       task.map(store => spec.name -> store)
                     }
                     .map(_.toMap)
                     .orDie
      distinctSources = {
        val names = projections.toList.flatMap(_.sourceNames).distinct
        if (names.isEmpty) List("_default") else names
      }
      eventStoresMap <- ZIO
                          .foreach(distinctSources) { src =>
                            for {
                              hub     <- Hub.unbounded[EventEnvelope[Any]]
                              buffer  <- Ref.make(List.empty[EventEnvelope[Any]])
                              counter <- Ref.make(0L)
                              store    = new InMemEventStore[Any](hub, buffer, counter)
                            } yield src -> (store.asInstanceOf[EventStore[_]])
                          }
                          .map(_.toMap)
      engine <- ProjectionEngine.makeWithStores(
                  projections.toList,
                  storesMap,
                  eventStoresMap,
                  cache,
                  config
                )
      _ <- engine.start
    } yield new TestEngine(projections.toList, storesMap, eventStoresMap, engine)

  /**
   * Alias so `TestEngine(proj)` works inside a `ZIO.scoped` for-comprehension.
   */
  def apply(projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine] =
    make(projections: _*)

  // ---------------------------------------------------------------------------
  // Internal in-memory EventStore used per source
  // ---------------------------------------------------------------------------

  private final class InMemEventStore[E](
    hub: Hub[EventEnvelope[E]],
    buffer: Ref[List[EventEnvelope[E]]],
    counter: Ref[Long]
  ) extends EventStore[E] {

    def subscribe: Hub[EventEnvelope[E]] = hub

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
}
