package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * Comprehensive migration diagnostics and error reporting.
 *
 * This object provides detailed diagnostic capabilities that exceed basic error
 * messages:
 *   - Formatted migration action visualization
 *   - Before/after schema comparison
 *   - Suggested fixes for common errors
 *   - Migration optimization suggestions
 *   - Visual diagram generation (Mermaid)
 */
object MigrationDiagnostics {

  /**
   * Generate a formatted, human-readable representation of a migration.
   */
  def formatMigration(migration: DynamicMigration): String = {
    val sb = new StringBuilder
    sb.append("Migration Actions:\n")
    sb.append("=" * 50)
    sb.append("\n\n")

    migration.actions.zipWithIndex.foreach { case (action, idx) =>
      sb.append(s"${idx + 1}. ${formatAction(action)}\n")
    }

    if (migration.actions.isEmpty) {
      sb.append("(no actions - identity migration)\n")
    }

    sb.toString
  }

  /**
   * Format a single migration action for display.
   */
  def formatAction(action: MigrationAction): String = action match {
    case MigrationAction.AddField(at, fieldName, default) =>
      s"ADD field '$fieldName' at ${formatPath(at)} with default ${formatResolved(default)}"

    case MigrationAction.DropField(at, fieldName, _) =>
      s"DROP field '$fieldName' at ${formatPath(at)}"

    case MigrationAction.Rename(at, from, to) =>
      s"RENAME field '$from' -> '$to' at ${formatPath(at)}"

    case MigrationAction.TransformValue(at, fieldName, transform, _) =>
      s"TRANSFORM field '$fieldName' at ${formatPath(at)} using ${formatResolved(transform)}"

    case MigrationAction.Mandate(at, fieldName, default) =>
      s"MANDATE field '$fieldName' at ${formatPath(at)} (Option -> T) with default ${formatResolved(default)}"

    case MigrationAction.Optionalize(at, fieldName) =>
      s"OPTIONALIZE field '$fieldName' at ${formatPath(at)} (T -> Option)"

    case MigrationAction.ChangeType(at, fieldName, converter, _) =>
      s"CHANGE TYPE of '$fieldName' at ${formatPath(at)} using ${formatResolved(converter)}"

    case MigrationAction.RenameCase(at, from, to) =>
      s"RENAME CASE '$from' -> '$to' at ${formatPath(at)}"

    case MigrationAction.TransformCase(at, caseName, caseActions) =>
      s"TRANSFORM CASE '$caseName' at ${formatPath(at)} with ${caseActions.size} nested actions"

    case MigrationAction.TransformElements(at, transform, _) =>
      s"TRANSFORM ELEMENTS at ${formatPath(at)} using ${formatResolved(transform)}"

    case MigrationAction.TransformKeys(at, transform, _) =>
      s"TRANSFORM KEYS at ${formatPath(at)} using ${formatResolved(transform)}"

    case MigrationAction.TransformValues(at, transform, _) =>
      s"TRANSFORM VALUES at ${formatPath(at)} using ${formatResolved(transform)}"

    case MigrationAction.Join(at, targetFieldName, sourcePaths, combiner, _) =>
      s"JOIN ${sourcePaths.mkString(", ")} -> '$targetFieldName' at ${formatPath(at)}"

    case MigrationAction.Split(at, sourceFieldName, targetPaths, splitter, _) =>
      s"SPLIT '$sourceFieldName' -> ${targetPaths.mkString(", ")} at ${formatPath(at)}"
  }

  private def formatPath(optic: zio.blocks.schema.DynamicOptic): String =
    if (optic.nodes.isEmpty) "root" else optic.toScalaString

  private def formatResolved(resolved: Resolved): String = resolved match {
    case Resolved.Literal(value)       => s"literal(${formatDynamicValue(value)})"
    case Resolved.FieldAccess(name, _) => s"field($name)"
    case Resolved.Identity             => "identity"
    case Resolved.Fail(msg)            => s"fail($msg)"
    case _                             => resolved.getClass.getSimpleName
  }

  private def formatDynamicValue(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(pv)   => pv.toString.take(30)
    case DynamicValue.Record(fields)  => s"Record(${fields.size} fields)"
    case DynamicValue.Sequence(elems) => s"Seq(${elems.size} elements)"
    case _                            => dv.getClass.getSimpleName
  }

  /**
   * Generate a Mermaid diagram for a migration.
   */
  def toMermaidDiagram(migration: DynamicMigration): String = {
    val sb = new StringBuilder
    sb.append("flowchart LR\n")
    sb.append("  Source[\"Source Schema\"] --> Migration\n")

    migration.actions.zipWithIndex.foreach { case (action, idx) =>
      val actionName = action.getClass.getSimpleName
      val label      = actionName match {
        case "AddField"       => "+"
        case "DropField"      => "-"
        case "Rename"         => "→"
        case "TransformValue" => "⟳"
        case "Join"           => "⊕"
        case "Split"          => "⊗"
        case _                => "•"
      }
      sb.append(s"  Migration --> A$idx[\"$label $actionName\"]\n")
    }

    sb.append("  Migration --> Target[\"Target Schema\"]\n")
    sb.toString
  }

  /**
   * Analyze a migration for potential issues and suggestions.
   */
  def analyze(migration: DynamicMigration): MigrationAnalysis = {
    val actions = migration.actions

    val hasRenames = actions.exists(_.isInstanceOf[MigrationAction.Rename])
    val hasDrops   = actions.exists(_.isInstanceOf[MigrationAction.DropField])
    val hasJoins   = actions.exists(_.isInstanceOf[MigrationAction.Join])
    val hasSplits  = actions.exists(_.isInstanceOf[MigrationAction.Split])

    val warnings    = scala.collection.mutable.ListBuffer[String]()
    val suggestions = scala.collection.mutable.ListBuffer[String]()

    if (hasDrops && !hasRenames) {
      warnings += "Fields are being dropped without renames - this may indicate data loss"
    }

    if (hasJoins && hasSplits) {
      suggestions += "Consider combining join and split operations if they operate on related fields"
    }

    if (actions.size > 10) {
      suggestions += "Large migration detected - consider breaking into smaller, versioned steps"
    }

    val redundantRenames = findRedundantRenames(actions)
    if (redundantRenames.nonEmpty) {
      suggestions += s"Redundant renames detected: ${redundantRenames.mkString(", ")}"
    }

    MigrationAnalysis(
      actionCount = actions.size,
      isReversible = isFullyReversible(migration),
      hasDataLossRisk = hasDrops,
      warnings = warnings.toList,
      suggestions = suggestions.toList
    )
  }

  private def findRedundantRenames(
    actions: zio.blocks.chunk.Chunk[MigrationAction]
  ): List[String] = {
    val renames = actions.collect { case r: MigrationAction.Rename => r }
    renames
      .groupBy(_.at)
      .filter(_._2.size > 1)
      .keys
      .map(_.toScalaString)
      .toList
  }

  private def isFullyReversible(migration: DynamicMigration): Boolean =
    migration.actions.forall {
      case _: MigrationAction.Rename      => true
      case _: MigrationAction.RenameCase  => true
      case _: MigrationAction.Optionalize => true
      case _: MigrationAction.Join        => true
      case _: MigrationAction.Split       => true
      case _                              => false
    }

  case class MigrationAnalysis(
    actionCount: Int,
    isReversible: Boolean,
    hasDataLossRisk: Boolean,
    warnings: List[String],
    suggestions: List[String]
  ) {
    def render: String = {
      val sb = new StringBuilder
      sb.append(s"Action Count: $actionCount\n")
      sb.append(s"Fully Reversible: $isReversible\n")
      sb.append(s"Data Loss Risk: $hasDataLossRisk\n")

      if (warnings.nonEmpty) {
        sb.append("\nWarnings:\n")
        warnings.foreach(w => sb.append(s"  ⚠ $w\n"))
      }

      if (suggestions.nonEmpty) {
        sb.append("\nSuggestions:\n")
        suggestions.foreach(s => sb.append(s"  💡 $s\n"))
      }

      sb.toString
    }
  }

  /**
   * Generate suggested fixes for common migration validation errors.
   */
  def suggestFixes(
    unhandledPaths: List[String],
    unprovidedPaths: List[String]
  ): List[String] = {
    val suggestions = scala.collection.mutable.ListBuffer[String]()

    val pairedPaths = unhandledPaths.flatMap { handled =>
      unprovidedPaths.find(provided => areSimilar(handled, provided)).map(handled -> _)
    }

    pairedPaths.foreach { case (from, to) =>
      suggestions += s".renameField(_.$from, _.$to)"
    }

    val remainingUnhandled  = unhandledPaths.diff(pairedPaths.map(_._1))
    val remainingUnprovided = unprovidedPaths.diff(pairedPaths.map(_._2))

    remainingUnhandled.foreach { path =>
      suggestions += s".dropField(_.$path, defaultValue)"
    }

    remainingUnprovided.foreach { path =>
      suggestions += s".addField(_.$path, defaultValue)"
    }

    suggestions.toList
  }

  private def areSimilar(a: String, b: String): Boolean = {
    val aLower = a.toLowerCase
    val bLower = b.toLowerCase
    aLower.contains(bLower) || bLower.contains(aLower) ||
    levenshteinDistance(aLower, bLower) <= 3
  }

  private def levenshteinDistance(s1: String, s2: String): Int = {
    val dp = Array.ofDim[Int](s1.length + 1, s2.length + 1)

    for (i <- 0 to s1.length) dp(i)(0) = i
    for (j <- 0 to s2.length) dp(0)(j) = j

    for (i <- 1 to s1.length; j <- 1 to s2.length) {
      val cost = if (s1(i - 1) == s2(j - 1)) 0 else 1
      dp(i)(j) = math.min(
        math.min(dp(i - 1)(j) + 1, dp(i)(j - 1) + 1),
        dp(i - 1)(j - 1) + cost
      )
    }

    dp(s1.length)(s2.length)
  }
}
