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

/**
 * Settings for a [[ProjectionEngine]].
 *
 * @param batchSize
 *   the most events read together while catching up on history. Each event of a
 *   batch is still applied, and its position saved, on its own.
 * @param batchTimeout
 *   how long catch-up waits for a batch to fill before it applies what it has
 * @param ringCapacity
 *   the capacity of the bounded hub that carries live events, where a hub is
 *   created from the config, as `TestEngine` does. An event store made with
 *   `SQLiteEventStore.make` has its own hub of 4,096 events.
 * @param rebuildParallelism
 *   the size of the groups in which the background pre-warm rebuilds pending
 *   projections when `lazyRebuild` is on. The groups, and the projections in
 *   them, are rebuilt one after the other.
 * @param lazyRebuild
 *   if `true`, a projection whose schema changed is only marked as pending at
 *   startup, and rebuilt on its first query or by the background pre-warm. If
 *   `false`, the default, it is rebuilt during `start`.
 * @param evolution
 *   settings for [[SchemaEvolution]]. The engine does not read them: it always
 *   tries the migration shortcut when a migration is registered for the
 *   projection, and uses its own `lazyRebuild` and `rebuildParallelism`.
 */
final case class ProjectionEngineConfig(
  batchSize: Int = 100,
  batchTimeout: zio.Duration = 50.millis,
  ringCapacity: Int = 4096,
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  evolution: SchemaEvolutionConfig = SchemaEvolutionConfig.default
)

object ProjectionEngineConfig {

  /**
   * The default settings: batches of 100 events with a 50 ms timeout, a hub
   * capacity of 4,096, groups of 4 for the pre-warm, and rebuilds during
   * `start`.
   */
  val default: ProjectionEngineConfig = ProjectionEngineConfig()
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

/**
 * Runs projections: it reads events from [[EventStore]]s, applies each
 * [[Projection]]'s handlers, and keeps the results in [[ProjectionStore]]s that
 * can be queried.
 *
 * A [[Projection]] only describes what should happen. The engine is the part
 * that does it. Each projection gets a primary store, which is one SQLite file
 * per projection on the JVM or an in-memory map in tests.
 *
 * Lifecycle: [[start]] first compares each projection's stored schema hash with
 * the current one, and brings stale stores up to date unless `lazyRebuild` is
 * on. Then, for each source of each projection, it catches up on the events
 * after the projection's saved position, reading them in batches with
 * `readFrom`, and only then starts a fiber that follows the new events from the
 * source's hub. `start` returns once every source has caught up. [[query]]
 * first applies any pending lazy rebuild.
 *
 * Routing: for each event the engine builds a [[ProjectionContext]] from the
 * [[EventEnvelope]], runs the matching handler, and applies the resulting
 * [[ProjectionAction]] to the store chosen by the projection's
 * [[ProjectionScope]]: the primary store for per-entity and global projections,
 * and a shard per routing key for cross-entity ones.
 *
 * Failures: an event whose handler throws, or that the store rejects, is logged
 * as an error and skipped; an event that no handler matches is skipped too, and
 * logged at debug level. Processing continues with the next event. The saved
 * position is updated after each event that succeeds, so a skipped event is not
 * retried once a later one succeeds.
 *
 * Persistence: the stores that `make`, `makeWithConfig` and
 * `makeWithStoresVarargs` create are SQLite files, so `sqlite-jdbc` must be on
 * the runtime classpath and the folders of the files must exist. A store opens
 * its file only when it is first used, so a missing driver or folder shows up
 * as a failure then, not when the engine is created.
 *
 * Create an engine with the factories on the companion object. `make` and
 * `makeWithConfig` give it no event stores, so it has nothing to read; to give
 * it event stores, create it with `makeWithStores` or `makeWithStoresVarargs`,
 * or use `TestEngine` in tests.
 *
 * @param specs
 *   list of projections this engine manages
 * @param primaryStores
 *   per-spec primary stores keyed by spec name
 * @param eventStores
 *   per-source event stores keyed by source name
 * @param cache
 *   shared [[TransactorCache]] for SQLite connections
 * @param config
 *   the settings the engine was created with
 * @param shardsRef
 *   sharded stores for cross-entity routing
 * @param pendingRebuildRef
 *   specs pending lazy rebuild
 * @param migrationRegistry
 *   registered schema migrations
 * @param evolutionSem
 *   semaphore guarding rebuilds
 */
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

  /**
   * The [[TransactorCache]] that opens the engine's SQLite files: the one
   * `make` and `makeWithConfig` created, or the one given to `makeWithStores`
   * or `makeWithStoresVarargs`.
   *
   * Exposed for tests and for code that needs a connection to the same files.
   */
  def transactorCache: TransactorCache = cache

  /**
   * Registers how the row type of a projection changed, from `Old` to `New`, so
   * that the change can be handled without a full rebuild.
   *
   * When the stored rows are out of date and every action of `migration` adds a
   * top-level field, the engine adds the new columns with
   * `ALTER TABLE ADD COLUMN` instead of replaying the events. The existing rows
   * get no value in the new columns. For any other change, it rebuilds the rows
   * from the events as usual.
   *
   * Register it before `start`, which is when the schema is checked, or, with
   * `lazyRebuild`, before the projection is first queried. The migration is
   * kept under the projection's name, so registering another one replaces it.
   *
   * @param spec
   *   the projection the migration applies to
   * @param migration
   *   the migration from `Old` to `New`
   * @tparam Old
   *   the previous row type
   * @tparam New
   *   the current row type
   */
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

  // Factory for shards: uses same store type as primary (SQLite on JVM, else InMemory)
  private def getOrCreateShard[A](spec: Projection[A], routingKey: String): Task[ProjectionStore[A]] = {
    val specName = spec.name
    shardsRef.get.flatMap { outer =>
      outer.get(specName).flatMap(_.get(routingKey)) match {
        case Some(cached) => ZIO.succeed(cached.asInstanceOf[ProjectionStore[A]])
        case None         =>
          val create: Task[ProjectionStore[A]] = {
            implicit val schema: Schema[A] = spec.schema
            implicit val ep: EntityPath[A] =
              spec.entityPath.getOrElse(EntityPath[A](specName, "id"))
            // If primary is SQLite, create SQLite shard with sanitized path
            val primary = primaryStores.get(specName)
            if (primary.exists(_.isInstanceOf[SQLiteProjectionStore[_]])) {
              val base      = spec.entityPath.map(_.basePath).getOrElse("projections")
              val sanitized = routingKey.replaceAll("[^a-zA-Z0-9_-]", "_").take(64)
              val shardPath = s"$base/${specName}_shard_$sanitized.db"
              SQLiteProjectionStore.make[A](shardPath, cache)
            } else InMemoryProjectionStore.make[A]
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
              resolveTargetStore(spec, env.event, ctx, binding.sourceName).flatMap { targetStore =>
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

  /**
   * Starts the engine.
   *
   * It first checks the schema of each projection and, unless `lazyRebuild` is
   * on, brings stale stores up to date, with the migration shortcut or a full
   * rebuild. Then, for each source of each projection, it applies the events
   * after the projection's saved position and starts a fiber that follows the
   * new events. With `lazyRebuild` on, it also starts a fiber that rebuilds the
   * pending projections in the background.
   *
   * A source that fails is logged and skipped. A source without an event store,
   * which only happens when the engine has no event stores at all, is skipped
   * with a warning. The fibers run until the enclosing `Scope` closes; there is
   * no separate stop method.
   *
   * @return
   *   a scoped task that completes once every source has caught up and its live
   *   fiber has been started
   */
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
              val primary =
                primaryStoreFor(specAny.asInstanceOf[Projection[Any]]).asInstanceOf[ProjectionStore[Any]]
              // C3: catch-up first, then subscribe for live; avoids race where subscribed queue
              // sees events that catch-up also replays. Subscribe after catch-up, filter seq > lastSeq.
              val bindTask: ZIO[Scope, Throwable, Unit] =
                for {
                  initialLast <- primary.getLastProcessedSeq
                  _           <- es
                         .readFrom(initialLast)
                         .groupedWithin(config.batchSize, config.batchTimeout)
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
                  queue <- es.subscribe.subscribe
                  _     <- ZStream
                         .fromQueue(queue)
                         .mapZIO { env =>
                           processBatch(
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

  /**
   * Queries the projection for the entity with the given id.
   *
   * Ensures any pending lazy rebuild for `spec` is applied before reading. For
   * cross-entity projections, also searches sharded stores if the primary has
   * no match.
   *
   * @param spec
   *   the projection to query
   * @param entityId
   *   the entity id to look up
   * @tparam A
   *   the row type of the projection
   * @return
   *   the row, or `None` if no store has it
   */
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

  /**
   * Reads the row with the given ID from the projection with the given name, or
   * `None` if there is no such projection or row.
   *
   * Unlike [[query]], it neither applies a pending lazy rebuild nor searches
   * shards, and the caller chooses the result type `A` without it being
   * checked.
   *
   * @param specName
   *   the name of the projection
   * @param entityId
   *   the ID of the row
   * @return
   *   the row, or `None`
   */
  def queryByName[A](specName: String, entityId: String): Task[Option[A]] =
    primaryStores.get(specName) match {
      case Some(store) => store.asInstanceOf[ProjectionStore[A]].findById(entityId)
      case None        => ZIO.succeed(None)
    }

  /**
   * The sequence number of the last event the projection has applied, which is
   * where the engine resumes after a restart. It is kept in the projection's
   * primary store, also for a cross-entity projection with shards.
   *
   * @param spec
   *   the projection
   */
  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long] =
    primaryStoreFor(spec).getLastProcessedSeq

  /** The primary store of each projection, keyed by projection name. */
  def storesMap: Map[String, ProjectionStore[_]] = primaryStores

  /** The event stores, keyed by source name. */
  def eventStoresMap: Map[String, EventStore[_]] = eventStores
}

object ProjectionEngine {

  /**
   * Creates a bounded hub for live events. Publishing suspends when the hub is
   * full, so a slow consumer slows the producer down instead of losing events.
   *
   * @param capacity
   *   the most events the hub holds
   */
  def boundedHub[E](capacity: Int = 4096): UIO[Hub[EventEnvelope[E]]] = Hub.bounded[EventEnvelope[E]](capacity)

  /**
   * Checks that a projection name is safe to use as a file name, and throws
   * `IllegalArgumentException` if it is not. A name must be non-empty and made
   * of letters, digits, `_` and `-`.
   *
   * @param name
   *   the projection name to check
   */
  def validateName(name: String): Unit =
    require(
      name.nonEmpty && !name.contains("/") && !name.contains("\\") && !name.contains("..") && name
        .matches("[a-zA-Z0-9_-]+"),
      s"invalid name $name"
    )

  /**
   * Checks that a folder path cannot escape the storage directory, and throws
   * `IllegalArgumentException` if it can. It rejects an empty path,
   * backslashes, `..`, `//`, and absolute paths.
   *
   * @param path
   *   the folder path to check
   */
  def validateBasePath(path: String): Unit = {
    require(
      path.nonEmpty && !path.contains("\\") && !path.contains("..") && !path.contains("//"),
      s"invalid basePath $path"
    )
    // also forbid absolute or traversal segments
    require(!path.startsWith("/") && !path.contains("/../") && !path.endsWith("/.."), s"invalid basePath $path")
  }

  private def validateSpec(spec: Projection[_]): Unit = {
    validateName(spec.name)
    spec.entityPath.foreach { ep =>
      validateBasePath(ep.basePath)
      require(!ep.basePath.contains("\\"), s"invalid basePath ${ep.basePath}")
    }
    // validate @path annotation if present
    spec.entityPath.foreach { _ =>
      // EntityPath derived already checks, but also validate via schema typeId annotation
      val typeId  = spec.schema.reflect.typeId
      val pathAnn = typeId.annotations.collectFirst {
        case ann if ann.name == "path" =>
          ann.args.collectFirst { case zio.blocks.typeid.AnnotationArg.Const(v: String) => v }
      }.flatten
      pathAnn.foreach(validateBasePath)
    }
  }

  /**
   * Creates an engine for the given projections, with the default
   * [[ProjectionEngineConfig]].
   *
   * It checks the projection names and folders, creates a [[TransactorCache]]
   * that is closed with the enclosing `Scope`, and a SQLite store for each
   * projection: `global/<name>.db` for a global projection and
   * `<basePath>/<name>.db` otherwise. The engine has no event stores, so it has
   * nothing to read; to give it some, use `makeWithStores` or
   * `makeWithStoresVarargs` instead.
   *
   * Fails if a name or folder is invalid.
   *
   * @param specs
   *   the projections to run
   */
  def make(specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine] =
    makeWithConfig(ProjectionEngineConfig.default, specs: _*)

  /**
   * Like [[make]], with the settings `config`.
   *
   * @param config
   *   the settings
   * @param specs
   *   the projections to run
   */
  def makeWithConfig(
    config: ProjectionEngineConfig,
    specs: Projection[_]*
  ): ZIO[Scope, Throwable, ProjectionEngine] =
    for {
      _             <- ZIO.attempt(specs.foreach(validateSpec))
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
                           // validate path components
                           validateBasePath(path.split("/").headOption.getOrElse(path))
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

  /**
   * Creates an engine from stores you made yourself, instead of creating them.
   * This is how an engine is given event stores to read from, and how
   * `TestEngine` wires its in-memory stores.
   *
   * It neither creates nor opens any store, and `stores` must have an entry for
   * every projection. An invalid projection name or folder is a defect, not a
   * typed error.
   *
   * @param specs
   *   the projections to run
   * @param stores
   *   the primary store of each projection, keyed by projection name
   * @param eventStores
   *   the event store of each source, keyed by the source name given to `from`
   * @param cache
   *   the cache that opens the SQLite files; the engine also opens the shards
   *   of cross-entity projections with it
   * @param config
   *   the settings
   */
  def makeWithStores(
    specs: List[Projection[_]],
    stores: Map[String, ProjectionStore[_]],
    eventStores: Map[String, EventStore[_]],
    cache: TransactorCache,
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  ): UIO[ProjectionEngine] =
    for {
      _          <- ZIO.attempt(specs.foreach(validateSpec)).orDie
      shardsRef  <- Ref.make(Map.empty[String, Map[String, ProjectionStore[_]]])
      pendingRef <- Ref.make(Map.empty[String, Boolean])
      migReg     <- Ref.make(Map.empty[String, zio.blocks.schema.migration.Migration[?, ?]])
      sem        <- Semaphore.make(1)
    } yield new ProjectionEngine(specs, stores, eventStores, cache, config, shardsRef, pendingRef, migReg, sem)

  /**
   * Like [[makeWithStores]] in that it takes event stores, but it creates the
   * primary stores itself, the way [[make]] does. The projections are passed in
   * a second parameter list.
   *
   * Unlike `makeWithStores`, an invalid projection name or folder fails the
   * task.
   *
   * @param cache
   *   the cache that opens the SQLite files
   * @param eventStores
   *   the event store of each source, keyed by the source name given to `from`
   * @param config
   *   the settings
   * @param specs
   *   the projections to run
   */
  def makeWithStoresVarargs(
    cache: TransactorCache,
    eventStores: Map[String, EventStore[_]],
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  )(specs: Projection[_]*): Task[ProjectionEngine] =
    for {
      _             <- ZIO.attempt(specs.foreach(validateSpec))
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
                           validateBasePath(path.split("/").headOption.getOrElse(path))
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
    // Validate store path for traversal (M3)
    validateBasePath(path.split("/").headOption.getOrElse(path))
    require(!path.contains("\\") && !path.contains(".."), s"invalid store path $path")
    val spec                         = specAny.asInstanceOf[Projection[Any]]
    implicit val schema: Schema[Any] = spec.schema.asInstanceOf[Schema[Any]]
    implicit val ep: EntityPath[Any] =
      spec.entityPath.map(_.asInstanceOf[EntityPath[Any]]).getOrElse(EntityPath[Any](spec.name, "id"))
    // JVM: SQLite store (projection is JVM-only per 0976c318); fallback to InMemory on any error
    SQLiteProjectionStore
      .make[Any](path, cache)
      .map(_.asInstanceOf[ProjectionStore[_]])
      .catchAll(_ => createInMemoryForSpec(specAny))
  }

  private def createInMemoryForSpec(specAny: Projection[_]): Task[ProjectionStore[_]] = {
    val spec                         = specAny.asInstanceOf[Projection[Any]]
    implicit val schema: Schema[Any] = spec.schema.asInstanceOf[Schema[Any]]
    implicit val ep: EntityPath[Any] =
      spec.entityPath.map(_.asInstanceOf[EntityPath[Any]]).getOrElse(EntityPath[Any]("projections", "id"))
    InMemoryProjectionStore.make[Any].map(_.asInstanceOf[ProjectionStore[_]])
  }
}
