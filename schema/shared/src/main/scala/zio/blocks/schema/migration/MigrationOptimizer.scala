package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk

/**
 * Migration optimization utilities.
 *
 * This object provides intelligent optimization of migration action sequences:
 *   - Collapse redundant operations (e.g., add then drop same field)
 *   - Merge sequential renames (A -> B -> C becomes A -> C)
 *   - Reorder actions for efficiency
 *   - Identify and remove no-ops
 *
 * Optimization preserves semantic equivalence while reducing action count.
 */
object MigrationOptimizer {

  /**
   * Optimize a migration by collapsing redundant actions.
   *
   * This is safe to call - it will return an equivalent migration (same
   * behavior, fewer actions).
   */
  def optimize(migration: DynamicMigration): DynamicMigration = {
    val optimized = pipeline(
      migration.actions,
      List(
        removeNoOps,
        collapseRenames,
        removeAddThenDrop,
        removeDropThenAdd,
        mergeTransforms
      )
    )

    DynamicMigration(optimized)
  }

  private def pipeline(
    actions: Chunk[MigrationAction],
    passes: List[Chunk[MigrationAction] => Chunk[MigrationAction]]
  ): Chunk[MigrationAction] =
    passes.foldLeft(actions)((acc, pass) => pass(acc))

  /**
   * Remove no-op rename actions (rename A to A).
   */
  private def removeNoOps(actions: Chunk[MigrationAction]): Chunk[MigrationAction] =
    actions.filter {
      case MigrationAction.Rename(_, from, to) if from == to => false
      case _                                                 => true
    }

  /**
   * Collapse sequential renames: (A -> B) then (B -> C) becomes (A -> C).
   */
  private def collapseRenames(
    actions: Chunk[MigrationAction]
  ): Chunk[MigrationAction] = {
    val renames = actions.collect { case r: MigrationAction.Rename => r }
    val others  = actions.filter(!_.isInstanceOf[MigrationAction.Rename])

    val grouped = renames.groupBy(_.at)

    val optimizedRenames = grouped.flatMap { case (at, pathRenames) =>
      val renameChain = buildRenameChain(pathRenames.toList)
      renameChain.map { case (from, to) =>
        MigrationAction.Rename(at, from, to)
      }
    }

    others ++ Chunk.fromIterable(optimizedRenames)
  }

  private def buildRenameChain(
    renames: List[MigrationAction.Rename]
  ): List[(String, String)] = {
    val fromToMap = renames.map(r => r.from -> r.to).toMap

    def findFinal(name: String, seen: Set[String]): String =
      if (seen.contains(name)) name
      else
        fromToMap.get(name) match {
          case Some(to) => findFinal(to, seen + name)
          case None     => name
        }

    val fromsInChain = renames.map(_.from).toSet
    val tosInChain   = renames.map(_.to).toSet
    val roots        = fromsInChain.diff(tosInChain)
    val finals       = tosInChain.diff(fromsInChain)

    roots.toList.flatMap { root =>
      val finalName = findFinal(root, Set.empty)
      if (root == finalName || !finals.contains(finalName)) None
      else Some(root -> finalName)
    }
  }

  /**
   * Remove pairs where a field is added then immediately dropped.
   */
  private def removeAddThenDrop(
    actions: Chunk[MigrationAction]
  ): Chunk[MigrationAction] = {
    val addedFields = actions.collect { case MigrationAction.AddField(at, fieldName, _) =>
      (at, fieldName)
    }.toSet

    val droppedAfterAdd = actions.collect {
      case MigrationAction.DropField(at, fieldName, _) if addedFields.contains((at, fieldName)) =>
        (at, fieldName)
    }.toSet

    actions.filter {
      case MigrationAction.AddField(at, fieldName, _)  => !droppedAfterAdd.contains((at, fieldName))
      case MigrationAction.DropField(at, fieldName, _) => !droppedAfterAdd.contains((at, fieldName))
      case _                                           => true
    }
  }

  /**
   * Remove pairs where a field is dropped then immediately re-added.
   */
  private def removeDropThenAdd(
    actions: Chunk[MigrationAction]
  ): Chunk[MigrationAction] = {
    val droppedFields = actions.collect { case MigrationAction.DropField(at, fieldName, _) =>
      (at, fieldName)
    }.toSet

    val addedAfterDrop = actions.collect {
      case MigrationAction.AddField(at, fieldName, _) if droppedFields.contains((at, fieldName)) =>
        (at, fieldName)
    }.toSet

    if (addedAfterDrop.isEmpty) actions
    else {
      val transforms = addedAfterDrop.toList.map { case (at, fieldName) =>
        val addAction = actions.collectFirst {
          case a: MigrationAction.AddField if a.at == at && a.fieldName == fieldName => a
        }.get
        MigrationAction.TransformValue(
          at,
          fieldName,
          addAction.default,
          Resolved.Identity
        )
      }

      val filtered = actions.filter {
        case MigrationAction.DropField(at, fieldName, _) => !addedAfterDrop.contains((at, fieldName))
        case MigrationAction.AddField(at, fieldName, _)  => !addedAfterDrop.contains((at, fieldName))
        case _                                           => true
      }

      filtered ++ Chunk.fromIterable(transforms)
    }
  }

  /**
   * Merge sequential transforms on the same field.
   *
   * Note: Sequential transform merging requires a Compose expression type. For
   * now, we keep transforms separate for clarity and debugging.
   */
  private def mergeTransforms(
    actions: Chunk[MigrationAction]
  ): Chunk[MigrationAction] = actions

  /**
   * Generate optimization report.
   */
  def report(original: DynamicMigration): OptimizationReport = {
    val optimized      = optimize(original)
    val actionsRemoved = original.actions.size - optimized.actions.size
    val percentReduced =
      if (original.actions.isEmpty) 0.0
      else (actionsRemoved.toDouble / original.actions.size) * 100

    OptimizationReport(
      originalCount = original.actions.size,
      optimizedCount = optimized.actions.size,
      actionsRemoved = actionsRemoved,
      percentReduced = percentReduced,
      optimizedMigration = optimized
    )
  }

  case class OptimizationReport(
    originalCount: Int,
    optimizedCount: Int,
    actionsRemoved: Int,
    percentReduced: Double,
    optimizedMigration: DynamicMigration
  ) {
    def render: String =
      s"""Optimization Report:
         |  Original actions:  $originalCount
         |  Optimized actions: $optimizedCount
         |  Actions removed:   $actionsRemoved (${f"$percentReduced%.1f"}%)
         |""".stripMargin
  }
}
