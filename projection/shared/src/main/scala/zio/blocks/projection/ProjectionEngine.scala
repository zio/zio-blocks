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

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

final case class ProjectionEngineConfig(
  batchSize: Int = 100,
  batchTimeout: zio.Duration = 50.millis,
  ringCapacity: Int = 4096,
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  evolution: SchemaEvolutionConfig = SchemaEvolutionConfig.default
)

object ProjectionEngineConfig {
  val default: ProjectionEngineConfig = ProjectionEngineConfig()
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

final class ProjectionEngine private[projection] (
  private val specs: List[Projection[_]],
  private val primaryStores: Map[String, ProjectionStore[_]],
  private val eventStores: Map[String, EventStore[_]],
  private val cache: TransactorCache,
  val config: ProjectionEngineConfig,
  private val shardsRef: Ref[Map[String, Map[String, ProjectionStore[_]]]],
  private val pendingRebuildRef: Ref[Map[String, Boolean]],
  private val migrationRegistry: Ref[Map[String, zio.blocks.schema.migration.Migration[?, ?]]],
  private val evolutionSem: Semaphore
) {
  // consume cache to avoid unused warning; also expose via method
  def transactorCache: TransactorCache = cache

  def registerMigration[Old, New](
    spec: Projection[New],
    migration: zio.blocks.schema.migration.Migration[Old, New]
  ): Task[Unit] =
    migrationRegistry.update(_ + (spec.name -> migration.asInstanceOf[zio.blocks.schema.migration.Migration[?, ?]]))

  private def eventStoreForSpec(spec: Projection[_]): Option[EventStore[Any]] = {
    val bindingSource = spec.bindings.headOption.map(_.sourceName)
    bindingSource
      .flatMap(eventStores.get)
      .map(_.asInstanceOf[EventStore[Any]])
      .orElse(eventStores.get("_default").map(_.asInstanceOf[EventStore[Any]]))
      .orElse(eventStores.values.headOption.map(_.asInstanceOf[EventStore[Any]]))
  }

  private def currentHashFor(spec: Projection[_]): String =
    SchemaHash.compute(using spec.schema.asInstanceOf[zio.blocks.schema.Schema[Any]])

  private def checkSchemaAndRebuild(specAny: Projection[_]): Task[Unit] = {
    val spec  = specAny.asInstanceOf[Projection[Any]]
    val store = primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
    val cur   = currentHashFor(specAny)
    store.getSchemaHash.catchAll(_ => ZIO.succeed(None)).flatMap {
      case None =>
        store.updateSchemaHash(cur).catchAll(_ => ZIO.unit)
      case Some(stored) if stored == cur =>
        ZIO.unit
      case Some(_) =>
        if (config.lazyRebuild) {
          pendingRebuildRef.update(_ + (spec.name -> true))
        } else {
          val esOpt = eventStoreForSpec(specAny)
          esOpt match {
            case None     => ZIO.unit
            case Some(es) =>
              migrationRegistry.get.flatMap { regs =>
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

  private def ensureRebuiltIfPending[A](spec: Projection[A]): Task[Unit] =
    pendingRebuildRef.get.map(_.getOrElse(spec.name, false)).flatMap {
      case false => ZIO.unit
      case true  =>
        evolutionSem.withPermit {
          pendingRebuildRef.get.map(_.getOrElse(spec.name, false)).flatMap {
            case false => ZIO.unit
            case true  =>
              val store = primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
              val esOpt = eventStoreForSpec(spec)
              esOpt match {
                case None     => pendingRebuildRef.update(_ - spec.name)
                case Some(es) =>
                  val cur = currentHashFor(spec)
                  migrationRegistry.get.flatMap { regs =>
                    val migOpt = regs.get(spec.name)
                    SchemaEvolution.tryMigrationShortcutForSpec(spec, store, migOpt, cur).flatMap {
                      case true  => pendingRebuildRef.update(_ - spec.name)
                      case false =>
                        SchemaEvolution.rebuild(store, es, spec.asInstanceOf[Projection[Any]], cur) *>
                          pendingRebuildRef.update(_ - spec.name)
                    }
                  }
              }
          }
        }
    }

  private def primaryStoreFor[A](spec: Projection[A]): ProjectionStore[A] =
    primaryStores(spec.name).asInstanceOf[ProjectionStore[A]]

  // Factory for shards: uses same store type as primary (InMemory vs SQLite)
  private def getOrCreateShard[A](spec: Projection[A], routingKey: String): Task[ProjectionStore[A]] = {
    val specName = spec.name
    shardsRef.get.flatMap { outer =>
      outer.get(specName).flatMap(_.get(routingKey)) match {
        case Some(cached) => ZIO.succeed(cached.asInstanceOf[ProjectionStore[A]])
        case None         =>
          // Create shard store of same type as primary (InMemory preferred for shared portability)
          // For cross-entity we still isolate shards via separate InMemory instances so query can search them
          val create: Task[ProjectionStore[A]] = {
            implicit val schema: Schema[A] = spec.schema
            // spec.entityPath may be None for Global; create dummy EntityPath if needed
            implicit val ep: EntityPath[A] =
              spec.entityPath.getOrElse(EntityPath[A](specName, "id"))
            InMemoryProjectionStore.make[A]
          }
          create.flatMap { newStore =>
            shardsRef.update { outer2 =>
              val inner = outer2.getOrElse(specName, Map.empty)
              outer2.updated(specName, inner.updated(routingKey, newStore.asInstanceOf[ProjectionStore[_]]))
            }.as(newStore)
          }
      }
    }
  }

  // Simplified shard fallback: if cross-entity, still use primary but trace routing
  private def resolveTargetStore[A](
    spec: Projection[A],
    event: Any,
    ctx: ProjectionContext,
    sourceName: String
  ): Task[ProjectionStore[A]] = {
    // Use ctx fallback to ensure parameter is not unused
    val fallbackEntityId = ctx.entityId
    spec.scope match {
      case ProjectionScope.Global =>
        ZIO.succeed(primaryStoreFor(spec))
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
            getOrCreateShard(spec, key).catchAll { _ =>
              ZIO.succeed(primaryStoreFor(spec))
            }
          case None =>
            ZIO.succeed(primaryStoreFor(spec))
        }
      case _ =>
        // PerEntity or default: use primary
        ZIO.succeed(primaryStoreFor(spec))
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
    spec: Projection[A],
    binding: SourceBinding[?, A],
    batch: zio.Chunk[EventEnvelope[Any]]
  ): Task[Unit] = {
    val primary = primaryStoreFor(spec)
    ZIO
      .foreachDiscard(batch) { env =>
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
            // No handler: skip but log at debug
            ZIO
              .logDebug(
                s"ProjectionEngine[${spec.name}] no handler for event ${env.event.getClass.getSimpleName} at seq ${env.seq}"
              )
              .unit
          case Some(action) =>
            resolveTargetStore(spec, env.event, ctx, binding.sourceName).flatMap { targetStore =>
              applyAction(targetStore.asInstanceOf[ProjectionStore[A]], action, ctx).catchAll { e =>
                ZIO.logError(
                  s"ProjectionEngine[${spec.name}] apply failed at seq ${env.seq}: ${e.getMessage}"
                ) *> ZIO.unit
              }
            }.catchAll { e =>
              ZIO.logError(
                s"ProjectionEngine[${spec.name}] routing failed at seq ${env.seq}: ${e.getMessage}"
              ) *> ZIO.unit
            }
        }
      }
      .zipRight {
        batch.lastOption match {
          case Some(last) =>
            primary.updateLastProcessedSeq(last.seq).catchAll { e =>
              ZIO.logError(s"ProjectionEngine[${spec.name}] updateLastProcessedSeq failed: ${e.getMessage}") *> ZIO.unit
            }
          case None => ZIO.unit
        }
      }
  }

  /** Start processing: catch-up then live. Runs scoped fibers per binding. */
  def start: ZIO[Scope, Nothing, Unit] = {
    val schemaChecks: ZIO[Any, Nothing, Unit] =
      ZIO.foreachDiscard(specs)(specAny => checkSchemaAndRebuild(specAny).orDie)

    val launchBindings: ZIO[Scope, Nothing, Unit] =
      ZIO.foreachDiscard(specs) { specAny =>
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
            eventStores
              .get(binding.sourceName)
              .map(_.asInstanceOf[EventStore[Any]])
              .orElse(eventStores.get("_default").map(_.asInstanceOf[EventStore[Any]]))
              .orElse(eventStores.values.headOption.map(_.asInstanceOf[EventStore[Any]]))

          esOpt match {
            case None =>
              ZIO
                .logWarning(
                  s"ProjectionEngine[${spec.name}] no EventStore for source '${binding.sourceName}' - skipping"
                )
                .unit
            case Some(es) =>
              es.subscribe.subscribe.flatMap { queue =>
                val primary =
                  primaryStoreFor(specAny.asInstanceOf[Projection[Any]]).asInstanceOf[ProjectionStore[Any]]
                val catchUp: Task[Unit] =
                  primary.getLastProcessedSeq.catchAll { e =>
                    ZIO.logError(s"ProjectionEngine[${spec.name}] getLastProcessedSeq failed: ${e.getMessage}") *> ZIO
                      .succeed(0L)
                  }.flatMap { lastSeq =>
                    es.readFrom(lastSeq)
                      .grouped(config.batchSize)
                      .mapZIO { chunk =>
                        if (chunk.isEmpty) ZIO.unit
                        else
                          processBatch(
                            spec.asInstanceOf[Projection[Any]],
                            binding.asInstanceOf[SourceBinding[?, Any]],
                            chunk.asInstanceOf[zio.Chunk[EventEnvelope[Any]]]
                          )
                      }
                      .runDrain
                      .catchAll { e =>
                        ZIO.logError(s"ProjectionEngine[${spec.name}] catch-up failed: ${e.getMessage}") *> ZIO.unit
                      }
                  }

                val liveBase: ZIO[Any, Nothing, Unit] =
                  ZStream
                    .fromQueue(queue)
                    .mapZIO { env =>
                      processBatch(
                        spec.asInstanceOf[Projection[Any]],
                        binding.asInstanceOf[SourceBinding[?, Any]],
                        zio.Chunk.single(env)
                      ).catchAll { e =>
                        ZIO.logError(s"ProjectionEngine[${spec.name}] live event failed: ${e.getMessage}") *> ZIO.unit
                      }
                    }
                    .runDrain
                val live: ZIO[Any, Nothing, Unit] = liveBase

                (catchUp *> live.forkScoped.unit).orDie
              }
          }
        }
      }

    val backgroundPrewarm: ZIO[Scope, Nothing, Unit] =
      if (!config.lazyRebuild) ZIO.unit
      else {
        ZIO
          .foreachDiscard(specs.grouped(config.rebuildParallelism).toList) { batch =>
            ZIO.foreachDiscard(batch) { specAny =>
              val spec  = specAny.asInstanceOf[Projection[Any]]
              val store = primaryStores(spec.name).asInstanceOf[ProjectionStore[Any]]
              val esOpt = eventStoreForSpec(specAny)
              esOpt match {
                case None     => ZIO.unit
                case Some(es) =>
                  pendingRebuildRef.get.flatMap(_.get(spec.name) match {
                    case Some(true) =>
                      evolutionSem.withPermit {
                        pendingRebuildRef.get.flatMap(_.get(spec.name) match {
                          case Some(true) =>
                            val cur = currentHashFor(specAny)
                            migrationRegistry.get.flatMap { regs =>
                              val migOpt = regs.get(spec.name)
                              SchemaEvolution.tryMigrationShortcutForSpec(specAny, store, migOpt, cur).flatMap {
                                case true  => pendingRebuildRef.update(_ - spec.name)
                                case false =>
                                  SchemaEvolution.rebuild(store, es, spec, cur) *> pendingRebuildRef.update(
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

  // ---------------------------------------------------------------------------
  // Query
  // ---------------------------------------------------------------------------

  def query[A](spec: Projection[A], entityId: String): Task[Option[A]] =
    ensureRebuiltIfPending(spec) *>
      primaryStoreFor(spec).findById(entityId).flatMap {
        case some @ Some(_) => ZIO.succeed(some)
        case None           =>
          spec.scope match {
            case ProjectionScope.CrossEntity(_) =>
              shardsRef.get.flatMap { outer =>
                outer.get(spec.name) match {
                  case None        => ZIO.succeed(None)
                  case Some(inner) =>
                    ZIO
                      .foreach(inner.values.toList)(
                        _.asInstanceOf[ProjectionStore[A]].findById(entityId).catchAll(_ => ZIO.succeed(None))
                      )
                      .map(_.collectFirst { case Some(v) => v })
                }
              }
            case _ => ZIO.succeed(None)
          }
      }

  /** Query by spec name + id (untyped helper) */
  def queryByName[A](specName: String, entityId: String): Task[Option[A]] =
    primaryStores.get(specName) match {
      case Some(store) => store.asInstanceOf[ProjectionStore[A]].findById(entityId)
      case None        => ZIO.succeed(None)
    }

  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long] =
    primaryStoreFor(spec).getLastProcessedSeq

  def storesMap: Map[String, ProjectionStore[_]] = primaryStores

  def eventStoresMap: Map[String, EventStore[_]] = eventStores
}

object ProjectionEngine {

  def make(specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine] =
    makeWithConfig(ProjectionEngineConfig.default, specs: _*)

  def makeWithConfig(
    config: ProjectionEngineConfig,
    specs: Projection[_]*
  ): ZIO[Scope, Throwable, ProjectionEngine] =
    for {
      cache         <- TransactorCache.make()
      shardsRef     <- Ref.make(Map.empty[String, Map[String, ProjectionStore[_]]])
      pendingRef    <- Ref.make(Map.empty[String, Boolean])
      migReg        <- Ref.make(Map.empty[String, zio.blocks.schema.migration.Migration[?, ?]])
      sem           <- Semaphore.make(1)
      primaryStores <- ZIO
                         .foreach(specs.toList) { specAny =>
                           val spec = specAny.asInstanceOf[Projection[Any]]
                           val path = spec.scope match {
                             case ProjectionScope.Global => s"global/${spec.name}.db"
                             case _                      =>
                               spec.entityPath match {
                                 case Some(ep) => s"${ep.basePath}/${spec.name}.db"
                                 case None     => s"projections/${spec.name}.db"
                               }
                           }
                           createStoreForSpec(specAny, path, cache).map(store => spec.name -> store)
                         }
                         .map(_.toMap)
    } yield new ProjectionEngine(
      specs.toList,
      primaryStores,
      Map.empty,
      cache,
      config,
      shardsRef,
      pendingRef,
      migReg,
      sem
    )

  // Test helper: inject explicit stores and eventStores
  def makeWithStores(
    specs: List[Projection[_]],
    stores: Map[String, ProjectionStore[_]],
    eventStores: Map[String, EventStore[_]],
    cache: TransactorCache,
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  ): UIO[ProjectionEngine] =
    for {
      shardsRef  <- Ref.make(Map.empty[String, Map[String, ProjectionStore[_]]])
      pendingRef <- Ref.make(Map.empty[String, Boolean])
      migReg     <- Ref.make(Map.empty[String, zio.blocks.schema.migration.Migration[?, ?]])
      sem        <- Semaphore.make(1)
    } yield new ProjectionEngine(specs, stores, eventStores, cache, config, shardsRef, pendingRef, migReg, sem)

  // Overload for varargs specs
  def makeWithStoresVarargs(
    cache: TransactorCache,
    eventStores: Map[String, EventStore[_]],
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  )(specs: Projection[_]*): Task[ProjectionEngine] =
    for {
      shardsRef     <- Ref.make(Map.empty[String, Map[String, ProjectionStore[_]]])
      pendingRef    <- Ref.make(Map.empty[String, Boolean])
      migReg        <- Ref.make(Map.empty[String, zio.blocks.schema.migration.Migration[?, ?]])
      sem           <- Semaphore.make(1)
      primaryStores <- ZIO
                         .foreach(specs.toList) { specAny =>
                           val spec = specAny.asInstanceOf[Projection[Any]]
                           val path = spec.scope match {
                             case ProjectionScope.Global => s"global/${spec.name}.db"
                             case _                      =>
                               spec.entityPath match {
                                 case Some(ep) => s"${ep.basePath}/${spec.name}.db"
                                 case None     => s"projections/${spec.name}.db"
                               }
                           }
                           createStoreForSpec(specAny, path, cache).map(store => spec.name -> store)
                         }
                         .map(_.toMap)
    } yield new ProjectionEngine(
      specs.toList,
      primaryStores,
      eventStores,
      cache,
      config,
      shardsRef,
      pendingRef,
      migReg,
      sem
    )

  private def createStoreForSpec(
    specAny: Projection[_],
    path: String,
    cache: TransactorCache
  ): Task[ProjectionStore[_]] = {
    // path and cache kept for future SQLite file-per-spec strategy; currently use InMemory for portability
    val _ = (path, cache)
    createInMemoryForSpec(specAny)
  }

  private def createInMemoryForSpec(specAny: Projection[_]): Task[ProjectionStore[_]] = {
    val spec                         = specAny.asInstanceOf[Projection[Any]]
    implicit val schema: Schema[Any] = spec.schema.asInstanceOf[Schema[Any]]
    implicit val ep: EntityPath[Any] =
      spec.entityPath.map(_.asInstanceOf[EntityPath[Any]]).getOrElse(EntityPath[Any]("projections", "id"))
    InMemoryProjectionStore.make[Any].map(_.asInstanceOf[ProjectionStore[_]])
  }
}
