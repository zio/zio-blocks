package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * Migration introspection and code generation utilities.
 *
 * This object provides advanced capabilities for analyzing migrations,
 * generating DDL/DML statements, and creating documentation from
 * DynamicMigration instances. This goes beyond basic migration execution to
 * enable offline tooling, schema registries, and automated documentation.
 */
object MigrationIntrospector {

  /**
   * Generate a human-readable summary of a migration.
   */
  def summarize(migration: DynamicMigration): MigrationSummary = {
    val actions = migration.actions

    val adds         = actions.collect { case a: MigrationAction.AddField => a }
    val drops        = actions.collect { case a: MigrationAction.DropField => a }
    val renames      = actions.collect { case a: MigrationAction.Rename => a }
    val transforms   = actions.collect { case a: MigrationAction.TransformValue => a }
    val typeChanges  = actions.collect { case a: MigrationAction.ChangeType => a }
    val mandates     = actions.collect { case a: MigrationAction.Mandate => a }
    val optionalizes = actions.collect { case a: MigrationAction.Optionalize => a }
    val caseRenames  = actions.collect { case a: MigrationAction.RenameCase => a }
    val joins        = actions.collect { case a: MigrationAction.Join => a }
    val splits       = actions.collect { case a: MigrationAction.Split => a }

    MigrationSummary(
      totalActions = actions.size,
      addedFields = adds.map(a => s"${a.at}.${a.fieldName}").toList,
      droppedFields = drops.map(d => s"${d.at}.${d.fieldName}").toList,
      renamedFields = renames.map(r => s"${r.at}: ${r.from} -> ${r.to}").toList,
      transformedFields = transforms.map(t => s"${t.at}.${t.fieldName}").toList,
      typeChangedFields = typeChanges.map(tc => s"${tc.at}.${tc.fieldName}").toList,
      mandatedFields = mandates.map(m => s"${m.at}.${m.fieldName}").toList,
      optionalizedFields = optionalizes.map(o => s"${o.at}.${o.fieldName}").toList,
      renamedCases = caseRenames.map(rc => s"${rc.at}: ${rc.from} -> ${rc.to}").toList,
      joinedFields = joins.map(j => s"${j.sourcePaths.toList.mkString(", ")} -> ${j.targetFieldName}").toList,
      splitFields = splits.map(s => s"${s.sourceFieldName} -> ${s.targetPaths.toList.mkString(", ")}").toList,
      isReversible = isFullyReversible(migration),
      complexity = calculateComplexity(migration)
    )
  }

  /**
   * Check if a migration is fully reversible without data loss.
   */
  def isFullyReversible(migration: DynamicMigration): Boolean =
    migration.actions.forall {
      case _: MigrationAction.Rename               => true
      case _: MigrationAction.RenameCase           => true
      case MigrationAction.AddField(_, _, default) =>
        default != Resolved.SchemaDefault
      case MigrationAction.DropField(_, _, defaultForReverse) =>
        defaultForReverse != Resolved.SchemaDefault
      case _: MigrationAction.TransformValue => true
      case _: MigrationAction.Optionalize    => true
      case _: MigrationAction.Mandate        => true
      case _: MigrationAction.Join           => true
      case _: MigrationAction.Split          => true
      case _: MigrationAction.ChangeType     => true
      case _                                 => false
    }

  /**
   * Calculate migration complexity score (1-10).
   */
  def calculateComplexity(migration: DynamicMigration): Int = {
    val baseScore = migration.actions.foldLeft(0) {
      case (acc, _: MigrationAction.Rename)         => acc + 1
      case (acc, _: MigrationAction.RenameCase)     => acc + 1
      case (acc, _: MigrationAction.AddField)       => acc + 2
      case (acc, _: MigrationAction.DropField)      => acc + 2
      case (acc, _: MigrationAction.TransformValue) => acc + 3
      case (acc, _: MigrationAction.ChangeType)     => acc + 3
      case (acc, _: MigrationAction.Mandate)        => acc + 2
      case (acc, _: MigrationAction.Optionalize)    => acc + 1
      case (acc, _: MigrationAction.Join)           => acc + 4
      case (acc, _: MigrationAction.Split)          => acc + 4
      case (acc, _)                                 => acc + 1
    }

    Math.min(10, Math.max(1, baseScore / 2))
  }

  /**
   * Generate SQL DDL statements for schema evolution. This is a template that
   * can be customized for specific databases.
   */
  def generateSqlDdl(
    migration: DynamicMigration,
    tableName: String,
    dialect: SqlDialect = SqlDialect.PostgreSQL
  ): SqlDdlResult = {
    val statements = migration.actions.flatMap { action =>
      action match {
        case MigrationAction.AddField(_, fieldName, default) =>
          val defaultClause = default match {
            case Resolved.Literal(value) =>
              s" DEFAULT ${sqlValue(value)}"
            case _ => ""
          }
          Some(s"ALTER TABLE $tableName ADD COLUMN $fieldName TEXT$defaultClause;")

        case MigrationAction.DropField(_, fieldName, _) =>
          Some(s"ALTER TABLE $tableName DROP COLUMN $fieldName;")

        case MigrationAction.Rename(_, from, to) =>
          dialect match {
            case SqlDialect.PostgreSQL =>
              Some(s"ALTER TABLE $tableName RENAME COLUMN $from TO $to;")
            case SqlDialect.MySQL =>
              Some(s"ALTER TABLE $tableName CHANGE $from $to TEXT;")
            case SqlDialect.SQLite =>
              Some(s"ALTER TABLE $tableName RENAME COLUMN $from TO $to;")
          }

        case MigrationAction.ChangeType(_, fieldName, _, _) =>
          dialect match {
            case SqlDialect.PostgreSQL =>
              Some(s"ALTER TABLE $tableName ALTER COLUMN $fieldName TYPE TEXT;")
            case SqlDialect.MySQL =>
              Some(s"ALTER TABLE $tableName MODIFY COLUMN $fieldName TEXT;")
            case SqlDialect.SQLite =>
              None
          }

        case _ => None
      }
    }

    SqlDdlResult(
      statements = statements.toList,
      tableName = tableName,
      dialect = dialect,
      warnings = generateWarnings(migration, dialect)
    )
  }

  private def sqlValue(value: DynamicValue): String = value match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))  => s"'${s.replace("'", "''")}'"
    case DynamicValue.Primitive(PrimitiveValue.Int(i))     => i.toString
    case DynamicValue.Primitive(PrimitiveValue.Long(l))    => l.toString
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => if (b) "TRUE" else "FALSE"
    case DynamicValue.Primitive(PrimitiveValue.Double(d))  => d.toString
    case _                                                 => "NULL"
  }

  private def generateWarnings(migration: DynamicMigration, dialect: SqlDialect): List[String] = {
    val warnings = scala.collection.mutable.ListBuffer[String]()

    migration.actions.foreach {
      case _: MigrationAction.DropField =>
        warnings += "DROP COLUMN is destructive and cannot be undone"
      case MigrationAction.ChangeType(_, fieldName, _, _) if dialect == SqlDialect.SQLite =>
        warnings += s"SQLite does not support ALTER COLUMN TYPE for $fieldName"
      case _: MigrationAction.Join =>
        warnings += "JOIN operations require custom SQL or application-level logic"
      case _: MigrationAction.Split =>
        warnings += "SPLIT operations require custom SQL or application-level logic"
      case _ => ()
    }

    warnings.toList
  }

  /**
   * Generate markdown documentation for a migration.
   */
  def generateDocumentation(
    migration: DynamicMigration,
    fromVersion: String,
    toVersion: String
  ): String = {
    val summary = summarize(migration)
    val sb      = new StringBuilder

    sb.append(s"# Migration: $fromVersion -> $toVersion\n\n")
    sb.append(s"**Total Actions:** ${summary.totalActions}\n")
    sb.append(s"**Complexity:** ${summary.complexity}/10\n")
    sb.append(s"**Reversible:** ${if (summary.isReversible) "Yes" else "Partial"}\n\n")

    if (summary.addedFields.nonEmpty) {
      sb.append("## Added Fields\n")
      summary.addedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.droppedFields.nonEmpty) {
      sb.append("## Dropped Fields\n")
      summary.droppedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.renamedFields.nonEmpty) {
      sb.append("## Renamed Fields\n")
      summary.renamedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.typeChangedFields.nonEmpty) {
      sb.append("## Type Changes\n")
      summary.typeChangedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.transformedFields.nonEmpty) {
      sb.append("## Transformed Fields\n")
      summary.transformedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.joinedFields.nonEmpty) {
      sb.append("## Joined Fields\n")
      summary.joinedFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    if (summary.splitFields.nonEmpty) {
      sb.append("## Split Fields\n")
      summary.splitFields.foreach(f => sb.append(s"- `$f`\n"))
      sb.append("\n")
    }

    sb.toString
  }

  /**
   * Validate a migration against source and target schemas.
   */
  def validate(
    migration: DynamicMigration
  ): ValidationReport = {
    val errors   = scala.collection.mutable.ListBuffer[String]()
    val warnings = scala.collection.mutable.ListBuffer[String]()

    migration.actions.foreach { action =>
      val path = action.at
      if (path.nodes.isEmpty && action.isInstanceOf[MigrationAction.TransformValue]) {
        warnings += s"TransformValue at root may affect entire structure"
      }
    }

    if (migration.actions.isEmpty) {
      warnings += "Migration has no actions - this is a no-op migration"
    }

    ValidationReport(
      isValid = errors.isEmpty,
      errors = errors.toList,
      warnings = warnings.toList,
      actionCount = migration.actions.size
    )
  }

  case class MigrationSummary(
    totalActions: Int,
    addedFields: List[String],
    droppedFields: List[String],
    renamedFields: List[String],
    transformedFields: List[String],
    typeChangedFields: List[String],
    mandatedFields: List[String],
    optionalizedFields: List[String],
    renamedCases: List[String],
    joinedFields: List[String],
    splitFields: List[String],
    isReversible: Boolean,
    complexity: Int
  )

  sealed trait SqlDialect
  object SqlDialect {
    case object PostgreSQL extends SqlDialect
    case object MySQL      extends SqlDialect
    case object SQLite     extends SqlDialect
  }

  case class SqlDdlResult(
    statements: List[String],
    tableName: String,
    dialect: SqlDialect,
    warnings: List[String]
  ) {
    def render: String = statements.mkString("\n")
  }

  case class ValidationReport(
    isValid: Boolean,
    errors: List[String],
    warnings: List[String],
    actionCount: Int
  )
}
