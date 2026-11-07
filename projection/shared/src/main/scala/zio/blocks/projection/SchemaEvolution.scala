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
import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.migration.{Migration, MigrationAction}
import zio.blocks.sql.{SqlDialect, Table}

/**
 * Settings for the functions of [[SchemaEvolution]] that take one.
 * [[ProjectionEngine]] does not read them; it has its own `lazyRebuild` and
 * `rebuildParallelism` in [[ProjectionEngineConfig]].
 *
 * @param rebuildParallelism
 *   how many projections `bulkRebuildPending` rebuilds at once
 * @param lazyRebuild
 *   not read by any function
 * @param enableMigrationShortcut
 *   whether `checkAndRebuild` tries the add-column shortcut before a full
 *   rebuild
 */
final case class SchemaEvolutionConfig(
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  enableMigrationShortcut: Boolean = true
)

/** Holds the default [[SchemaEvolutionConfig]]. */
object SchemaEvolutionConfig {

  /**
   * The default settings: 4 rebuilds at once, and the migration shortcut on.
   */
  val default: SchemaEvolutionConfig = SchemaEvolutionConfig()
}

/**
 * Detects schema drift via [[SchemaHash]] and rebuilds or migrates projection
 * stores. [[ProjectionEngine]] calls these functions during `start` and
 * `query`; call them yourself to rebuild a projection on demand.
 *
 * @see
 *   [[https://zioblocks.com/reference/projection/migration/ Migration]]
 */
object SchemaEvolution {

  /**
   * Whether the store holds a schema hash that differs from the current hash of
   * `A`. A store without a hash, such as a new one, gives `false`.
   */
  def needsRebuild[A: Schema](store: ProjectionStore[A]): Task[Boolean] =
    for {
      current <- ZIO.succeed(SchemaHash.compute[A])
      stored  <- store.getSchemaHash
    } yield stored.exists(_ != current)

  /**
   * Brings the store up to date if [[needsRebuild]] says so: with the migration
   * shortcut when `config.enableMigrationShortcut` is on and `migrationOpt`
   * qualifies, and otherwise with a full [[rebuild]]. Returns whether it
   * changed anything.
   */
  @scala.annotation.nowarn
  def checkAndRebuild[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    config: SchemaEvolutionConfig = SchemaEvolutionConfig.default,
    migrationOpt: Option[Migration[?, A]] = None
  ): Task[Boolean] =
    for {
      current <- ZIO.succeed(SchemaHash.compute[A])
      stored  <- store.getSchemaHash
      needs    = stored.exists(_ != current)
      rebuilt <- if (!needs) ZIO.succeed(false)
                 else if (!config.enableMigrationShortcut) rebuild(store, eventStore, spec, current).as(true)
                 else
                   tryMigrationShortcut(store, migrationOpt, current).flatMap {
                     case true  => ZIO.succeed(true)
                     case false => rebuild(store, eventStore, spec, current).as(true)
                   }
    } yield rebuilt

  /**
   * Rebuilds the rows from the events: recreates the table (or empties it if
   * that fails), sets the watermark to `0`, applies the events that
   * `eventStore.readAll()` returns through the handlers of `spec`, saves the
   * highest sequence number as the watermark, and saves `currentHash`.
   *
   * An event whose action fails is logged and skipped, and an `Insert` that
   * fails is tried again as an upsert. A read of the events that fails is
   * logged too, and the hash is saved anyway.
   */
  def rebuild[A](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    currentHash: String
  ): Task[Unit] =
    for {
      _ <- store.recreateTable().catchAll(_ => store.truncate)
      _ <- store.updateLastProcessedSeq(0L)
      _ <- replayAll(store, eventStore, spec)
      _ <- store.updateSchemaHash(currentHash)
    } yield ()

  /** Same as [[rebuild]], with the current hash of `A`'s schema. */
  @scala.annotation.nowarn
  def rebuildWithHash[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A]
  ): Task[Unit] = {
    val h = SchemaHash.compute[A]
    rebuild(store, eventStore, spec, h)
  }

  private def replayAll[A](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A]
  ): Task[Unit] = {
    val esAny = eventStore.asInstanceOf[EventStore[Any]]
    (for {
      ref <- Ref.make[Long](0L)
      _   <- esAny
             .readAll()
             .mapZIO { env =>
               ref.update(m => math.max(m, env.seq)) *> {
                 val ctx    = ProjectionContext(env.entityId, env.timestamp, env.seq, Some(env.entityId))
                 val action = spec.handle(env.event, ctx)
                 action match {
                   case None      => ZIO.unit
                   case Some(act) =>
                     applyAction(store, act, ctx).catchAll(e =>
                       ZIO.logError(
                         s"SchemaEvolution replay apply failed at seq ${env.seq}: ${e.getMessage}"
                       ) *> ZIO.unit
                     )
                 }
               }
             }
             .runDrain
      max <- ref.get
      _   <- if (max > 0) store.updateLastProcessedSeq(max) else ZIO.unit
    } yield ()).catchAll(e => ZIO.logError(s"SchemaEvolution replay failed: ${e.getMessage}") *> ZIO.unit)
  }

  private def applyAction[A](
    store: ProjectionStore[A],
    action: ProjectionAction[A],
    ctx: ProjectionContext
  ): Task[Unit] =
    action match {
      case ProjectionAction.Insert(v)    => store.insert(v).catchAll(_ => store.upsert(v))
      case ProjectionAction.Upsert(v)    => store.upsert(v)
      case ProjectionAction.Update(mods) => store.updateFields(ctx.entityId, mods)
      case ProjectionAction.Delete       => store.delete(ctx.entityId)
      case ProjectionAction.Truncate     => store.truncate
      case ProjectionAction.Noop         => ZIO.unit
    }

  // ---------------------------------------------------------------------------
  // Migration shortcut: ALTER TABLE ADD COLUMN for simple AddField migrations
  // ---------------------------------------------------------------------------

  /**
   * Whether `migration` has at least one action, and every action adds a
   * top-level field. Only such a migration can take the add-column shortcut.
   */
  def isSimpleAddFieldMigration(migration: Migration[?, ?]): Boolean = {
    val actions = migration.dynamicMigration.actions
    actions.nonEmpty && actions.forall {
      case _: MigrationAction.AddField => true
      case _                           => false
    } && actions.forall {
      case a: MigrationAction.AddField => a.at.nodes.size == 1 && a.at.nodes.head.isInstanceOf[DynamicOptic.Node.Field]
      case _                           => false
    }
  }

  /**
   * Takes the add-column shortcut if `migrationOpt` is a simple add-field
   * migration: adds a column for each added field, named in snake_case and
   * typed from `A` (or `TEXT` when the field is not a column of `A`), and saves
   * `currentHash`. Returns `false`, changing nothing, when there is no such
   * migration. A column that cannot be added is logged and skipped, and the
   * result is still `true`.
   */
  @scala.annotation.nowarn
  def tryMigrationShortcut[A: Schema: EntityPath](
    store: ProjectionStore[A],
    migrationOpt: Option[Migration[?, A]],
    currentHash: String
  ): Task[Boolean] =
    migrationOpt match {
      case None    => ZIO.succeed(false)
      case Some(m) =>
        if (!isSimpleAddFieldMigration(m.asInstanceOf[Migration[?, ?]])) ZIO.succeed(false)
        else {
          val addActions = m.dynamicMigration.actions.collect { case a: MigrationAction.AddField => a }
          val table      = Table.derived[A]
          val colMetaMap = table.columnsMeta.map(c => c.name -> c).toMap
          ZIO
            .foreachDiscard(addActions) { action =>
              val fieldName = action.fieldName.getOrElse("")
              val snake     = zio.blocks.sql.SqlNameMapper.SnakeCase(fieldName)
              val sqlType   = colMetaMap.get(snake) match {
                case Some(meta) => SqlDialect.SQLite.typeName(meta.dbValue)
                case None       => "TEXT"
              }
              store
                .addColumn(snake, sqlType)
                .catchAll(e =>
                  ZIO.logWarning(
                    s"SchemaEvolution migration shortcut addColumn $snake failed: ${e.getMessage}"
                  ) *> ZIO.unit
                )
            }
            .zipRight(store.updateSchemaHash(currentHash))
            .as(true)
            .catchAll(e => ZIO.logWarning(s"SchemaEvolution migration shortcut failed: ${e.getMessage}").as(false))
        }
    }

  /**
   * [[tryMigrationShortcut]] for a projection whose row type is not known
   * statically, as the engine has it.
   */
  def tryMigrationShortcutForSpec(
    spec: Projection[_],
    store: ProjectionStore[_],
    migrationOpt: Option[Migration[?, ?]],
    currentHash: String
  ): Task[Boolean] = {
    val s                            = spec.asInstanceOf[Projection[Any]]
    implicit val schema: Schema[Any] = s.schema.asInstanceOf[Schema[Any]]
    implicit val ep: EntityPath[Any] =
      s.entityPath.map(_.asInstanceOf[EntityPath[Any]]).getOrElse(EntityPath[Any](s.name, "id"))
    tryMigrationShortcut[Any](
      store.asInstanceOf[ProjectionStore[Any]],
      migrationOpt.map(_.asInstanceOf[Migration[?, Any]]),
      currentHash
    )
  }

  /**
   * Adds `migration` to `registry` under `specName`, replacing any migration
   * already there.
   */
  @scala.annotation.nowarn
  def registerMigration[Old, New: Schema: EntityPath](
    migration: Migration[Old, New],
    registry: Ref[Map[String, Migration[?, ?]]],
    specName: String
  ): Task[Unit] =
    registry.update(_ + (specName -> migration.asInstanceOf[Migration[?, ?]]))

  // ---------------------------------------------------------------------------
  // Lazy rebuild helpers
  // ---------------------------------------------------------------------------

  /**
   * If `pendingRef` marks `spec` as pending, brings the store up to date (with
   * the shortcut or a full [[rebuild]]) and removes the mark. `rebuildSem`
   * makes concurrent callers wait, and the mark is checked again once the
   * permit is held, so the work runs once.
   */
  def lazyRebuildIfNeeded[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    pendingRef: Ref[Map[String, Boolean]],
    rebuildSem: Semaphore,
    migrationOpt: Option[Migration[?, A]] = None
  ): Task[Unit] =
    pendingRef.get.map(_.getOrElse(spec.name, false)).flatMap {
      case false => ZIO.unit
      case true  =>
        rebuildSem.withPermit {
          pendingRef.get.map(_.getOrElse(spec.name, false)).flatMap {
            case false => ZIO.unit
            case true  =>
              val current = SchemaHash.compute[A]
              tryMigrationShortcut(store, migrationOpt, current).flatMap {
                case true  => pendingRef.update(_ - spec.name)
                case false =>
                  rebuild(store, eventStore, spec, current) *>
                    pendingRef.update(_ - spec.name)
              }
          }
        }
    }

  /**
   * Brings every projection that `pendingRef` marks as pending up to date,
   * `config.rebuildParallelism` at a time, and removes the marks. Each reads
   * the event store named after its first source, or after the projection when
   * it has none, or else any event store. A projection that fails is logged and
   * stays pending.
   */
  def bulkRebuildPending(
    specs: List[Projection[_]],
    stores: Map[String, ProjectionStore[_]],
    eventStores: Map[String, EventStore[_]],
    pendingRef: Ref[Map[String, Boolean]],
    migrations: Map[String, Migration[?, ?]],
    config: SchemaEvolutionConfig
  ): Task[Unit] =
    pendingRef.get.flatMap { pending =>
      val toRebuild = specs.filter(s => pending.getOrElse(s.name, false))
      if (toRebuild.isEmpty) ZIO.unit
      else
        ZIO
          .foreachPar(toRebuild) { specAny =>
            val store = stores(specAny.name).asInstanceOf[ProjectionStore[Any]]
            val es    = eventStores
              .get(specAny.sources.headOption.map(_.sourceName).getOrElse(specAny.name))
              .orElse(eventStores.headOption.map(_._2))
              .getOrElse(null)
            if (es == null) ZIO.unit
            else {
              val cur    = SchemaHash.compute[Any](using specAny.schema.asInstanceOf[Schema[Any]])
              val migOpt = migrations.get(specAny.name)
              tryMigrationShortcutForSpec(specAny, store, migOpt, cur).flatMap {
                case true  => pendingRef.update(_ - specAny.name)
                case false =>
                  val specCast = specAny.asInstanceOf[Projection[Any]]
                  rebuild[Any](store, es.asInstanceOf[EventStore[Any]], specCast, cur) *>
                    pendingRef.update(_ - specAny.name)
              }.catchAll(e => ZIO.logError(s"bulk rebuild $specAny failed: ${e.getMessage}") *> ZIO.unit)
            }
          }
          .withParallelism(config.rebuildParallelism)
          .unit
    }
}
