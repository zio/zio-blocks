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

package zio.blocks.projection

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

import scala.reflect.ClassTag

// ---------------------------------------------------------------------------
// RoutingMode
// ---------------------------------------------------------------------------

/**
 * Decides which row or shard of a projection an incoming event is applied to.
 *
 * A routing mode belongs to a [[SourceBinding]] and is chosen on
 * [[FromBuilder]] with `routeToSelf`, `routeToAll` or `routedBy`. The mode
 * produces a ''routing key'' for each event (see `Projection.routingKey`), and
 * it also determines the projection's [[ProjectionScope]].
 */
sealed trait RoutingMode
object RoutingMode {

  /**
   * Applies the event to the entity it was recorded under, that is
   * `ctx.entityId`. This is the default when a handler is added without a
   * source.
   */
  case object RouteToSelf extends RoutingMode

  /**
   * Applies every event to the same store, whichever entity it belongs to. It
   * produces no routing key. Use it for totals and other global aggregates,
   * together with `Projection.global`.
   */
  case object RouteToAll extends RoutingMode

  /**
   * Applies the event to the group named by a key read from the event itself,
   * for example the owner of a repository.
   *
   * @param extractor
   *   reads the routing key from the event
   */
  final case class RoutedBy[E](extractor: E => String) extends RoutingMode {

    /**
     * Runs the extractor on an event whose type is only known as `Any`. The
     * cast is unchecked, so an event of the wrong type fails at runtime.
     */
    def extractAny(value: Any): String = extractor.asInstanceOf[Any => String](value)
  }
}

// ---------------------------------------------------------------------------
// ProjectionScope
// ---------------------------------------------------------------------------

/**
 * How a [[Projection]]'s data is split up. It is never set directly: see
 * `Projection.scope` for how it is derived from the way the projection was
 * built. The engine uses it to choose the store an event goes to and the file
 * the store lives in.
 */
sealed trait ProjectionScope
object ProjectionScope {

  /** One row per entity ID. This is the default. */
  case object PerEntity extends ProjectionScope

  /**
   * One shared store for the whole projection, kept in its own `global/` folder.
   * Used for totals and counters.
   */
  case object Global extends ProjectionScope

  /**
   * Events are grouped into shards by a key read from the event, so one shard
   * can hold data from many entities.
   *
   * @param routedBy
   *   reads the shard key from an event
   */
  final case class CrossEntity(routedBy: Any => String) extends ProjectionScope
}

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

/**
 * Says what to do when an event of type `E` arrives: a function from the event
 * and its [[ProjectionContext]] to a [[ProjectionAction]] on the projected type
 * `A`.
 *
 * Handlers are created by [[HandlerBuilder]], not by hand. They contain no
 * effects: a handler only describes the change, and the engine applies it.
 *
 * @param eventClass
 *   the runtime class used to recognise events this handler is for
 * @param schema
 *   the schema of the event type
 * @param tag
 *   the name of the event type, used in messages
 * @param fn
 *   turns an event into the action to apply
 */
final case class Handler[E, A](
  eventClass: Class[E],
  schema: Schema[E],
  tag: String,
  fn: (E, ProjectionContext) => ProjectionAction[A]
) {

  /**
   * Runs the handler on an event whose type is only known as `Any`. The cast is
   * unchecked, so call [[matches]] first.
   */
  def runAny(event: Any, ctx: ProjectionContext): ProjectionAction[A] =
    fn(event.asInstanceOf[E], ctx)

  /** Whether `event` is an instance of this handler's event class. */
  def matches(event: Any): Boolean = eventClass.isInstance(event)
}

// ---------------------------------------------------------------------------
// SourceBinding
// ---------------------------------------------------------------------------

/**
 * The rules a [[Projection]] applies to one named event source: how its events
 * are routed, and which handlers react to them.
 *
 * A binding is added by `from(sourceName)` followed by a routing call, and each
 * `on[E]` handler is then attached to the most recently added binding. The type
 * parameter `E` is not used by any field.
 *
 * @param sourceName
 *   the name of the event source, as given to `Projection.from`
 * @param routing
 *   how events from this source are routed
 * @param handlers
 *   the handlers for this source, tried in the order they were added
 */
final case class SourceBinding[E, A](
  sourceName: String,
  routing: RoutingMode,
  handlers: List[Handler[?, A]]
)

// ---------------------------------------------------------------------------
// Projection
// ---------------------------------------------------------------------------

/**
 * A declarative description of a read model `A` built from events: which
 * sources to listen to, how to route their events, and what each kind of event
 * does to `A`.
 *
 * A `Projection` is immutable data and does nothing by itself. Every builder
 * call returns a new `Projection`, and an engine (such as `ProjectionEngine`)
 * later reads it to process events. Handlers only describe changes as
 * [[ProjectionAction]]s, so they cannot perform effects.
 *
 * Create one with `Projection[A](name)` (one row per entity) or
 * `Projection.global[A](name)` (shared totals), then chain `from`, a routing
 * call, and `on`:
 *
 * {{{
 * val greetings = Projection[Greeting]("greetings")
 *   .from("people")
 *   .routeToSelf
 *   .on[PersonGreeted]
 *   .insert((e, ctx) => Greeting(ctx.entityId, s"Hello, ${e.name}!"))
 * }}}
 *
 * Further `from(...)` calls add more sources, and further `on[...]` calls add
 * more handlers to the latest source. An `on[...]` with no `from(...)` before it
 * creates a default source named `"_default"` that routes to the entity itself.
 *
 * Call [[validate]] before starting an engine to catch an empty projection.
 *
 * @param name
 *   the projection's name, which also names its store file
 * @param schema
 *   the schema of the projected type `A`
 * @param entityPath
 *   where the store lives and which field of `A` is its ID. It is `None` for
 *   global projections
 * @param isGlobal
 *   whether this is a global projection
 * @param bindings
 *   the sources with their routing and handlers, in the order they were added
 */
final class Projection[A] private[projection] (
  val name: String,
  val schema: Schema[A],
  val entityPath: Option[EntityPath[A]],
  val isGlobal: Boolean,
  val bindings: List[SourceBinding[?, A]]
) {

  /**
   * Starts a new source binding. Follow it with `routeToSelf`, `routeToAll`,
   * `routedBy` or `routedByField` to choose how the source's events are routed.
   *
   * @param sourceName
   *   the name of the event source
   */
  def from(sourceName: String): FromBuilder[A] =
    new FromBuilder[A](this, sourceName)

  /**
   * Starts a handler for events of type `E` and attaches it to the most
   * recently added source. Follow it with `insert`, `delete`, `aggregate` or
   * another [[HandlerBuilder]] method to say what the event does.
   *
   * If no source has been added yet, a source named `"_default"` that routes to
   * the entity itself is created first.
   */
  def on[E: Schema](using ct: ClassTag[E]): HandlerBuilder[E, A] =
    new HandlerBuilder[E, A](
      this,
      summon[Schema[E]],
      ct.runtimeClass.asInstanceOf[Class[E]]
    )

  private[projection] def addBinding(binding: SourceBinding[?, A]): Projection[A] =
    new Projection[A](name, schema, entityPath, isGlobal, bindings :+ binding)

  private[projection] def addHandler[E](handler: Handler[E, A]): Projection[A] =
    if (bindings.isEmpty) {
      val default = SourceBinding[Any, A](
        "_default",
        RoutingMode.RouteToSelf,
        List(handler.asInstanceOf[Handler[?, A]])
      )
      new Projection[A](name, schema, entityPath, isGlobal, List(default))
    } else {
      val last    = bindings.last
      val updated = last.copy(handlers = last.handlers :+ handler.asInstanceOf[Handler[?, A]])
      new Projection[A](name, schema, entityPath, isGlobal, bindings.init :+ updated)
    }

  /**
   * The projection's [[ProjectionScope]], derived from how it was built:
   *   - [[ProjectionScope.Global]] if it was created with `Projection.global`
   *   - otherwise [[ProjectionScope.CrossEntity]] if any source uses `routedBy`,
   *     taking the key from the first such source
   *   - otherwise [[ProjectionScope.PerEntity]]
   *
   * Note that `routeToAll` alone does not make a projection global.
   */
  def scope: ProjectionScope =
    if (isGlobal) ProjectionScope.Global
    else
      bindings.collectFirst { case sb if sb.routing.isInstanceOf[RoutingMode.RoutedBy[?]] => sb.routing } match {
        case Some(rb: RoutingMode.RoutedBy[?]) =>
          val extractor = rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String]
          ProjectionScope.CrossEntity(extractor)
        case _ => ProjectionScope.PerEntity
      }

  /** All handlers of all sources, in binding order. */
  def allHandlers: List[Handler[?, A]] = bindings.flatMap(_.handlers)

  /** The names of the sources this projection listens to. */
  def sourceNames: List[String] = bindings.map(_.sourceName)

  /** The source bindings, same as `bindings`. */
  def sources: List[SourceBinding[?, A]] = bindings

  /**
   * Finds the handler for `event` and runs it, returning the action to apply, or
   * `None` if no handler matches the event's class.
   *
   * When `source` is given, only the handlers of the first binding with that
   * name are considered. Otherwise all handlers are searched in binding order,
   * and the first match wins, even across sources. Pass `source` when the
   * projection has several sources, so that an event is not handled by another
   * source's handler.
   *
   * @param event
   *   the event to handle
   * @param ctx
   *   the metadata of the event
   * @param source
   *   restricts the search to this source
   */
  def handle(event: Any, ctx: ProjectionContext, source: Option[String] = None): Option[ProjectionAction[A]] =
    source match {
      case Some(src) =>
        bindings
          .find(_.sourceName == src)
          .flatMap(_.handlers.find(_.eventClass.isInstance(event)))
          .map(_.runAny(event, ctx))
      case None =>
        allHandlers.find(_.eventClass.isInstance(event)).map(_.runAny(event, ctx))
    }

  /** Same as [[handle]]. Kept for binary compatibility. */
  def dispatch(event: Any, ctx: ProjectionContext, source: Option[String] = None): Option[ProjectionAction[A]] =
    handle(event, ctx, source)

  /**
   * The routing key of `event` for the named source: `ctx.entityId` for
   * `routeToSelf`, the extracted key for `routedBy`, and `None` for `routeToAll`
   * or when there is no source with that name.
   */
  def routingKey(event: Any, ctx: ProjectionContext, sourceName: String): Option[String] =
    bindings.find(_.sourceName == sourceName).flatMap { b =>
      b.routing match {
        case RoutingMode.RouteToSelf     => Some(ctx.entityId)
        case RoutingMode.RouteToAll      => None
        case rb: RoutingMode.RoutedBy[?] =>
          Some(rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String](event))
      }
    }

  /**
   * The routing key of `event` using the most recently added source, or
   * `ctx.entityId` if there are no sources. The result follows the same rules as
   * [[routingKey]].
   */
  def routingKeyForDefault(event: Any, ctx: ProjectionContext): Option[String] =
    if (bindings.isEmpty) Some(ctx.entityId)
    else
      bindings.last.routing match {
        case RoutingMode.RouteToSelf     => Some(ctx.entityId)
        case RoutingMode.RouteToAll      => None
        case rb: RoutingMode.RoutedBy[?] =>
          Some(rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String](event))
      }

  /**
   * Checks the projection for problems that would stop it from doing anything,
   * and returns a message for each. The list is empty if none are found.
   *
   * It reports a projection with no sources, one with no handlers, a source with
   * an empty name, and a handler with a null event class.
   */
  def validate(): List[String] = {
    val warnings = scala.collection.mutable.ListBuffer.empty[String]
    if (bindings.isEmpty) warnings += s"Projection $name has no bindings"
    if (allHandlers.isEmpty) warnings += s"Projection $name has no handlers"
    bindings.foreach { b =>
      if (b.sourceName.isEmpty) warnings += s"Projection $name has binding with empty sourceName"
      b.handlers.foreach { h =>
        if (h.eventClass == null) warnings += s"Handler for ${h.tag} has null class"
      }
    }
    warnings.toList
  }

  override def toString: String =
    s"Projection(name=$name, isGlobal=$isGlobal, bindings=${bindings.size}, handlers=${allHandlers.size})"
}

object Projection {

  /**
   * Creates a per-entity projection. The `EntityPath` says where its store lives
   * and which field of `A` is the ID.
   *
   * @param name
   *   the projection's name
   */
  def apply[A: Schema: EntityPath](name: String): Projection[A] =
    new Projection[A](name, summon[Schema[A]], Some(summon[EntityPath[A]]), isGlobal = false, Nil)

  /**
   * Creates a global projection, whose events all go to one shared store
   * (`global/<name>.db`). Use it with `routeToAll` for totals and counters. It
   * needs no `EntityPath`.
   *
   * @param name
   *   the projection's name
   */
  def global[A: Schema](name: String): Projection[A] =
    new Projection[A](name, summon[Schema[A]], None, isGlobal = true, Nil)

  /**
   * Like `apply(name)`, but takes the `EntityPath` explicitly instead of finding
   * it implicitly.
   */
  def apply[A](name: String, entityPath: EntityPath[A])(using schema: Schema[A]): Projection[A] =
    new Projection[A](name, schema, Some(entityPath), isGlobal = false, Nil)
}

// ---------------------------------------------------------------------------
// HandlerBuilder
// ---------------------------------------------------------------------------

/**
 * Says what an event of type `E` does to the projected type `A`. It is returned
 * by `Projection.on` and is finished by calling one of its methods, each of
 * which returns the [[Projection]] with the new handler added.
 *
 * The methods fall into three groups:
 *   - whole rows: `insert`, `delete`
 *   - field changes: `aggregate`, `update`, `updateWithField`, `updateField`
 *   - anything else: `custom`
 */
final class HandlerBuilder[E, A] private[projection] (
  spec: Projection[A],
  schemaE: Schema[E],
  eventClass: Class[E]
) {

  private def tag: String =
    try schemaE.reflect.typeId.name
    catch { case _: Throwable => eventClass.getSimpleName.stripSuffix("$") }

  private def add(fn: (E, ProjectionContext) => ProjectionAction[A]): Projection[A] =
    spec.addHandler(Handler[E, A](eventClass, schemaE, tag, fn))

  /** Inserts the row that `f` builds from the event and its context. */
  def insert(f: (E, ProjectionContext) => A): Projection[A] =
    add((e, ctx) => ProjectionAction.Insert(f(e, ctx)))

  /** Deletes the entity's row. */
  def delete: Projection[A] =
    add((_, _) => ProjectionAction.Delete)

  /**
   * Uses any [[ProjectionAction]] that `f` returns, for cases the other methods
   * do not cover, such as `Upsert` or `Noop`.
   */
  def custom(f: (E, ProjectionContext) => ProjectionAction[A]): Projection[A] =
    add(f)

  /**
   * Applies the same [[FieldUpdate]] every time this event arrives, whatever the
   * event contains. Typically used for counters on a global projection.
   */
  def aggregate(update: FieldUpdate): Projection[A] =
    add((_, _) => ProjectionAction.Update(Chunk(update)))

  /** Like the single-update `aggregate`, applying several updates at once. */
  def aggregate(updates: Chunk[FieldUpdate]): Projection[A] =
    add((_, _) => ProjectionAction.Update(updates))

  /** Same as `aggregate(update)`. */
  def aggregateField(update: FieldUpdate): Projection[A] = aggregate(update)

  /**
   * Sets the named column to the value that `f` computes from the event. The
   * name is used exactly as written, so it must already be the column name.
   */
  def updateWithField(fieldName: String, f: (E, ProjectionContext) => Any): Projection[A] =
    add((e, ctx) => ProjectionAction.Update(Chunk(FieldUpdate.Set(fieldName, f(e, ctx)))))

  /**
   * Sets one field to the value that `f` computes from the event. The field is
   * picked with a selector such as `_.email`, checked at compile time, and
   * mapped to its `snake_case` column name.
   */
  inline def update(inline selector: A => Any)(f: (E, ProjectionContext) => Any): Projection[A] =
    ${ ProjectionMacros.updateImpl[A, E]('this, 'selector, 'f) }

  /** Same as `updateWithField(fieldName, f)`. */
  def updateField(fieldName: String)(f: (E, ProjectionContext) => Any): Projection[A] =
    updateWithField(fieldName, f)
}

// ---------------------------------------------------------------------------
// FromBuilder
// ---------------------------------------------------------------------------

/**
 * Chooses how the events of one named source are routed. It is returned by
 * `Projection.from`, and each method returns the [[Projection]] with a new
 * [[SourceBinding]] added. Handlers are attached afterwards with `on`.
 */
final class FromBuilder[A] private[projection] (
  spec: Projection[A],
  sourceName: String
) {

  /**
   * Routes each event by a key that `selector` reads from it, so that events
   * with the same key are applied to the same group. This makes the projection
   * [[ProjectionScope.CrossEntity]].
   */
  def routedBy[E](selector: E => String): Projection[A] =
    spec.addBinding(
      SourceBinding[Any, A](sourceName, RoutingMode.RoutedBy(selector.asInstanceOf[Any => String]), Nil)
    )

  /**
   * Like `routedBy`, but picks the key with a field selector such as
   * `_.ownerId`, checked at compile time. The field's value is turned into the
   * key with `toString`.
   */
  inline def routedByField[E](inline selector: E => Any): Projection[A] =
    ${ ProjectionMacros.routedByFieldImpl[A, E]('this, 'selector) }

  /** Applies every event from this source to the same store. */
  def routeToAll: Projection[A] =
    spec.addBinding(SourceBinding[Any, A](sourceName, RoutingMode.RouteToAll, Nil))

  /** Applies each event to the entity it was recorded under (`ctx.entityId`). */
  def routeToSelf: Projection[A] =
    spec.addBinding(SourceBinding[Any, A](sourceName, RoutingMode.RouteToSelf, Nil))
}
