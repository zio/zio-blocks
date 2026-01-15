package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.migration.MigrationAction._
import scala.annotation.unused

class MigrationBuilder[A, B](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction] = Vector.empty
) {

  private def toOptic(nodes: IndexedSeq[DynamicOptic.Node]): DynamicOptic =
    DynamicOptic(nodes.toVector)

  private def extractFieldName(path: DynamicOptic): String =
    path.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => "unknown"
    }

  def addField[T](target: ToDynamicOptic[B, T], default: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = target.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ AddField(toOptic(path.nodes), default))
  }

  def addField[T](target: ToDynamicOptic[B, T], value: Any): MigrationBuilder[A, B] = {
    val dv = value match {
      case i: Int     => DynamicValue.Primitive(PrimitiveValue.Int(i))
      case s: String  => DynamicValue.Primitive(PrimitiveValue.String(s))
      case b: Boolean => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
      case other      => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
    }
    this.addField(target, SchemaExpr.Constant(dv))
  }

  def dropField[T](source: ToDynamicOptic[A, T], defaultExpr: Option[SchemaExpr[_]] = None)(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] = {
    val path          = source.apply()
    val actualDefault = defaultExpr.getOrElse(SchemaExpr.DefaultValue(schema))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ DropField(toOptic(path.nodes), actualDefault))
  }

  def renameField[T1, T2](from: ToDynamicOptic[A, T1], to: ToDynamicOptic[B, T2]): MigrationBuilder[A, B] = {
    val fromPath = from.apply()
    val toPath   = to.apply()
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ Rename(toOptic(fromPath.nodes), extractFieldName(toPath))
    )
  }

  def transformField[T1, T2](
    from: ToDynamicOptic[A, T1],
    @unused target: ToDynamicOptic[B, T2],
    transform: SchemaExpr[_]
  ): MigrationBuilder[A, B] = {
    val fromPath = from.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformValue(toOptic(fromPath.nodes), transform))
  }

  def mandateField[T](
    from: ToDynamicOptic[A, Option[T]],
    @unused target: ToDynamicOptic[B, T],
    default: SchemaExpr[_]
  ): MigrationBuilder[A, B] = {
    val path = from.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Mandate(toOptic(path.nodes), default))
  }

  def optionalizeField[T](
    source: ToDynamicOptic[A, T],
    @unused target: ToDynamicOptic[B, Option[T]]
  ): MigrationBuilder[A, B] = {
    val path = source.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Optionalize(toOptic(path.nodes)))
  }

  def changeFieldType[T1, T2](
    from: ToDynamicOptic[A, T1],
    @unused target: ToDynamicOptic[B, T2],
    converter: SchemaExpr[_]
  ): MigrationBuilder[A, B] = {
    val path = from.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ ChangeType(toOptic(path.nodes), converter))
  }

  def joinFields[T](
    sources: Vector[ToDynamicOptic[A, Any]],
    target: ToDynamicOptic[B, T],
    combiner: SchemaExpr[_]
  ): MigrationBuilder[A, B] = {
    val targetPath  = target.apply()
    val sourcePaths = sources.map(_.apply())
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ Join(toOptic(targetPath.nodes), sourcePaths.map(p => toOptic(p.nodes)), combiner)
    )
  }

  def splitField[T](
    source: ToDynamicOptic[A, T],
    targets: Vector[ToDynamicOptic[B, Any]],
    splitter: SchemaExpr[_]
  ): MigrationBuilder[A, B] = {
    val sourcePath  = source.apply()
    val targetPaths = targets.map(_.apply())
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ Split(toOptic(sourcePath.nodes), targetPaths.map(p => toOptic(p.nodes)), splitter)
    )
  }

  def transformElements[T](at: ToDynamicOptic[A, Vector[T]], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path  = at.apply()
    val optic = toOptic(path.nodes :+ DynamicOptic.Node.Elements)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformElements(optic, transform))
  }

  def transformKeys[K, V](at: ToDynamicOptic[A, Map[K, V]], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = at.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformKeys(toOptic(path.nodes), transform))
  }

  def transformValues[K, V](at: ToDynamicOptic[A, Map[K, V]], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = at.apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformValues(toOptic(path.nodes), transform))
  }

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ RenameCase(DynamicOptic.root, from, to))

  def transformCase[CaseA, CaseB](
    @unused at: String,
    @unused caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformCase(DynamicOptic.root, Vector.empty))

  private def validateFullMigration(): Unit =
    if (actions.isEmpty && sourceSchema != targetSchema) {
      throw new RuntimeException("Incomplete Migration: Actions are required when schemas differ.")
    }

  def build: Migration[A, B] = {
    validateFullMigration()
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {
  def make[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)
}
