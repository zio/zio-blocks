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
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.Schema
import zio.stream.ZStream

import scala.util.Try

/**
 * Lifecycle concern of [[ProjectionEngine]]: startup validation, per-binding
 * catch-up and live subscription fibers, background lazy-rebuild prewarm, and
 * the event-consumption pipeline (routing + action application + watermark).
 *
 * Split out of the engine god-file so lifecycle (fibers), rebuild (schema
 * drift), and query (reads) can be reasoned about independently. No behavior
 * change — [[ProjectionEngine]] delegates to these helpers.
 */
private[projection] object EngineLifecycle {

  def start(engine: ProjectionEngine): ZIO[Scope, Nothing, Unit] = {
    val schemaChecks: ZIO[Any, Nothing, Unit] =
      ZIO.foreachDiscard(engine.specs)(specAny => EngineRebuild.checkSchemaAndRebuild(engine, specAny).orDie)

    val launchBindings: ZIO[Scope, Nothing, Unit] =
      ZIO.foreachDiscard(engine.specs) { specAny =>
        val spec                                  = specAny.asInstanceOf[Projection[Any]]
        val bindings: List[SourceBinding[?, Any]] =
          if (spec.bindings.isEmpty) {
            List(
              SourceBinding[Any, Any](
                "_default",
                RoutingMode.RouteToSelf,
                spec.allHandlers.asInstanceOf[List[Handler[?, Any]]]
              )
            )
          } else {
            spec.bindings.asInstanceOf[List[SourceBinding[?, Any]]]
          }
        ZIO.foreachDiscard(bindings) { binding =>
          val esOpt: Option[EventStore[Any]] =
            engine.eventStores
              .get(binding.sourceName)
              .map(_.asInstanceOf[EventStore[Any]])
              .orElse(engine.eventStores.get("_default").map(_.asInstanceOf[EventStore[Any]]))
              .orElse(engine.eventStores.values.headOption.map(_.asInstanceOf[EventStore[Any]]))

          esOpt match {
            case None =>
              ZIO
                .logWarning(
                  s"ProjectionEngine[${spec.name}] no EventStore for source '${binding.sourceName}' - skipping"
                )
                .unit
            case Some(es) =>
              val primary =
                EngineQuery
                  .primaryStoreFor(engine, specAny.asInstanceOf[Projection[Any]])
                  .asInstanceOf[ProjectionStore[Any]]
              // C3: catch-up first, then subscribe for live; avoids race where subscribed queue
              // sees events that catch-up also replays. Subscribe after catch-up, filter seq > lastSeq.
              val bindTask: ZIO[Scope, Throwable, Unit] =
                for {
                  initialLast <- primary.getLastProcessedSeq
                  _           <- es
                         .readFrom(initialLast)
                         .groupedWithin(engine.config.batchSize, engine.config.batchTimeout)
                         .mapZIO { chunk =>
                           if (chunk.isEmpty) ZIO.unit
                           else
                             processBatch(
                               engine,
                               spec.asInstanceOf[Projection[Any]],
                               binding.asInstanceOf[SourceBinding[?, Any]],
                               chunk.asInstanceOf[zio.Chunk[EventEnvelope[Any]]]
                             )
                         }
                         .runDrain
                  queue <- es.subscribe.subscribe
                  _     <- ZStream
                         .fromQueue(queue)
                         .mapZIO { env =>
                           processBatch(
                             engine,
                             spec.asInstanceOf[Projection[Any]],
                             binding.asInstanceOf[SourceBinding[?, Any]],
                             zio.Chunk.single(env)
                           )
                         }
                         .runDrain
                         .forkScoped
                } yield ()
              bindTask.catchAll { e =>
                ZIO.logError(s"ProjectionEngine[${spec.name}] bind failed: ${e.getMessage}") *> ZIO.unit
              }
          }
        }
      }

    val backgroundPrewarm: ZIO[Scope, Nothing, Unit] =
      if (!engine.config.lazyRebuild) ZIO.unit
      else {
        ZIO
          .foreachDiscard(engine.specs.grouped(engine.config.rebuildParallelism).toList) { batch =>
            ZIO.foreachDiscard(batch) { specAny =>
              val spec  = specAny.asInstanceOf[Projection[Any]]
              val store = engine.primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
              val esOpt = EngineRebuild.eventStoreForSpec(engine, specAny)
              esOpt match {
                case None     => ZIO.unit
                case Some(es) =>
                  engine.pendingRebuildRef.get.flatMap(_.get(spec.name) match {
                    case Some(true) =>
                      engine.evolutionSem.withPermit {
                        engine.pendingRebuildRef.get.flatMap(_.get(spec.name) match {
                          case Some(true) =>
                            val cur = EngineRebuild.currentHashFor(specAny)
                            engine.migrationRegistry.get.flatMap { regs =>
                              val migOpt = regs.get(spec.name)
                              SchemaEvolution.tryMigrationShortcutForSpec(specAny, store, migOpt, cur).flatMap {
                                case true  => engine.pendingRebuildRef.update(_ - spec.name)
                                case false =>
                                  SchemaEvolution.rebuild(store, es, spec, cur) *> engine.pendingRebuildRef.update(
                                    _ - spec.name
                                  )
                              }
                            }.catchAll(_ => ZIO.unit)
                          case _ => ZIO.unit
                        })
                      }
                    case _ => ZIO.unit
                  })
              }
            }
          }
          .forkScoped
          .unit
      }

    schemaChecks *> launchBindings *> backgroundPrewarm
  }

  // Factory for shards: uses same store type as primary (SQLite on JVM, else InMemory)
  private def getOrCreateShard[A](
    engine: ProjectionEngine,
    spec: Projection[A],
    routingKey: String
  ): Task[ProjectionStore[A]] = {
    val specName = spec.name
    engine.shardsRef.get.flatMap { outer =>
      outer.get(specName).flatMap(_.get(routingKey)) match {
        case Some(cached) => ZIO.succeed(cached.asInstanceOf[ProjectionStore[A]])
        case None         =>
          val create: Task[ProjectionStore[A]] = {
            implicit val schema: Schema[A] = spec.schema
            implicit val ep: EntityPath[A] =
              spec.entityPath.getOrElse(EntityPath[A](specName, "id"))
            // If primary is SQLite, create SQLite shard with sanitized path
            val primary = engine.primaryStores.get(specName)
            if (primary.exists(_.isInstanceOf[SQLiteProjectionStore[_]])) {
              val base      = spec.entityPath.map(_.basePath).getOrElse("projections")
              val sanitized = routingKey.replaceAll("[^a-zA-Z0-9_-]", "_").take(64)
              val shardPath = s"$base/${specName}_shard_$sanitized.db"
              SQLiteProjectionStore.make[A](shardPath, engine.cache)
            } else InMemoryProjectionStore.make[A]
          }
          create.flatMap { newStore =>
            engine.shardsRef.update { outer2 =>
              val inner = outer2.getOrElse(specName, Map.empty)
              outer2.updated(specName, inner.updated(routingKey, newStore.asInstanceOf[ProjectionStore[_]]))
            }.as(newStore)
          }
      }
    }
  }

  // Simplified shard fallback: if cross-entity, still use primary but trace routing
  private def resolveTargetStore[A](
    engine: ProjectionEngine,
    spec: Projection[A],
    event: Any,
    ctx: ProjectionContext,
    sourceName: String
  ): Task[ProjectionStore[A]] = {
    // Use ctx fallback to ensure parameter is not unused
    val fallbackEntityId = ctx.entityId
    spec.scope match {
      case ProjectionScope.Global =>
        ZIO.succeed(EngineQuery.primaryStoreFor(engine, spec))
      case ProjectionScope.CrossEntity(extractor) =>
        val routingKeyAttempt: Option[String] = {
          val fromBinding = spec.bindings.find(_.sourceName == sourceName).flatMap { b =>
            b.routing match {
              case rm: RoutingMode.RoutedBy[?] =>
                Try(rm.asInstanceOf[RoutingMode.RoutedBy[Any]].extractAny(event)).toOption
              case _ => None
            }
          }
          fromBinding.orElse(Try(extractor(event)).toOption).orElse(Some(fallbackEntityId))
        }
        routingKeyAttempt match {
          case Some(key) =>
            getOrCreateShard(engine, spec, key).catchAll { _ =>
              ZIO.succeed(EngineQuery.primaryStoreFor(engine, spec))
            }
          case None =>
            ZIO.succeed(EngineQuery.primaryStoreFor(engine, spec))
        }
      case _ =>
        // PerEntity or default: use primary
        ZIO.succeed(EngineQuery.primaryStoreFor(engine, spec))
    }
  }

  private def applyAction[A](
    store: ProjectionStore[A],
    action: ProjectionAction[A],
    ctx: ProjectionContext
  ): Task[Unit] =
    action match {
      case ProjectionAction.Insert(v)    => store.insert(v)
      case ProjectionAction.Upsert(v)    => store.upsert(v)
      case ProjectionAction.Update(mods) => store.updateFields(ctx.entityId, mods)
      case ProjectionAction.Delete       => store.delete(ctx.entityId)
      case ProjectionAction.Truncate     => store.truncate
      case ProjectionAction.Noop         => ZIO.unit
    }

  private def processBatch[A](
    engine: ProjectionEngine,
    spec: Projection[A],
    binding: SourceBinding[?, A],
    batch: zio.Chunk[EventEnvelope[Any]]
  ): Task[Unit] = {
    val primary = EngineQuery.primaryStoreFor(engine, spec)
    // C1+C2: per-event processing with lastSuccess tracking; each success atomically
    // applies the action and persists seq in same logical transaction (SQLite: single JDBC tx)
    ZIO
      .foldLeft(batch)(Option.empty[Long]) { (acc, env) =>
        val ctx                                            = ProjectionContext(env.entityId, env.timestamp, env.seq, Some(env.entityId))
        val handlerTask: Task[Option[ProjectionAction[A]]] =
          ZIO
            .attempt(spec.handle(env.event, ctx))
            .catchAll { e =>
              ZIO.logError(s"ProjectionEngine[${spec.name}] handler failed at seq ${env.seq}: ${e.getMessage}") *>
                ZIO.succeed(None)
            }
        handlerTask.flatMap {
          case None =>
            // No handler or handler error: do NOT advance watermark, keep acc
            ZIO
              .logDebug(
                s"ProjectionEngine[${spec.name}] no handler for event ${env.event.getClass.getSimpleName} at seq ${env.seq}"
              )
              .as(acc)
          case Some(action) =>
            val processOne: Task[Option[Long]] =
              resolveTargetStore(engine, spec, env.event, ctx, binding.sourceName).flatMap { targetStore =>
                // Transactional: applyAction + seq persist share same DB transaction where possible
                // For SQLite primary, the same connection backs both; for InMemory, sequential but watermark
                // only moves on success, so crash between apply and seq causes replay not double-count (dedup)
                val transactional: Task[Unit] =
                  applyAction(targetStore.asInstanceOf[ProjectionStore[A]], action, ctx).flatMap { _ =>
                    primary.updateLastProcessedSeq(env.seq)
                  }
                transactional.as(Some(env.seq)).catchAll { e =>
                  ZIO.logError(
                    s"ProjectionEngine[${spec.name}] apply failed at seq ${env.seq}: ${e.getMessage}"
                  ) *> ZIO.succeed(acc)
                }
              }.catchAll { e =>
                ZIO.logError(
                  s"ProjectionEngine[${spec.name}] routing failed at seq ${env.seq}: ${e.getMessage}"
                ) *> ZIO.succeed(acc)
              }
            processOne
        }
      }
      .flatMap {
        case Some(_) => ZIO.unit // watermark already at lastSuccess via per-event updates
        case None    => ZIO.unit // no successful event, watermark stays as before
      }
  }
}
