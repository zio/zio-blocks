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

sealed trait RoutingMode
object RoutingMode {
  case object RouteToSelf                              extends RoutingMode
  case object RouteToAll                               extends RoutingMode
  final case class RoutedBy[E](extractor: E => String) extends RoutingMode {
    def extractAny(value: Any): String = extractor.asInstanceOf[Any => String](value)
  }
}

// ---------------------------------------------------------------------------
// ProjectionScope
// ---------------------------------------------------------------------------

sealed trait ProjectionScope
object ProjectionScope {
  case object PerEntity                                 extends ProjectionScope
  case object Global                                    extends ProjectionScope
  final case class CrossEntity(routedBy: Any => String) extends ProjectionScope
}

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

final case class Handler[E, A](
  eventClass: Class[E],
  schema: Schema[E],
  tag: String,
  fn: (E, ProjectionContext) => ProjectionAction[A]
) {
  def runAny(event: Any, ctx: ProjectionContext): ProjectionAction[A] =
    fn(event.asInstanceOf[E], ctx)
  def matches(event: Any): Boolean = eventClass.isInstance(event)
}

// ---------------------------------------------------------------------------
// SourceBinding
// ---------------------------------------------------------------------------

final case class SourceBinding[E, A](
  sourceName: String,
  routing: RoutingMode,
  handlers: List[Handler[?, A]]
)

// ---------------------------------------------------------------------------
// ProjectionSpec
// ---------------------------------------------------------------------------

final class ProjectionSpec[A] private[projection] (
  val name: String,
  val schema: Schema[A],
  val entityPath: Option[EntityPath[A]],
  val isGlobal: Boolean,
  val bindings: List[SourceBinding[?, A]]
) {

  def from(sourceName: String): FromBuilder[A] =
    new FromBuilder[A](this, sourceName)

  def on[E: Schema](using ct: ClassTag[E]): HandlerBuilder[E, A] =
    new HandlerBuilder[E, A](
      this,
      summon[Schema[E]],
      ct.runtimeClass.asInstanceOf[Class[E]]
    )

  private[projection] def addBinding(binding: SourceBinding[?, A]): ProjectionSpec[A] =
    new ProjectionSpec[A](name, schema, entityPath, isGlobal, bindings :+ binding)

  private[projection] def addHandler[E](handler: Handler[E, A]): ProjectionSpec[A] =
    if (bindings.isEmpty) {
      val default = SourceBinding[Any, A](
        "_default",
        RoutingMode.RouteToSelf,
        List(handler.asInstanceOf[Handler[?, A]])
      )
      new ProjectionSpec[A](name, schema, entityPath, isGlobal, List(default))
    } else {
      val last    = bindings.last
      val updated = last.copy(handlers = last.handlers :+ handler.asInstanceOf[Handler[?, A]])
      new ProjectionSpec[A](name, schema, entityPath, isGlobal, bindings.init :+ updated)
    }

  def scope: ProjectionScope =
    if (isGlobal) ProjectionScope.Global
    else
      bindings.collectFirst { case sb if sb.routing.isInstanceOf[RoutingMode.RoutedBy[?]] => sb.routing } match {
        case Some(rb: RoutingMode.RoutedBy[?]) =>
          val extractor = rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String]
          ProjectionScope.CrossEntity(extractor)
        case _ => ProjectionScope.PerEntity
      }

  def allHandlers: List[Handler[?, A]] = bindings.flatMap(_.handlers)

  def sourceNames: List[String] = bindings.map(_.sourceName)

  def handle(event: Any, ctx: ProjectionContext): Option[ProjectionAction[A]] =
    allHandlers.find(_.eventClass.isInstance(event)).map(_.runAny(event, ctx))

  def dispatch(event: Any, ctx: ProjectionContext): Option[ProjectionAction[A]] = handle(event, ctx)

  def routingKey(event: Any, ctx: ProjectionContext, sourceName: String): Option[String] =
    bindings.find(_.sourceName == sourceName).flatMap { b =>
      b.routing match {
        case RoutingMode.RouteToSelf     => Some(ctx.entityId)
        case RoutingMode.RouteToAll      => None
        case rb: RoutingMode.RoutedBy[?] =>
          Some(rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String](event))
      }
    }

  def routingKeyForDefault(event: Any, ctx: ProjectionContext): Option[String] =
    if (bindings.isEmpty) Some(ctx.entityId)
    else
      bindings.last.routing match {
        case RoutingMode.RouteToSelf     => Some(ctx.entityId)
        case RoutingMode.RouteToAll      => None
        case rb: RoutingMode.RoutedBy[?] =>
          Some(rb.asInstanceOf[RoutingMode.RoutedBy[Any]].extractor.asInstanceOf[Any => String](event))
      }

  def validate(): List[String] = {
    val warnings = scala.collection.mutable.ListBuffer.empty[String]
    bindings.foreach { b =>
      b.handlers.foreach { h =>
        if (h.eventClass == null) warnings += s"Handler for ${h.tag} has null class"
      }
    }
    warnings.toList
  }

  override def toString: String =
    s"ProjectionSpec(name=$name, isGlobal=$isGlobal, bindings=${bindings.size}, handlers=${allHandlers.size})"
}

object ProjectionSpec {

  def apply[A: Schema: EntityPath](name: String): ProjectionSpec[A] =
    new ProjectionSpec[A](name, summon[Schema[A]], Some(summon[EntityPath[A]]), isGlobal = false, Nil)

  def global[A: Schema](name: String): ProjectionSpec[A] =
    new ProjectionSpec[A](name, summon[Schema[A]], None, isGlobal = true, Nil)

  def apply[A](name: String, entityPath: EntityPath[A])(using schema: Schema[A]): ProjectionSpec[A] =
    new ProjectionSpec[A](name, schema, Some(entityPath), isGlobal = false, Nil)
}

// ---------------------------------------------------------------------------
// HandlerBuilder
// ---------------------------------------------------------------------------

final class HandlerBuilder[E, A] private[projection] (
  spec: ProjectionSpec[A],
  schemaE: Schema[E],
  eventClass: Class[E]
) {

  private def tag: String =
    try schemaE.reflect.typeId.name
    catch { case _: Throwable => eventClass.getSimpleName.stripSuffix("$") }

  private def add(fn: (E, ProjectionContext) => ProjectionAction[A]): ProjectionSpec[A] =
    spec.addHandler(Handler[E, A](eventClass, schemaE, tag, fn))

  def insert(f: (E, ProjectionContext) => A): ProjectionSpec[A] =
    add((e, ctx) => ProjectionAction.Insert(f(e, ctx)))

  def delete: ProjectionSpec[A] =
    add((_, _) => ProjectionAction.Delete)

  def custom(f: (E, ProjectionContext) => ProjectionAction[A]): ProjectionSpec[A] =
    add(f)

  def aggregate(update: FieldUpdate): ProjectionSpec[A] =
    add((_, _) => ProjectionAction.Update(Chunk(update)))

  def aggregate(updates: Chunk[FieldUpdate]): ProjectionSpec[A] =
    add((_, _) => ProjectionAction.Update(updates))

  def aggregateField(update: FieldUpdate): ProjectionSpec[A] = aggregate(update)

  def updateWithField(fieldName: String, f: (E, ProjectionContext) => Any): ProjectionSpec[A] =
    add((e, ctx) => ProjectionAction.Update(Chunk(FieldUpdate.Set(fieldName, f(e, ctx)))))

  inline def update(inline selector: A => Any)(f: (E, ProjectionContext) => Any): ProjectionSpec[A] =
    ${ ProjectionSpecMacros.updateImpl[A, E]('this, 'selector, 'f) }

  def updateField(fieldName: String)(f: (E, ProjectionContext) => Any): ProjectionSpec[A] =
    updateWithField(fieldName, f)
}

// ---------------------------------------------------------------------------
// FromBuilder
// ---------------------------------------------------------------------------

final class FromBuilder[A] private[projection] (
  spec: ProjectionSpec[A],
  sourceName: String
) {

  def routedBy[E](selector: E => String): ProjectionSpec[A] =
    spec.addBinding(
      SourceBinding[Any, A](sourceName, RoutingMode.RoutedBy(selector.asInstanceOf[Any => String]), Nil)
    )

  inline def routedByField[E](inline selector: E => Any): ProjectionSpec[A] =
    ${ ProjectionSpecMacros.routedByFieldImpl[A, E]('this, 'selector) }

  def routeToAll: ProjectionSpec[A] =
    spec.addBinding(SourceBinding[Any, A](sourceName, RoutingMode.RouteToAll, Nil))

  def routeToSelf: ProjectionSpec[A] =
    spec.addBinding(SourceBinding[Any, A](sourceName, RoutingMode.RouteToSelf, Nil))
}
