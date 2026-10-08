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
 * A running [[ProjectionEngine]] wired entirely in memory, for tests and
 * examples.
 *
 * For every projection it creates an [[InMemoryProjectionStore]], and for every
 * distinct source name used by the projections it creates an in-memory event
 * store backed by a `Hub`. If no projection declares a source, a single
 * `"_default"` source is created. The engine is started when the `TestEngine`
 * is built and stopped when the enclosing `Scope` closes, so no database or
 * files are involved. This replaces the verbose `ProjectionEngine.makeWithStores`
 * wiring.
 *
 * Use [[append]] to feed events in and [[query]] to read the projected rows
 * back. Appending only publishes the event; the engine processes it
 * asynchronously, so a query issued straight after an append may not see the
 * result yet.
 *
 * Events are stored with the event class's simple name as their tag, and they
 * are kept in memory only.
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
 *
 * @param projections
 *   the projections this engine runs, in the order they were given
 * @param stores
 *   the in-memory store of each projection, keyed by projection name
 * @param engine
 *   the underlying engine that processes the events
 */
final class TestEngine private (
  val projections: List[Projection[_]],
  val stores: Map[String, ProjectionStore[_]],
  private val eventStores: Map[String, EventStore[_]],
  val engine: ProjectionEngine
) {

  /**
   * Appends an event for the given entity.
   *
   * With a single source the event goes to it and any failure is returned. With
   * several sources the event goes to every source whose binding has a handler
   * for the event's runtime class, or to all sources if none has. In the
   * several-sources case a failure while appending to one source is ignored.
   *
   * To pick the source yourself, use the overload that takes a source name.
   *
   * @param entityId
   *   the entity the event is recorded under, exposed to handlers as
   *   `ctx.entityId`
   * @param event
   *   the event to append
   */
  @scala.annotation.nowarn
  def append[E: Schema](entityId: String, event: E): Task[Unit] =
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

  /**
   * Appends an event to one named source.
   *
   * Fails with a `RuntimeException` listing the known sources if `source` is
   * not one of them.
   *
   * @param source
   *   the source name, as passed to `Projection.from`
   */
  @scala.annotation.nowarn
  def append[E: Schema](entityId: String, event: E, source: String): Task[Unit] =
    eventStores.get(source) match {
      case Some(es) => es.asInstanceOf[EventStore[E]].append(entityId, event).unit
      case None     => ZIO.fail(new RuntimeException(s"Unknown source '$source' known=${eventStores.keys.mkString(",")}"))
    }

  /**
   * Reads the row with the given ID from a projection, or `None` if there is
   * none.
   *
   * @param projection
   *   the projection to read from
   * @param id
   *   the entity ID of the row
   */
  def query[A](projection: Projection[A], id: String): Task[Option[A]] =
    engine.query(projection, id)

  /**
   * Like [[query]], but finds the projection by its name instead of by value.
   * The caller chooses the result type `A`, and it is not checked.
   */
  def queryByName[A](specName: String, id: String): Task[Option[A]] =
    engine.queryByName[A](specName, id)

  /** The underlying [[ProjectionEngine]], same as `engine`. */
  def underlying: ProjectionEngine = engine
}

object TestEngine {

  /**
   * Builds and starts a `TestEngine` for the given projections with the default
   * [[ProjectionEngineConfig]].
   *
   * The engine runs until the enclosing `Scope` closes.
   */
  def make(projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine] =
    makeWithConfig(ProjectionEngineConfig.default, projections: _*)

  /**
   * Builds and starts a `TestEngine` with a custom [[ProjectionEngineConfig]].
   *
   * The config's `ringCapacity` sets the size of each source's `Hub`. A failure
   * while creating a store is treated as a defect, not a typed error.
   */
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
                              hub     <- Hub.bounded[EventEnvelope[Any]](config.ringCapacity)
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
