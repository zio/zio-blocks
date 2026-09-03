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

/**
 * ProjectionEngine runs projections — materializing event streams into
 * queryable read models.
 *
 * Each [[Projection]] defines how events map to an entity `A`. The engine backs
 * each projection with a [[ProjectionStore]] (one SQLite file per spec on the
 * JVM, or an in-memory map in tests) and keeps it up to date by consuming one
 * or more [[EventStore]] streams.
 *
 * Lifecycle: on [[start]] the engine checks stored schema hashes and rebuilds
 * any stale stores, then launches a catch-up fiber per binding (`readFrom` +
 * `groupedWithin`) followed by a live subscription fiber. Queries via [[query]]
 * first ensure any pending lazy rebuild is applied.
 *
 * Persistence: when `sqlite-jdbc` is on the classpath the engine uses
 * [[SQLiteProjectionStore]]; otherwise it falls back to
 * `InMemoryProjectionStore`. Add `libraryDependencies += "org.xerial" %
 * "sqlite-jdbc" % "3.53.4.0"` for SQLite persistence, or it falls back to
 * `InMemory` (see `docs/reference/projection.md`).
 *
 * ==Why ZIO lives here==
 *
 * Projection is the one module in this family that is ZIO-coupled by design:
 * keeping read models current requires background fibers (catch-up plus live
 * subscription per binding), Hub/Queue event streams, scoped transactor
 * lifetimes, and semaphore-guarded rebuilds. The rule is that ZIO effects are
 * allowed exactly when fibers, streams, or lifetime management are required —
 * the engine qualifies, while telemetry, config, context, and scope stay
 * ZIO-free (and the OTLP exporters remain plain JVM callbacks for the same
 * reason).
 *
 * ==Where the logic lives==
 *
 * The engine class below is a thin facade; the logic is split by concern:
 * [[EngineLifecycle]] (startup, fibers, consumption pipeline),
 * [[EngineRebuild]] (migration registry, schema-drift rebuilds), and
 * [[EngineQuery]] (reads over primary and sharded stores).
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
 *   engine tuning (batching, timeouts, rebuild parallelism)
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
  private[projection] val specs: List[Projection[_]],
  private[projection] val primaryStores: Map[String, ProjectionStore[_]],
  private[projection] val eventStores: Map[String, EventStore[_]],
  private[projection] val cache: TransactorCache,
  val config: ProjectionEngineConfig,
  private[projection] val shardsRef: Ref[Map[String, Map[String, ProjectionStore[_]]]],
  private[projection] val pendingRebuildRef: Ref[Map[String, Boolean]],
  private[projection] val migrationRegistry: Ref[Map[String, zio.blocks.schema.migration.Migration[?, ?]]],
  private[projection] val evolutionSem: Semaphore
) {

  /**
   * Returns the shared [[TransactorCache]] that backs SQLite transactors.
   *
   * Exposed for testing and for consumers that need direct transactor access.
   */
  def transactorCache: TransactorCache = cache

  /**
   * Registers a schema migration for the given projection.
   *
   * The migration is used during schema-evolution checks to attempt an
   * `ALTER TABLE ADD COLUMN` shortcut instead of a full rebuild when the change
   * is a simple `AddField`.
   *
   * @param spec
   *   the projection the migration applies to
   * @param migration
   *   the migration from `Old` to `New`
   * @tparam Old
   *   the previous entity type
   * @tparam New
   *   the current entity type
   * @return
   *   a task that registers the migration
   */
  def registerMigration[Old, New](
    spec: Projection[New],
    migration: zio.blocks.schema.migration.Migration[Old, New]
  ): Task[Unit] =
    EngineRebuild.registerMigration(this, spec, migration)

  /**
   * Starts the engine: validates schemas, rebuilds stale stores, then launches
   * per-binding catch-up and live fibers.
   *
   * Must be run inside a `Scope`; all fibers and transactors are tied to that
   * scope and are released when it closes.
   *
   * @return
   *   a scoped task that completes when fibers are launched (not when they
   *   finish)
   */
  def start: ZIO[Scope, Nothing, Unit] =
    EngineLifecycle.start(this)

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
   *   the projection entity type
   * @return
   *   the entity if found
   */
  def query[A](spec: Projection[A], entityId: String): Task[Option[A]] =
    EngineQuery.query(this, spec, entityId)

  /** Query by spec name + id (untyped helper) */
  def queryByName[A](specName: String, entityId: String): Task[Option[A]] =
    EngineQuery.queryByName(this, specName, entityId)

  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long] =
    EngineQuery.getLastProcessedSeq(this, spec)

  def storesMap: Map[String, ProjectionStore[_]] = primaryStores

  def eventStoresMap: Map[String, EventStore[_]] = eventStores
}

object ProjectionEngine {

  // M1: bounded Hub (ringCapacity 4096) — respects backpressure via suspending publish
  def boundedHub[E](capacity: Int = 4096): UIO[Hub[EventEnvelope[E]]] = Hub.bounded[EventEnvelope[E]](capacity)

  // M3: path traversal validation
  def validateName(name: String): Unit =
    require(
      name.nonEmpty && !name.contains("/") && !name.contains("\\") && !name.contains("..") && name
        .matches("[a-zA-Z0-9_-]+"),
      s"invalid name $name"
    )

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

  def make(specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine] =
    makeWithConfig(ProjectionEngineConfig.default, specs: _*)

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

  // Test helper: inject explicit stores and eventStores
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

  // Overload for varargs specs
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
