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

import zio.*

/**
 * Schema-evolution concern of [[ProjectionEngine]]: migration registration,
 * stale-store detection, and (lazy or eager) rebuilds.
 *
 * Split out of the engine god-file so lifecycle (fibers), rebuild (schema
 * drift), and query (reads) can be reasoned about independently. No behavior
 * change — [[ProjectionEngine]] delegates to these helpers.
 */
private[projection] object EngineRebuild {

  def registerMigration[Old, New](
    engine: ProjectionEngine,
    spec: Projection[New],
    migration: zio.blocks.schema.migration.Migration[Old, New]
  ): Task[Unit] =
    engine.migrationRegistry.update(
      _ + (spec.name -> migration.asInstanceOf[zio.blocks.schema.migration.Migration[?, ?]])
    )

  def eventStoreForSpec(engine: ProjectionEngine, spec: Projection[_]): Option[EventStore[Any]] = {
    val bindingSource = spec.bindings.headOption.map(_.sourceName)
    bindingSource
      .flatMap(engine.eventStores.get)
      .map(_.asInstanceOf[EventStore[Any]])
      .orElse(engine.eventStores.get("_default").map(_.asInstanceOf[EventStore[Any]]))
      .orElse(engine.eventStores.values.headOption.map(_.asInstanceOf[EventStore[Any]]))
  }

  def currentHashFor(spec: Projection[_]): String =
    SchemaHash.compute(using spec.schema.asInstanceOf[zio.blocks.schema.Schema[Any]])

  def checkSchemaAndRebuild(engine: ProjectionEngine, specAny: Projection[_]): Task[Unit] = {
    val spec  = specAny.asInstanceOf[Projection[Any]]
    val store = engine.primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
    val cur   = currentHashFor(specAny)
    store.getSchemaHash.catchAll(_ => ZIO.succeed(None)).flatMap {
      case None =>
        store.updateSchemaHash(cur).catchAll(_ => ZIO.unit)
      case Some(stored) if stored == cur =>
        ZIO.unit
      case Some(_) =>
        if (engine.config.lazyRebuild) {
          engine.pendingRebuildRef.update(_ + (spec.name -> true))
        } else {
          val esOpt = eventStoreForSpec(engine, specAny)
          esOpt match {
            case None     => ZIO.unit
            case Some(es) =>
              engine.migrationRegistry.get.flatMap { regs =>
                val migOpt = regs.get(spec.name)
                SchemaEvolution.tryMigrationShortcutForSpec(specAny, store, migOpt, cur).flatMap {
                  case true  => ZIO.unit
                  case false => SchemaEvolution.rebuild(store, es, spec, cur)
                }
              }.catchAll(e =>
                ZIO.logError(s"SchemaEvolution rebuild failed for ${spec.name}: ${e.getMessage}") *> ZIO.unit
              )
          }
        }
    }
  }

  def ensureRebuiltIfPending[A](engine: ProjectionEngine, spec: Projection[A]): Task[Unit] =
    engine.pendingRebuildRef.get.map(_.getOrElse(spec.name, false)).flatMap {
      case false => ZIO.unit
      case true  =>
        engine.evolutionSem.withPermit {
          engine.pendingRebuildRef.get.map(_.getOrElse(spec.name, false)).flatMap {
            case false => ZIO.unit
            case true  =>
              val store = engine.primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
              val esOpt = eventStoreForSpec(engine, spec)
              esOpt match {
                case None     => engine.pendingRebuildRef.update(_ - spec.name)
                case Some(es) =>
                  val cur = currentHashFor(spec)
                  engine.migrationRegistry.get.flatMap { regs =>
                    val migOpt = regs.get(spec.name)
                    SchemaEvolution.tryMigrationShortcutForSpec(spec, store, migOpt, cur).flatMap {
                      case true  => engine.pendingRebuildRef.update(_ - spec.name)
                      case false =>
                        SchemaEvolution.rebuild(store, es, spec.asInstanceOf[Projection[Any]], cur) *>
                          engine.pendingRebuildRef.update(_ - spec.name)
                    }
                  }
              }
          }
        }
    }
}
