package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.migration.macros.AccessorMacros
import zio.blocks.schema.migration.MigrationAction._
import scala.annotation.unused

class MigrationBuilder[A, B](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction] = Vector.empty
) {

  private def toOptic(nodes: IndexedSeq[DynamicOptic.Node]): DynamicOptic = {
    DynamicOptic(nodes.toVector)
  }

  private def extractFieldName(path: DynamicOptic): String =
    path.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => "unknown"
    }

  private def toConstantExpr(value: Any): SchemaExpr[_] = {
    val dv = value match {
      case i: Int     => DynamicValue.Primitive(PrimitiveValue.Int(i))
      case s: String  => DynamicValue.Primitive(PrimitiveValue.String(s))
      case b: Boolean => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
      case d: Double  => DynamicValue.Primitive(PrimitiveValue.BigDecimal(java.math.BigDecimal.valueOf(d)))
      case l: Long    => DynamicValue.Primitive(PrimitiveValue.BigDecimal(java.math.BigDecimal.valueOf(l)))
      case other      => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
    }
    SchemaExpr.Constant(dv)
  }

  inline def addField[T](inline target: B => T, default: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(target).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ AddField(toOptic(path.nodes), default))
  }

  inline def addField[T](inline target: B => T, value: Any): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(target).apply()
    val constantExpr = toConstantExpr(value) 
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ AddField(toOptic(path.nodes), constantExpr))
  }

  inline def dropField[T](
    inline source: A => T, 
    defaultExpr: Option[SchemaExpr[_]] = None
  )(implicit schema: Schema[T]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(source).apply()
    val actualDefault = defaultExpr.getOrElse(SchemaExpr.DefaultValue(schema))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ DropField(toOptic(path.nodes), actualDefault))
  }

  inline def renameField[T1, T2](inline from: A => T1, inline to: B => T2): MigrationBuilder[A, B] = {
    val fromPath = AccessorMacros.derive(from).apply()
    val toPath = AccessorMacros.derive(to).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Rename(toOptic(fromPath.nodes), extractFieldName(toPath)))
  }

  inline def transformField[T1, T2](inline from: A => T1, @unused inline to: B => T2, transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val fromPath = AccessorMacros.derive(from).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformValue(toOptic(fromPath.nodes), transform))
  }

  inline def mandateField[T](inline from: A => Option[T], @unused inline to: B => T, default: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(from).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Mandate(toOptic(path.nodes), default))
  }

  inline def optionalizeField[T](inline source: A => T, @unused inline target: B => Option[T]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(source).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Optionalize(toOptic(path.nodes)))
  }

  inline def changeFieldType[T1, T2](inline from: A => T1, @unused inline to: B => T2, converter: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(from).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ ChangeType(toOptic(path.nodes), converter))
  }

  inline def joinFields[T](inline sources: Vector[A => Any], inline target: B => T, combiner: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val targetPath = AccessorMacros.derive(target).apply()
    val sourcePaths = sources.map(s => AccessorMacros.derive(s.asInstanceOf[A => Any]).apply())
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Join(toOptic(targetPath.nodes), sourcePaths.map(p => toOptic(p.nodes)), combiner))
  }

  inline def splitField[T](inline source: A => T, inline targets: Vector[B => Any], splitter: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val sourcePath = AccessorMacros.derive(source).apply()
    val targetPaths = targets.map(t => AccessorMacros.derive(t.asInstanceOf[B => Any]).apply())
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Split(toOptic(sourcePath.nodes), targetPaths.map(p => toOptic(p.nodes)), splitter))
  }

  inline def transformElements[T](inline at: A => Vector[T], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(at).apply()
    val optic = toOptic(path.nodes :+ DynamicOptic.Node.Elements)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformElements(optic, transform))
  }

  inline def transformKeys[K, V](inline at: A => Map[K, V], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(at).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformKeys(toOptic(path.nodes), transform))
  }

  inline def transformValues[K, V](inline at: A => Map[K, V], transform: SchemaExpr[_]): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(at).apply()
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformValues(toOptic(path.nodes), transform))
  }

  def renameCase(from: String, to: String): MigrationBuilder[A, B] = {
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ RenameCase(DynamicOptic.root, from, to))
  }

  def transformCase[CaseA, CaseB](@unused at: String, @unused caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]): MigrationBuilder[A, B] = {
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformCase(DynamicOptic.root, Vector.empty))
  }

  private def validateFullMigration(): Unit = {
    if (actions.isEmpty && sourceSchema != targetSchema) {
      throw new RuntimeException("Incomplete Migration: Actions are required when Source and Target schemas differ.")
    }
  }

  def build: Migration[A, B] = {
    validateFullMigration()
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  def buildPartial: Migration[A, B] = {
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }
}

object MigrationBuilder {
  def make[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)
}