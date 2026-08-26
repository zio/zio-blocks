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

final case class SchemaEvolutionConfig(
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  enableMigrationShortcut: Boolean = true
)

object SchemaEvolutionConfig {
  val default: SchemaEvolutionConfig = SchemaEvolutionConfig()
}

object SchemaEvolution {

  def needsRebuild[A: Schema](store: ProjectionStore[A]): Task[Boolean] =
    for {
      current <- ZIO.succeed(SchemaHash.compute[A])
      stored  <- store.getSchemaHash
    } yield stored.exists(_ != current)

  def checkAndRebuild[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: ProjectionSpec[A],
    config: SchemaEvolutionConfig = SchemaEvolutionConfig.default,
    migrationOpt: Option[Migration[?, A]] = None
  ): Task[Boolean] = {
    val _ = config
    for {
      current <- ZIO.succeed(SchemaHash.compute[A])
      stored  <- store.getSchemaHash
      needs    = stored.exists(_ != current)
      rebuilt <- if (!needs) ZIO.succeed(false)
                 else
                   tryMigrationShortcut(store, migrationOpt, current).flatMap {
                     case true  => ZIO.succeed(true)
                     case false => rebuild(store, eventStore, spec, current).as(true)
                   }
    } yield rebuilt
  }

  def rebuild[A](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: ProjectionSpec[A],
    currentHash: String
  ): Task[Unit] =
    for {
      _ <- store.recreateTable().catchAll(_ => store.truncate)
      _ <- store.updateLastProcessedSeq(0L)
      _ <- replayAll(store, eventStore, spec)
      _ <- store.updateSchemaHash(currentHash)
    } yield ()

  def rebuildWithHash[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: ProjectionSpec[A]
  ): Task[Unit] = {
    val _ = summon[EntityPath[A]]
    val h = SchemaHash.compute[A]
    rebuild(store, eventStore, spec, h)
  }

  private def replayAll[A](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: ProjectionSpec[A]
  ): Task[Unit] = {
    val esAny = eventStore.asInstanceOf[EventStore[Any]]
    ZIO.unit.flatMap { _ =>
      esAny
        .readAll()
        .mapZIO { env =>
          val ctx    = ProjectionContext(env.entityId, env.timestamp, env.seq, Some(env.entityId))
          val action = spec.handle(env.event, ctx)
          action match {
            case None      => ZIO.unit
            case Some(act) =>
              applyAction(store, act, ctx).catchAll(e =>
                ZIO.logError(s"SchemaEvolution replay apply failed at seq ${env.seq}: ${e.getMessage}") *> ZIO.unit
              )
          }
        }
        .runDrain
        .zipRight {
          esAny.readAll().runCollect.map(_.lastOption.map(_.seq).getOrElse(0L)).flatMap { last =>
            if (last > 0) store.updateLastProcessedSeq(last) else ZIO.unit
          }
        }
        .catchAll(e => ZIO.logError(s"SchemaEvolution replay failed: ${e.getMessage}") *> ZIO.unit)
    }
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

  def tryMigrationShortcut[A: Schema: EntityPath](
    store: ProjectionStore[A],
    migrationOpt: Option[Migration[?, A]],
    currentHash: String
  ): Task[Boolean] = {
    val _ = summon[EntityPath[A]]
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
            .catchAll(_ => ZIO.succeed(false))
        }
    }
  }

  def tryMigrationShortcutForSpec(
    spec: ProjectionSpec[_],
    store: ProjectionStore[_],
    migrationOpt: Option[Migration[?, ?]],
    currentHash: String
  ): Task[Boolean] = {
    val s                            = spec.asInstanceOf[ProjectionSpec[Any]]
    implicit val schema: Schema[Any] = s.schema.asInstanceOf[Schema[Any]]
    implicit val ep: EntityPath[Any] =
      s.entityPath.map(_.asInstanceOf[EntityPath[Any]]).getOrElse(EntityPath[Any](s.name, "id"))
    tryMigrationShortcut[Any](
      store.asInstanceOf[ProjectionStore[Any]],
      migrationOpt.map(_.asInstanceOf[Migration[?, Any]]),
      currentHash
    )
  }

  def registerMigration[Old, New: Schema: EntityPath](
    migration: Migration[Old, New],
    registry: Ref[Map[String, Migration[?, ?]]],
    specName: String
  ): Task[Unit] = {
    val _ = summon[Schema[New]]
    val _ = summon[EntityPath[New]]
    registry.update(_ + (specName -> migration.asInstanceOf[Migration[?, ?]]))
  }

  // ---------------------------------------------------------------------------
  // Lazy rebuild helpers
  // ---------------------------------------------------------------------------

  def lazyRebuildIfNeeded[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: ProjectionSpec[A],
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

  def bulkRebuildPending(
    specs: List[ProjectionSpec[_]],
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
          .foreachPar(toRebuild.grouped(config.rebuildParallelism).toList) { batch =>
            ZIO.foreachDiscard(batch) { specAny =>
              val store = stores(specAny.name).asInstanceOf[ProjectionStore[Any]]
              val es    = eventStores.values.headOption.getOrElse(null)
              if (es == null) ZIO.unit
              else {
                val cur    = SchemaHash.compute[Any](using specAny.schema.asInstanceOf[Schema[Any]])
                val migOpt = migrations.get(specAny.name)
                tryMigrationShortcutForSpec(specAny, store, migOpt, cur).flatMap {
                  case true  => pendingRef.update(_ - specAny.name)
                  case false =>
                    val specCast = specAny.asInstanceOf[ProjectionSpec[Any]]
                    rebuild[Any](store, es.asInstanceOf[EventStore[Any]], specCast, cur) *>
                      pendingRef.update(_ - specAny.name)
                }.catchAll(e => ZIO.logError(s"bulk rebuild $specAny failed: ${e.getMessage}") *> ZIO.unit)
              }
            }
          }
          .unit
    }
}
